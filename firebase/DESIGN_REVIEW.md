# Rà soát thiết kế CSDL (sơ đồ ERD nhóm) – phần Nhận diện, Chẩn đoán, Hồ sơ cây, Lịch chăm sóc

Phạm vi: Users, Plants, Species, Diseases, Diagnoses, CareSchedules, CareLogs, Identifications.
Bỏ qua (làm sau): Alerts, Devices, Nodes, SensorData, WateringLogs (IoT); ChatSessions, Messages (chatbot);
LockHistory (admin).

## 0. Lỗi chung của sơ đồ

| # | Vấn đề | Sửa |
|---|---|---|
| 0.1 | Sơ đồ vẽ kiểu SQL phẳng (mọi bảng ngang hàng, FK `Usersid`). Firestore cài đặt là **lồng**: `users/{uid}/plants/{plantId}/{careSchedules,careLogs,diagnoses}`, `users/{uid}/identifications`. | Giữ trường FK (`userId`, `plantId`) trong tài liệu con để truy vấn collection group và để rules kiểm tra quyền. Ghi chú trên sơ đồ: "Plants, Diagnoses, CareSchedules, CareLogs, Identifications là subcollection của Users". |
| 0.2 | Kiểu `varchar(255)` cho dữ liệu có cấu trúc: `threshold`, `customThreshold`, `treatmentSnapshot`, `topCandidates`, `affectedSpeciesIds`, `fcmTokens`, `causes`, `symptoms`, `treatment*`, `prevention`. | Đổi sang **map** (`threshold`, `customThreshold`, `treatmentSnapshot`) hoặc **array** (các trường còn lại). |
| 0.3 | Trường enum ghi `varchar`, `tinyint(1)` cho bool. | Ghi rõ enum: `taskType`, `severity`, `role`, `status`, `lightNeed`; bool là `boolean`. |
| 0.4 | Nhiều bảng thiếu `createdAt`/`updatedAt`. | Bổ sung (xem từng bảng). |
| 0.5 | Tên FK viết `Plantsid`, `Speciesid` (không phải camelCase). | Thống nhất `plantId`, `speciesId`, `diseaseId`, `careScheduleId`, `userId`. |

## 1. Users
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `fcmTokens`: varchar → **array<string>** | Một người dùng nhiều thiết bị. |
| Giữ | `role` (user/admin), `status` (active/locked) | Rules mới: **người dùng không tự đổi được** `role`/`status`; quyền admin thật kiểm tra bằng custom claim. |
| Ghi chú | Dữ liệu cũ dùng `displayName`, `photoUrl`, `firstName`, `lastName`, `lastUpdated` | Đã bổ sung `fullName`, `avatarUrl`, `role`, `status`, `fcmTokens`, `createdAt`, `lastLoginAt` cho 5 tài liệu hiện có, **không xóa** trường cũ (app + admin web còn đọc). |

## 2. Species
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `threshold`: varchar → **map Threshold** `{soilMoistureCritical, soilMoistureMin/Max, temperatureMin/Max, airHumidityMin/Max, lightMin/Max}` | Ràng buộc min<max, critical<min. |
| Sửa | `aiLabel` phải **duy nhất** và chép **nguyên văn** nhãn model (vd. `"Snake plant (Sanseviera)"`) | Ánh xạ kết quả nhận diện chính xác. |
| Sửa | `lightNeed` → enum `LOW / MEDIUM / BRIGHT_INDIRECT / FULL_SUN` | |
| Thêm | `careTemplates` array `{taskType, repeatIntervalDays, title, note}` | Đề xuất lịch chăm sóc khoa học khi thêm cây (QT06: chỉ đề xuất, người dùng xác nhận). |
| Thêm | `otherNames, englishName, synonyms, family, origin, careLevel, growthRate, matureSize, wateringDescription, fertilizerDescription, soil/temperature/humidityDescription, toxicity{pets,humans,note}, propagation, commonProblems, careNotes, seasonalNotes` | Nội dung màn kết quả nhận diện. |
| Thêm | `searchKeywords`, `legacyId`, `dataVersion`, `createdAt`, `updatedAt` | Tìm loài không dấu; liên kết danh mục cũ `plants` (1–29). |

