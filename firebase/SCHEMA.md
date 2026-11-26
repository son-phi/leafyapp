# LeafyApp – Lược đồ Firestore (Nhận diện · Chẩn đoán · Hồ sơ cây · Lịch chăm sóc)

Nguyên tắc: **giữ đúng bảng, trường, quan hệ của thiết kế ERD (LEAFYAPP_V3) và schema web admin**
(`admin-web-leafy/shared/catalog.ts`). App chỉ thêm một số **trường mở rộng** – đánh dấu **[mở rộng]** –
khi chức năng không thể suy ra từ trường gốc. Web admin đọc theo danh sách trường của nó nên bỏ qua trường mở rộng,
và khi sửa chỉ cập nhật trường của nó nên trường mở rộng được giữ nguyên.
Không gồm IoT (devices, nodes, sensorData, wateringLogs, alerts) và chatbot (chatSessions, messages).

```
species/{speciesId}                              danh mục loài   (web admin quản lý)
diseases/{aiLabel}                               danh mục bệnh   (web admin quản lý)
users/{uid}
  ├─ identifications/{id}                        lịch sử nhận diện loài
  └─ plants/{plantId}                            hồ sơ cây trồng
       ├─ careSchedules/{id}                     lịch chăm sóc (định kỳ + việc điều trị)
       ├─ careLogs/{id}                          nhật ký chăm sóc
       └─ diagnoses/{id}                         lịch sử chẩn đoán
```

## species/{speciesId}
| Trường | Kiểu | Ghi chú |
|---|---|---|
| commonName, scientificName, description | string | |
| aiLabel | string (duy nhất) | Nhãn model nhận diện, khớp nguyên văn |
| imageUrl, imagePath | string | Ảnh mẫu (18 loài chưa có ảnh) |
| lightNeed | LOW / MEDIUM / HIGH / DIRECT | |
| wateringCycleDays, fertilizingCycleDays | int > 0 | Loài không bón phân: 365 + `careGuide.noFertilizer` |
| threshold | map 8 trường | soilMoisture/temperature/airHumidity/light Min–Max. IoT đồng bộ nguyên khối xuống ESP32 |
| searchName, searchPrefixes, createdAt, updatedAt, updatedBy | | Do admin dùng để tìm, sắp xếp (seed tính giống admin) |
| careGuide [mở rộng] | map | Nội dung "Thông tin chăm sóc": tên khác, họ, nguồn gốc, độ khó, mô tả ánh sáng/tưới/bón/đất/nhiệt độ/độ ẩm, độc tính, nhân giống, sâu bệnh, lưu ý, mùa |
| careTemplates [mở rộng] | array | Lịch gợi ý khi Thêm cây trồng. Loài admin tạo mới chưa có → app sinh từ chu kỳ tưới/bón |
| treatmentCautions [mở rộng] | array<string> | Lưu ý khi xử lý bệnh cho loài (không dùng dầu, không làm ướt lá, cây có độc…) |
| diseaseOverrides [mở rộng] | map diseaseId → {likelyCauses, isNormalCondition, normalNote, plans{MILD/MODERATE/SEVERE}} | Phần riêng của loài với bệnh (49 cặp). Thay bảng careRules |

## diseases/{aiLabel}
| Trường | Kiểu | Ghi chú |
|---|---|---|
| aiLabel | string | = id tài liệu = `disease_id` model trả về (6 bệnh; `healthy` không phải bệnh) |
| nameVi, scientificName | string | |
| imageUrl, imagePath | string | Chưa có ảnh minh họa |
| affectedSpeciesIds | array<speciesId> | Quan hệ n–n Species–Diseases (loài thường mắc) |
| causes, symptoms, treatmentMild, treatmentModerate, treatmentSevere, prevention | string nhiều dòng ("• …") | Đúng kiểu của admin; app tách theo dòng |
| searchName, createdAt, updatedAt, updatedBy | | Admin |
| plans [mở rộng] | map MILD/MODERATE/SEVERE → {careInfo, tasks, adjustments} | Việc cần làm + đề xuất đổi lịch. Bệnh admin tạo mới chưa có → app dùng treatmentX + việc mặc định |
| requiresPesticide, safetyNote [mở rộng] | | Lưu ý an toàn thuốc (QT09) |

