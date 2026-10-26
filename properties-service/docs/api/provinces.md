# ProvinceController — Tỉnh/thành

Base path: `/api/v1/provinces`

| Method | Path | Data payload |
|---|---|---|
| POST | `/api/v1/provinces` | `ProvinceResponse` |
| GET | `/api/v1/provinces` | `ProvinceResponse[]` |
| GET | `/api/v1/provinces/{id}` | `ProvinceResponse` |
| PUT | `/api/v1/provinces/{id}` | `ProvinceResponse` |
| DELETE | `/api/v1/provinces/{id}` | `null` |

`id` là số dương. POST/PUT dùng `Content-Type: application/json`.

## ProvinceRequest

```json
{"code":"01","name":"Hà Nội","divisionType":"Thành phố trực thuộc trung ương"}
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `code` | string | Có | Không rỗng, tối đa 20 ký tự |
| `name` | string | Có | Không rỗng, tối đa 150 ký tự |
| `divisionType` | string/null | Không | Tối đa 100 ký tự |

## ProvinceResponse

`provinceId` (string), `code`, `name`, `divisionType`, `createdAt`, `updatedAt`.

Lỗi validation có thể trả `400`; ID không tồn tại có thể trả `404`.