## 3. Diseases
| Loại | Trường | Lý do |
|---|---|---|
| **Bỏ** | `Speciesid` (FK 1 loài) | Model chẩn đoán phân loại **triệu chứng** dùng chung cho mọi loài; một bệnh gặp ở nhiều loài và ngược lại → quan hệ **n–n**, thể hiện bằng bảng `CareRules` (mục 8b). |
| Sửa | `aiLabel` = đúng `disease_id` model trả về; dùng luôn làm **id tài liệu** | 7 lớp: `healthy, leaf_spot, blight_large_necrosis, powdery_coating, rust_pustules, tip_margin_necrosis, chlorosis_discoloration`. |
| Sửa | `causes, symptoms, treatmentMild/Moderate/Severe, prevention` → **array<string>** | Hiển thị theo từng bước. |
| Sửa | `scientificName` → ghi nhóm tác nhân (nhiều chi nấm/vi khuẩn hoặc "phi sinh học") | Lớp triệu chứng không ứng với một loài mầm bệnh. |
| Thêm | `nameEn, description, lesionType, isHealthy, requiresPesticide, safetyNote, severityMap, createdAt, updatedAt` | `safetyNote` cho QT09; `isHealthy` để bản ghi "Không phát hiện bệnh" có `diseaseId` hợp lệ. |
| Đã làm | Xóa 3 tài liệu cũ `diseases/0, /1, /3`; chẩn đoán cũ (`disease_logs`) được ánh xạ sang aiLabel mới khi chuyển dữ liệu | |

## 4. Plants
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `customThreshold`: varchar → **map Threshold?**, chỉ dùng khi `useSpeciesThresholds = false` | |
| Thêm | `healthStatus` enum `UNKNOWN/HEALTHY/MILD/MODERATE/SEVERE`, `lastCheckAt`, `lastDiagnosisId` | Danh sách vườn hiển thị tình trạng mà không phải đọc toàn bộ lịch sử chẩn đoán. Chỉ cập nhật từ chẩn đoán **đáng tin** (QT02). |
| Thêm | `speciesName, scientificName` (bản sao) | Hiển thị danh sách nhanh. |
| Thêm | `identificationId`, `updatedAt` | |
| Ràng buộc | `plantedDate` ≤ hôm nay | |

## 5. Diagnoses
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `Diseasesid` → `diseaseId` **nullable** | `null` khi model không xác định được; khỏe thì = `healthy`. |
| Sửa | `severity` → enum `NONE/MILD/MODERATE/SEVERE` (6 mức model → 3 mức) | Thêm `modelSeverityLevel` giữ nguyên nhãn gốc của model. |
| Sửa | `treatmentSnapshot`: varchar → **map** `{diseaseName, severity, steps[], prevention[], safetyNote}` | QT04: bản sao không đổi khi admin sửa danh mục. |
| Sửa | `confidence` thống nhất thang **0–100** | Model trả %, Identifications cũng dùng %. |
| Thêm | `userId, aiLabel, isReliable, trend (IMPROVED/STABLE/WORSE), source (FROM_HOME/FROM_PLANT), modelVersion, note` | QT01/QT02/QT05; QT03. |
| Quan hệ | `speciesId` là dư thừa (suy được từ `plants.speciesId`) | Giữ làm **bản sao** để thống kê; ghi rõ là denormalized, không phải FK độc lập trên sơ đồ. |

## 6. CareSchedules
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `taskType` → enum `WATERING/FERTILIZING/PRUNING/SPRAYING/HEALTH_CHECK/OTHER` | Dữ liệu cũ WATER/FERTILIZER/CUT/MIST/ROTATE được quy đổi khi đọc. |
| Sửa | `active`: tinyint → boolean | |
| Thêm | `timeOfDay "HH:mm"`, `nextDueAt`, `lastDoneAt` | Hạn kế tiếp = **ngày hoàn thành thực tế + chu kỳ** (tránh dồn việc quá hạn); hẹn giờ nhắc. |
| Thêm | `userId, title, source (SPECIES_TEMPLATE/USER/TREATMENT), sourceDiagnosisId, createdAt, updatedAt` | Biết lịch sinh từ gợi ý loài hay từ hướng xử lý bệnh. |

## 7. CareLogs
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `CareSchedulesid` → `careScheduleId` **nullable** | Cho phép ghi nhật ký việc làm ngoài lịch. |
| Thêm | `userId, title, diagnosisId?, createdAt` | Sơ đồ thiếu `createdAt`; liên kết việc xử lý bệnh với lần chẩn đoán. |

