# Báo cáo Task C6 – Outbox + phát `AccountRegistered`

> Nguồn: `docs/plan-infra-auth-team.md` Task C6 + `docs/Đặc tả…` mục 5.3, 7.1, Table 16 + `AIRule.md`.
> Quyết định của bạn: payload gồm profile từ request; relay đồng bộ; được phép sửa hosts
> (thực tế không sửa được do thiếu quyền admin — dùng cách khác, xem mục 4).

## 1. File tạo mới (5)

- `outbox/AccountRegisteredPayload.java`: `{accountId, accountType, username, fullName,
  email, phone}` đúng Table 16 (profile lấy từ request vì auth_db không lưu — mục 9.1).
- `outbox/OutboxEventPublisher.java`: dựng `EventEnvelope.of("AccountRegistered", payload,
  correlationId)` (version 1, `eventId` random, UTC), serialize **cả envelope** lưu cột
  `payload` (JSONB), `topic=auth.events`, `eventKey=accountId`, `eventType=AccountRegistered`,
  `save()` cùng transaction tạo account. **Không** `kafkaTemplate.send` trực tiếp trong
  `AccountService` (đúng 5.3). `correlationId` đọc từ MDC (filter đã gắn) → header →
  random. `ObjectMapper` riêng có `JavaTimeModule` (serialize `Instant occurredAt`).
- `outbox/OutboxRelay.java`: `@Scheduled(fixedDelay=1000)` + `@Transactional`:
  `findTop100...PublishedAtIsNullOrderByCreatedAtAsc` → `kafka.send(topic,key,json).get(10s)`
  → thành công set `published_at` + save; lỗi → warn + để tick sau (không crash app;
  `InterruptedException` hoàn lại cờ interrupt). Producer `acks=all` + idempotence lấy từ
  `application.yml` (C1); `KafkaTemplate<String,String>` do Boot tự cấu hình.
- Test (không cần infra): `outbox/OutboxEventPublisherTest` (2 case: envelope đúng chuẩn —
  `eventType/version/eventId==id/payload` đủ field, `phone:null` giữ lại; mỗi lần sinh
  `eventId` khác nhau) + `outbox/OutboxRelayTest` (3 case: gửi đúng topic/key + đánh dấu
  published; lỗi → giữ lại; không có dòng mới → không gửi).

## 2. File sửa (2, tối thiểu)

- `service/AccountService.java`: `register()` + `createAccount()` gọi
  `outboxPublisher.publishAccountRegistered(...)` cuối hàm, trong cùng `@Transactional`
  (publisher là bean riêng nên join transaction hiện tại — rollback vẫn xóa dòng outbox
  đúng semantics) + import.
- `service/AccountServiceTest.java`: thêm `@Mock OutboxEventPublisher` (vì `@InjectMocks`).

## 3. Kết quả verify

- `mvn clean verify` → **Tests run: 58, Failures: 0, Errors: 0, BUILD SUCCESS**
  (53 cũ + 2 publisher + 3 relay).
- **Live test Kafka thật** (mục 5): `POST /register` → 201 → relay gửi trong ~1–2s →
  `kafka-console-consumer` đọc được đúng envelope `AccountRegistered` v1;
  DB `outbox_event.sent=t`; relay 0 warning. Đúng tiêu chí plan (kill Kafka → gửi lại
  ở tick sau nhờ logic catch-tiếp-tục; consumer User Service idempotent nên gửi trùng
  chấp nhận được).

## 4. Hạ tầng test thật (ghi lại để team tái dùng)

1. `rental-kafka` advertise `kafka:9092` (chỉ resolve trong Docker network) và **không
   publish 9092 ra host** → app chạy trên host không gửi được.
2. Sửa `C:\Windows\System32\drivers\etc\hosts` **bị từ chối quyền** (shell không admin).
   Giải pháp không cần admin, chỉ ảnh hưởng app đang test:
   - Proxy `kafka-proxy` (tạm thời, đúng mẫu `pg-proxy` có sẵn):
     `docker run -d --name kafka-proxy --network rental-system_rental-net -p 9092:9092
     alpine/socat:1.8.0.3 TCP-LISTEN:9092,fork,reuseaddr TCP:kafka:9092`.
   - File `extra-hosts` (`127.0.0.1 kafka`) + JVM flag
     `-Djdk.net.hosts.file=...\extra-hosts` khi boot app → advertised listener resolve được.
3. Topic `auth.events` đã có sẵn (do `kafka-init` tạo từ trước) — không phải tạo.
4. Lâu dài: chạy app trong compose network (`kafka:9092` resolve thẳng) thì không cần
   proxy/hosts-file. `kafka-proxy` chỉ là cầu tạm cho dev test trên host — xóa khi xong:
   `docker rm -f kafka-proxy`.

## 5. Bằng chứng live test (04/10/2026)

- `POST /register c6live01@gmail.com` → **201** `{"accountId":10,...,"accountType":"CUSTOMER"}`.
- Consumer `auth.events` (from-beginning):
  `{"eventId":"c00031e5-...","payload":{"email":"c6live01@gmail.com","phone":null,
  "fullName":"C6 Live","username":"c6live01@gmail.com","accountId":10,
  "accountType":"CUSTOMER"},"version":1,"eventType":"AccountRegistered",
  "occurredAt":"2026-10-04T02:51:23Z","correlationId":"55a66f71-..."}`
- `select event_type, event_key, published_at is not null as sent from outbox_event` →
  `AccountRegistered | 10 | t`.
- Các account tạo trước C6 (test C4/C5) không có dòng outbox — đúng vì code C6 chưa tồn
  tại lúc đó; từ nay mọi đường tạo account đều phát event.

## 6. Hướng dẫn tự test

```powershell
mvn -f auth-service\pom.xml clean verify   # 58 test, khong can infra
# Live test: start rental-postgres + pg-proxy + rental-kafka + kafka-proxy (muc 4),
# boot app voi -Djdk.net.hosts.file (muc 4), POST /register, consume:
docker exec rental-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic auth.events --from-beginning --max-messages 50 --timeout-ms 15000
```

## 7. Nợ sang Epic D (gateway) và E (nghiệm thu)

- **D1**: dựng `api-gateway/` (hiện root `mvn package` vẫn báo thiếu module — có từ trước).
- **D2**: `JwtAuthenticationFilter` (xóa header giả, whitelist, verify HS256/`iss`/`exp`,
  gắn `X-User-*`, chặn `/internal/**` → 404) + `CorrelationIdFilter` + CORS.
- **D3**: CORS qua env + `GatewayErrorHandler` đúng format + log `correlationId`.
- **E1**: `AuthFlowIT` (Testcontainers Postgres+Kafka: register → login → me → refresh →
  logout), Swagger mỗi service, `.github/workflows/ci.yml`.
