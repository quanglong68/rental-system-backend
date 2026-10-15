# Báo cáo phiên làm việc BE: fix CORS gateway cho FE (`:8443`)

Ngày: 2026-10-07. Không viết thêm code service nào trong phiên này (Eureka, gateway
filter, auth-service đã DONE từ trước). Chỉ sửa cấu hình CORS + recreate container.

## 1. Triệu chứng

- Ấn Đăng ký trên FE (`http://localhost:8443`) thất bại; header request tới
  `http://localhost:8080/api/v1/auth/register` đầy đủ nhưng không có response.
- Log `rental-gateway` không hề thấy request đó, trong khi gọi trực tiếp
  (PowerShell/`curl`) vẫn `register → 201` bình thường.

## 2. Nguyên nhân

Container `rental-gateway` đang chạy với `CORS_ALLOWED_ORIGINS=http://localhost:3000`
(verify bằng `docker inspect`), thiếu origin FE `:8443` → browser chặn ngay ở
preflight `OPTIONS`, request thật không bao giờ tới gateway. File `.env` chưa được
cập nhật theo FE (xem `AIRule`/kế hoạch: CORS qua env).

## 3. Đã làm

| File / thao tác | Nội dung |
|---|---|
| `.env` (local, gitignored) | `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8443` |
| `.env.example` (commit cho team) | Cùng giá trị trên + comment ghi chú FE Vite chạy `:8443` |
| `docker compose --env-file ..\.env up -d --force-recreate api-gateway` (từ `infra/`) | Recreate để nhận env mới; container healthy sau ~25s |

## 4. Verify

- `docker inspect rental-gateway` → `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8443`.
- Preflight tay: `OPTIONS /api/v1/auth/register` với `Origin: http://localhost:8443`
  → `200` + `Access-Control-Allow-Origin: http://localhost:8443`.
- Chuỗi smoke sau fix (qua gateway `:8080`): xem báo cáo FE phiên này
  (register/login/me/refresh/replay/logout đủ mã trạng thái).

## 5. Lưu ý cho team

- Mọi lần đổi `.env` phải recreate service liên quan (`up -d` thường không tự recreate
  khi chỉ đổi env) — kiểm tra bằng `docker inspect`, đừng tin `ps` báo `Up`.
- `.env` là local/dev-only, không commit; giá trị mẫu chuẩn nằm ở `.env.example`.