## 8. Identifications
| Loại | Trường | Lý do |
|---|---|---|
| Sửa | `topCandidates`: varchar → **array** `[{aiLabel, confidence, speciesId?}]` (tối đa 3) | |
| Sửa | `Speciesid` → `speciesId` **nullable** | Nhãn chưa có trong danh mục. |
| Thêm | `selectedSpeciesId`, `isReliable`, `plantId?`, `modelVersion` | Độ tin cậy < 60% người dùng tự chọn loài (QT01); biết lần nhận diện nào tạo ra cây. |

## 8b. Thiếu bảng: kế hoạch điều trị theo loài × bệnh × mức độ
Sơ đồ chỉ có `Diseases.treatmentMild/Moderate/Severe` – **giống nhau cho mọi loài**, và Diagnoses chỉ lưu một chuỗi
`treatmentSnapshot`. Không thể hiện được: cùng bệnh nhưng mỗi loài xử lý khác (Lan ý nhạy flo, cây bắt mồi không bón phân,
cây củ vàng lá sau mùa hoa là bình thường…), và chẩn đoán phải sinh ra việc cần làm, đổi lịch chăm sóc, theo dõi tiến triển.

| Loại | Bảng / trường | Ý nghĩa |
|---|---|---|
| **Thêm bảng** | `CareRules (speciesId, diseaseId, likelihood, likelyCauses, avoid, isNormalCondition, normalNote, plans)` | Thay `Diseases.Speciesid` và mảng `affectedSpeciesIds`: quan hệ n–n có thuộc tính |
| Thêm | `Diseases.plans{MILD/MODERATE/SEVERE}` | Việc cần làm + đề xuất đổi lịch chung theo mức độ |
| Sửa | `Diagnoses.treatmentSnapshot` → map gộp (chung + riêng loài) | QT04 |
| Thêm | `Diagnoses.scheduleChanges, treatmentScheduleIds, previousDiagnosisId` | Lưu đề xuất đổi lịch và việc đã sinh; nối chuỗi lịch sử để theo dõi tiến triển |
| Thêm | `CareSchedules.kind, priority, occurrences, completedCount, status, pausedUntil` | Tách việc điều trị khỏi lịch định kỳ; lần chẩn đoán mới thay kế hoạch cũ (SUPERSEDED) |
| Bỏ | bản ghi `healthy` trong Diseases | "Khỏe" không phải bệnh: Diagnoses lưu `aiLabel = healthy`, `diseaseId = null` |
| Quan hệ | Diagnoses 1–n CareSchedules (`sourceDiagnosisId`), Diagnoses 1–n CareLogs (`diagnosisId`), Diagnoses 0..1–1 Diagnoses (`previousDiagnosisId`) | |

## 9. Quan hệ cần sửa trên sơ đồ
| Quan hệ | Sơ đồ | Đúng |
|---|---|---|
| Species – Diseases | 1–n (FK `Speciesid` trong Diseases) | **n–n** qua bảng `CareRules` |
| Identifications – Plants | không có | **1 – 0..1** (`identifications.plantId`, `plants.identificationId`) |
| Diagnoses – Species | FK riêng | Bỏ đường FK hoặc ghi "bản sao"; quan hệ chuẩn đi qua Plants |
| CareLogs – CareSchedules | bắt buộc | **0..1** (nhật ký ngoài lịch) |
| CareLogs – Diagnoses | không có | **0..1** (`careLogs.diagnosisId`) |
| CareSchedules – Diagnoses | không có | **0..1** (`sourceDiagnosisId`) khi lịch tạo từ hướng xử lý |
| Plants – Users | 1–n | Đúng; trong Firestore là subcollection |

## 10. Trạng thái trên Firestore (leafyapp-cf95a)
- `species`: 47 loài, `aiLabel` khớp 47/47 nhãn model, có `careTemplates`, `threshold`.
- `diseases`: 6 bệnh (id = aiLabel, không có `healthy`), có `plans` theo mức; đã xóa 3 tài liệu cũ.
- `careRules`: 282 tài liệu (47 loài × 6 bệnh).
- `users`: 5 tài liệu đã bổ sung trường theo thiết kế.
- Dữ liệu cũ của 5 người dùng đã chuyển sang `plants` (35), `careSchedules` (9), `careLogs` (6), `diagnoses` (3) bằng `seed/migrate_legacy.py`; 15 bản ghi mồ côi không chuyển. Collection cũ và danh mục `plants` đã xóa (26/11/2026), trừ 1 cây và `plants/21` mà IoT đang liên kết; sao lưu ở `D:\DATN\backup-firestore`.
- Security Rules mới đã publish (chặn tự đổi `role`/`status`).
