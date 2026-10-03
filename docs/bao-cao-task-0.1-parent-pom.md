# Báo cáo hoàn thành – Task 0.1: Parent `pom.xml` + quy ước chung

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task 0.1) + đặc tả mục 2 (Java 21, Boot 3.3.x, Cloud 2023.0.x) + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, đã verify build xanh.

## 1. Môi trường đã kiểm tra (không đoán mò)

| Công cụ | Kết quả |
|---|---|
| Java | 21.0.10 LTS (đúng yêu cầu Java 21) |
| Maven | 3.9.11 |
| Docker | 29.8.0 |
| Repo | Chưa có `pom.xml` nào (glob `*.xml` = 0 file) → tạo mới là đúng, không trùng lặp (AIRule §2) |

## 2. File tạo mới (1 file)

| File | Nội dung chính |
|---|---|
| `pom.xml` (root) | Parent pom, `groupId=com.rental`, `version=1.0.0-SNAPSHOT`, `<packaging>pom</packaging>` |

## 3. Logic đã triển khai

1. **Modules** (đúng docs mục 12, `common-lib` đứng trước vì gateway/auth phụ thuộc):
   `common-lib → discovery-server → api-gateway → auth-service`.
2. **Quy ước chung:** `release=21`, `sourceEncoding=UTF-8`, `user.timezone=UTC` (đúng docs mục 1: thời gian UTC).
3. **BOM import:** `spring-boot-dependencies 3.3.5` + `spring-cloud-dependencies 2023.0.3` (cặp tương thích Boot 3.3.x ↔ Cloud 2023.0.x).
4. **Quản lý version tường minh:** `jjwt 0.12.6` (api/impl/jackson – Auth ký, Gateway verify, HS256), `mapstruct 1.6.3`, `lombok 1.18.34`, `lombok-mapstruct-binding 0.2.0`, `springdoc-openapi 2.5.0`, `common-lib ${project.version}`.
5. **Compiler:** `maven-compiler-plugin` với `annotationProcessorPaths` Lombok + MapStruct-processor + binding (để `@Data` và `@Mapper` chạy chung không lỗi).
6. **Test:** `maven-surefire-plugin` ép `-Duser.timezone=UTC -Dfile.encoding=UTF-8`.
7. **Bảo mật (AIRule §5):** parent pom chỉ quản lý version, không khai báo secret nào; không thêm lib ngoài đặc tả.

## 4. Lỗi phát hiện khi verify và cách sửa

- **Hiện tượng:** `mvn help:evaluate -Dexpression=lombok.version` trả về `null` → property của BOM import **không** kế thừa được vào pom cha.
- **Hệ lụy nếu không sửa:** `${lombok.version}` trong `annotationProcessorPaths` vỡ khi compile module con.
- **Đã sửa:** khai báo tường minh `<lombok.version>1.18.34</lombok.version>` (đúng version Boot 3.3.5 quản lý). Verify lại → `1.18.34`.

## 5. Kết quả verify

```
mvn validate -N  →  BUILD SUCCESS (0.5s)
mvn help:evaluate →  lombok.version = 1.18.34
```

> Ghi chú: dùng cờ `-N` (non-recursive) vì 4 module con chưa được scaffold (Task A1/B1/C1/D1). Khi các module có mặt, `mvn validate` thường sẽ xanh. Đây là hành vi chuẩn của Maven multi-module, không phải lỗi.

## 6. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task 0.2:** `infra/docker-compose.yml` + `.env.example` + `infra/postgres/init-multiple-dbs.sh` (7 DB qua `POSTGRES_MULTIPLE_DATABASES`, Kafka sẵn topic `auth.events`, MinIO bucket `report-images`).
