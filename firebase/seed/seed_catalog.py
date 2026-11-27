"""
Seed danh mục `species` + `diseases` ĐÚNG SCHEMA THIẾT KẾ / WEB ADMIN, kèm trường mở rộng tối thiểu của app.

    python seed_catalog.py --export out.json          # xem trước, không ghi
    python seed_catalog.py --key <service_account.json>

species/{id}  (admin: shared/catalog.ts SpeciesSchema)
  commonName, scientificName, aiLabel, imageUrl, imagePath, description,
  lightNeed LOW|MEDIUM|HIGH|DIRECT, wateringCycleDays, fertilizingCycleDays (>0), threshold (đúng 8 trường)
  + searchName, searchPrefixes, createdAt, updatedAt, updatedBy (admin dùng để tìm/sắp xếp)
  [mở rộng] careGuide, careTemplates, treatmentCautions, diseaseOverrides
diseases/{aiLabel}  (admin: DiseaseSchema)
  nameVi, scientificName, aiLabel, imageUrl, imagePath, affectedSpeciesIds, causes, symptoms,
  treatmentMild, treatmentModerate, treatmentSevere, prevention  (văn bản nhiều dòng)
  + searchName, createdAt, updatedAt, updatedBy
  [mở rộng] plans, requiresPesticide, safetyNote

Ghi bằng set() (không merge) để bỏ các trường cũ không còn trong schema; imageUrl đang có trên Firestore được giữ.
Xóa collection `careRules` (đã gộp vào species.diseaseOverrides).
"""
import argparse
import json
import os
import re
import sys
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import disease_catalog as dc  # noqa: E402
import species_catalog as sc  # noqa: E402
import treatment_plans as tp  # noqa: E402

SPECIES = getattr(sc, "SPECIES", None) or getattr(sc, "CATALOG")
LIGHT_MAP = {"LOW": "LOW", "MEDIUM": "MEDIUM", "BRIGHT_INDIRECT": "HIGH", "FULL_SUN": "DIRECT"}
THRESHOLD_KEYS = ["soilMoistureMin", "soilMoistureMax", "temperatureMin", "temperatureMax",
                  "airHumidityMin", "airHumidityMax", "lightMin", "lightMax"]
GUIDE_KEYS = ["otherNames", "englishName", "synonyms", "family", "origin", "careLevel", "growthRate", "matureSize",
              "lightDescription", "wateringDescription", "fertilizerDescription", "soilDescription",
              "temperatureDescription", "humidityDescription", "toxicity", "propagation", "commonProblems",
              "careNotes", "seasonalNotes"]


# ---- giống hệt shared/normalize.ts của web admin ----
def normalize_search(value: str) -> str:
    v = unicodedata.normalize("NFD", value or "")
    v = "".join(c for c in v if unicodedata.category(c) != "Mn").replace("đ", "d").replace("Đ", "D")
    return re.sub(r"\s+", " ", v.lower()).strip()


def search_prefixes(*names) -> list:
    out = []
    for name in names:
        v = normalize_search(name or "")[:40]
        for i in range(1, len(v) + 1):
            if v[:i] not in out:
                out.append(v[:i])
    return out


def text(lines) -> str:
    return "\n".join(f"• {x}" for x in lines if x)


def species_docs(images):
    pairs = tp.build()
    docs = []
    for s in SPECIES:
        mine = [p for p in pairs if p["speciesId"] == s["id"]]
        # lưu ý chung của loài: giống nhau ở mọi bệnh → treatmentCautions (ghi 1 lần)
        common_info = set.intersection(*[set(p["plans"]["MILD"]["careInfo"]) for p in mine]) if mine else set()
        cautions = []
        for p in mine:
            for x in p["avoid"] + sorted(common_info):
                if x not in cautions:
                    cautions.append(x)
        overrides = {}
        for p in mine:
            plans = {}
            for lv, plan in p["plans"].items():
                info = [x for x in plan["careInfo"] if x not in common_info]
                if info or plan["tasks"] or plan["adjustments"]:
                    plans[lv] = {"careInfo": info, "tasks": plan["tasks"], "adjustments": plan["adjustments"]}
            if plans or p["likelyCauses"] or p["isNormalCondition"]:
                overrides[p["diseaseId"]] = {"likelyCauses": p["likelyCauses"], "isNormalCondition": p["isNormalCondition"],
                                             "normalNote": p["normalNote"], "plans": plans}
        no_fert = s["fertilizingCycleDays"] <= 0
        guide = {k: s[k] for k in GUIDE_KEYS}
        guide["lightLabel"] = sc.LIGHT_NEED_VI[s["lightNeed"]]
        guide["noFertilizer"] = no_fert
        docs.append({
            "id": s["id"],
            "commonName": s["commonName"], "scientificName": s["scientificName"], "aiLabel": s["aiLabel"],
            "imageUrl": images.get(s["id"]), "imagePath": "", "description": s["description"],
            "lightNeed": LIGHT_MAP[s["lightNeed"]],
            "wateringCycleDays": s["wateringCycleDays"],
            "fertilizingCycleDays": 365 if no_fert else s["fertilizingCycleDays"],
            "threshold": {k: float(s["threshold"][k]) for k in THRESHOLD_KEYS},
            "searchName": normalize_search(s["commonName"]),
            "searchPrefixes": search_prefixes(s["commonName"], s["scientificName"]),
            "careGuide": guide,
            "careTemplates": s["careTemplates"],
            "treatmentCautions": cautions,
            "diseaseOverrides": overrides,
        })
    return docs


