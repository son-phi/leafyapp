"""
Kế hoạch điều trị theo  LOÀI × BỆNH × MỨC ĐỘ.

  diseases/{diseaseId}.plans[LEVEL]                    phần CHUNG của bệnh ở mức đó (mọi loài)
  careRules/{speciesId}__{diseaseId}             phần RIÊNG của loài với bệnh đó
      .plans[LEVEL]  {careInfo[], tasks[], adjustments[]}   bổ sung / thay thế phần chung
      .avoid[], likelyCauses[], likelihood, isNormalCondition, normalNote

App gộp (chung + riêng) → `treatmentSnapshot` của lần chẩn đoán → sinh task điều trị
(careSchedules kind=TREATMENT, gắn diagnosisId) và đề xuất chỉnh lịch chăm sóc hiện có
(chỉ áp dụng khi người dùng xác nhận – QT06).

Task:        {key, title, description, taskType, dueInDays, repeatIntervalDays, occurrences, priority}
             repeatIntervalDays = 0 → làm một lần; occurrences = số lần lặp trong đợt điều trị.
             priority: URGENT | HIGH | NORMAL.  key trùng nhau → phần riêng của loài THAY phần chung.
Adjustment:  {taskType, action: SCALE|PAUSE|SET, factor?, days?, reason}
             SCALE: nhân chu kỳ hiện tại; PAUSE: tạm dừng lịch `days` ngày; SET: đặt chu kỳ = days.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import disease_catalog as dc  # noqa: E402
import species_catalog as sc  # noqa: E402

SPECIES = getattr(sc, "SPECIES", None) or getattr(sc, "CATALOG")
LEVELS = ("MILD", "MODERATE", "SEVERE")


def T(key, title, desc, taskType="OTHER", due=0, every=0, times=1, priority="NORMAL"):
    return dict(key=key, title=title, description=desc, taskType=taskType, dueInDays=due,
                repeatIntervalDays=every, occurrences=times, priority=priority)


def A(taskType, action, reason, factor=None, days=None):
    d = dict(taskType=taskType, action=action, reason=reason)
    if factor is not None:
        d["factor"] = factor
    if days is not None:
        d["days"] = days
    return d


def recheck(days, priority="NORMAL"):
    return T("recheck", "Chụp lại lá để kiểm tra tiến triển",
             "Chẩn đoán lại cùng vị trí lá để so sánh với lần này.", "HEALTH_CHECK", due=days, priority=priority)


ISOLATE = T("isolate", "Cách ly cây bệnh", "Để cây cách các chậu khác ít nhất 1 m cho tới khi khỏi.", due=0, priority="HIGH")
REMOVE_LEAVES = T("remove_leaves", "Cắt bỏ lá bệnh",
                  "Dùng kéo đã lau cồn 70%, bỏ lá vào túi kín, không ủ phân.", "PRUNING", due=0, priority="HIGH")
CHECK_ROOTS = T("check_roots", "Kiểm tra rễ",
                "Rút cây khỏi chậu: rễ khỏe trắng/chắc; rễ thối nâu, mềm, có mùi thì cắt bỏ và trồng lại vào đất mới.",
                "OTHER", due=1, priority="URGENT")
BASE_WATER = A("OTHER", "PAUSE", "Tạm dừng phun sương lên lá vì lá ướt làm bệnh lây lan.", days=21)

# ---------------- PHẦN CHUNG THEO BỆNH × MỨC ----------------
GENERIC = {
    "leaf_spot": {
        "MILD": dict(tasks=[REMOVE_LEAVES, recheck(7)], adjustments=[BASE_WATER]),
        "MODERATE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                                T("spray", "Phun thuốc gốc đồng", "Phun đều 2 mặt lá theo liều trên nhãn.", "SPRAYING",
                                  due=1, every=7, times=3, priority="HIGH"), recheck(7)],
                         adjustments=[BASE_WATER]),
        "SEVERE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                              T("spray", "Phun thuốc gốc đồng", "Phun đều 2 mặt lá theo liều trên nhãn.", "SPRAYING",
                                due=0, every=7, times=4, priority="URGENT"),
                              T("disinfect", "Khử trùng chậu, dụng cụ", "Rửa xà phòng, lau cồn hoặc ngâm nước Javel pha loãng 1:9.",
                                due=2), recheck(5, "HIGH")],
                       adjustments=[BASE_WATER, A("FERTILIZING", "PAUSE", "Ngừng bón khi cây đang bệnh nặng, tránh ra lá non dễ nhiễm.", days=28)]),
    },
    "blight_large_necrosis": {
        "MILD": dict(tasks=[REMOVE_LEAVES, recheck(7)], adjustments=[BASE_WATER]),
        "MODERATE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                                T("soil_check", "Kiểm tra độ ẩm đất trước khi tưới",
                                  "Chỉ tưới khi lớp đất mặt 2–3 cm đã khô.", "WATERING", due=0, every=2, times=7),
                                T("spray", "Phun thuốc trừ nấm", "Gốc đồng, hoặc thuốc chuyên trị mốc xám nếu có lớp mốc xám.",
                                  "SPRAYING", due=1, every=7, times=2, priority="HIGH"), recheck(5)],
                         adjustments=[BASE_WATER, A("WATERING", "SCALE", "Mảng hoại tử thường do úng/ẩm cao: giãn lịch tưới.", factor=1.5)]),
        "SEVERE": dict(tasks=[CHECK_ROOTS, REMOVE_LEAVES, ISOLATE,
                              T("repot", "Thay đất, chậu sạch", "Đất mới thoát nước tốt, chậu có lỗ thoát.", "OTHER", due=1, priority="URGENT"),
                              recheck(3, "HIGH")],
                       adjustments=[BASE_WATER, A("WATERING", "SCALE", "Rễ yếu sau khi xử lý: giãn lịch tưới.", factor=1.5),
                                    A("FERTILIZING", "PAUSE", "Không bón 4 tuần sau khi thay đất.", days=28)]),
    },
    "powdery_coating": {
        "MILD": dict(tasks=[REMOVE_LEAVES,
                            T("spray", "Phun kali bicarbonate / dầu neem", "Khoảng 5 g/1 lít nước + vài giọt xà phòng.",
                              "SPRAYING", due=0, every=7, times=2), recheck(7)], adjustments=[]),
        "MODERATE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                                T("spray", "Phun lưu huỳnh hoặc dầu neem", "Không phun lưu huỳnh khi > 32 °C hoặc trong 2 tuần sau khi phun dầu.",
                                  "SPRAYING", due=0, every=7, times=3, priority="HIGH"), recheck(7)],
                         adjustments=[A("FERTILIZING", "PAUSE", "Đạm kích thích chồi non mềm dễ nhiễm phấn trắng.", days=21)]),
        "SEVERE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                              T("spray", "Phun thuốc nội hấp", "Nhóm triazole hoặc azoxystrobin, luân phiên hoạt chất.",
                                "SPRAYING", due=0, every=10, times=3, priority="URGENT"), recheck(5, "HIGH")],
                       adjustments=[A("FERTILIZING", "PAUSE", "Ngừng bón đến khi hết phấn.", days=28)]),
    },
    "rust_pustules": {
        "MILD": dict(tasks=[REMOVE_LEAVES, ISOLATE, recheck(7)], adjustments=[BASE_WATER]),
        "MODERATE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                                T("spray", "Phun lưu huỳnh / mancozeb", "Phun kỹ mặt dưới lá.", "SPRAYING", due=0, every=7, times=3, priority="HIGH"),
                                recheck(7)], adjustments=[BASE_WATER]),
        "SEVERE": dict(tasks=[REMOVE_LEAVES, ISOLATE,
                              T("spray", "Phun thuốc nội hấp", "Triazole hoặc azoxystrobin, luân phiên hoạt chất.", "SPRAYING",
                                due=0, every=10, times=3, priority="URGENT"),
                              T("decide", "Cân nhắc hủy cây", "Nếu phủ kín tán sau 2 lần phun, hủy cây để tránh lây.", due=14),
                              recheck(5, "HIGH")],
                       adjustments=[BASE_WATER, A("FERTILIZING", "PAUSE", "Ngừng bón khi cây bệnh nặng.", days=28)]),
    },
    "tip_margin_necrosis": {
        "MILD": dict(tasks=[T("trim_tips", "Cắt mép lá khô", "Cắt theo dáng lá, chừa viền nâu mỏng.", "PRUNING", due=1),
                            T("soil_check", "Kiểm tra đất trước khi tưới", "Không để đất khô kiệt rồi tưới đẫm.", "WATERING", due=0, every=2, times=7),
                            recheck(14)], adjustments=[]),
        "MODERATE": dict(tasks=[T("trim_tips", "Cắt mép lá khô", "Cắt theo dáng lá, chừa viền nâu mỏng.", "PRUNING", due=1),
                                T("flush", "Xả muối khoáng", "Tưới chậm lượng nước gấp 2–3 lần thể tích chậu cho chảy hết.", "WATERING", due=0, priority="HIGH"),
                                T("humidity", "Tăng độ ẩm quanh cây", "Khay sỏi nước, tránh gió điều hòa.", due=0), recheck(14)],
                         adjustments=[A("FERTILIZING", "PAUSE", "Tạm ngừng bón để giảm tích muối.", days=42)]),
        "SEVERE": dict(tasks=[T("flush", "Xả muối khoáng", "Tưới chậm lượng nước gấp 2–3 lần thể tích chậu.", "WATERING", due=0, priority="HIGH"),
                              T("repot", "Thay đất mới", "Đất cũ đóng muối hoặc đã dùng trên 1–2 năm.", due=3, priority="HIGH"),
                              CHECK_ROOTS, recheck(14)],
                       adjustments=[A("FERTILIZING", "PAUSE", "Ngừng bón 6 tuần sau khi thay đất.", days=42)]),
    },
    "chlorosis_discoloration": {
        "MILD": dict(tasks=[T("soil_check", "Kiểm tra độ ẩm đất", "Đất sũng thì ngừng tưới đến khi khô 2–3 cm mặt.", "WATERING", due=0, every=2, times=5),
                            T("light", "Kiểm tra vị trí đặt cây", "Chuyển tới chỗ sáng gián tiếp nếu đang đặt chỗ tối.", due=0),
                            recheck(14)], adjustments=[]),
        "MODERATE": dict(tasks=[T("soil_check", "Kiểm tra độ ẩm đất", "Đất sũng thì ngừng tưới đến khi khô 2–3 cm mặt.", "WATERING", due=0, every=2, times=7),
                                T("feed", "Bón phân cân đối / vi lượng", "NPK pha loãng; lá non vàng gân xanh: sắt chelate; lá già vàng giữa gân: MgSO4 1–2 g/L.",
                                  "FERTILIZING", due=2, priority="HIGH"),
                                T("mites", "Soi mặt dưới lá tìm nhện đỏ", "Có tơ mảnh, chấm nhỏ di động thì rửa lá và tăng độ ẩm.", "HEALTH_CHECK", due=0),
                                recheck(14)],
                         adjustments=[A("WATERING", "SCALE", "Vàng lá phổ biến nhất do tưới nhiều: giãn lịch tưới.", factor=1.3)]),
        "SEVERE": dict(tasks=[CHECK_ROOTS,
                              T("repot", "Trồng lại vào đất mới", "Đất thoát nước tốt, chậu có lỗ; chưa bón 4 tuần đầu.", due=1, priority="URGENT"),
                              recheck(7, "HIGH")],
                       adjustments=[A("WATERING", "SCALE", "Rễ đang yếu: giãn lịch tưới.", factor=1.5),
                                    A("FERTILIZING", "PAUSE", "Không bón 4 tuần sau khi trồng lại.", days=28)]),
    },
}

# ---------------- ĐẶC ĐIỂM LOÀI ẢNH HƯỞNG ĐẾN ĐIỀU TRỊ ----------------
SUCCULENT = {"aloe-vera", "jade-plant", "kalanchoe", "snake-plant", "zz-plant", "ponytail-palm", "christmas-cactus"}
OIL_SENSITIVE = {"jade-plant", "boston-fern", "birds-nest-fern", "asparagus-fern", "kalanchoe", "christmas-cactus"}
HAIRY_NO_WET = {"african-violet", "iron-cross-begonia", "begonia"}
FLUORIDE = {"dracaena", "peace-lily", "calathea", "ctenanthe", "prayer-plant", "rattlesnake-plant",
            "areca-palm", "parlor-palm", "yucca"}
PALMS = {"areca-palm", "parlor-palm"}
CROWN_ROT = {"orchid", "birds-nest-fern"}
BULBS = {"daffodils", "hyacinth", "tulip", "lilium", "lily-of-the-valley"}
CARNIVOROUS = {"venus-flytrap"}


def species_part(s, dis):
    sid, did = s["id"], dis["aiLabel"]
    avoid, causes, care = [], [], []
    plans = {lv: dict(careInfo=[], tasks=[], adjustments=[]) for lv in LEVELS}
    normal, normal_note = False, ""

    def add(levels, tasks=(), adjustments=(), info=()):
        for lv in levels:
            plans[lv]["tasks"] += list(tasks)
            plans[lv]["adjustments"] += list(adjustments)
            plans[lv]["careInfo"] += list(info)

    if sid in SUCCULENT:
        care.append(f"{s['commonName']} là cây mọng nước: chỉ tưới khi đất khô hoàn toàn.")
        avoid.append("Không phun sương, không để nước đọng ở gốc.")
        if did in ("blight_large_necrosis", "chlorosis_discoloration"):
            causes.insert(0, "Úng nước – nguyên nhân hàng đầu ở cây mọng nước")
            add(("MODERATE", "SEVERE"), adjustments=[A("WATERING", "PAUSE", "Cây mọng nước: ngừng tưới đến khi đất khô hẳn.", days=10)])
    if sid in OIL_SENSITIVE:
        avoid.append("Không dùng dầu khoáng, dầu neem, xà phòng diệt côn trùng – loài này dễ cháy lá với dầu/xà phòng.")
        if did == "powdery_coating":
            add(LEVELS, tasks=[T("spray", "Phun kali bicarbonate (không dùng dầu)", "5 g/1 lít nước, thử trên 1 lá trước.",
                                 "SPRAYING", due=0, every=7, times=3)])
    if sid in HAIRY_NO_WET:
        avoid.append("Không làm ướt lá; tưới từ đáy chậu bằng nước nhiệt độ phòng.")
        care.append("Lá có lông giữ nước, dễ phát bệnh nấm khi ướt.")
        if did == "leaf_spot" and sid == "african-violet":
            causes.insert(0, "Nước lạnh rơi lên lá tạo đốm vàng nhạt (không do mầm bệnh)")
    if sid in FLUORIDE and did == "tip_margin_necrosis":
        causes.insert(0, "Nhạy với flo/clo trong nước máy và muối phân bón")
        add(LEVELS, tasks=[T("water_source", "Đổi nguồn nước tưới", "Dùng nước lọc, nước mưa, hoặc nước máy để qua đêm.",
                             "WATERING", due=0, priority="HIGH")])
        avoid.append("Không dùng phân có nhiều superphosphate (chứa flo).")
    if sid in PALMS:
        avoid.append("Không cắt lá cọ còn xanh một phần – cây đang thu hồi dinh dưỡng từ lá đó.")
        if did == "chlorosis_discoloration":
            causes.insert(0, "Thiếu kali/magie (lá già vàng, đốm vàng)")
            add(("MODERATE", "SEVERE"), tasks=[T("feed", "Bón phân cho cọ (có K, Mg, Mn)",
                                                 "Phân chuyên cho cọ theo nhãn; MgSO4 1–2 g/L nếu lá già vàng giữa gân.",
                                                 "FERTILIZING", due=2, priority="HIGH")])
    if sid == "sago-palm" and did == "chlorosis_discoloration":
        causes.insert(0, "Lá non vàng, xoăn (frizzle top) do thiếu mangan")
        add(("MODERATE", "SEVERE"), tasks=[T("feed", "Bổ sung mangan sulfat", "Theo liều trên nhãn, tưới quanh gốc.",
                                             "FERTILIZING", due=2, priority="HIGH")])
    if sid in CROWN_ROT:
        avoid.append("Không để nước đọng ở nõn/tâm cây; tưới vào giá thể.")
        if did in ("blight_large_necrosis", "chlorosis_discoloration"):
            add(("SEVERE",), tasks=[T("check_roots", "Kiểm tra rễ và giá thể",
                                      "Giá thể bí, mục thì thay mới; cắt rễ thối nâu, mềm." if sid == "orchid"
                                      else "Cắt phần gốc thối, để khô vết cắt rồi trồng lại.", due=1, priority="URGENT")])
    if sid in BULBS and did == "chlorosis_discoloration":
        normal = True
        normal_note = ("Lá vàng dần SAU KHI ra hoa là chu kỳ ngủ nghỉ tự nhiên của cây củ – không phải bệnh. "
                       "Để lá tự khô hẳn mới cắt để củ tích dinh dưỡng; giảm dần tưới.")
    if sid in CARNIVOROUS:
        avoid += ["Không bón phân vào đất.", "Không dùng nước máy – chỉ nước cất, nước mưa hoặc nước RO.",
                  "Không phun thuốc lên bẫy."]
        for lv in LEVELS:
            plans[lv]["adjustments"].append(A("FERTILIZING", "PAUSE", "Cây bắt mồi không được bón phân.", days=365))
        if did == "chlorosis_discoloration":
            causes.insert(0, "Nước nhiều khoáng (nước máy) hoặc thiếu nắng trực tiếp")
    if sid == "chrysanthemum" and did == "rust_pustules":
        causes.insert(0, "Gỉ sắt trắng (Puccinia horiana) lây rất nhanh qua giọt nước")
        add(("SEVERE",), tasks=[T("decide", "Hủy cây", "Gỉ sắt trắng nặng khó chữa: hủy cây, không ủ phân.", due=1, priority="URGENT")])
    tox = s.get("toxicity") or {}
    if tox.get("pets") or tox.get("humans"):
        care.append(f"Cây có độc: {tox.get('note', '').strip().rstrip('.')}."
                    " Đeo găng khi cắt tỉa, bỏ lá cắt xa tầm trẻ em và vật nuôi.")

    for lv in LEVELS:
        plans[lv]["careInfo"] = care + plans[lv]["careInfo"]

    matched = any(k in " ".join(s.get("commonProblems") or []).lower().replace("rệp phấn trắng", "")
                  for k in dis["matchKeywords"])
    return {
        "speciesId": sid, "diseaseId": did,
        "speciesName": s["commonName"], "diseaseName": dis["nameVi"],
        "likelihood": "COMMON" if matched else "POSSIBLE",
        "likelyCauses": causes, "avoid": avoid,
        "isNormalCondition": normal, "normalNote": normal_note,
        "plans": plans,
    }


def generic_plan(did, level):
    d = next(x for x in dc.DISEASES if x["aiLabel"] == did)
    key = {"MILD": "treatmentMild", "MODERATE": "treatmentModerate", "SEVERE": "treatmentSevere"}[level]
    g = GENERIC[did][level]
    return dict(careInfo=expand_steps(d, level), tasks=g["tasks"], adjustments=g["adjustments"])


def expand_steps(d, level):
    """Thay câu "Thực hiện các bước của mức nhẹ/trung bình" bằng chính các bước đó."""
    mild = list(d["treatmentMild"])
    mod = [x for y in d["treatmentModerate"] for x in (mild if "mức nhẹ" in y else [y])]
    sev = [x for y in d["treatmentSevere"] for x in (mod if "mức trung bình" in y else mild if "mức nhẹ" in y else [y])]
    return {"MILD": mild, "MODERATE": mod, "SEVERE": sev}[level]


def merge(generic, specific):
    """Cùng logic với app (TreatmentPlanBuilder): task trùng key → bản của loài thay bản chung."""
    tasks = {t["key"]: t for t in generic["tasks"]}
    for t in specific["tasks"]:
        tasks[t["key"]] = t
    adj = {(a["taskType"], a["action"]): a for a in generic["adjustments"]}
    for a in specific["adjustments"]:
        adj[(a["taskType"], a["action"])] = a
    return dict(careInfo=specific["careInfo"] + generic["careInfo"], tasks=list(tasks.values()), adjustments=list(adj.values()))


def build():
    pairs = [species_part(s, d) for s in SPECIES for d in dc.DISEASES]
    return pairs


def validate():
    assert set(GENERIC) == {d["aiLabel"] for d in dc.DISEASES}
    for did, lv in GENERIC.items():
        for level in LEVELS:
            assert lv[level]["tasks"], f"{did}/{level}: thiếu task"
            for t in lv[level]["tasks"]:
                assert t["taskType"] in ("WATERING", "FERTILIZING", "PRUNING", "SPRAYING", "HEALTH_CHECK", "OTHER")
                assert t["occurrences"] >= 1 and (t["occurrences"] == 1 or t["repeatIntervalDays"] > 0), t
    pairs = build()
    assert len(pairs) == len(SPECIES) * len(dc.DISEASES)
    return len(pairs)