## users/{uid}
`email, fullName, avatarUrl, role (user|admin), status (active|locked), fcmTokens[], createdAt, lastLoginAt`
+ `searchName, searchPrefixes` (trigger của admin tự tính). Người dùng không tự đổi `role/status` (rules).
`lockHistory` do admin ghi qua Cloud Functions.

## users/{uid}/plants/{plantId}
`userId, speciesId, name, imageUrl, plantedDate, useSpeciesThresholds, customThreshold (map 8 trường), createdAt`
+ [mở rộng] `healthStatus` (UNKNOWN|HEALTHY|MILD|MODERATE|SEVERE), `lastCheckAt`: hiển thị tình trạng trong vườn,
chỉ đổi khi kết quả chẩn đoán đáng tin (QT02).

## …/plants/{plantId}/careSchedules/{id}
`plantId, taskType, startAt (ngày + giờ nhắc), repeatIntervalDays, note, active`
+ [mở rộng] `title` (tên việc), `nextDueAt` (= ngày làm thực tế + chu kỳ; tạm dừng = dời ngày này),
`sourceDiagnosisId` (việc điều trị sinh từ lần chẩn đoán nào), `occurrences` (số lần của việc điều trị).
Suy ra khi đọc, không lưu: loại (có sourceDiagnosisId = điều trị), giờ nhắc (từ startAt), số lần đã làm và
lần làm gần nhất (đếm careLogs). Lần chẩn đoán mới đặt `active = false` cho việc điều trị cũ còn dở.

## …/plants/{plantId}/careLogs/{id}
`plantId, careScheduleId (null = việc ngoài lịch), taskType, performedAt, note` + [mở rộng] `title`.

## …/plants/{plantId}/diagnoses/{id}
`plantId, speciesId, diseaseId (null nếu khỏe/không xác định), imageUrl, confidence (0–100), severity
(NONE|MILD|MODERATE|SEVERE), lesionAreaPercent, treatmentSnapshot, createdAt` + [mở rộng] `aiLabel`
(phân biệt `healthy` với "không xác định").
**treatmentSnapshot** = bản sao hướng xử lý tại thời điểm chẩn đoán (QT04): phần chung của bệnh + phần riêng của
loài ở đúng mức độ, kèm `scheduleChanges` (đề xuất đổi lịch, đã áp dụng hay chưa – QT06) và `modelNote`.
Suy ra khi đọc: đáng tin = confidence ≥ 60 và không có modelNote (QT01); xu hướng so với lần đáng tin trước (QT05).

## users/{uid}/identifications/{id}
`userId, speciesId (loài cuối cùng: AI ≥ 60% hoặc người dùng chọn), imageUrl, aiLabel, confidence, topCandidates[], createdAt`.

## Quan hệ
Users 1–n Plants, Users 1–n Identifications, Species 1–n Plants, Species 0..1–n Identifications,
Species n–n Diseases (`diseases.affectedSpeciesIds`; nội dung riêng ở `species.diseaseOverrides`),
Plants 1–n Diagnoses, Diseases 0..1–n Diagnoses, Plants 1–n CareSchedules, CareSchedules 0..1–n CareLogs,
Plants 1–n CareLogs, Diagnoses 0..1–n CareSchedules (`sourceDiagnosisId`).

## Script
- `seed/seed_catalog.py --key <sa.json>`: ghi species + diseases đúng schema (nguồn: species_catalog.py,
  disease_catalog.py, treatment_plans.py); `--export` để xem trước.
- `seed/normalize_user_data.py --key <sa.json>`: đưa dữ liệu người dùng về schema này.
- `seed/migrate_legacy.py`: (đã chạy) chuyển dữ liệu cũ user_plants/care_tasks/... sang cấu trúc mới.
- Sơ đồ: `diagram/build_diagram.py` → `diagram/leafy_firestore_erd.png`.

## Dữ liệu cũ
Đã xóa danh mục `plants`, `careRules`, `users/*/user_plants|care_tasks|task_history|disease_logs`
(sao lưu `D:\DATN\backup-firestore\`), trừ `users/q3mDAQ…/user_plants/ytpcH40i0APKClFBlCHh` mà IoT đang liên kết.
Vườn gia đình (`gardens`) giữ cấu trúc cũ.