def disease_docs():
    pairs = tp.build()
    docs = []
    for d in dc.DISEASES:
        did = d["aiLabel"]
        affected = [p["speciesId"] for p in pairs if p["diseaseId"] == did and p["likelihood"] == "COMMON"]
        plans = {lv: tp.generic_plan(did, lv) for lv in tp.LEVELS}
        docs.append({
            "id": did,
            "nameVi": d["nameVi"], "scientificName": d["scientificName"] or None, "aiLabel": did,
            "imageUrl": None, "imagePath": "",
            "affectedSpeciesIds": affected or sorted({p["speciesId"] for p in pairs if p["diseaseId"] == did and
                                                      any(v["tasks"] for v in p["plans"].values())})[:20],
            "causes": text(d["causes"]), "symptoms": text(d["symptoms"]),
            "treatmentMild": text(plans["MILD"]["careInfo"]),
            "treatmentModerate": text(plans["MODERATE"]["careInfo"]),
            "treatmentSevere": text(plans["SEVERE"]["careInfo"]),
            "prevention": text(d["prevention"]) or None,
            "searchName": normalize_search(d["nameVi"]),
            "plans": plans,
            "requiresPesticide": d["requiresPesticide"],
            "safetyNote": dc.SAFETY_NOTE if d["requiresPesticide"] else "",
        })
    return docs


def validate(species, diseases):
    ids = {s["id"] for s in species}
    for s in species:
        t = s["threshold"]
        assert list(t) == THRESHOLD_KEYS, s["id"]
        assert t["soilMoistureMin"] <= t["soilMoistureMax"] and t["temperatureMin"] <= t["temperatureMax"]
        assert t["airHumidityMin"] <= t["airHumidityMax"] and t["lightMin"] <= t["lightMax"]
        assert s["lightNeed"] in ("LOW", "MEDIUM", "HIGH", "DIRECT") and s["fertilizingCycleDays"] > 0
        assert set(s["diseaseOverrides"]) <= {d["aiLabel"] for d in diseases}
    for d in diseases:
        assert d["affectedSpeciesIds"] and set(d["affectedSpeciesIds"]) <= ids, d["id"]
        for k in ("causes", "symptoms", "treatmentMild", "treatmentModerate", "treatmentSevere"):
            assert isinstance(d[k], str) and d[k], (d["id"], k)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--key")
    ap.add_argument("--export")
    a = ap.parse_args()
    dc.validate(); tp.validate()

    db = None
    images = {}
    if a.key:
        import firebase_admin
        from firebase_admin import credentials, firestore
        firebase_admin.initialize_app(credentials.Certificate(a.key))
        db = firestore.client()
        images = {d.id: (d.to_dict() or {}).get("imageUrl") for d in db.collection("species").stream()}

    species, diseases = species_docs(images), disease_docs()
    validate(species, diseases)
    n_over = sum(len(s["diseaseOverrides"]) for s in species)
    print(f"species {len(species)} (diseaseOverrides: {n_over} cặp), diseases {len(diseases)}")
    if a.export:
        json.dump({"species": species, "diseases": diseases}, open(a.export, "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
        print("Đã xuất", a.export)
        return
    if db is None:
        sys.exit("Cần --key hoặc --export")

    from firebase_admin import firestore
    now = firestore.SERVER_TIMESTAMP
    existing = {("species", d.id): d.to_dict().get("createdAt") for d in db.collection("species").stream()}
    existing.update({("diseases", d.id): d.to_dict().get("createdAt") for d in db.collection("diseases").stream()})
    batch = db.batch()
    for col, docs in (("species", species), ("diseases", diseases)):
        for doc in docs:
            data = {k: v for k, v in doc.items() if k != "id"}
            data.update(updatedAt=now, updatedBy="seed", createdAt=existing.get((col, doc["id"])) or now)
            batch.set(db.collection(col).document(doc["id"]), data)
    batch.commit()
    old = list(db.collection("careRules").select([]).stream())
    for i in range(0, len(old), 400):
        b = db.batch()
        for d in old[i:i + 400]:
            b.delete(d.reference)
        b.commit()
    print(f"Đã ghi {len(species)} species, {len(diseases)} diseases; xóa {len(old)} careRules")


if __name__ == "__main__":
    main()
