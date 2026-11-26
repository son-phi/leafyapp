"""
Chuyển dữ liệu cũ của người dùng sang cấu trúc mới (giữ nguyên id tài liệu để app khử trùng lặp):

  users/{uid}/user_plants/{id}   → users/{uid}/plants/{id}
  users/{uid}/care_tasks/{id}    → users/{uid}/plants/{userPlantId}/careSchedules/{id}
  users/{uid}/task_history/{id}  → users/{uid}/plants/{plantId}/careLogs/{id}
  users/{uid}/disease_logs/{id}  → users/{uid}/plants/{plantId}/diagnoses/{id}

Bản ghi mồ côi (lịch của cây đã xóa, lịch sử của lịch đã xóa) không chuyển, chỉ báo cáo.
Dữ liệu cũ KHÔNG bị xóa (trừ khi --delete-legacy).

    python migrate_legacy.py --key <sa.json> [--dry-run] [--delete-legacy]
"""
import argparse
import datetime as dt
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import disease_catalog as dc  # noqa: E402

TZ = dt.timezone(dt.timedelta(hours=7))  # giờ Việt Nam
TYPE_MAP = {"WATER": ("WATERING", "Tưới nước"), "FERTILIZER": ("FERTILIZING", "Bón phân"),
            "CUT": ("PRUNING", "Cắt tỉa"), "MIST": ("OTHER", "Phun sương"), "ROTATE": ("OTHER", "Xoay chậu")}
# tên bệnh cũ (disease_logs.diseaseName) → aiLabel mới
OLD_DISEASE = {"nâu đầu lá": "tip_margin_necrosis", "đốm nấm": "leaf_spot", "vàng lá": "chlorosis_discoloration",
               "phấn trắng": "powdery_coating", "gỉ sắt": "rust_pustules"}


def ts(ms):
    try:
        v = int(float(ms))
        return dt.datetime.fromtimestamp(v / 1000, dt.timezone.utc) if v > 0 else None
    except (TypeError, ValueError):
        return None


