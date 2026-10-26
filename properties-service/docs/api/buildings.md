# BuildingController — Tòa nhà

Base path: `/api/v1/buildings`

| Method | Path | Query/body | Data payload |
|---|---|---|---|
| GET | `/api/v1/buildings` | `page`, `size`, `wardId`, `sort` | `Page<BuildingResponse>` |
| GET | `/api/v1/buildings/search` | `keyword`, `provinceId`, `wardId`, `page`, `size`, `sort` | `Page<BuildingSearchResponse>` |
| GET | `/api/v1/buildings/nearby` | `latitude`, `longitude`, `radiusMeters`, `page`, `size` | `Page<BuildingSearchResponse>` |
| POST | `/api/v1/buildings` | `BuildingRequest` | `BuildingResponse`; trạng thái luôn khởi tạo `ACTIVE`; HTTP 201 + `Location` |
| GET | `/api/v1/buildings/{buildingId}` | — | `BuildingResponse` |
| PUT | `/api/v1/buildings/{buildingId}` | `BuildingRequest` | `BuildingResponse` |
| DELETE | `/api/v1/buildings/{buildingId}` | — | `null` |

Mặc định `page=0`, `size=20`; yêu cầu `page >= 0`, `1 <= size <= 100`. List/search mặc định `sort=createdAt,desc`; thuộc tính hỗ trợ `createdAt`, `updatedAt`, `name`, hướng `asc`/`desc`. Search nhận keyword và lọc province/ward tùy chọn.

Nearby yêu cầu latitude [-90,90], longitude [-180,180], `radiusMeters > 0`; giới hạn bán kính mặc định 50.000m, chỉnh qua `BUILDING_SEARCH_MAX_RADIUS_METERS`. Sắp xếp mặc định gần nhất trước.

## Trạng thái tòa nhà

`buildingStatus` là chuỗi và nhận một trong các giá trị sau:

| Mã | Ý nghĩa |
|---|---|
| `ACTIVE` | Tòa nhà đang được khai thác cho thuê. |
| `INACTIVE` | Tòa nhà tạm ngừng tiếp nhận hoạt động cho thuê mới. |
| `MAINTENANCE` | Tòa nhà đang bảo trì. |
| `CLOSED` | Tòa nhà đã ngừng khai thác lâu dài. |

Khi tạo tòa nhà, hệ thống đặt `buildingStatus` thành `ACTIVE`. Trường này trong request tạo bị bỏ qua; client không thể tạo thẳng tòa nhà ở trạng thái khác.

Khi cập nhật bằng `PUT /api/v1/buildings/{buildingId}`, có thể gửi `buildingStatus` để đổi trạng thái. Nếu bỏ trường này hoặc gửi `null`, trạng thái hiện tại được giữ nguyên. Các mã không thuộc bảng trên bị từ chối với HTTP 400 (`INVALID_BUILDING_STATUS`).

Hệ thống tự đổi `ACTIVE` thành `INACTIVE` khi tòa nhà có phòng nhưng không còn người ở. Quy tắc được kiểm tra sau thay đổi phòng và sau khi cập nhật dữ liệu người thuê; ngày trả phòng vẫn được tính là ngày còn ở. Building chưa có phòng vẫn giữ `ACTIVE`. Việc tự cập nhật không ghi đè `MAINTENANCE` hoặc `CLOSED`, và hiện không tự đổi `INACTIVE` trở lại `ACTIVE`.

Chỉ được chuyển sang `CLOSED` khi không còn người ở và không có phòng mang trạng thái `OCCUPIED` hoặc `UPCOMING_VACANT`. Nếu điều kiện chưa đạt, API trả HTTP 409 (`BUILDING_HAS_RESIDENTS`).

## BuildingRequest

```json
{"name":"Green Apartment","address":"123 Nguyễn Huệ","wardId":"898785309339156481","provinceId":"898785309339156482","latitude":10.7769,"longitude":106.7009,"description":"Căn hộ cho thuê","buildingStatus":"ACTIVE"}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `name` | string | Có | Không rỗng, tối đa 200 ký tự |
| `address` | string | Có | Không rỗng, tối đa 500 ký tự |
| `wardId`, `provinceId` | string số dương | Có | ID đơn vị hành chính |
| `latitude` | number | Có | -90 đến 90 |
| `longitude` | number | Có | -180 đến 180 |
| `description` | string/null | Không | — |
| `buildingStatus` | string/null | Không | Khi cập nhật: `ACTIVE`, `INACTIVE`, `MAINTENANCE` hoặc `CLOSED`; khi tạo luôn là `ACTIVE` |

`BuildingResponse`: `buildingId` (string), `name`, `address`, `wardId` (string), `provinceId` (string), `latitude`, `longitude`, `description`, `buildingStatus`, `createdAt`, `updatedAt`.

`BuildingSearchResponse` bổ sung `buildingStatus`, `provinceName`, `wardName`, `distanceMeters` (có thể null) và `buildingUrlImage` (string/null). Trong kết quả `/search` và `/nearby`, `buildingUrlImage` là URL của ảnh đầu tiên của tòa nhà theo `display_order` tăng dần; nếu cùng thứ tự thì theo `building_image_id` tăng dần. Giá trị là `null` khi tòa nhà chưa có ảnh. Lỗi thường gặp: `400` input/page/sort/status, `404` building hoặc tham chiếu ward/province, `409` xóa building đang có phòng hoặc chuyển `CLOSED` khi chưa đủ điều kiện.
