# WardController — Phường/xã

Base path: `/api/v1/wards`

| Method | Path | Data payload |
|---|---|---|
| POST | `/api/v1/wards` | `WardResponse` |
| GET | `/api/v1/wards` | `WardResponse[]` |
| GET | `/api/v1/wards/{id}` | `WardResponse` |
| PUT | `/api/v1/wards/{id}` | `WardResponse` |
| DELETE | `/api/v1/wards/{id}` | `null` |
| GET | `/api/v1/wards/province/{provinceId}` | `WardResponse[]` |

`id` và `provinceId` là số dương. POST/PUT dùng JSON.

## WardRequest

```json
{"provinceId":"898785309339156480","code":"00001","name":"Phường Bến Nghé","divisionType":"Phường"}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `provinceId` | string số dương | Có | ID tỉnh |
| `code` | string | Có | Không rỗng, tối đa 20 ký tự |
| `name` | string | Có | Không rỗng, tối đa 150 ký tự |
| `divisionType` | string/null | Không | Tối đa 100 ký tự |

`WardResponse`: `wardId` (string), `provinceId` (string), `code`, `name`, `divisionType`, `createdAt`, `updatedAt`. Validation có thể trả `400`; ward/province không tồn tại có thể trả `404`.