def direct(url):
    if not url:
        return None
    m = re.search(r"/d/([\w-]+)", url) or re.search(r"[?&]id=([\w-]+)", url)
    return f"https://drive.google.com/uc?export=view&id={m.group(1)}" if m and "drive.google" in url else url


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--delete-legacy", action="store_true")
    a = ap.parse_args()

    import firebase_admin
    from firebase_admin import credentials, firestore
    firebase_admin.initialize_app(credentials.Certificate(a.key))
    db = firestore.client()

    species = {d.id: d.to_dict() for d in db.collection("species").stream()}
    by_legacy = {s["legacyId"]: sid for sid, s in species.items() if s.get("legacyId")}
    diseases = {d["aiLabel"]: d for d in dc.DISEASES}
    total = dict(plants=0, schedules=0, logs=0, diagnoses=0, orphans=0)

    for user in db.collection("users").stream():
        uid = user.id
        uref = user.reference
        ops = []
        legacy_refs = []

        # ---- cây ----
        plants = {}
        for snap in uref.collection("user_plants").stream():
            d = snap.to_dict() or {}
            legacy_refs.append(snap.reference)
            if d.get("gardenId"):          # cây của vườn gia đình: không đụng tới
                continue
            try:
                sid = by_legacy.get(int(d.get("plantId") or 0))
            except ValueError:
                sid = None
            sp = species.get(sid) or {}
            created = ts(d.get("dateAdded")) or dt.datetime.now(dt.timezone.utc)
            plants[snap.id] = (d.get("nickname") or sp.get("commonName") or "Cây của tôi").strip()
            ops.append((uref.collection("plants").document(snap.id), {
                "userId": uid, "speciesId": sid or "", "speciesName": sp.get("commonName", ""),
                "scientificName": sp.get("scientificName", ""), "name": plants[snap.id],
                "imageUrl": direct(d.get("imagePath")), "plantedDate": created,
                "useSpeciesThresholds": True, "customThreshold": None,
                "healthStatus": "UNKNOWN", "lastCheckAt": None, "lastDiagnosisId": None,
                "identificationId": None, "createdAt": created, "updatedAt": firestore.SERVER_TIMESTAMP,
                "migratedFrom": "user_plants",
            }))
            total["plants"] += 1

        # ---- lịch chăm sóc ----
        tasks = {}
        for snap in uref.collection("care_tasks").stream():
            d = snap.to_dict() or {}
            legacy_refs.append(snap.reference)
            pid = d.get("userPlantId")
            if pid not in plants:
                total["orphans"] += 1
                continue
            ttype, title = TYPE_MAP.get(str(d.get("type")).upper(), ("OTHER", "Chăm sóc"))
            tod = f"{int(d.get('timeHour') or 8):02d}:{int(d.get('timeMinute') or 0):02d}"
            start = ts(d.get("startDate")) or dt.datetime.now(dt.timezone.utc)
            tasks[snap.id] = (pid, ttype, title)
            ops.append((uref.collection("plants").document(pid).collection("careSchedules").document(snap.id), {
                "userId": uid, "plantId": pid, "taskType": ttype, "title": title, "startAt": start,
                "repeatIntervalDays": max(int(d.get("frequencyDays") or 1), 1), "timeOfDay": tod,
                "nextDueAt": ts(d.get("nextDueDate")) or start, "lastDoneAt": ts(d.get("lastCompletedDate")),
                "note": "", "active": str(d.get("autoReminder", True)) != "False",
                "source": "USER", "sourceDiagnosisId": None,
                "createdAt": start, "updatedAt": firestore.SERVER_TIMESTAMP, "migratedFrom": "care_tasks",
            }))
            total["schedules"] += 1

        # ---- nhật ký chăm sóc ----
        for snap in uref.collection("task_history").stream():
            d = snap.to_dict() or {}
            legacy_refs.append(snap.reference)
            t = tasks.get(d.get("taskId"))
            done = ts(d.get("completedDate"))
            if not t or not done:
                total["orphans"] += 1
                continue
            pid, ttype, title = t
            ops.append((uref.collection("plants").document(pid).collection("careLogs").document(snap.id), {
                "userId": uid, "plantId": pid, "careScheduleId": d.get("taskId"), "taskType": ttype,
                "title": title, "performedAt": done, "note": "", "diagnosisId": None,
                "createdAt": done, "migratedFrom": "task_history",
            }))
            total["logs"] += 1

        # ---- lịch sử chẩn đoán ----
        last_diag = {}
        for snap in uref.collection("disease_logs").stream():
            d = snap.to_dict() or {}
            legacy_refs.append(snap.reference)
            pid = d.get("plantId")
            label = OLD_DISEASE.get(str(d.get("diseaseName", "")).strip().lower())
            if pid not in plants:
                total["orphans"] += 1
                continue
            info = diseases.get(label)
            at = ts(d.get("timestamp")) or dt.datetime.now(dt.timezone.utc)
            sid = next((op[1]["speciesId"] for op in ops if op[0].id == pid and "speciesId" in op[1]), None)
            ops.append((uref.collection("plants").document(pid).collection("diagnoses").document(snap.id), {
                "userId": uid, "plantId": pid, "speciesId": sid, "diseaseId": label,
                "aiLabel": label or "", "imageUrl": None,
                # bản cũ không lưu độ tin cậy/mức độ → không đáng tin, không tính xu hướng (QT02)
                "confidence": 0.0, "severity": "NONE", "modelSeverityLevel": None, "lesionAreaPercent": 0.0,
                "treatmentSnapshot": {
                    "diseaseId": label, "diseaseName": info["nameVi"] if info else d.get("diseaseName", ""),
                    "speciesId": sid, "severity": "NONE", "likelyCauses": [],
                    "careInfo": info["treatmentMild"] if info else [], "tasks": [], "avoid": [],
                    "prevention": info["prevention"] if info else [], "isNormalCondition": False, "normalNote": "",
                    "safetyNote": dc.SAFETY_NOTE if info and info["requiresPesticide"] else "",
                },
                "scheduleChanges": [], "treatmentScheduleIds": [], "previousDiagnosisId": None,
                "isReliable": False, "trend": None, "source": "FROM_PLANT", "modelVersion": None,
                "note": f"Chuyển từ lịch sử cũ: {d.get('diseaseName', '')}", "createdAt": at,
                "migratedFrom": "disease_logs",
            }))
            if at >= last_diag.get(pid, (at, None))[0]:
                last_diag[pid] = (at, snap.id)
            total["diagnoses"] += 1
        for pid, (at, did) in last_diag.items():
            for op in ops:
                if op[0].id == pid and "speciesId" in op[1]:
                    op[1]["lastDiagnosisId"] = did   # healthStatus giữ UNKNOWN vì bản ghi không đáng tin

        print(f"{uid}: {sum(1 for o in ops if o[1].get('migratedFrom') == 'user_plants')} cây, "
              f"{len(ops)} tài liệu mới")
        if a.dry_run:
            continue
        for i in range(0, len(ops), 400):
            batch = db.batch()
            for ref, data in ops[i:i + 400]:
                batch.set(ref, data, merge=True)
            batch.commit()
        if a.delete_legacy:
            for ref in legacy_refs:
                ref.delete()

    print("Tổng:", total)


if __name__ == "__main__":
    main()
