# RoomPriceController — Giá phòng

Base path: `/api/v1/rooms/{roomId}/prices`; `roomId` là số dương.

| Method | Path | Query/body | Data payload |
|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/prices` | `SetRoomPriceRequest` | `RoomPriceResponse` |
| GET | `/api/v1/rooms/{roomId}/prices/current` | `date` tùy chọn | `RoomPriceResponse` |
| GET | `/api/v1/rooms/{roomId}/prices/history` | — | `RoomPriceResponse[]` |

Không truyền `date` thì dùng ngày hiện tại. `date` có định dạng `YYYY-MM-DD`. History sắp xếp `effectiveFrom` giảm dần.

## SetRoomPriceRequest

```json
{"price":5000000,"effectiveFrom":"2030-01-01"}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `price` | number | Có | Không âm |
| `effectiveFrom` | string ngày | Có | Không được trong quá khứ |

`RoomPriceResponse`: `id` và `roomId` (string), `price`, `effectiveFrom`, `effectiveTo` (có thể null), `createdAt`, `updatedAt`. Giá trước tự kết thúc ngày ngay trước ngày hiệu lực mới. Lỗi validation/ngày quá khứ có thể trả `400`; không tìm thấy phòng/giá có thể trả `404`.
