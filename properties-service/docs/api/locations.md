# Địa danh công khai

Base path: `/api/v1/locations`
Controller: `LocationController` — dùng chung service/mapper với `ProvinceController` và `WardController`.

Các endpoint này **không cần token** (khớp whitelist gateway và đặc tả mục 6.4), dùng cho form đăng tin, bộ lọc tòa nhà và trang chủ.

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/locations/provinces` | `ProvinceResponse[]` |
| GET | `/api/v1/locations/provinces/{provinceId}/wards` | `WardResponse[]` |

## Ghi chú mô hình dữ liệu

Đặc tả mô tả cấu trúc 3 cấp `province → district → ward`, nhưng migration `V6__replace_ward_district_with_province.sql` đã bỏ bảng `district` và chuyển sang mô hình 2 cấp `province → ward`. Vì vậy:

- Không có endpoint `/api/v1/locations/provinces/{id}/districts` hay `/api/v1/locations/districts/{id}/wards`.
- Phường/xã được tra cứu trực tiếp theo tỉnh qua `/api/v1/locations/provinces/{provinceId}/wards`.

## Lỗi

- `provinceId` không phải số dương → `400 Bad Request` (`ErrorResponse` chuẩn `common-lib`).
- Danh sách rỗng trả `200 OK` với mảng rỗng.

CRUD quản trị vẫn nằm ở [provinces.md](provinces.md) và [wards.md](wards.md) (yêu cầu token; ghi dữ liệu cần `ADMIN`).
