"""
Danh mục bệnh trên lá cho collection `diseases`.

Model chẩn đoán (houseplant_api, disease_pipeline / pipeline_config.json) trả về 7 lớp
TRIỆU CHỨNG (không phải tên mầm bệnh cụ thể): healthy, leaf_spot, blight_large_necrosis,
powdery_coating, rust_pustules, tip_margin_necrosis, chlorosis_discoloration.
=> id tài liệu = aiLabel = đúng `disease_id` model trả về, khớp nguyên văn.
`healthy` KHÔNG phải bệnh nên không có tài liệu: chẩn đoán khỏe lưu diseaseId = null, aiLabel = "healthy".
Hướng xử lý ở đây là phần CHUNG theo mức độ; phần riêng từng loài nằm ở `careRules` (treatment_plans.py).

`scientificName` vì vậy ghi nhóm tác nhân thường gặp, không phải một loài nấm duy nhất.
Hướng xử lý theo 3 mức của ứng dụng (6 mức model → 3 mức):
  Rất nhẹ/Nhẹ → MILD, Trung bình → MODERATE, Nặng/Rất nặng → SEVERE.
Nguồn tham khảo: tài liệu khuyến nông về bệnh cây cảnh trong nhà (UC IPM, RHS, Missouri
Botanical Garden, Clemson HGIC), nội dung đã lược lại cho người trồng tại nhà.
"""

SAFETY_NOTE = (
    "Đọc kỹ nhãn và dùng đúng liều lượng. Đeo găng tay, khẩu trang khi pha và phun; phun ở nơi "
    "thoáng khí, tránh xa trẻ em, vật nuôi, thực phẩm. Thử trên 1–2 lá trước khi phun toàn cây."
)

