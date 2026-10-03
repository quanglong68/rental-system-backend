# Báo cáo hoàn thành – Task B1: discovery-server (Eureka)

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task B1) + đặc tả mục 4.1 + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, chạy thật nghiệm thu dashboard.

## 1. File tạo mới (4 file) + 2 file sửa

| File | Nội dung |
|---|---|
| `discovery-server/pom.xml` | `spring-cloud-starter-netflix-eureka-server` + actuator + test; plugin Boot repackage; `finalName=discovery-server.jar` (khớp `COPY` trong Dockerfile) |
| `.../discovery/DiscoveryServerApplication.java` | `@EnableEurekaServer` + `@SpringBootApplication` |
| `discovery-server/src/main/resources/application.yml` | Port 8761, standalone (`register-with-eureka=false`, `fetch-registry=false`), actuator `health,info`; `hostname`/`defaultZone` qua env; self-preservation **mặc định false** (xem §3) |
| `discovery-server/Dockerfile` | Multi-stage: `maven:3.9.11-eclipse-temurin-21` build → `eclipse-temurin:21-jre` + `wget` (cho healthcheck), `-Duser.timezone=UTC`; build từ **repo root** để thấy parent pom |
| `infra/docker-compose.yml` (sửa) | discovery chuyển sang `build: {context: .., dockerfile: discovery-server/Dockerfile}` |
| `.dockerignore` (mới, root) | Loại `target/ .git/ .idea/ docs/ *.md .env infra/postgres/` khỏi build context |
| `pom.xml` root (sửa) | Thêm `execution repackage` vào pluginManagement (xem §3) |

## 2. Kết quả verify (chạy thật, không chỉ compile)

```
mvn -f discovery-server/pom.xml package  →  BUILD SUCCESS (repackaged BOOT-INF)
docker compose config  →  exit=0
java -jar discovery-server.jar:
  /actuator/health            → {"status":"UP"}
  / (dashboard)               → 200, có tiêu đề Eureka
  /eureka/apps                → 0 app (đúng: chưa service nào đăng ký)
```

## 3. Hai quyết định/bẫy đã xử lý

1. **Sai lầm suýt mắc — `repackage` không tự chạy:** jar build ra báo `no main manifest attribute`, log không có dòng repackage. Nguyên nhân: dự án import BOM thay vì kế thừa `spring-boot-starter-parent`, mà execution `repackage` nằm ở starter-parent chứ không phải BOM (BOM chỉ mang `dependencyManagement`). Đã khai `execution repackage` tường minh trong parent pluginManagement → mọi service sau (auth, gateway) được hưởng, không lặp lại lỗi.
2. **Lệch nhỏ so với plan:** `enable-self-preservation` để mặc định `false` (plan ghi `true`) vì dev 1 node — self-preservation giữ instance chết trong danh sách gây nhiễu; vẫn chỉnh được qua `EUREKA_SELF_PRESERVATION=true`.
3. Lần `compose config` báo exit=1 chỉ do tôi đứng sai thư mục (root thay vì `infra/`), không phải lỗi file — chạy lại đúng ra exit=0.

## 4. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task C1:** khung auth-service (port 8081, datasource `auth_db`, Eureka client, Kafka producer config, JWT props, Dockerfile).
