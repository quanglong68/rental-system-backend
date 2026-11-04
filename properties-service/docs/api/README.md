# API documentation for frontend

Đối chiếu với controller và DTO hiện hành. Base path `/api/v1`; mặc định chạy tại `http://localhost:8083` (đổi bằng `SERVER_PORT`).

| Controller | Tài liệu |
|---|---|
| BuildingController | [buildings.md](buildings.md) |
| ImageStorageController | [images.md](images.md) |
| ProvinceController | [provinces.md](provinces.md) |
| LocationController | [locations.md](locations.md) |
| RoomController | [rooms.md](rooms.md) |
| RoomStatisticsController | [rooms.md — thống kê phòng](rooms.md#thống-kê-phòng) |
| RoomPriceController | [room-prices.md](room-prices.md) |
| RoomTypeController | [room-types.md](room-types.md) |
| RoomOccupantController | [room-occupants.md](room-occupants.md) |
| WardController | [wards.md](wards.md) |

## Quy ước

- Endpoint trả thẳng DTO (không bọc envelope), đúng chuẩn `common-lib` của các service khác.
- Danh sách phân trang trả `PageResponse<T>` của `common-lib`: `{content, page, size, totalElements, totalPages}`.
- Lỗi trả `ErrorResponse` của `common-lib`: `{code, message, status, traceId, details?}`.
- POST tạo mới trả `201 Created`; DELETE trả `204 No Content` (không body).
- ID `Long` trong response được serialize thành **string**. Hãy giữ ID như chuỗi để tránh mất độ chính xác JavaScript. Khi truyền ID, dùng số dương.
- `Instant` theo ISO 8601 UTC; `LocalDate` theo `YYYY-MM-DD`.
- Springdoc API docs tại `/v3/api-docs` và Swagger UI bị tắt mặc định; bật bằng `OPENAPI_ENABLED` và `SWAGGER_UI_ENABLED`.
