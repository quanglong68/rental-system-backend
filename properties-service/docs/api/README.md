# API documentation for frontend

Đối chiếu với controller và DTO hiện hành. Base path `/api/v1`; mặc định chạy tại `http://localhost:8083` (đổi bằng `SERVER_PORT`).

| Controller | Tài liệu |
|---|---|
| BuildingController | [buildings.md](buildings.md) |
| ImageStorageController | [images.md](images.md) |
| ProvinceController | [provinces.md](provinces.md) |
| RoomController | [rooms.md](rooms.md) |
| RoomStatisticsController | [rooms.md — thống kê phòng](rooms.md#thống-kê-phòng) |
| RoomPriceController | [room-prices.md](room-prices.md) |
| RoomTypeController | [room-types.md](room-types.md) |
| RoomOccupantController | [room-occupants.md](room-occupants.md) |
| WardController | [wards.md](wards.md) |

## Quy ước

- JSON endpoint trả `ApiResponse<T>`; mỗi tài liệu mô tả kiểu payload `data`. Các trường envelope bên ngoài `data` được định nghĩa bởi thư viện `fptu.exe202.signify.apiresponse`.
- ID `Long` trong response được serialize thành **string**. Hãy giữ ID như chuỗi để tránh mất độ chính xác JavaScript. Khi truyền ID, dùng số dương.
- `Instant` theo ISO 8601 UTC; `LocalDate` theo `YYYY-MM-DD`.
- Phân trang Spring dùng `Page` với `content`, `totalElements`, `totalPages`, `size`, `number` và các cờ trang.
- Springdoc API docs tại `/v3/api-docs` và Swagger UI bị tắt mặc định; bật bằng `OPENAPI_ENABLED` và `SWAGGER_UI_ENABLED`.
