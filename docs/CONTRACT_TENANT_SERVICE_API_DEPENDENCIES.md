# API phối hợp với `contract-tenant-service`

Tài liệu này chỉ ghi những API đồng bộ mà `contract-tenant-service` thực sự cần gọi sang service khác, cùng các luồng đã dùng Kafka. Mục tiêu là để các team thống nhất đúng giao diện, tránh tạo endpoint trùng hoặc gọi qua lại gây vòng lặp.

## 1. Property Service: lấy thông tin phòng

| Nội dung | Chi tiết |
|---|---|
| API | `GET /internal/rooms/{roomId}` |
| Khi nào gọi | Khi xử lý sự kiện `RoommateApplicationAccepted`, để kiểm tra phòng còn chỗ trước khi thêm người thuê. |
| Response cần có | `200 OK` kèm `id`, `buildingId`, `roomNumber`, `maxCapacity`, `status`. Ví dụ bên dưới. |
| Không tìm thấy phòng | Trả `404 NOT_FOUND`. |
| Service lỗi hoặc không gọi được | Contract trả lỗi `503 DEPENDENCY_UNAVAILABLE` và **không thêm người thuê**. |

```json
{
  "id": 42,
  "buildingId": 7,
  "roomNumber": "A.402",
  "maxCapacity": 3,
  "status": "OCCUPIED"
}
```

Contract dùng `maxCapacity` để so với số người đang ở. Không trả sức chứa mặc định nếu không đọc được thông tin phòng. Theo giao diện hiện tại, Property đã có API tra cứu phòng này; vui lòng giữ response tương thích, không cần tạo thêm API chi tiết phòng khác.

## 2. User/Staff Service: lấy các tòa nhân viên được phân công

| Nội dung | Chi tiết |
|---|---|
| API | `GET /internal/staff/{staffId}/buildings` |
| Khi nào gọi | Trước khi cho nhân viên xem hoặc xử lý hợp đồng/yêu cầu trả phòng, để kiểm tra nhân viên có quyền với tòa nhà đó không. `ADMIN` không cần gọi API này. |
| Response cần có | `200 OK` kèm `staffId` và danh sách `buildingIds`. |
| Không tìm thấy nhân viên | Trả `404 NOT_FOUND`. |
| Service lỗi hoặc thiếu `buildingIds` | Contract trả `503 DEPENDENCY_UNAVAILABLE` và từ chối thao tác; không được hiểu là nhân viên có quyền trên mọi tòa. |

```json
{
  "staffId": 101,
  "buildingIds": [7, 9]
}
```

Theo giao diện hiện tại, API này đã có trong `user-notification-service`; không cần tạo endpoint khác có cùng chức năng. `staffId` là ID tài khoản của người đang đăng nhập.

## 3. User Service: kiểm tra khách hàng tồn tại

| Nội dung | Chi tiết |
|---|---|
| API | `GET /internal/customers/{customerId}/exists` |
| Khi nào gọi | Trước khi tạo hợp đồng DRAFT thủ công (không qua Kafka), để xác nhận khách hàng có trong hệ thống. |
| Response cần có | `204 No Content` nếu khách hàng tồn tại. |
| Không tìm thấy khách hàng | Trả `404 NOT_FOUND` kèm mã `CUSTOMER_NOT_FOUND`. |
| Service lỗi hoặc không gọi được | Contract trả `503 DEPENDENCY_UNAVAILABLE` và **không tạo hợp đồng**. |

API này do `user-notification-service` cung cấp, dùng `CustomerRepository.existsById()`. Endpoint nội bộ, không mở qua API Gateway. Feign client dùng `contextId = "customerClient"` để phân biệt với `StaffClient` cùng service name.

## 4. Các luồng không cần gọi REST qua lại

Không tạo REST call từ Contract sang Listing hoặc Billing cho các luồng dưới đây. Dữ liệu đã được trao đổi bằng Kafka hoặc API nội bộ của Contract:

| Luồng | Cách phối hợp |
|---|---|
| Listing báo khách đã được duyệt thuê | Listing phát `RentalRequestAccepted` trên topic `listing.events`; Contract nhận sự kiện và tạo hợp đồng DRAFT. |
| Listing báo khách được duyệt ở ghép | Listing phát `RoommateApplicationAccepted` trên topic `listing.events`; Contract tìm hợp đồng **ACTIVE** của phòng, kiểm tra sức chứa rồi thêm applicant hoặc phát sự kiện từ chối. Contract **không tạo DRAFT** cho luồng này. |
| Contract thay đổi trạng thái | Contract phát sự kiện trên `contract.events`; Property, Listing, Billing và Notification tự nhận sự kiện cần dùng. |
| Billing cần dữ liệu hợp đồng tính tiền tháng | Billing gọi API đọc nội bộ ở mục 4, không cần Contract gọi ngược sang Billing. |

Contract phát các sự kiện chính: `ContractSigned`, `ContractRenewed`, `ContractEnded`, `TenantAdded`, `TenantAddRejected`, `TenantRemoved`, `MoveOutRequested`, `MoveOutApproved`.

## 5. API nội bộ Contract cung cấp cho service khác

Các API dưới đây do `contract-tenant-service` cung cấp để service khác đọc dữ liệu. Chúng không phải API cần team khác tạo.

| Service cần gọi | API | Dùng để làm gì |
|---|---|---|
| Billing | `GET /internal/contracts/billing-data?month={month}&year={year}` | Lấy hợp đồng có hiệu lực trong tháng, tiền thuê, ngày hợp đồng và thời gian ở của từng người (`joinedAt`, `leftAt`, `isRepresentative`) để tính tiền. |
| Listing | `GET /internal/rooms/{roomId}/tenants/{customerId}` | Kiểm tra khách có đang là người thuê trong hợp đồng ACTIVE hay không. |

Đây là API nội bộ giữa các service, không mở qua API Gateway cho người dùng cuối.

## 6. Quy ước gọi API

- Các service gọi nhau trong mạng nội bộ qua Eureka/OpenFeign.
- Chuyển tiếp các header định danh và truy vết: `X-User-Id`, `X-User-Type`, `X-User-Roles`, `X-Correlation-Id`. Tác vụ chạy nền dùng `X-User-Type: SYSTEM`.
- Timeout: kết nối 2 giây, đọc response 3 giây. Chỉ GET được retry tối đa 1 lần; không retry thao tác ghi.
- Lỗi timeout, 5xx hoặc service không hoạt động được trả thành `503 DEPENDENCY_UNAVAILABLE`. Khi không xác minh được quyền hoặc sức chứa, Contract phải dừng thao tác an toàn.
- Không gọi đồng bộ vòng lại service đang gọi mình trong cùng luồng xử lý. Dùng Kafka event hoặc API đọc nội bộ phù hợp ở trên.

## Nguồn đối chiếu

Đặc tả hệ thống: `docs/Đặc tả Hệ thống Microservices – Quản lý Cho thuê Phòng.docx` (mục 5.2, 5.3, 6.2, 9.2, 9.3, 9.4 và 10). Các đường dẫn API được đối chiếu với Feign client/controller hiện có của Contract và User/Staff.
