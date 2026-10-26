# RoomController — Phòng

Base path: `/api/v1/rooms`

| Method | Path | Query/body | Data payload |
|---|---|---|---|
| POST | `/api/v1/rooms` | `RoomRequest` | `RoomResponse` |
| GET | `/api/v1/rooms/{id}` | — | `RoomResponse` |
| GET | `/api/v1/rooms` | `buildingId`, `page`, `size`, `sort` | `Page<RoomResponse>` |
| GET | `/api/v1/rooms/statistics` | `buildingId` (tùy chọn), ADMIN | `RoomStatisticsResponse` |
| GET | `/api/v1/rooms/building/{buildingId}` | `page`, `size` | `Page<RoomResponse>` |
| PUT | `/api/v1/rooms/{id}` | `RoomRequest` | `RoomResponse` |
| DELETE | `/api/v1/rooms/{id}` | — | `null` |

List mặc định `page=0`, `size=20`, `sort=createdAt,desc`; `page >= 0`, `1 <= size <= 100`. Sort hỗ trợ `createdAt`, `updatedAt`, `roomCode`, `status`, `basePrice` với hướng `asc`/`desc`. `buildingId` là filter tùy chọn.

## Thống kê phòng

`GET /api/v1/rooms/statistics?buildingId=898785309339156480`

Quyền `ADMIN`, theo các header danh tính đáng tin cậy do Gateway truyền: `X-User-Id`, `X-User-Type`, `X-User-Roles`. Thiếu danh tính trả `401`, không có quyền trả `403`. `buildingId` phải là số nguyên dương (`400` nếu sai); bỏ qua để thống kê toàn bộ. Tòa nhà không tồn tại hoặc đã xóa trả các số đếm bằng `0`.

Payload `data` trong `ApiResponse<RoomStatisticsResponse>`:

```json
{"totalRooms":100,"operatingRooms":60,"roomsNeedingUpdate":8,"businessDate":"2026-10-26"}
```

| Field | Kiểu | Quy tắc |
|---|---|---|
| `totalRooms` | number (long) | Tổng phòng thuộc tòa nhà chưa xóa, kể cả phòng bảo trì |
| `operatingRooms` | number (long) | Phòng có **hơn 1 người** đang ở; phòng chỉ có 1 người không thuộc chỉ số này, không phụ thuộc `status` |
| `roomsNeedingUpdate` | number (long) | Số phòng thỏa ít nhất một tiêu chí cần cập nhật bên dưới; mỗi phòng chỉ đếm một lần |
| `businessDate` | string | Ngày hiện tại theo `Asia/Ho_Chi_Minh` |

Người đang ở được tính theo `user_id` khác nhau trong từng phòng: `move_in_date <= businessDate`, `move_out_date` chưa có hoặc `>= businessDate`; trạng thái `ACTIVE`, hoặc `ENDED` nhưng có ngày rời phòng. Tính cả ngày vào và ngày ra; loại người chưa đến/đã rời trước ngày tính. Khác với số bản ghi assignment trong occupancy-summary, thống kê này không đếm trùng cùng một người trong cùng phòng.

Các tiêu chí cần cập nhật đã thống nhất:

- Có người ở nhưng phòng đang `VACANT` hoặc `MAINTENANCE`.
- Không có người ở nhưng phòng đang `OCCUPIED` hoặc `UPCOMING_VACANT`.
- Số người đang ở vượt `maxCapacity`.
- Mã phòng thiếu/trống; sức chứa thiếu hoặc < 1; diện tích thiếu hoặc <= 0; số phòng ngủ/tắm thiếu hoặc < 0; giá cơ bản thiếu hoặc < 0; trạng thái thiếu/không hợp lệ. Giá bằng 0 và số phòng ngủ/tắm bằng 0 vẫn hợp lệ. Liên kết tòa nhà/loại phòng được bảo đảm bởi NOT NULL và khóa ngoại hiện có.

Phòng `MAINTENANCE` không có người ở không bị đánh dấu chỉ vì bảo trì. Phòng `UPCOMING_VACANT` có người ở không bị đánh dấu chỉ vì sắp trống. Hai chỉ số `operatingRooms` và `roomsNeedingUpdate` có thể giao nhau, không cộng lại thành tổng số phòng.

Đây là dấu hiệu cần quản lý kiểm tra dựa trên dữ liệu đã đồng bộ, không phải xác nhận thực địa: sự kiện Kafka chậm hoặc hợp đồng đã ký nhưng chưa có người ở có thể làm xuất hiện phòng cần kiểm tra. API chỉ đọc, không tự sửa trạng thái hay phát sự kiện; không gọi service ngoài. Thống kê thực hiện bằng một truy vấn tổng hợp, không phụ thuộc phân trang.

### Kiểm thử (2026-10-26)

`gradlew.bat --no-daemon test --tests '*RoomStatisticsIntegrationTest' build`: **thành công**, 3 test tích hợp PostgreSQL/MockMvc, bao gồm số người 0/1/2, trùng user, ngày vào/ra, múi giờ, bộ lọc, dữ liệu thiếu, vượt sức chứa, HTTP 400/401/403. Đã sửa test support để nối `currentSchema` bằng `&` khi URL Testcontainers có sẵn query parameter. Trên môi trường Windows kiểm thử, đặt `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<workspace>\.gradle\tmp` với thư mục tồn tại để tránh lỗi socket tạm của Java.

Bộ test toàn dự án chưa xanh: lượt đầu có 30/72 test lỗi; lượt chạy lại sau sửa test support vẫn có lỗi ở kiểm thử ID, kết nối database và không hoàn tất nên đã dừng. Kết quả build thành công bên trên chỉ áp dụng bộ test thống kê được chọn, không khẳng định toàn bộ test dự án đã đạt.

## RoomRequest

```json
{"buildingId":"898785309339156480","roomTypeId":"898785309339156481","roomCode":"A-101","maxCapacity":4,"area":35.5,"bedroomCount":1,"bathroomCount":1,"basePrice":5000000,"status":"AVAILABLE"}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `buildingId`, `roomTypeId` | string số dương | Có | ID liên kết |
| `roomCode` | string | Có | Không rỗng, tối đa 50 ký tự |
| `maxCapacity` | integer | Có | >= 1 |
| `area` | number | Có | >= 0.01 |
| `bedroomCount`, `bathroomCount` | integer | Có | >= 0 |
| `basePrice` | number | Có | >= 0 |
| `status` | string | Có | Ví dụ `AVAILABLE`; giá trị hợp lệ theo nghiệp vụ |

`RoomResponse`: `id`, `buildingId`, `roomTypeId` (đều string), `roomCode`, `maxCapacity`, `status`, `area`, `bedroomCount`, `bathroomCount`, `basePrice`, `createdAt`, `updatedAt`. Input/page/sort sai có thể trả `400`; tài nguyên không tồn tại có thể trả `404`.
