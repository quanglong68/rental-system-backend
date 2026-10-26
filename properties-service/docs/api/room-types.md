# RoomTypeController — Loại phòng

Base path: `/api/v1/room-types`

| Method | Path | Query/body | Data payload |
|---|---|---|---|
| POST | `/api/v1/room-types` | `RoomTypeRequest` | `RoomTypeResponse` |
| GET | `/api/v1/room-types/{id}` | — | `RoomTypeResponse` |
| GET | `/api/v1/room-types` | `page`, `size` | `Page<RoomTypeResponse>` |
| PUT | `/api/v1/room-types/{id}` | `RoomTypeRequest` | `RoomTypeResponse` |
| DELETE | `/api/v1/room-types/{id}` | — | `null` |

List mặc định `page=0`, `size=20`, sort `createdAt` giảm dần. Controller không đặt giới hạn page/size riêng.

## RoomTypeRequest

```json
{"typeCode":"STUDIO","name":"Studio","description":"Phòng dạng studio"}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `typeCode` | string | Có | Không rỗng, tối đa 50 ký tự |
| `name` | string | Có | Không rỗng, tối đa 100 ký tự |
| `description` | string/null | Không | — |

`RoomTypeResponse`: `id` (string), `typeCode`, `name`, `description`, `createdAt`, `updatedAt`. Validation có thể trả `400`; ID không tồn tại có thể trả `404`.
