# Báo cáo hoàn thành – Task 0.2: `infra/docker-compose.yml` + `.env.example`

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task 0.2) + đặc tả mục 4, 5.3, 12 + `AIRule.md` + 5 chốt Q1–Q5.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, đã verify `docker compose config` exit=0.

## 1. Kiểm tra trước khi tạo (AIRule §2)

- Glob `infra/**/*` = 0 file, `.env*` = 0 file → cả 3 file dưới đây là tạo mới, không trùng lặp.

## 2. File tạo mới (3 file)

| File | Vai trò |
|---|---|
| `infra/docker-compose.yml` | 9 services: postgres, kafka, kafka-init, kafka-ui, minio, minio-init, discovery-server, auth-service, api-gateway |
| `infra/postgres/init-multiple-dbs.sh` | Script init DB, mount vào `/docker-entrypoint-initdb.d/` (chạy 1 lần khi volume còn trống) |
| `.env.example` | Placeholder toàn bộ biến môi trường, không giá trị thật (AIRule §5) |

## 3. Logic đã triển khai (đối chiếu đặc tả)

1. **Postgres** (`postgis/postgis:16-3.4`, đúng mục 2): script đọc `POSTGRES_MULTIPLE_DATABASES` tạo 7 DB `auth_db,user_db,property_db,listing_db,contract_db,billing_db,asset_db`. User riêng theo quy ước `<PREFIX>_DB_USER/_PASSWORD` (vd `auth_db` → `AUTH_DB_*`), được `GRANT CONNECT` + `ALL ON SCHEMA public` + default privileges (đủ quyền cho Flyway, đúng mục 4.3 "mỗi service một user riêng"). Tự bật `CREATE EXTENSION postgis` cho `property_db` (mục 4.3 + tìm bán kính mục 9.2).
2. **Kafka** (`apache/kafka:3.8.0`, KRaft 1 broker, `PLAINTEXT://kafka:9092`, `AUTO_CREATE_TOPICS_ENABLE=false`): container `kafka-init` tạo sẵn topic `auth.events` 3 partition RF=1 (mục 5.3, Table 5). `kafka-ui` ở 8090 cho dev (mục 3).
3. **MinIO**: `server /data --console-address ":9001"`; `minio-init` (image `mc`) tạo bucket `report-images` với `--ignore-existing` (mục 4.3).
4. **Publish port đúng spec (mục 4, 6.2):** chỉ `8080` (gateway) + `8090` (kafka-ui) + `9001` (minio console). Postgres/kafka/minio-API/discovery/auth chỉ trong `rental-net`.
5. **Thứ tự khởi động (mục 4.3):** healthcheck `pg_isready` / `kafka-topics --list` / `mc ready` / `actuator/health` + `depends_on` có `condition` (postgres/kafka/minio → discovery → service → gateway).
6. **Secret (AIRule §5 + Q2):** compose chỉ dùng `${VAR}` / `${VAR:?message}` (fail-fast khi thiếu `.env`); `.env.example` toàn placeholder (`JWT_SECRET=your_32_character_secret_here`, password `change_me_in_dot_env`). Không có secret thật trong git.
7. **Biến môi trường theo mục 12:** `JWT_SECRET/JWT_ISSUER` (auth+gateway), `SPRING_DATASOURCE_*` từng service, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `MINIO_*`, `CORS_ALLOWED_ORIGINS`.

## 4. Lỗi phát hiện khi verify và cách sửa

1. **Thiếu publish 9001:** lần `config` đầu chỉ thấy `8080`, `8090` → đã thêm `ports: ["9001:9001"]` cho minio. Verify lại đủ 3 port.
2. **`exit 0` trong script init:** entrypoint postgres có thể *source* (thay vì execute) file `.sh`, lúc đó `exit` sẽ giết cả entrypoint → đã đổi sang khối `if/else`, an toàn cả 2 chế độ.
3. **Không có bash local** (bash.exe là stub WSL hỏng) nên không chạy `bash -n` được → thay bằng kiểm tra tĩnh: file LF (không CR), cân bằng `if/fi` 5/5, `do/done` 1/1, heredoc `<<EOSQL`/đóng 2/2.

## 5. Kết quả verify

```
docker compose --env-file ../.env.example config  →  exit=0, 9 services
published ports: 8080 (gateway), 8090 (kafka-ui), 9001 (minio console) — đúng spec
init-multiple-dbs.sh: LF-OK, cấu trúc cân bằng
```

> Chưa `docker compose up` thật vì Dockerfile của discovery/auth/gateway là Task B1/C1/D1 (chưa có). `config` đã chứng minh YAML + interpolation đúng. Lần up đầu sẽ tạo 7 DB + topic + bucket tự động.

## 6. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task A1:** khung `common-lib` (`pom.xml`, `Headers`, `UserTypes`, `EventEnvelope`) — ưu tiên vì gateway/auth phụ thuộc.