DISEASES = [
    dict(
        aiLabel="leaf_spot",
        nameVi="Đốm lá",
        nameEn="Leaf spot",
        scientificName="Nấm Cercospora, Alternaria, Colletotrichum, Septoria; vi khuẩn Xanthomonas, Pseudomonas",
        lesionType="necrosis",
        requiresPesticide=True,
        description="Các đốm hoại tử rải rác trên phiến lá, phần lớn do nấm hoặc vi khuẩn phát triển khi lá ướt lâu.",
        causes=["Lá ướt kéo dài (tưới lên lá, phun sương buổi tối)",
                "Thông gió kém, cây đặt quá sát nhau",
                "Lây từ cây bệnh, dụng cụ hoặc nước bắn",
                "Nước lạnh đọng trên lá (hay gặp ở Hoa tử la lan – không do mầm bệnh)"],
        symptoms=["Đốm tròn hoặc góc cạnh màu nâu, đen, đôi khi có quầng vàng",
                  "Tâm đốm khô, có thể thủng; đốm do vi khuẩn thường trông úng nước",
                  "Đốm lan rộng và liên kết thành mảng khi ẩm cao"],
        treatmentMild=["Cắt bỏ lá có đốm bằng kéo đã lau cồn; bỏ lá bệnh vào túi kín, không ủ phân.",
                       "Chỉ tưới vào gốc, tưới buổi sáng để lá kịp khô.",
                       "Tăng thông gió, giãn khoảng cách và cách ly cây bệnh."],
        treatmentModerate=["Thực hiện các bước của mức nhẹ.",
                           "Phun thuốc trừ bệnh gốc đồng (copper hydroxide/oxychloride) – dùng được cho cả nấm và vi khuẩn; "
                           "hoặc thuốc trừ nấm chứa mancozeb/chlorothalonil theo liều trên nhãn.",
                           "Lặp lại sau 7–10 ngày, phun đều cả hai mặt lá."],
        treatmentSevere=["Cắt bỏ toàn bộ lá bị nặng, chỉ giữ lá còn khỏe.",
                         "Phun thuốc như mức trung bình, theo dõi lá mới mọc.",
                         "Nếu đốm úng nước lan nhanh vào thân (thường do vi khuẩn), nên hủy cây để tránh lây.",
                         "Khử trùng chậu, dụng cụ trước khi dùng lại."],
        prevention=["Không tưới lên lá; tránh phun sương vào buổi tối.",
                    "Giữ cây thông thoáng, không xếp sát nhau.",
                    "Vệ sinh lá rụng trên mặt chậu."],
        matchKeywords=["đốm lá", "đốm nâu", "đốm nấm", "đốm xám"],
    ),
    dict(
        aiLabel="blight_large_necrosis",
        nameVi="Cháy lá / hoại tử mảng lớn",
        nameEn="Blight / large necrosis",
        scientificName="Nấm Botrytis cinerea (mốc xám), Phytophthora, Rhizoctonia; vi khuẩn; hoặc tổn thương do nắng, lạnh, phân bón",
        lesionType="necrosis",
        requiresPesticide=True,
        description="Mảng mô chết lớn, lan nhanh trên lá. Có thể do nấm/vi khuẩn hoặc do tác nhân phi sinh học (cháy nắng, lạnh, bón phân quá liều).",
        causes=["Ẩm cao, lá ướt và mát (điều kiện của mốc xám Botrytis)",
                "Úng nước gây thối rễ, thối thân lan lên lá",
                "Nắng gắt đột ngột, gió lạnh hoặc bón phân quá liều làm cháy mô"],
        symptoms=["Mảng nâu, đen lớn, thường bắt đầu từ mép hoặc chóp lá rồi lan vào trong",
                  "Mô bệnh mềm, úng nước; có thể có lớp mốc xám khi ẩm (Botrytis)",
                  "Cháy nắng: mảng bạc trắng hoặc nâu khô ở phía lá hướng ra nắng"],
        treatmentMild=["Cắt bỏ phần lá bị cháy, vệ sinh lá rụng.",
                       "Giảm độ ẩm quanh cây, ngừng tưới lên lá, tăng thông gió.",
                       "Nếu nghi cháy nắng/lạnh: chuyển cây tới chỗ sáng gián tiếp, tránh gió lùa (không cần thuốc)."],
        treatmentModerate=["Thực hiện các bước của mức nhẹ.",
                           "Kiểm tra đất và rễ: nếu đất sũng nước, ngừng tưới cho đến khi lớp đất mặt khô.",
                           "Khi có mốc xám hoặc mảng úng lan nhanh: phun thuốc trừ nấm gốc đồng hoặc thuốc chuyên trị Botrytis "
                           "theo liều trên nhãn, lặp lại sau 7–10 ngày."],
        treatmentSevere=["Cắt bỏ toàn bộ lá, cành bị hoại tử.",
                         "Rút cây kiểm tra rễ; cắt rễ thối nâu, mềm, trồng lại vào đất mới thoát nước tốt, chậu sạch.",
                         "Nếu thân/gốc đã thối mềm, nên hủy cây để tránh lây sang cây khác."],
        prevention=["Tưới đúng nhu cầu, chậu có lỗ thoát nước.",
                    "Không để cây trong góc ẩm, bí gió; tránh nắng trưa gắt với cây ưa bóng.",
                    "Bón phân pha loãng theo hướng dẫn, không bón khi đất khô."],
        matchKeywords=["cháy lá", "botrytis", "nắng gắt", "tổn thương lạnh"],
    ),
    dict(
        aiLabel="powdery_coating",
        nameVi="Phấn trắng",
        nameEn="Powdery mildew",
        scientificName="Nấm bộ Erysiphales (Erysiphe, Podosphaera, Golovinomyces, Oidium)",
        lesionType="powdery",
        requiresPesticide=True,
        description="Lớp phấn trắng xám như bột trên mặt lá do nấm phấn trắng ký sinh bề mặt.",
        causes=["Không khí ẩm (nhất là ban đêm) nhưng mặt lá khô",
                "Thiếu sáng, thông gió kém, cây quá dày",
                "Bào tử phát tán qua không khí từ cây bệnh"],
        symptoms=["Lớp bột trắng hoặc xám trên mặt lá, chồi non, nụ hoa",
                  "Lá bên dưới lớp phấn vàng dần, xoăn, khô",
                  "Lau đi được nhưng mọc lại sau vài ngày"],
        treatmentMild=["Cắt bỏ lá có phấn, bỏ vào túi kín.",
                       "Đưa cây tới chỗ sáng và thoáng hơn, cách ly với cây khác.",
                       "Phun dung dịch kali bicarbonate (hoặc baking soda) khoảng 5 g/1 lít nước + vài giọt xà phòng, "
                       "hoặc dầu neem theo nhãn; phun 7 ngày/lần."],
        treatmentModerate=["Thực hiện các bước của mức nhẹ.",
                           "Phun thuốc chứa lưu huỳnh hoặc dầu khoáng/dầu neem theo nhãn, 7–10 ngày/lần.",
                           "Không phun lưu huỳnh khi trời trên 32 °C hoặc trong vòng 2 tuần sau khi phun dầu (dễ cháy lá)."],
        treatmentSevere=["Cắt bỏ phần lớn lá, chồi bị phủ phấn.",
                         "Dùng thuốc trừ nấm nội hấp nhóm triazole (vd. myclobutanil, tebuconazole) hoặc azoxystrobin "
                         "theo nhãn; luân phiên hoạt chất để tránh kháng thuốc.",
                         "Theo dõi lá mới 2–3 tuần; nếu tái phát liên tục nên cân nhắc thay cây."],
        prevention=["Đặt cây nơi đủ sáng, thông gió.",
                    "Không bón thừa đạm (kích thích chồi non mềm dễ nhiễm).",
                    "Tưới vào gốc, kiểm tra mặt lá thường xuyên với Thu hải đường, Hoa cúc, Sống đời."],
        matchKeywords=["phấn trắng"],
    ),
    dict(
        aiLabel="rust_pustules",
        nameVi="Gỉ sắt",
        nameEn="Rust",
        scientificName="Nấm bộ Pucciniales (Puccinia, Uromyces…; vd. Puccinia horiana – gỉ sắt trắng hoa cúc)",
        lesionType="rust",
        requiresPesticide=True,
        description="Các mụn nhỏ chứa bào tử màu cam, nâu đỏ hoặc trắng kem, chủ yếu ở mặt dưới lá.",
        causes=["Lá ướt liên tục nhiều giờ, ẩm độ cao",
                "Bào tử lây qua gió, nước bắn, cây mới mua"],
        symptoms=["Mặt trên lá có đốm vàng nhạt",
                  "Mặt dưới tương ứng có mụn nhô lên màu cam, nâu gỉ hoặc trắng kem; chạm vào có bột bám tay",
                  "Lá vàng, khô và rụng sớm khi nặng"],
        treatmentMild=["Cắt bỏ ngay lá có mụn gỉ, cho vào túi kín.",
                       "Không tưới lên lá, giữ lá khô; cách ly cây."],
        treatmentModerate=["Thực hiện các bước của mức nhẹ.",
                           "Phun thuốc trừ nấm chứa lưu huỳnh hoặc mancozeb theo nhãn, phun kỹ mặt dưới lá, 7–10 ngày/lần."],
        treatmentSevere=["Cắt bỏ phần lớn tán lá bị bệnh.",
                         "Dùng thuốc nội hấp nhóm triazole (vd. tebuconazole, propiconazole) hoặc azoxystrobin theo nhãn, "
                         "luân phiên hoạt chất.",
                         "Nếu cây bị phủ kín (thường gặp ở hoa cúc), nên hủy cây để tránh lây lan."],
        prevention=["Tưới gốc vào buổi sáng, tránh ướt lá.",
                    "Kiểm tra mặt dưới lá cây mới mua trước khi đặt cạnh cây khác.",
                    "Giữ khoảng cách giữa các chậu."],
        matchKeywords=["gỉ sắt"],
    ),
    dict(
        aiLabel="tip_margin_necrosis",
        nameVi="Cháy mép / chóp lá",
        nameEn="Tip and margin necrosis",
        scientificName="Chủ yếu do tác nhân phi sinh học (sinh lý)",
        lesionType="necrosis",
        requiresPesticide=False,
        description="Chóp và mép lá khô nâu. Phần lớn trường hợp là rối loạn sinh lý do nước, độ ẩm hoặc muối khoáng, không phải bệnh lây.",
        causes=["Không khí khô (độ ẩm thấp, gần điều hòa, lò sưởi)",
                "Tưới không đều: để đất khô kiệt rồi tưới đẫm",
                "Tích tụ muối khoáng từ phân bón hoặc nước máy; flo/clo (Phất dụ, Lan ý, Đuôi công rất nhạy)",
                "Nắng gắt trực tiếp, gió nóng"],
        symptoms=["Chóp lá và dọc mép lá chuyển nâu, khô giòn",
                  "Ranh giới giữa phần khô và phần xanh thường có viền vàng",
                  "Có thể thấy lớp muối trắng trên mặt đất hoặc thành chậu"],
        treatmentMild=["Cắt phần mép khô theo dáng lá, chừa lại một viền nâu mỏng để không làm tổn thương mô xanh.",
                       "Tưới đều đặn theo nhu cầu của loài; không để đất khô kiệt.",
                       "Tăng độ ẩm quanh cây (khay sỏi nước, đặt các chậu gần nhau, tránh luồng gió điều hòa)."],
        treatmentModerate=["Thực hiện các bước của mức nhẹ.",
                           "Xả muối: tưới chậm lượng nước gấp khoảng 2–3 lần thể tích chậu cho chảy hết qua lỗ thoát.",
                           "Dùng nước lọc, nước mưa hoặc nước máy để qua đêm với các loài nhạy flo/clo.",
                           "Tạm ngừng bón phân 4–6 tuần, sau đó bón pha loãng một nửa liều."],
        treatmentSevere=["Thực hiện các bước của mức trung bình.",
                         "Thay đất mới nếu đất cũ đóng muối hoặc đã dùng trên 1–2 năm.",
                         "Kiểm tra rễ: nếu có rễ thối, cắt bỏ và trồng lại vào chậu thoát nước tốt.",
                         "Không cần dùng thuốc bảo vệ thực vật cho tình trạng này."],
        prevention=["Duy trì lịch tưới đều, kiểm tra độ ẩm đất trước khi tưới.",
                    "Xả chậu định kỳ 1–2 tháng/lần nếu bón phân thường xuyên.",
                    "Tránh nắng trưa gắt và luồng gió nóng/lạnh trực tiếp."],
        matchKeywords=["chóp lá", "mép lá", "cháy chóp"],
    ),
    dict(
        aiLabel="chlorosis_discoloration",
        nameVi="Vàng lá / mất màu",
        nameEn="Chlorosis / discoloration",
        scientificName="Thường do thiếu dinh dưỡng (N, Mg, Fe, Mn), úng rễ hoặc thiếu sáng; đôi khi do nhện đỏ hoặc virus",
        lesionType="chlorosis",
        requiresPesticide=False,
        description="Lá mất diệp lục, chuyển vàng hoặc nhạt màu. Cần xem vị trí lá vàng để đoán nguyên nhân.",
        causes=["Tưới quá nhiều, úng làm rễ thối không hút được dinh dưỡng (nguyên nhân phổ biến nhất)",
                "Thiếu đạm (N): lá già phía dưới vàng đều",
                "Thiếu magie (Mg): lá già vàng giữa các gân, gân còn xanh",
                "Thiếu sắt/mangan (Fe, Mn): lá non vàng, gân xanh – thường do pH đất cao",
                "Thiếu sáng kéo dài; lá già tự nhiên; nhện đỏ chích hút (lá lấm tấm vàng)"],
        symptoms=["Lá vàng toàn phần hoặc vàng giữa các gân",
                  "Lá nhạt màu, đốm vàng lấm tấm, mất vân/đốm đặc trưng của giống",
                  "Lá vàng rồi rụng"],
        treatmentMild=["Kiểm tra độ ẩm đất trước tiên: nếu đất ẩm sũng, ngừng tưới đến khi lớp đất mặt 2–3 cm khô.",
                       "Một vài lá già dưới cùng vàng là bình thường – chỉ cần cắt bỏ.",
                       "Chuyển cây tới vị trí sáng gián tiếp hơn nếu đang đặt chỗ tối."],
        treatmentModerate=["Thực hiện các bước của mức nhẹ.",
                           "Bón phân NPK cân đối pha loãng theo nhãn trong mùa sinh trưởng.",
                           "Lá non vàng gân xanh: bổ sung phân vi lượng có sắt chelate (Fe-EDTA/EDDHA) theo nhãn.",
                           "Lá già vàng giữa gân: tưới magie sulfat (muối Epsom) khoảng 1–2 g/lít nước, 1 lần/tháng.",
                           "Soi mặt dưới lá: nếu có tơ mảnh và chấm nhỏ di động (nhện đỏ), rửa lá bằng nước và tăng độ ẩm."],
        treatmentSevere=["Rút cây kiểm tra rễ; cắt bỏ rễ thối nâu, mềm, có mùi.",
                         "Trồng lại vào đất mới thoát nước tốt, chậu có lỗ thoát; chưa bón phân 4 tuần đầu.",
                         "Nếu lá vàng loang lổ dạng khảm, biến dạng và không cải thiện, có thể do virus – nên cách ly hoặc hủy cây."],
        prevention=["Tưới theo nhu cầu của loài, dùng chậu có lỗ thoát nước.",
                    "Bón phân định kỳ trong mùa sinh trưởng, ngừng hoặc giảm vào mùa nghỉ.",
                    "Đảm bảo đủ ánh sáng theo nhu cầu của loài."],
        matchKeywords=["vàng", "mất màu", "mất đốm", "đốm nhạt", "thiếu magie", "thiếu mg"],
    ),
]

HEALTHY_LABEL = "healthy"
MODEL_LABELS = ["healthy", "leaf_spot", "blight_large_necrosis", "powdery_coating",
                "rust_pustules", "tip_margin_necrosis", "chlorosis_discoloration"]

# 6 mức của model (pipeline_config.json SEVERITY_SCALE) → 3 mức của ứng dụng
SEVERITY_MAP = {"Không bệnh": "NONE", "Rất nhẹ": "MILD", "Nhẹ": "MILD",
                "Trung bình": "MODERATE", "Nặng": "SEVERE", "Rất nặng": "SEVERE"}


def validate():
    labels = [d["aiLabel"] for d in DISEASES]
    expected = [m for m in MODEL_LABELS if m != HEALTHY_LABEL]
    assert sorted(labels) == sorted(expected), f"aiLabel lệch model: {set(labels) ^ set(expected)}"
    for d in DISEASES:
        if True:
            for k in ("causes", "symptoms", "treatmentMild", "treatmentModerate", "treatmentSevere", "prevention"):
                assert d[k], f"{d['aiLabel']}: thiếu {k}"
    return len(DISEASES)
