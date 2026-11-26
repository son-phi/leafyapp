"""
Đưa dữ liệu người dùng về đúng schema thiết kế (+ trường mở rộng tối thiểu của app).

    python normalize_user_data.py --key <sa.json> [--dry-run]

users/{uid}                 email, fullName, avatarUrl, role, status, fcmTokens, createdAt, lastLoginAt
                            (+ searchName, searchPrefixes – admin dùng để tìm). Bỏ displayName, photoUrl, firstName,
                            lastName, lastUpdated (tên/ảnh trống thì lấy từ Firebase Auth).
plants/{id}                 userId, speciesId, name, imageUrl, plantedDate, useSpeciesThresholds, customThreshold,
                            createdAt + [mở rộng] healthStatus, lastCheckAt
careSchedules/{id}          plantId, taskType, startAt (kèm giờ nhắc), repeatIntervalDays, note, active
                            + [mở rộng] title, nextDueAt, sourceDiagnosisId, occurrences
careLogs/{id}               plantId, careScheduleId, taskType, performedAt, note + [mở rộng] title
diagnoses/{id}              plantId, speciesId, diseaseId, imageUrl, confidence, severity, lesionAreaPercent,
                            treatmentSnapshot, createdAt + [mở rộng] aiLabel
identifications/{id}        userId, speciesId, imageUrl, aiLabel, confidence, topCandidates, createdAt
Không đụng: gardens, devices, iot*, users/{uid}/user_plants (IoT đang liên kết), lockHistory.
"""
import argparse
import datetime as dt
import os
import re
import sys
import unicodedata

KEEP = {
    "users": {"email", "fullName", "avatarUrl", "role", "status", "fcmTokens", "createdAt", "lastLoginAt",
              "searchName", "searchPrefixes"},
    "plants": {"userId", "speciesId", "name", "imageUrl", "plantedDate", "useSpeciesThresholds", "customThreshold",
               "createdAt", "healthStatus", "lastCheckAt"},
    "careSchedules": {"plantId", "taskType", "startAt", "repeatIntervalDays", "note", "active",
                      "title", "nextDueAt", "sourceDiagnosisId", "occurrences"},
    "careLogs": {"plantId", "careScheduleId", "taskType", "performedAt", "note", "title"},
    "diagnoses": {"plantId", "speciesId", "diseaseId", "imageUrl", "confidence", "severity", "lesionAreaPercent",
                  "treatmentSnapshot", "createdAt", "aiLabel"},
    "identifications": {"userId", "speciesId", "imageUrl", "aiLabel", "confidence", "topCandidates", "createdAt"},
}
THRESHOLD_KEYS = ["soilMoistureMin", "soilMoistureMax", "temperatureMin", "temperatureMax",
                  "airHumidityMin", "airHumidityMax", "lightMin", "lightMax"]


def normalize_search(value):
    v = unicodedata.normalize("NFD", value or "")
    v = "".join(c for c in v if unicodedata.category(c) != "Mn").replace("đ", "d").replace("Đ", "D")
    return re.sub(r"\s+", " ", v.lower()).strip()


def search_prefixes(*names):
    out = []
    for n in names:
        v = normalize_search(n)[:40]
        for i in range(1, len(v) + 1):
            if v[:i] not in out:
                out.append(v[:i])
    return out


def with_time(ts, hhmm):
    """Gộp giờ nhắc (timeOfDay cũ) vào startAt theo giờ Việt Nam."""
    if not ts or not hhmm:
        return ts
    tz = dt.timezone(dt.timedelta(hours=7))
    h, m = (int(x) for x in hhmm.split(":")[:2])
    local = ts.astimezone(tz).replace(hour=h, minute=m, second=0, microsecond=0)
    return local.astimezone(dt.timezone.utc)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    import firebase_admin
    from firebase_admin import auth, credentials, firestore
    firebase_admin.initialize_app(credentials.Certificate(a.key))
    db = firestore.client()
    writes = []   # (ref, data)

    def clean(kind, data, extra=None):
        out = {k: v for k, v in data.items() if k in KEEP[kind]}
        out.update(extra or {})
        return out

    for u in db.collection("users").stream():
        d = u.to_dict() or {}
        try:
            au = auth.get_user(u.id)
        except Exception:
            au = None
        full = d.get("fullName") or d.get("displayName") or (au.display_name if au else "") or ""
        avatar = d.get("avatarUrl") or d.get("photoUrl") or (au.photo_url if au else "") or ""
        writes.append((u.reference, clean("users", d, {
            "fullName": full, "avatarUrl": avatar, "role": d.get("role", "user"), "status": d.get("status", "active"),
            "fcmTokens": d.get("fcmTokens", []), "email": d.get("email") or (au.email if au else ""),
            "searchName": normalize_search(full or d.get("email", "")),
            "searchPrefixes": search_prefixes(full, d.get("email", ""))})))

        for p in u.reference.collection("plants").stream():
            pd = p.to_dict() or {}
            ct = pd.get("customThreshold")
            if isinstance(ct, dict):
                ct = {k: float(ct.get(k, 0)) for k in THRESHOLD_KEYS}
            writes.append((p.reference, clean("plants", pd, {"customThreshold": ct, "userId": pd.get("userId") or u.id})))

            for s in p.reference.collection("careSchedules").stream():
                sd = s.to_dict() or {}
                treatment = bool(sd.get("sourceDiagnosisId")) or sd.get("kind") == "TREATMENT"
                status = sd.get("status", "ACTIVE")
                active = bool(sd.get("active", True)) and status not in ("DONE", "SUPERSEDED")
                next_due = sd.get("nextDueAt")
                if status == "PAUSED" and sd.get("pausedUntil"):      # tạm dừng có hạn → dời hạn kế tiếp
                    active, next_due = True, sd["pausedUntil"]
                extra = {"active": active, "nextDueAt": next_due,
                         "startAt": with_time(sd.get("startAt"), sd.get("timeOfDay"))}
                if treatment:
                    extra["occurrences"] = max(int(sd.get("occurrences") or 1), 1)
                data = clean("careSchedules", sd, extra)
                if not treatment:
                    data.pop("occurrences", None)
                    data.pop("sourceDiagnosisId", None)
                writes.append((s.reference, data))

            for l in p.reference.collection("careLogs").stream():
                writes.append((l.reference, clean("careLogs", l.to_dict() or {})))

            for g in p.reference.collection("diagnoses").stream():
                gd = g.to_dict() or {}
                snap = dict(gd.get("treatmentSnapshot") or {})
                if gd.get("scheduleChanges"):
                    snap["scheduleChanges"] = gd["scheduleChanges"]
                if gd.get("note"):
                    snap["modelNote"] = gd["note"]
                writes.append((g.reference, clean("diagnoses", gd, {"treatmentSnapshot": snap or None})))

        for i in u.reference.collection("identifications").stream():
            idd = i.to_dict() or {}
            writes.append((i.reference, clean("identifications", idd, {
                "speciesId": idd.get("selectedSpeciesId") or idd.get("speciesId"), "userId": idd.get("userId") or u.id})))

    print(f"Sẽ ghi lại {len(writes)} tài liệu (set, bỏ trường ngoài schema)")
    if a.dry_run:
        for ref, data in writes[:6]:
            print(" ", ref.path, sorted(data))
        return
    for i in range(0, len(writes), 400):
        b = db.batch()
        for ref, data in writes[i:i + 400]:
            b.set(ref, data)
        b.commit()
    print("Xong")


if __name__ == "__main__":
    main()
