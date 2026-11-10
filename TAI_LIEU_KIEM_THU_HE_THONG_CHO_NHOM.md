# TÀI LIỆU HƯỚNG DẪN KIỂM THỬ TOÀN DIỆN HỆ THỐNG PHÒNG TRỌ
### Dành cho thành viên nhóm thực hiện kiểm thử hệ thống (Manual Test Suite)
**Phiên bản hệ thống:** V3.0 (Tháng 11/2026 - Tích hợp VNPay Sandbox & Nhắn tin nội bộ Staff Chat)
**Kiến trúc:** Microservices Spring Boot (13 dịch vụ) + Kafka + React TypeScript + PostgreSQL + MinIO

---

## 🌐 THÔNG TIN MÔI TRƯỜNG KIỂM THỬ

| Thành phần | Đường dẫn / Thông tin truy cập | Ghi chú |
| :--- | :--- | :--- |
| **Frontend URL (Production)** | `https://rental-system-frontend-ten.vercel.app` | Giao diện Web trên Vercel |
| **Backend Gateway (Ngrok)** | `https://quaintly-ageless-corina.ngrok-free.dev` | Tunnel kết nối API Gateway 8080 |
| **Backend Local (Docker)** | `http://localhost:8080` | Khi chạy nội bộ máy chủ |
| **Cổng thanh toán VNPay Sandbox** | Ngân hàng: `NCB`<br>Số thẻ: `9704198526191432198`<br>Tên chủ thẻ: `NGUYEN VAN A`<br>Ngày phát hành: `07/15`<br>Mã OTP: `123456` | Kiểm thử thanh toán hóa đơn thật |
| **Tài khoản nhận VietQR Cọc HĐ** | Ngân hàng: `MBBank`<br>STK: `0348888999`<br>Chủ TK: `BQL CHUOI PHONG TRO THONG MINH` | Quét mã VietQR nộp cọc hợp đồng |
| **Script làm mới dữ liệu sạch (1-Click)** | `seed/reset-and-seed.bat` hoặc `start-fresh-system.bat` | Khôi phục 100% dữ liệu gốc bất kỳ lúc nào |

> ⚠️ **LƯU Ý QUAN TRỌNG VỀ DỮ LIỆU KIỂM THỬ (TEST DATA REPRODUCIBILITY):**
> Bất kỳ lúc nào bạn cần làm sạch dữ liệu sau khi test xong hoặc muốn đưa hệ thống về trạng thái ban đầu, hãy chạy file `seed/reset-and-seed.bat` (hoặc PowerShell: `.\seed\reset-and-seed.ps1`). Toàn bộ phòng trống, hợp đồng mẫu, thông báo và tài khoản sẽ được nạp lại mới 100%!

## PHẦN 1: BẢNG TỔNG HỢP MASTER TÀI KHOẢN & MẬT KHẨU

### 📌 Phân biệt 2 loại tài khoản Admin:
1. **Admin Khởi tạo (Bootstrap Admin)**: `e2e.admin@example.com` / Mật khẩu: `E2ePass123!` — Đây là tài khoản hệ thống dùng cho migration và script ngầm. **Khi kiểm thử giao diện thủ công, bạn KHÔNG cần dùng tài khoản này.**
2. **Admin Nghiệp vụ Toàn Hệ Thống**: `seed.admin@test.local` / Mật khẩu: `Test@12345` — Đây là tài khoản Quản trị viên chính thức của chuỗi trọ. **Dùng tài khoản này khi kiểm thử vai trò Admin.**

👉 **Mật khẩu chuẩn chung cho toàn bộ nhân sự và khách hàng:** `Test@12345`

### 1.1. Khối Nhân Sự & Quản Trị Nội Bộ (Staff Accounts)

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Phạm Vi Phụ Trách | Quyền Hạn & Nhiệm Vụ Trên UI |
| :--- | :--- | :--- | :--- | :--- |
| **ADMIN** | `seed.admin@test.local` | `Test@12345` | Toàn bộ hệ thống | Quản trị tối cao: Quản lý tòa nhà, danh mục phòng, duyệt hợp đồng, báo cáo doanh thu tài chính, quản lý phân quyền nhân sự, bảng giá dịch vụ. |
| **QUẢN LÝ** | `seed.manager.a@test.local` | `Test@12345` | Tòa Seed House A (Bình Thạnh) | Thẩm định duyệt hợp đồng, ký số đại diện bên cho thuê bằng chuột, duyệt trả phòng, phân công thợ, chốt điện nước và quản lý tài chính Tòa A. |
| **QUẢN LÝ** | `seed.manager.b@test.local` | `Test@12345` | Tòa Seed House B (Bình Thạnh) | Thẩm định yêu cầu thuê phòng B202/B204/B205, ký số HĐ Tòa B, quản lý khách thuê và vận hành công nợ Tòa B. |
| **SALE** | `seed.sale.a@test.local` | `Test@12345` | Tòa Seed House A (Bình Thạnh) | Tiếp nhận Lead, xác minh ảnh CCCD 2 mặt của khách, xác nhận lịch hẹn xem phòng, dẫn khách xem phòng. Menu tinh gọn: không xem hóa đơn và bảo trì. |
| **SALE** | `seed.sale.b@test.local` | `Test@12345` | Tòa Seed House B (Bình Thạnh) | Tiếp nhận Lead Tòa B, xác minh hồ sơ khách, xếp lịch và dẫn xem phòng B202/B205 (VIEWED). Không có quyền duyệt hợp đồng. |
| **KỸ THUẬT** | `seed.tech.a@test.local` | `Test@12345` | Tòa Seed House A (Bình Thạnh) | Tiếp nhận phiếu báo hỏng được giao, bấm xử lý (IN_PROGRESS), nhập chi phí vật tư và hoàn tất sửa chữa; ghi số công tơ điện nước Tòa A; có quyền xem danh sách Nhân sự và nhắn tin nội bộ. |
| **KỸ THUẬT** | `seed.tech.b@test.local` | `Test@12345` | Tòa Seed House B (Bình Thạnh) | Kiểm tra hiện trạng thiết bị phòng, xử lý sự cố bảo trì và hỗ trợ ghi nhận chỉ số công tơ điện nước Tòa B; nhắn tin nội bộ trao đổi công việc. |

### 1.2. Khối Cư Dân & Khách Hàng Kiểm Thử (Customer Accounts)

| Tình Trạng Khách | Tài Khoản (Username) | Mật Khẩu | Phòng Đang Ở | Mục Đích Sử Dụng Trong Kiểm Thử |
| :--- | :--- | :--- | :--- | :--- |
| **Khách Đang Thuê (Chủ phòng)** | `seed.active@test.local` | `Test@12345` | Phòng A104 (Seed House A) | Chủ phòng A104. Dùng để test: Đăng tin tìm bạn ở ghép & duyệt ứng viên (Luồng 4), gửi phiếu báo hỏng có ảnh (Luồng 5), thanh toán VNPay Sandbox (Luồng 8). |
| **Khách Sắp Ra (Hết hạn HĐ)** | `seed.renew@test.local` | `Test@12345` | Phòng A105 (Seed House A) | Hợp đồng sắp hết hạn. Dùng để test gửi form 'Yêu cầu trả phòng' có báo trước (Luồng 6) để kiểm tra buffer 10 ngày dọn dẹp và quyết toán cọc. |
| **Khách Quá Hạn Thanh Toán** | `seed.terminate@test.local` | `Test@12345` | Phòng A107 (Seed House A) | Dùng để kiểm thử hóa đơn phòng ở trạng thái nợ quá hạn (OVERDUE), cảnh báo chuông nhắc nợ và tính năng chặn gia hạn hợp đồng. |
| **Thành Viên Ở Ghép (Roommate)** | `seed.mate@test.local` | `Test@12345` | Phòng A104 (Seed House A) | Người ở ghép cùng A104. Dùng để kiểm tra: Hóa đơn cá nhân chia tách theo số ngày (Luồng 4) và kiểm thử chặn quyền yêu cầu trả phòng (Luồng 14). |
| **Khách Mới Toanh (Chưa có phòng)** | `seed.new@test.local` | `Test@12345` | Chưa có hợp đồng | Khách hàng mới. Dùng để test: Đặt lịch xem phòng (Luồng 1), Nộp hồ sơ đăng ký thuê phòng B204 (Luồng 2), Nộp đơn xin ở ghép (Luồng 4). |
| **Khách Cạnh Tranh Thuê** | `seed.compete@test.local` | `Test@12345` | Chưa có hợp đồng | Cùng nộp đơn thuê phòng B205. Dùng để kiểm thử cơ chế tự động từ chối (REJECT) khi một khách hàng khác đã hoàn tất ký hợp đồng phòng đó. |
| **Khách Đang Chờ Duyệt** | `seed.pending@test.local` | `Test@12345` | Chưa có hợp đồng | Có yêu cầu thuê ở trạng thái PENDING. Dùng để test tính năng Khách hàng chủ động bấm 'Hủy yêu cầu' và ghi lý do hủy (Luồng 3). |
| **Khách Có Lịch Hẹn Chờ** | `seed.appt@test.local` | `Test@12345` | Chưa có hợp đồng | Có lịch hẹn xem phòng SCHEDULED. Dùng để test tính năng Khách hàng chủ động bấm 'Hủy lịch hẹn' (Luồng 3). |
| **Khách Đã Được Duyệt Hồ Sơ** | `seed.accept@test.local` | `Test@12345` | Chưa có hợp đồng | Đơn đã ACCEPTED / Tạo DRAFT. Dùng để test tính năng chặn hủy đơn khi đã duyệt và kiểm thử nộp cọc VietQR + ký chuột. |
| **Khách Bị Từ Chối Hồ Sơ** | `seed.reject@test.local` | `Test@12345` | Chưa có hợp đồng | Đơn bị REJECTED. Dùng để kiểm tra giao diện hiển thị rõ lý do bị từ chối và chặn các thao tác tiếp theo. |
| **Khách Mới Bàn Giao** | `seed.handover@test.local` | `Test@12345` | Phòng A108 (Seed House A) | Khách mới ký xong HĐ, chuyển vào hôm nay. Dùng để test ghi mốc công tơ bàn giao khách mới (tab 1) không tính tiền (Luồng 7A). |

---

## PHẦN 2: CÁC QUY ĐỊNH VẬN HÀNH HỆ THỐNG CẦN NẮM VỮNG TRƯỚC KHI TEST

### 2.1. Danh Sách Chủ Nhà (Bên A) Pháp Lý Theo Từng Tòa Nhà
Hệ thống đã chuẩn hóa thông tin Chủ nhà hiển thị đồng bộ từ trang Khám phá, Drawer hồ sơ đến Hợp đồng PDF:

| Tòa Nhà / Dự Án | Họ Tên Chủ Nhà (Bên A) | Số Điện Thoại | Số CCCD | Địa Chỉ Thường Trú |
| :--- | :--- | :--- | :--- | :--- |
| **Seed House A** | Ông Nguyễn Văn An | 0903 123 456 | 079080001234 | 12 Đường Seed A, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh |
| **Seed House B** | Ông Trần Đức Bình | 0912 345 678 | 079085005678 | 18 Đường Seed B, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh |
| **Mộc Residence (Studio)** | Bà Lê Hoàng Mai | 0987 654 321 | 079188009999 | 45 Trương Định, Phường Bến Nghé, Quận 3, TP. Hồ Chí Minh |
| **Lam Garden** | Ông Phạm Quốc Cường | 0933 888 999 | 079075001122 | 68 Đường số 2, Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh |

### 2.2. Thời Hạn Hợp Đồng Chuẩn Hóa Mặc Định 12 Tháng (1 Năm)
- Toàn bộ các luồng đăng ký thuê, tạo hợp đồng nháp và văn bản PDF đều mặc định thời hạn là **12 tháng (1 năm)** tính từ ngày bắt đầu.
- Giúp giao diện gọn gàng, người thuê không phải chọn thủ công các kỳ hạn ngắn không phù hợp.

### 2.3. Quy Định Chuyển Tiền Cọc & Vẽ Chữ Ký Chuột Trên Canvas
1. **Nộp cọc trước khi ký:** Khách bắt buộc phải chuyển khoản cọc qua mã VietQR và bấm xác nhận trước khi hệ thống mở khóa chức năng ký số.
2. **Vẽ chữ ký chuột trên Canvas:** Cả Khách hàng và Quản lý dùng chuột (hoặc chạm tay nếu dùng màn hình cảm ứng) để vẽ chữ ký trực tiếp trên khung Canvas Signature Pad.
3. **Thời hạn ký hợp đồng 2 ngày:** Kể từ lúc hợp đồng nháp được tạo, các bên có tối đa 2 ngày để hoàn tất ký kết; quá hạn hệ thống sẽ tự động hủy hợp đồng và mở lại phòng.

### 2.4. Quy Định Phân Quyền & Giao Tiếp Nội Bộ Mới Nhất
1. **Nhân viên Sale:** Chỉ phụ trách Leads, Lịch xem phòng, Khách hàng, Hợp đồng, Nhân sự và Tin nhắn. **Menu Hóa đơn và Bảo trì đã bị ẩn/chặn hoàn toàn khỏi tài khoản Sale**.
2. **Kỹ thuật viên:** Được mở quyền truy cập danh sách Nhân sự (`/admin/staff`) và Tin nhắn (`/admin/messages`) để phối hợp xử lý sự cố.
3. **Nút Nhắn tin nội bộ (Staff Chat):** Tại trang `/admin/staff`, mỗi nhân viên đều có nút "Nhắn tin" bên cạnh để mở ngay cuộc trò chuyện 1-1 với đồng nghiệp.

---

## PHẦN 3: CHI TIẾT 15 LUỒNG KIỂM THỬ HỆ THỐNG (END-TO-END MANUAL TEST FLOWS)

### LUỒNG 1: QUY TRÌNH ĐẶT LỊCH HẸN XEM PHÒNG ĐỘC LẬP & SALE ĐÓN TIẾP

**🎯 Mục tiêu kiểm thử:** Khách hàng chủ động chọn ngày giờ và đặt lịch xem phòng trực tiếp trên trang tin đăng Explore. Luồng này hoàn toàn ĐỘC LẬP với yêu cầu thuê phòng, không yêu cầu ảnh CCCD hay tiền cọc, và kết thúc sau khi Sale xác nhận đã dẫn khách xem phòng (VIEWED).

**🏢 Dữ liệu / Tiền điều kiện:** Phòng B202 hoặc B205 (Tòa Seed House B). Trạng thái phòng: Trống sẵn sàng đón khách.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách hàng (Customer)** | `seed.new@test.local` | `Test@12345` | Đăng nhập -> Xem chi tiết phòng B202 -> Bấm 'Đặt lịch xem phòng' -> Chọn ngày giờ, ghi chú đón tiếp -> Xác nhận đặt lịch. |
| **Nhân viên Sale Tòa B** | `seed.sale.b@test.local` | `Test@12345` | Đăng nhập -> Vào /admin/leads (tab Lịch hẹn) -> Bấm 'Xác nhận' (CONFIRMED) -> Dẫn khách xem xong bấm 'Đã xem' (VIEWED). |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Khách hàng chọn phòng:** Đăng nhập tài khoản khách hàng seed.new@test.local. Truy cập trang Khám phá (/listings). Tìm và bấm vào thẻ phòng B202 thuộc tòa Seed House B. Xem thông tin giá thuê, diện tích, tiện ích và chủ nhà phụ trách (Ông Trần Đức Bình).
2. **Bước 2 - Mở modal Đặt lịch xem phòng:** Tại trang chi tiết phòng B202, quan sát góc phải hiển thị 2 nút bấm màu sắc độc lập: nút chính màu xanh navy 'Đặt lịch xem phòng' và nút phụ viền trắng 'Đăng ký thuê phòng'. Bấm nút 'Đặt lịch xem phòng'.
3. **Bước 3 - Điền thông tin và xác nhận đặt lịch:** Hộp thoại modal 'Đặt lịch xem phòng B202' hiển thị. Chọn ngày xem thực tế (ví dụ: ngày mai), chọn khung giờ (ví dụ: 10:30 sáng) và nhập ghi chú đón tiếp (ví dụ: 'Tôi muốn xem phòng cùng bạn'). Bấm nút 'Xác nhận đặt lịch xem'.
4. **Bước 4 - Khách hàng theo dõi lịch hẹn:** Sau khi đặt thành công, hệ thống chuyển hướng hoặc khách vào menu 'Lịch xem phòng' (/requests). Kiểm tra lịch hẹn mới hiển thị với badge trạng thái màu vàng 'Chờ xác nhận' (SCHEDULED) kèm huy hiệu 'Mới'.
5. **Bước 5 - Sale tiếp nhận và xác nhận lịch hẹn:** Đăng xuất tài khoản khách. Đăng nhập tài khoản Sale Tòa B seed.sale.b@test.local. Vào menu 'Quản lý Leads' (/admin/leads), chọn tab 'Lịch xem phòng'. Tìm thấy lịch hẹn phòng B202 vừa tạo. Bấm nút 'Xác nhận' -> Trạng thái đổi thành 'Đã xác nhận' (CONFIRMED).
6. **Bước 6 - Sale hoàn tất đón tiếp khách xem phòng:** Sau khi nhân viên Sale đón tiếp và dẫn khách xem phòng thực tế tại tòa nhà Seed House B, quay lại hệ thống bấm nút 'Đã xem' -> Trạng thái lịch hẹn chuyển sang 'Đã xem' (VIEWED). Luồng kiểm thử hoàn tất thành công.

#### ✅ Kết quả mong đợi (Expected Results):

- Lịch hẹn xem phòng được tạo độc lập, không bắt buộc nộp CCCD hay đóng cọc.
- Badge trạng thái chuyển tuần tự: Chờ xác nhận (SCHEDULED) -> Đã xác nhận (CONFIRMED) -> Đã xem (VIEWED).
- Nhân viên Sale Tòa B chỉ nhìn thấy lịch hẹn thuộc tòa nhà Seed House B mình phụ trách.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 1.1]`: *Trang Khám phá (Explore) danh sách phòng trọ có sẵn.*
> - `[Ảnh 1.2]`: *Chi tiết phòng B202 tách riêng 2 nút Đặt lịch xem phòng và Đăng ký thuê.*
> - `[Ảnh 1.3]`: *Modal Đặt lịch hẹn xem phòng độc lập (chọn ngày giờ và ghi chú).*
> - `[Ảnh 1.4]`: *Màn hình Khách hàng theo dõi lịch xem phòng trạng thái SCHEDULED.*
> - `[Ảnh 1.5]`: *Nhân viên Sale Tòa B tiếp nhận và bấm Xác nhận lịch hẹn (CONFIRMED).*
> - `[Ảnh 1.6]`: *Nhân viên Sale cập nhật trạng thái Đã dẫn xem xong (VIEWED).*

---

### LUỒNG 2: QUY TRÌNH THUÊ PHÒNG, NỘP CỌC VIETQR, VẼ CHỮ KÝ CHUỘT CANVAS & QUẢN LÝ DUYỆT HĐ

**🎯 Mục tiêu kiểm thử:** Khách hàng nộp hồ sơ thuê phòng chính thức kèm ảnh CCCD 2 mặt. Sale thẩm định hồ sơ, Quản lý phê duyệt tạo hợp đồng nháp. Khách hàng chuyển khoản tiền cọc qua VietQR trước khi mở khóa khung vẽ chữ ký điện tử bằng chuột trên Canvas. Quản lý ký số hoàn tất kích hoạt hợp đồng.

**🏢 Dữ liệu / Tiền điều kiện:** Phòng B204 (Tòa Seed House B) | Tiền thuê: 6.500.000đ/tháng | Tiền cọc: 6.500.000đ | Hạn ký: 2 ngày | Thời hạn HĐ chuẩn: 12 tháng (1 năm).

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách hàng (Customer)** | `seed.new@test.local` | `Test@12345` | Bấm 'Đăng ký thuê phòng' -> Tải ảnh CCCD 2 mặt -> Chờ duyệt -> Quét mã VietQR nộp cọc -> Dùng chuột vẽ chữ ký trên Canvas -> Gửi chữ ký. |
| **Nhân viên Sale Tòa B** | `seed.sale.b@test.local` | `Test@12345` | Vào /admin/leads -> Bấm 'Xem hồ sơ' kiểm tra 2 mặt ảnh CCCD -> Bấm 'Xác minh hồ sơ' (VERIFIED). Không có quyền duyệt HĐ. |
| **Quản lý Tòa B** | `seed.manager.b@test.local` | `Test@12345` | Vào /admin/contracts -> Bấm 'Duyệt yêu cầu & Tạo HĐ nháp' -> Khách ký xong -> Quản lý kiểm tra tiền cọc, dùng chuột ký số đại diện bên cho thuê -> Kích hoạt HĐ. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Khách mở Drawer đăng ký thuê phòng:** Khách hàng seed.new@test.local vào trang chi tiết phòng B204 -> Bấm nút 'Đăng ký thuê phòng'. Ngăn kéo (Drawer) trượt ra từ bên phải màn hình. Bước 1 hiển thị các điều khoản: Thời hạn hợp đồng mặc định là 12 tháng (1 năm), Chủ nhà là Ông Trần Đức Bình, tiền cọc 6.500.000đ. Tích chọn cam kết đồng ý điều khoản -> Bấm 'Tiếp tục'.
2. **Bước 2 - Điền thông tin cá nhân:** Tại Bước 2 của Drawer, điền thông tin người thuê: Họ và tên, Số điện thoại, Số CCCD (đủ 12 chữ số), Ngày sinh (đảm bảo đủ từ 18 tuổi trở lên). Bấm 'Tiếp tục'.
3. **Bước 3 - Tải ảnh CCCD 2 mặt & chọn ngày bắt đầu:** Tại Bước 3, bấm tải lên 2 tệp ảnh mặt trước và mặt sau của thẻ CCCD. Chọn ngày bắt đầu dọn vào ở (ví dụ: ngày 15 của tháng). Bấm 'Nộp hồ sơ thuê phòng'. Hệ thống hiển thị thông báo gửi hồ sơ thành công, trạng thái hồ sơ là PENDING.
4. **Bước 4 - Nhân viên Sale xác minh hồ sơ CCCD:** Đăng nhập Sale Tòa B seed.sale.b@test.local -> Vào menu /admin/leads (tab Hồ sơ thuê). Thấy yêu cầu mới của phòng B204. Bấm nút 'Hồ sơ' -> Modal hiển thị hình ảnh 2 mặt CCCD của khách. Bấm 'Xác minh hồ sơ' (VERIFIED). Lưu ý: Sale không có nút Duyệt hợp đồng.
5. **Bước 5 - Quản lý phê duyệt hồ sơ và tạo hợp đồng nháp:** Đăng nhập Quản lý Tòa B seed.manager.b@test.local -> Vào /admin/contracts. Thấy yêu cầu phòng B204 đã được Sale xác minh. Quản lý kiểm tra điều khoản và bấm 'Duyệt yêu cầu & Tạo hợp đồng nháp'. Hợp đồng chuyển sang trạng thái DRAFT (Nháp), hệ thống đếm ngược thời hạn ký 2 ngày.
6. **Bước 6 - Khách hàng nộp cọc VietQR:** Khách hàng seed.new@test.local đăng nhập lại, vào /requests (hoặc /contracts) mở hợp đồng nháp. Tại Bước 1: Khung nộp cọc hiển thị mã VietQR động cùng STK Ban quản lý (0348888999, MBBank) với đúng số tiền cọc 6.500.000đ. Khách bấm 'Xác nhận đã chuyển tiền cọc' -> Hệ thống mở khóa bước ký tên.
7. **Bước 7 - Khách hàng dùng chuột vẽ chữ ký trên Canvas:** Khung vẽ chữ ký điện tử (Canvas Signature Pad) mở ra. Khách hàng dùng chuột máy tính hoặc ngón tay (trên màn hình cảm ứng) vẽ nét chữ ký thực tế của mình -> Có thể bấm 'Xóa vẽ lại' nếu chưa ưng ý. Sau khi vẽ xong, bấm 'Ký hợp đồng'. Hệ thống chuyển chữ ký thành vector nhúng vào hợp đồng.
8. **Bước 8 - Quản lý ký số đại diện bên cho thuê và kích hoạt hợp đồng:** Quản lý Tòa B seed.manager.b@test.local mở hợp đồng, xem chữ ký của khách và xác nhận cọc đã vào tài khoản. Quản lý dùng chuột vẽ chữ ký số đại diện bên cho thuê lên Canvas -> Bấm 'Duyệt và Kích hoạt hợp đồng'. Hợp đồng chuyển trạng thái ACTIVE, hệ thống tự động sinh file PDF hợp đồng đầy đủ 2 chữ ký. Phòng B204 chuyển sang trạng thái OCCUPIED (Đang thuê).

#### ✅ Kết quả mong đợi (Expected Results):

- Hồ sơ thuê bắt buộc phải có ảnh CCCD 2 mặt và người thuê đủ 18 tuổi.
- Thời hạn hợp đồng luôn cố định 12 tháng (1 năm) trên toàn hệ thống.
- Khách hàng bắt buộc phải xác nhận nộp cọc VietQR trước khi mở khóa khung vẽ chữ ký chuột.
- Chữ ký là hình vẽ thực tế trên Canvas (không phải nút bấm giả lập), được nhúng vào file PDF hợp đồng.
- Sau khi Quản lý ký duyệt, HĐ chuyển thành ACTIVE và phòng B204 không còn hiển thị trống trên Explore.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 2.1]`: *Drawer đăng ký thuê phòng - Bước 1 Điều khoản (12 tháng, Chủ nhà Trần Đức Bình).*
> - `[Ảnh 2.2]`: *Drawer Bước 3 - Tải ảnh CCCD 2 mặt và chọn ngày bắt đầu dọn vào ở.*
> - `[Ảnh 2.3]`: *Sale kiểm tra ảnh CCCD và bấm Xác minh hồ sơ (VERIFIED).*
> - `[Ảnh 2.4]`: *Quản lý bấm Duyệt tạo hợp đồng nháp (DRAFT).*
> - `[Ảnh 2.5]`: *Khách mở hợp đồng, quét mã VietQR nộp tiền cọc 6.500.000đ.*
> - `[Ảnh 2.6]`: *Khách hàng dùng chuột vẽ nét chữ ký trên khung Canvas Signature Pad.*
> - `[Ảnh 2.7]`: *Quản lý vẽ chữ ký số đại diện bên cho thuê và bấm Duyệt kích hoạt hợp đồng.*
> - `[Ảnh 2.8]`: *Hợp đồng chuyển trạng thái ACTIVE và tải file PDF hợp đồng có đủ 2 chữ ký.*

---

### LUỒNG 3: QUY TRÌNH KHÁCH HÀNG CHỦ ĐỘNG HỦY YÊU CẦU & HỦY LỊCH HẸN

**🎯 Mục tiêu kiểm thử:** Cho phép khách hàng chủ động hủy yêu cầu thuê phòng hoặc lịch xem phòng khi thay đổi kế hoạch lưu trú, đồng thời bảo đảm hệ thống kiểm soát chặt chẽ: chỉ được hủy khi đơn đang ở trạng thái PENDING hoặc SCHEDULED, và chặn hủy khi đơn đã được duyệt hoặc đã ký kết.

**🏢 Dữ liệu / Tiền điều kiện:** Tài khoản có yêu cầu PENDING (seed.pending), có lịch hẹn SCHEDULED (seed.appt) và yêu cầu đã duyệt ACCEPTED (seed.accept).

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách có yêu cầu PENDING** | `seed.pending@test.local` | `Test@12345` | Vào /requests -> Bấm nút 'Hủy yêu cầu' -> Nhập lý do hủy -> Xác nhận. |
| **Khách có lịch hẹn SCHEDULED** | `seed.appt@test.local` | `Test@12345` | Vào /requests (tab Lịch hẹn) -> Bấm nút 'Hủy lịch hẹn' -> Xác nhận. |
| **Khách có yêu cầu ACCEPTED** | `seed.accept@test.local` | `Test@12345` | Vào /requests -> Kiểm tra nút 'Hủy yêu cầu' bị ẩn hoặc vô hiệu hóa để bảo vệ cọc. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Khách hàng hủy yêu cầu thuê phòng PENDING:** Đăng nhập tài khoản seed.pending@test.local. Truy cập menu 'Yêu cầu của tôi' (/requests). Nhìn thấy yêu cầu thuê phòng đang có badge màu cam 'Chờ xử lý' (PENDING). Bấm nút 'Hủy yêu cầu'.
2. **Bước 2 - Nhập lý do hủy và xác nhận:** Hộp thoại xác nhận xuất hiện yêu cầu nhập lý do hủy (ví dụ: 'Thay đổi kế hoạch học tập, chọn khu vực khác'). Bấm nút 'Xác nhận hủy'. Hệ thống cập nhật trạng thái yêu cầu sang 'Đã hủy' (CANCELLED), giải phóng phòng cho người khác.
3. **Bước 3 - Khách hàng hủy lịch hẹn xem phòng SCHEDULED:** Đăng xuất. Đăng nhập tài khoản seed.appt@test.local. Vào /requests, chọn tab 'Lịch xem phòng'. Tìm thấy lịch hẹn đang ở trạng thái 'Chờ xác nhận' (SCHEDULED). Bấm nút 'Hủy lịch hẹn' -> Xác nhận hủy. Lịch hẹn chuyển sang trạng thái CANCELLED.
4. **Bước 4 - Kiểm tra chốt chặn: Khách đã được duyệt không được tự ý hủy:** Đăng nhập tài khoản seed.accept@test.local. Vào /requests. Thấy yêu cầu thuê đã được Quản lý duyệt (ACCEPTED / Đã tạo HĐ nháp). Kiểm tra giao diện: Nút 'Hủy yêu cầu' hoàn toàn bị ẩn để tránh trường hợp khách phá cọc tự do sau khi hợp đồng đã được khởi tạo.

#### ✅ Kết quả mong đợi (Expected Results):

- Khách hàng có thể hủy yêu cầu thuê PENDING và lịch hẹn SCHEDULED dễ dàng với lý do rõ ràng.
- Sau khi hủy, trạng thái đổi thành CANCELLED và giải phóng slot phòng.
- Các yêu cầu đã ở trạng thái ACCEPTED hoặc CONTRACT_CREATED bị khóa tính năng tự hủy.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 3.1]`: *Khách bấm nút Hủy yêu cầu thuê phòng đang ở trạng thái PENDING.*
> - `[Ảnh 3.2]`: *Hộp thoại nhập lý do hủy yêu cầu thuê phòng và thông báo thành công.*
> - `[Ảnh 3.3]`: *Khách hàng bấm nút Hủy lịch hẹn xem phòng SCHEDULED.*
> - `[Ảnh 3.4]`: *Màn hình kiểm tra yêu cầu đã ACCEPTED không có nút hủy để bảo vệ thỏa thuận.*

---

### LUỒNG 4: QUY TRÌNH Ở GHÉP, DUYỆT ỨNG VIÊN & TỰ ĐỘNG CHIA TÁCH HÓA ĐƠN

**🎯 Mục tiêu kiểm thử:** Khách đang thuê phòng (chủ phòng) đăng bài tìm bạn ở ghép công khai. Ứng viên gửi đơn xin ở ghép. Chủ phòng thẩm định hồ sơ, duyệt ứng viên vào phòng. Hệ thống tự động ghi nhận thành viên lưu trú và chia tách hóa đơn tiền phòng/điện nước theo tỷ lệ số ngày thực tế (StayInterval).

**🏢 Dữ liệu / Tiền điều kiện:** Phòng A104 (Seed House A) đang có hợp đồng ACTIVE. Chủ phòng: seed.active@test.local. Ứng viên: seed.new@test.local.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Chủ phòng A104** | `seed.active@test.local` | `Test@12345` | Vào /my-home -> Tạo bài đăng tìm bạn ở ghép -> Chờ ứng viên nộp -> Xem hồ sơ ứng viên và bấm 'Chấp nhận'. |
| **Ứng viên xin ở ghép** | `seed.new@test.local` | `Test@12345` | Vào trang /roommates -> Tìm bài đăng phòng A104 -> Bấm 'Ứng tuyển' -> Điền giới thiệu và gửi đơn. |
| **Bạn cùng phòng (sau khi ghép)** | `seed.mate@test.local` | `Test@12345` | Đăng nhập /my-home -> Kiểm tra thông tin phòng chung và hóa đơn cá nhân chia tách theo số ngày. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Chủ phòng đăng bài tìm bạn ở ghép:** Đăng nhập tài khoản chủ phòng seed.active@test.local. Vào menu 'Trang chủ của tôi' (/my-home), chọn tab hoặc nút 'Tìm bạn ở ghép' -> Bấm 'Tạo bài đăng'. Nhập thông tin: Số người cần tìm (1 người), chi phí chia đều ước tính, tiêu chí bạn cùng phòng (ví dụ: 'Sinh viên năm 2 trở lên, giữ gìn vệ sinh chung, không hút thuốc') -> Bấm 'Đăng bài'.
2. **Bước 2 - Ứng viên tìm bài đăng và nộp hồ sơ ở ghép:** Đăng xuất. Đăng nhập tài khoản ứng viên seed.new@test.local. Truy cập menu 'Ở ghép' (/roommates) trên thanh điều hướng chính. Tìm thấy bài đăng của phòng A104 Tòa Seed House A. Bấm nút 'Ứng tuyển ở ghép'. Điền thông tin giới thiệu bản thân, thói quen sinh hoạt và số điện thoại liên hệ -> Bấm 'Gửi hồ sơ ứng tuyển'.
3. **Bước 3 - Chủ phòng xét duyệt ứng viên:** Đăng xuất. Đăng nhập lại chủ phòng seed.active@test.local. Vào /my-home (mục Bạn ở ghép). Nhìn thấy hồ sơ ứng tuyển mới của seed.new@test.local. Bấm xem chi tiết thông tin giới thiệu -> Bấm nút 'Chấp nhận' (ACCEPT).
4. **Bước 4 - Hệ thống cập nhật danh sách cư dân phòng:** Sau khi chấp nhận, ứng viên được thêm vào danh sách người lưu trú chính thức của phòng A104 kèm mốc thời gian bắt đầu ở (StayInterval). Chủ phòng và ứng viên cùng nhìn thấy nhau trong danh sách thành viên phòng trên giao diện /my-home.
5. **Bước 5 - Kiểm tra tự động chia tách hóa đơn theo số ngày:** Đăng nhập tài khoản người ở ghép seed.mate@test.local (hoặc seed.new@test.local). Vào /my-home -> Mục 'Hóa đơn'. Kiểm tra: Hóa đơn tiền phòng và điện nước không phải là tổng cả phòng mà được tự động chia tỷ lệ tương ứng theo số ngày thực tế thành viên lưu trú trong kỳ chốt số.

#### ✅ Kết quả mong đợi (Expected Results):

- Bài đăng ở ghép được đăng tải công khai lên mục /roommates.
- Chủ phòng có toàn quyền duyệt hoặc từ chối các ứng viên nộp hồ sơ.
- Khi ứng viên được duyệt, hệ thống tự động ghi nhận thành viên lưu trú vào hợp đồng phòng.
- Hóa đơn tháng được tự động chia tách minh bạch theo công thức StayInterval giữa các thành viên.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 4.1]`: *Chủ phòng A104 mở form và đăng bài tìm bạn ở ghép.*
> - `[Ảnh 4.2]`: *Trang danh sách bài đăng ở ghép công khai (/roommates) hiển thị bài đăng A104.*
> - `[Ảnh 4.3]`: *Ứng viên gửi đơn xin ở ghép kèm thông tin giới thiệu bản thân.*
> - `[Ảnh 4.4]`: *Chủ phòng xem danh sách ứng viên và bấm nút Chấp nhận (ACCEPT).*
> - `[Ảnh 4.5]`: *Màn hình /my-home hiển thị đầy đủ danh sách thành viên cùng ở phòng A104.*
> - `[Ảnh 4.6]`: *Hóa đơn tháng tự động chia tách số tiền cần thanh toán cho từng người.*

---

### LUỒNG 5: QUY TRÌNH BÁO HỎNG, PHÂN CÔNG & SỬA CHỮA THIẾT BỊ (BẢO TRÌ)

**🎯 Mục tiêu kiểm thử:** Khách thuê gửi yêu cầu sửa chữa thiết bị kèm ảnh chụp sự cố thực tế. Quản lý tiếp nhận và điều phối phân công kỹ thuật viên phụ trách. Kỹ thuật viên tiếp nhận xử lý, hoàn tất sửa chữa, nhập chi phí vật tư phát sinh. Cư dân nhận thông báo realtime và nghiệm thu kết quả.

**🏢 Dữ liệu / Tiền điều kiện:** Phòng A104 (Seed House A). Khách thuê: seed.active@test.local. Sự cố: Vòi nước lavabo bị rò rỉ.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách thuê báo hỏng** | `seed.active@test.local` | `Test@12345` | Vào /repairs -> Bấm 'Báo sự cố mới' -> Tải ảnh vòi nước rò rỉ -> Gửi yêu cầu. |
| **Quản lý Tòa A** | `seed.manager.a@test.local` | `Test@12345` | Vào /admin/maintenance -> Xem hình ảnh và mô tả -> Bấm 'Phân công kỹ thuật' -> Giao việc cho seed.tech.a. |
| **Kỹ thuật viên Tòa A** | `seed.tech.a@test.local` | `Test@12345` | Vào /admin/tech-tasks -> Bấm 'Bắt đầu xử lý' (IN_PROGRESS) -> Hoàn tất sửa chữa -> Nhập chi phí linh kiện -> Bấm Hoàn tất (COMPLETED). |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Khách thuê tạo phiếu báo hỏng thiết bị:** Đăng nhập tài khoản khách thuê seed.active@test.local. Truy cập menu 'Bảo trì & Sự cố' (/repairs). Bấm nút 'Báo sự cố mới'. Nhập tiêu đề: 'Rò rỉ vòi nước lavabo', chọn khu vực: 'Nhà vệ sinh', chọn mức độ khẩn cấp: 'Bình thường', nhập mô tả chi tiết: 'Vòi nước bị rỉ liên tục gây thất thoát nước'. Bấm tải lên ảnh chụp hiện trạng vòi nước -> Bấm 'Gửi yêu cầu'.
2. **Bước 2 - Quản lý tiếp nhận và phân công kỹ thuật:** Đăng xuất. Đăng nhập Quản lý Tòa A seed.manager.a@test.local. Truy cập menu 'Bảo trì' (/admin/maintenance). Thấy phiếu báo hỏng mới của phòng A104 với badge màu vàng 'Chờ xử lý' (PENDING). Bấm xem chi tiết hình ảnh và nội dung -> Bấm nút 'Phân công kỹ thuật' -> Chọn kỹ thuật viên phụ trách seed.tech.a@test.local -> Bấm 'Xác nhận giao việc'.
3. **Bước 3 - Kỹ thuật viên tiếp nhận tác vụ:** Đăng xuất. Đăng nhập Kỹ thuật viên Tòa A seed.tech.a@test.local. Vào menu 'Tác vụ kỹ thuật' (/admin/tech-tasks). Nhìn thấy tác vụ sửa vòi nước phòng A104 vừa được giao. Bấm nút 'Bắt đầu xử lý' -> Trạng thái phiếu chuyển sang màu xanh dương 'Đang xử lý' (IN_PROGRESS).
4. **Bước 4 - Kỹ thuật viên hoàn tất sửa chữa và nhập chi phí:** Sau khi tiến hành kiểm tra và thay thế gioăng cao su, quấn băng tan vòi nước tại phòng A104, kỹ thuật viên bấm nút 'Hoàn tất xử lý'. Hộp thoại nghiệm thu hiện ra: Nhập mô tả giải pháp ('Đã thay mới gioăng cao su chịu nhiệt và quấn băng tan cố định vòi'), nhập chi phí vật tư phụ tùng: 50.000 VNĐ -> Bấm 'Xác nhận hoàn tất'. Trạng thái chuyển thành 'Đã hoàn tất' (COMPLETED).
5. **Bước 5 - Cư dân nhận thông báo và nghiệm thu:** Đăng xuất. Đăng nhập lại khách thuê seed.active@test.local. Quan sát chuông thông báo hiển thị thông báo mới: 'Sự cố vòi nước lavabo phòng A104 đã được sửa chữa xong'. Khách vào /repairs thấy phiếu đổi sang màu xanh lá cây COMPLETED, hiển thị đầy đủ giải pháp xử lý và chi phí linh kiện để cư dân nghiệm thu.

#### ✅ Kết quả mong đợi (Expected Results):

- Phiếu báo hỏng bắt buộc phải có mô tả và ảnh chụp sự cố đính kèm.
- Quản lý có thể phân công đích danh kỹ thuật viên phụ trách tòa nhà.
- Kỹ thuật viên cập nhật được trạng thái từ IN_PROGRESS sang COMPLETED kèm chi phí vật tư minh bạch.
- Cư dân nhận thông báo tự động và xem được biên bản nghiệm thu trực tuyến.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 5.1]`: *Khách thuê gửi phiếu báo hỏng thiết bị đính kèm ảnh chụp sự cố thực tế.*
> - `[Ảnh 5.2]`: *Quản lý xem xét chi tiết sự cố và phân công kỹ thuật viên seed.tech.a xử lý.*
> - `[Ảnh 5.3]`: *Kỹ thuật viên nhận tác vụ trong danh sách và bấm Bắt đầu xử lý (IN_PROGRESS).*
> - `[Ảnh 5.4]`: *Kỹ thuật nhập phương án khắc phục, chi phí vật tư và bấm Hoàn tất sửa chữa.*
> - `[Ảnh 5.5]`: *Khách thuê nhận chuông thông báo hoàn tất sửa chữa và nghiệm thu kết quả.*

---

### LUỒNG 6: QUY TRÌNH TRẢ PHÒNG, DUYỆT DỌN ĐI & QUYẾT TOÁN HOÀN CỌC (BUFFER 10 NGÀY DỌN DẸP)

**🎯 Mục tiêu kiểm thử:** Khách thuê sắp hết hạn gửi yêu cầu trả phòng có báo trước. Quản lý phê duyệt ngày dọn đi, hệ thống tự động gắn nhãn UPCOMING_VACANT và mở lại tin đăng trên Explore với ngày sẵn sàng vào ở được cộng thêm 10 ngày dọn dẹp (Cleaning Buffer). Đến ngày bàn giao, Quản lý quyết toán hoàn trả tiền cọc minh bạch.

**🏢 Dữ liệu / Tiền điều kiện:** Phòng A105 (Tòa Seed House A). Khách thuê: seed.renew@test.local. Tiền cọc đang giữ: 6.000.000đ.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách thuê sắp hết hạn** | `seed.renew@test.local` | `Test@12345` | Vào /my-home -> Mở form 'Yêu cầu trả phòng' -> Chọn ngày trả và lý do -> Bấm Gửi yêu cầu. |
| **Quản lý Tòa A** | `seed.manager.a@test.local` | `Test@12345` | Vào /admin/contracts -> Thấy badge 'Có yêu cầu dọn đi' -> Bấm Duyệt -> Khảo sát công tơ cuối kỳ -> Quyết toán hoàn cọc. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Khách thuê gửi thông báo trả phòng:** Đăng nhập tài khoản khách thuê sắp hết hạn seed.renew@test.local (đang ở phòng A105). Vào menu 'Trang chủ của tôi' (/my-home). Bấm vào mục 'Yêu cầu trả phòng' (Move-out Request). Chọn ngày dự kiến trả phòng (ví dụ: ngày 28 của tháng) và nhập lý do: 'Hết hạn hợp đồng, chuyển về quê sinh sống' -> Bấm 'Gửi yêu cầu trả phòng'.
2. **Bước 2 - Quản lý xem xét và phê duyệt dọn đi:** Đăng xuất. Đăng nhập Quản lý Tòa A seed.manager.a@test.local. Vào menu 'Hợp đồng' (/admin/contracts). Tìm phòng A105 thấy gắn badge thông báo màu cam 'Có yêu cầu dọn đi'. Bấm xem chi tiết đề xuất ngày trả phòng của khách -> Bấm nút 'Duyệt trả phòng'. Phòng A105 chuyển sang trạng thái UPCOMING_VACANT.
3. **Bước 3 - Kiểm tra tin đăng tự động mở lại với buffer 10 ngày:** Truy cập trang công khai Khám phá phòng (/listings). Tìm thẻ phòng A105. Kiểm tra thông tin: Phòng A105 đã được tự động hiển thị mở lại cho khách mới đặt lịch, trong đó ngày phòng sẵn sàng vào ở (Available Date) chính xác bằng: Ngày khách cũ trả phòng + 10 ngày dọn dẹp vệ sinh theo đúng quy định.
4. **Bước 4 - Khảo sát công tơ và quyết toán hoàn cọc:** Đến ngày trả phòng thực tế, Quản lý cùng Kỹ thuật tiến hành kiểm tra tài sản phòng và chốt chỉ số điện nước cuối cùng. Quản lý vào /admin/contracts -> Bấm 'Quyết toán cọc' (Deposit Settlement). Nhập các khoản khấu trừ nếu có (tiền điện nước tháng cuối, phí vệ sinh) -> Hệ thống tự động tính số tiền cọc hoàn trả thực tế cho khách.
5. **Bước 5 - Khách thuê nhận biên lai hoàn cọc:** Đăng nhập lại khách thuê seed.renew@test.local. Vào /my-home kiểm tra biên lai quyết toán hoàn cọc trực tuyến. Khách hàng đối chiếu các khoản khấu trừ minh bạch và nhận tiền hoàn cọc qua tài khoản ngân hàng.

#### ✅ Kết quả mong đợi (Expected Results):

- Khách thuê gửi yêu cầu trả phòng có báo trước ngày cụ thể.
- Quản lý duyệt chuyển trạng thái hợp đồng và phòng sang UPCOMING_VACANT.
- Tin đăng phòng tự động mở lại với Available Date = Ngày trả phòng + 10 ngày dọn dẹp.
- Quyết toán hoàn cọc tính toán chuẩn xác số tiền cọc ban đầu trừ đi các chi phí tồn đọng.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 6.1]`: *Khách thuê gửi form yêu cầu trả phòng có báo trước.*
> - `[Ảnh 6.2]`: *Quản lý xem xét ngày dọn đi và bấm duyệt yêu cầu trả phòng.*
> - `[Ảnh 6.3]`: *Tin đăng phòng A105 tự động mở lại với ngày sẵn sàng có cộng 10 ngày buffer dọn dẹp.*
> - `[Ảnh 6.4]`: *Quản lý thực hiện quyết toán hoàn cọc và khấu trừ chi phí phát sinh.*
> - `[Ảnh 6.5]`: *Khách thuê kiểm tra biên lai quyết toán hoàn cọc trực tuyến.*

---

### LUỒNG 7: CHỐT SỐ ĐIỆN NƯỚC BÀN GIAO KHÁCH MỚI & CHỐT SỔ CUỐI THÁNG (2 KỊCH BẢN)

**🎯 Mục tiêu kiểm thử:** Tách bạch 2 kịch bản ghi chỉ số điện nước độc lập: (7A) Ghi chỉ số BÀN GIAO cho khách mới chuyển vào hôm nay - đây là mốc xuất phát điểm, không tính tiền và không sinh hóa đơn; (7B) Chốt số ĐỊNH KỲ cuối tháng cho các phòng đang thuê - nhập chỉ số tháng trước trong 5 ngày đầu tháng, phòng thiếu bị đánh dấu đỏ, và hóa đơn tự động sinh ngay khi đủ chỉ số.

**🏢 Dữ liệu / Tiền điều kiện:** Kịch bản 7A: Phòng A108 (khách mới chuyển vào hôm nay seed.handover@test.local, công tơ SEED-A108-E mốc 2.500 kWh, SEED-A108-W mốc 300 m³). Kịch bản 7B: Phòng A104 (đã có chỉ số), Phòng A105 & A107 (chưa có chỉ số, dòng đỏ cảnh báo).

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách thuê mới (Kịch bản 7A)** | `seed.handover@test.local` | `Test@12345` | Đăng nhập /my-home -> Xác nhận hợp đồng bắt đầu từ hôm nay và chưa có hóa đơn. |
| **Kỹ thuật viên Tòa A** | `seed.tech.a@test.local` | `Test@12345` | Vào /admin/meter-readings -> Tab 'Bàn giao khách mới' (nhập mốc đầu cho A108: 2.560 / 315) -> Chuyển tab 'Chốt chỉ số tháng' nhập cho A105/A107. |
| **Quản lý Tòa A (Kịch bản 7B)** | `seed.manager.a@test.local` | `Test@12345` | Vào /admin/finance -> Kiểm tra cảnh báo đỏ kỳ chốt và xác nhận hóa đơn tự động sinh sau khi kỹ thuật lưu chỉ số. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 (7A) - Khách mới kiểm tra hợp đồng:** Đăng nhập khách mới seed.handover@test.local. Vào /my-home kiểm tra hợp đồng phòng A108 có hiệu lực từ ngày hôm nay. Tại mục Hóa đơn, xác nhận hoàn toàn chưa có hóa đơn nào vì phòng vừa mới nhận bàn giao.
2. **Bước 2 (7A) - Kỹ thuật ghi mốc công tơ bàn giao:** Đăng xuất. Đăng nhập Kỹ thuật Tòa A seed.tech.a@test.local. Mở chuông thông báo thấy nhắc nhở: 'Cần ghi chỉ số điện nước bàn giao phòng A108 hôm nay'. Vào menu /admin/meter-readings, chọn tab 'Bàn giao khách mới'. Nhìn thấy phòng A108 với ngày chuyển vào là hôm nay. Nhập số công tơ thực tế: Điện 2.560 kWh, Nước 315 m³ -> Bấm 'Lưu mốc bàn giao'.
3. **Bước 3 (7A) - Kiểm tra tính chất không sinh hóa đơn của mốc bàn giao:** Sau khi lưu, phòng A108 biến mất khỏi tab 'Bàn giao khách mới'. Vào tab 'Chốt chỉ số tháng' xác nhận không có phòng A108 ở kỳ hiện tại. Sang /admin/finance kiểm tra hoàn toàn không sinh hóa đơn cho A108. Mốc này chỉ dùng làm initialIndex để tính tiền cho kỳ tháng sau.
4. **Bước 4 (7B) - Kỹ thuật mở tab Chốt chỉ số tháng:** Tại màn hình /admin/meter-readings, chọn tab 'Chốt chỉ số tháng'. Kỳ chốt mặc định hiển thị là tháng trước (ví dụ: tháng 10). Quan sát danh sách phòng: Phòng A104 hiển thị bình thường (đã đủ chỉ số), Phòng A105 và A107 hiển thị dòng đỏ nổi bật 'Yêu cầu nhập'.
5. **Bước 5 (7B) - Kỹ thuật nhập chỉ số điện nước phòng A105:** Tại hàng phòng A105: Số cũ hiển thị là 1.200 kWh (điện) và 130 m³ (nước). Kỹ thuật viên nhập số mới: Điện 1.350 kWh (tiêu thụ 150 kWh), Nước 145 m³ (tiêu thụ 15 m³) -> Bấm nút 'Lưu chỉ số'. Hàng phòng A105 mất dòng đỏ cảnh báo và chuyển sang trạng thái đã ghi nhận.
6. **Bước 6 (7B) - Kiểm tra hóa đơn tự động sinh (Zero-Click Invoice):** Đăng xuất. Đăng nhập Quản lý Tòa A seed.manager.a@test.local. Truy cập menu 'Tài chính' (/admin/finance). Kiểm tra danh sách hóa đơn: Hóa đơn kỳ tháng trước của phòng A105 ĐÃ TỰ ĐỘNG XUẤT HIỆN với đầy đủ tiền phòng, tiền điện (150 kWh x đơn giá) và tiền nước (15 m³ x đơn giá) mà Quản lý không cần bấm nút 'Sinh hóa đơn'!

#### ✅ Kết quả mong đợi (Expected Results):

- Tách biệt 100% giữa 2 tab: Tab 'Bàn giao khách mới' và Tab 'Chốt chỉ số tháng'.
- Mốc bàn giao khách mới chỉ là chỉ số ban đầu, tuyệt đối không tính tiền và không tạo hóa đơn.
- Tab chốt tháng có cảnh báo đỏ đối với các phòng chưa nhập chỉ số trong 5 ngày đầu tháng.
- Hóa đơn tự động sinh ngay lập tức khi phòng được nhập đủ tất cả đồng hồ điện nước đang hoạt động.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 7.1]`: *Tab 'Bàn giao khách mới' hiển thị phòng A108 chuyển vào hôm nay.*
> - `[Ảnh 7.2]`: *Kỹ thuật nhập và lưu chỉ số bàn giao đầu tiên phòng A108.*
> - `[Ảnh 7.3]`: *Tab 'Chốt chỉ số tháng' hiển thị A104 đủ chỉ số, A105/A107 có dòng đỏ 'Yêu cầu nhập'.*
> - `[Ảnh 7.4]`: *Kỹ thuật nhập và lưu số công tơ điện nước mới cho phòng A105.*
> - `[Ảnh 7.5]`: *Màn hình Tài chính Quản lý hiển thị hóa đơn A105 tự động sinh mà không cần bấm phát hành.*

---

### LUỒNG 8: QUY TRÌNH PHÁT HÀNH HÓA ĐƠN & THANH TOÁN: CỔNG VNPAY SANDBOX THẬT & VIETQR / TIỀN MẶT

**🎯 Mục tiêu kiểm thử:** Khách thuê thanh toán hóa đơn trực tuyến thông qua cổng thanh toán VNPay Sandbox thật (sử dụng thẻ test ngân hàng NCB, mã hóa HMAC-SHA512 an toàn, xác thực OTP, webhook IPN gạch nợ tự động chuyển hóa đơn sang PAID). Đồng thời kiểm tra song song kênh thanh toán quét mã VietQR và Quản lý ghi nhận thu tiền mặt tại quầy.

**🏢 Dữ liệu / Tiền điều kiện:** Hóa đơn phòng A104 (seed.active@test.local) chưa thanh toán. Cổng thanh toán VNPay Sandbox với thẻ test NCB chuẩn.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách thuê thanh toán VNPay** | `seed.active@test.local` | `Test@12345` | Vào /my-home -> Mở hóa đơn phòng A104 -> Bấm 'Thanh toán VNPay Sandbox' -> Nhập thẻ test NCB -> Nhập OTP -> Kiểm tra kết quả PAID. |
| **Khách thuê có hóa đơn quá hạn** | `seed.terminate@test.local` | `Test@12345` | Vào /my-home -> Kiểm tra hóa đơn phòng A107 hiển thị badge màu đỏ 'Quá hạn' (OVERDUE) và chuông nhắc nợ. |
| **Quản lý Tòa A** | `seed.manager.a@test.local` | `Test@12345` | Vào /admin/finance -> Theo dõi danh sách hóa đơn 4 trạng thái màu sắc -> Bấm 'Ghi nhận thu tiền mặt' cho khách đóng trực tiếp. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Khách mở hóa đơn và chọn thanh toán VNPay Sandbox:** Đăng nhập khách thuê seed.active@test.local (phòng A104). Vào menu 'Trang chủ của tôi' (/my-home), kéo xuống mục Hóa đơn tháng. Mở chi tiết hóa đơn chưa thanh toán. Nhận diện nút 'Thanh toán VNPay' (màu xanh dương có logo VNPay). Bấm vào nút này.
2. **Bước 2 - Chuyển hướng sang cổng thanh toán VNPay Sandbox:** Hệ thống tự động sinh mã tham chiếu giao dịch vnp_TxnRef duy nhất, ký mã hóa HMAC-SHA512 an toàn và điều hướng người dùng sang trang thanh toán chính thức của VNPay Sandbox (đường dẫn https://sandbox.vnpayment.vn/paymentv2/vpcpay.html).
3. **Bước 3 - Chọn phương thức thẻ NCB và điền thông tin thẻ test:** Tại màn hình VNPay Sandbox: Chọn mục 'Thẻ nội địa và tài khoản ngân hàng' -> Chọn ngân hàng 'NCB'. Nhập chính xác thông tin thẻ test quy chuẩn:
- Số thẻ: 9704198526191432198
- Tên chủ thẻ: NGUYEN VAN A
- Ngày phát hành: 07/15
-> Bấm nút 'Tiếp tục'.
4. **Bước 4 - Xác thực mã OTP và hoàn tất thanh toán:** Màn hình chuyển sang bước xác thực OTP. Nhập mã OTP mặc định của Sandbox: 123456 -> Bấm 'Thanh toán'. Cổng VNPay xử lý thành công giao dịch (mã kết quả vnp_ResponseCode = 00).
5. **Bước 5 - Kiểm tra trang kết quả Return URL và hóa đơn tự động PAID:** Cổng VNPay tự động chuyển hướng khách hàng quay trở lại hệ thống tại đường dẫn /payment/vnpay-return. Màn hình hiển thị thông báo chúc mừng 'Thanh toán hóa đơn thành công qua VNPay' kèm mã giao dịch, số tiền và thời gian. Quay lại /my-home, kiểm tra hóa đơn phòng A104 ĐÃ TỰ ĐỘNG CHUYỂN SANG TRẠNG THÁI 'ĐÃ THU' (PAID) màu xanh lá cây.
6. **Bước 6 - Quản lý theo dõi bảng hóa đơn 4 màu và thu tiền mặt tại quầy:** Đăng nhập Quản lý Tòa A seed.manager.a@test.local. Vào menu /admin/finance. Quan sát danh sách hóa đơn với 4 trạng thái màu sắc trực quan: Chưa thu (Vàng), Thu một phần (Xanh dương), Đã thu (Xanh lá - bao gồm hóa đơn A104 vừa trả qua VNPay), Quá hạn (Đỏ san hô). Đối với khách đóng tiền mặt tại văn phòng, Quản lý bấm nút 'Ghi nhận thu tiền mặt' -> Nhập số tiền -> Hóa đơn được gạch nợ thành PAID ngay lập tức.

#### ✅ Kết quả mong đợi (Expected Results):

- Tích hợp cổng thanh toán VNPay Sandbox thật hoạt động ổn định và chính xác.
- Cơ chế mã hóa bảo mật HMAC-SHA512 và IPN webhook gạch nợ tự động chuẩn xác.
- Bảo đảm tính idempotent: Ngăn chặn thanh toán lặp 2 lần cho cùng một hóa đơn.
- Giao diện phân định rõ ràng 4 trạng thái màu sắc hóa đơn và hỗ trợ song song thu tiền mặt.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 8.1]`: *Khách mở chi tiết hóa đơn và bấm nút 'Thanh toán VNPay Sandbox'.*
> - `[Ảnh 8.2]`: *Giao diện cổng thanh toán VNPay Sandbox thực tế.*
> - `[Ảnh 8.3]`: *Nhập thông tin thẻ test ngân hàng NCB và mã OTP xác thực 123456.*
> - `[Ảnh 8.4]`: *Trang kết quả thanh toán /payment/vnpay-return hiển thị thành công.*
> - `[Ảnh 8.5]`: *Hóa đơn phòng A104 tự động chuyển trạng thái PAID (màu xanh lá).*
> - `[Ảnh 8.6]`: *Bảng tổng hợp hóa đơn 4 trạng thái màu sắc trực quan trên màn hình Quản lý.*
> - `[Ảnh 8.7]`: *Quản lý thực hiện thao tác ghi nhận thu tiền mặt tại quầy.*

---

### LUỒNG 9: TRUNG TÂM QUẢN TRỊ HỆ THỐNG TOÀN DIỆN (ROLE QUẢN TRỊ VIÊN - ADMIN)

**🎯 Mục tiêu kiểm thử:** Kiểm tra quyền hạn tối cao của Quản Trị Viên (Super Admin) trên toàn bộ hệ thống: Xem dashboard hợp nhất, quản lý danh mục tất cả tòa nhà và phòng trọ, cấu hình chủ nhà pháp lý, phân quyền nhân sự, báo cáo tài chính toàn chuỗi và bảng giá dịch vụ.

**🏢 Dữ liệu / Tiền điều kiện:** Tài khoản Quản Trị Viên toàn hệ thống: seed.admin@test.local.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Quản Trị Viên (ADMIN)** | `seed.admin@test.local` | `Test@12345` | Đăng nhập -> Kiểm tra toàn bộ phân hệ /admin: Dashboard, Tòa nhà, Phòng, Báo cáo tài chính, Nhân sự, Bảng giá, Tài sản. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Kiểm tra Dashboard quản trị hợp nhất:** Đăng nhập seed.admin@test.local. Truy cập trang chủ quản trị (/admin). Kiểm tra các chỉ số tổng quan: Tổng số tòa nhà (4 tòa), tổng số phòng, tỷ lệ lấp đầy phòng, doanh thu tháng và biểu đồ phân bổ công nợ toàn hệ thống.
2. **Bước 2 - Quản lý danh mục tòa nhà và chủ nhà pháp lý:** Vào menu 'Tòa nhà' (/admin/buildings). Xem danh sách đầy đủ 4 tòa nhà: Seed House A, Seed House B, Mộc Residence, Lam Garden. Bấm 'Sửa' kiểm tra thông tin pháp lý của từng Chủ nhà (Bên A): Họ tên, số điện thoại, số CCCD và địa chỉ thường trú đã được cập nhật chuẩn xác.
3. **Bước 3 - Quản lý danh mục phòng toàn hệ thống:** Vào menu 'Phòng' (/admin/rooms). Xem danh sách phòng của toàn bộ chuỗi trọ. Thử nghiệm bộ lọc: Lọc theo tòa nhà, lọc theo tầng, lọc theo trạng thái (Trống, Đang thuê, Chờ dọn vào, Đang sửa chữa).
4. **Bước 4 - Quản lý nhân sự và phân bổ tòa nhà phụ trách:** Vào menu 'Nhân sự' (/admin/staff). Xem danh sách tất cả nhân viên (Quản lý tòa, Nhân viên Sale, Kỹ thuật viên). Kiểm tra thông tin chức vụ, email, số điện thoại và tòa nhà được gán phụ trách. Nhận diện nút 'Nhắn tin' cạnh mỗi nhân viên để kết nối nhanh.
5. **Bước 5 - Báo cáo tài chính & Doanh thu chuỗi:** Vào menu 'Tài chính' (/admin/finance). Xem báo cáo tổng hợp doanh thu tiền phòng, tiền điện, tiền nước và các khoản phụ thu của tất cả các tòa. Kiểm tra bộ lọc theo kỳ chốt và trạng thái thu.
6. **Bước 6 - Quản lý tài sản & Bảng giá dịch vụ niêm yết:** Vào menu 'Tài sản' (/admin/assets) xem kiểm kê thiết bị trang bị theo phòng. Vào menu 'Bảng giá dịch vụ' (/admin/service-rates) kiểm tra đơn giá điện/kWh, nước/m3, internet và phí dịch vụ niêm yết của hệ thống.

#### ✅ Kết quả mong đợi (Expected Results):

- Admin có quyền xem và thao tác trên dữ liệu của tất cả các tòa nhà không bị giới hạn.
- Thông tin Chủ nhà Bên A của 4 tòa hiển thị đầy đủ, không bị rỗng.
- Tất cả các menu quản trị hoạt động mượt mà, phân quyền chính xác.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 9.1]`: *Dashboard tổng quan hệ thống toàn diện của Quản trị viên (Admin).*
> - `[Ảnh 9.2]`: *Danh mục quản lý tất cả các tòa nhà và cấu hình chủ nhà pháp lý.*
> - `[Ảnh 9.3]`: *Danh sách phòng trọ toàn bộ các tòa nhà trên hệ thống kèm bộ lọc.*
> - `[Ảnh 9.4]`: *Màn hình Quản lý nhân sự, phân quyền và gán tòa phụ trách.*
> - `[Ảnh 9.5]`: *Báo cáo tài chính, doanh thu và công nợ toàn hệ thống.*
> - `[Ảnh 9.6]`: *Quản lý danh mục trang thiết bị tài sản và Bảng giá dịch vụ niêm yết.*

---

### LUỒNG 10: NGHIỆP VỤ VẬN HÀNH TÒA NHÀ & QUẢN LÝ KHÁCH CƯ TRÚ (ROLE QUẢN LÝ - MANAGER)

**🎯 Mục tiêu kiểm thử:** Kiểm tra toàn bộ nghiệp vụ điều hành hàng ngày của Quản lý tòa: Thẩm định hồ sơ thuê phòng, ký số HĐ bằng nét vẽ chuột trên Canvas, quản lý sổ cư dân lưu trú, duyệt dọn đi, phân công thợ kỹ thuật và quản lý công nợ trong phạm vi tòa nhà phụ trách.

**🏢 Dữ liệu / Tiền điều kiện:** Quản lý Tòa Seed House A: seed.manager.a@test.local.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Quản lý Tòa A** | `seed.manager.a@test.local` | `Test@12345` | Đăng nhập -> Kiểm tra Dashboard Tòa A -> Duyệt HĐ nháp -> Ký số trên Canvas -> Quản lý sổ cư dân -> Phân công thợ -> Theo dõi tài chính Tòa A. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Dashboard vận hành riêng của Quản lý tòa:** Đăng nhập seed.manager.a@test.local. Truy cập Dashboard quản trị. Xác nhận số liệu thống kê (phòng trống, phòng đang ở, yêu cầu cần xử lý) chỉ tính riêng cho Tòa Seed House A.
2. **Bước 2 - Quản lý hợp đồng & Ký số điện tử bằng chuột:** Vào menu 'Hợp đồng' (/admin/contracts). Xem danh sách hợp đồng của Tòa A. Mở một hợp đồng nháp DRAFT, kiểm tra điều khoản và chữ ký của khách thuê -> Quản lý dùng chuột vẽ chữ ký số đại diện bên cho thuê trên Canvas -> Bấm kích hoạt hợp đồng.
3. **Bước 3 - Quản lý sổ đăng ký cư dân (Tenants Ledger):** Vào menu 'Cư dân' (/admin/tenants). Xem sổ theo dõi cư trú thực tế của Tòa A: Danh sách người thuê chính, người ở ghép, số phòng đang ở, số điện thoại, số CCCD và tình trạng khai báo tạm trú.
4. **Bước 4 - Điều phối và giám sát công việc bảo trì:** Vào menu 'Bảo trì' (/admin/maintenance). Xem danh sách sự cố hư hỏng thiết bị phát sinh tại Tòa A. Thực hiện phân công việc cho kỹ thuật viên phụ trách tòa nhà.
5. **Bước 5 - Quản lý tài chính & Ghi nhận thu tiền mặt:** Vào menu 'Tài chính' (/admin/finance). Theo dõi tình hình nợ tiền nhà của các phòng thuộc Tòa A, kiểm tra các hóa đơn đã thu qua VNPay và thực hiện bấm ghi nhận thu tiền mặt tại quầy đối với khách nộp trực tiếp.

#### ✅ Kết quả mong đợi (Expected Results):

- Quản lý chỉ nhìn thấy và thao tác trên dữ liệu thuộc tòa nhà mình phụ trách (BuildingScope chuẩn xác).
- Chữ ký số của Quản lý được vẽ thực tế bằng chuột trên Canvas và lưu vào file PDF hợp đồng.
- Sổ cư dân hiển thị đầy đủ cả chủ phòng và thành viên ở ghép.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 10.1]`: *Bảng điều khiển vận hành hàng ngày của Quản lý tòa.*
> - `[Ảnh 10.2]`: *Danh sách quản lý hợp đồng thuê phòng theo tòa.*
> - `[Ảnh 10.3]`: *Giao diện Quản lý vẽ chữ ký số trên Canvas và kích hoạt hợp đồng.*
> - `[Ảnh 10.4]`: *Sổ đăng ký cư trú và danh sách khách đang lưu trú tại tòa.*
> - `[Ảnh 10.5]`: *Bảng theo dõi và điều phối các yêu cầu sửa chữa bảo trì.*
> - `[Ảnh 10.6]`: *Quản lý tài chính và ghi nhận thu tiền phòng theo tòa.*

---

### LUỒNG 11: PIPELINE CHĂM SÓC KHÁCH HÀNG & TIẾP NHẬN LEADS (ROLE KINH DOANH - SALE)

**🎯 Mục tiêu kiểm thử:** Kiểm tra phân hệ làm việc chuyên trách của Nhân viên Sale: Menu được tinh gọn tuyệt đối (đã ẩn hoàn toàn Hóa đơn và Bảo trì), tập trung vào tiếp nhận khách tiềm năng (Leads), xác minh ảnh CCCD 2 mặt, quản lý lịch hẹn xem phòng và kết nối nhắn tin nội bộ với Quản lý tòa.

**🏢 Dữ liệu / Tiền điều kiện:** Nhân viên Sale Tòa Seed House A: seed.sale.a@test.local.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Nhân viên Sale Tòa A** | `seed.sale.a@test.local` | `Test@12345` | Đăng nhập -> Kiểm tra menu tinh gọn (không có Hóa đơn, Bảo trì) -> Vào /admin/leads -> Xác minh hồ sơ CCCD -> Xác nhận lịch hẹn -> Nhắn tin với Quản lý. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Kiểm tra menu điều hướng tinh gọn của Sale:** Đăng nhập seed.sale.a@test.local. Quan sát thanh menu bên trái: Xác nhận menu 'Hóa đơn' và 'Bảo trì' ĐÃ HOÀN TOÀN BIẾN MẤT. Menu của Sale chỉ gồm: Khám phá, Leads & Lịch hẹn, Khách hàng, Hợp đồng, Nhân sự, Tin nhắn.
2. **Bước 2 - Quản lý lịch hẹn xem phòng:** Vào menu 'Quản lý Leads' (/admin/leads), chọn tab 'Lịch xem phòng'. Xem danh sách khách đặt lịch hẹn xem phòng thuộc Tòa Seed House A. Thực hiện bấm 'Xác nhận' (CONFIRMED) hoặc 'Đã xem' (VIEWED).
3. **Bước 3 - Tiếp nhận hồ sơ và xác minh CCCD 2 mặt:** Chuyển sang tab 'Hồ sơ thuê phòng'. Nhìn thấy các yêu cầu thuê phòng mới của khách. Bấm nút 'Xem hồ sơ' -> Modal mở ra hiển thị thông tin cá nhân và 2 ảnh chụp mặt trước, mặt sau thẻ CCCD. Bấm nút 'Xác minh hồ sơ' (VERIFIED). Xác nhận Sale không có nút Duyệt hợp đồng.
4. **Bước 4 - Kết nối nhắn tin nội bộ với Quản lý tòa:** Vào menu 'Nhân sự' (/admin/staff). Nhìn thấy danh sách nhân sự Tòa A. Tìm hàng của Quản lý seed.manager.a@test.local -> Bấm nút 'Nhắn tin' màu xanh -> Mở ngay khung chat nội bộ để trao đổi về hồ sơ khách vừa xác minh.

#### ✅ Kết quả mong đợi (Expected Results):

- Menu của Sale được tinh gọn đúng chuẩn nghiệp vụ: Không nhìn thấy và không thể truy cập menu Hóa đơn hay Bảo trì.
- Sale có quyền xem ảnh CCCD 2 mặt và xác minh hồ sơ khách hàng.
- Sale không có quyền duyệt hợp đồng hay phát hành hóa đơn.
- Có nút Nhắn tin trên trang Nhân sự để liên lạc trực tiếp với Quản lý tòa.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 11.1]`: *Giao diện menu điều hướng tinh gọn của nhân viên Sale (đã loại bỏ Hóa đơn và Bảo trì).*
> - `[Ảnh 11.2]`: *Pipeline quản lý danh sách lịch hẹn xem phòng của khách hàng.*
> - `[Ảnh 11.3]`: *Modal Sale kiểm tra 2 mặt ảnh CCCD của khách và bấm Xác minh hồ sơ (VERIFIED).*
> - `[Ảnh 11.4]`: *Sale bấm nút Nhắn tin trong trang Nhân sự để mở hội thoại với Quản lý tòa.*

---

### LUỒNG 12: QUẢN LÝ TÁC VỤ SỬA CHỮA & CÔNG TƠ ĐIỆN NƯỚC (ROLE KỸ THUẬT - TECHNICIAN)

**🎯 Mục tiêu kiểm thử:** Kiểm tra phân hệ làm việc của Kỹ thuật viên: Tiếp nhận tác vụ sửa chữa được giao, cập nhật tiến độ xử lý và chi phí vật tư; ghi nhận chỉ số công tơ điện nước bàn giao và định kỳ; truy cập trang Nhân sự và nhắn tin trao đổi nội bộ.

**🏢 Dữ liệu / Tiền điều kiện:** Kỹ thuật viên Tòa Seed House A: seed.tech.a@test.local.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Kỹ thuật viên Tòa A** | `seed.tech.a@test.local` | `Test@12345` | Đăng nhập -> Vào /admin/tech-tasks xử lý sự cố -> Vào /admin/meter-readings ghi chỉ số -> Vào /admin/staff nhắn tin với đồng nghiệp. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Tiếp nhận và xử lý tác vụ bảo trì:** Đăng nhập seed.tech.a@test.local. Truy cập menu 'Tác vụ kỹ thuật' (/admin/tech-tasks). Xem danh sách các công việc sửa chữa được Quản lý phân công. Bấm xem chi tiết ảnh chụp và mô tả hư hỏng -> Bấm 'Bắt đầu xử lý' (IN_PROGRESS).
2. **Bước 2 - Hoàn tất tác vụ và ghi nhận chi phí vật tư:** Sau khi khắc phục xong sự cố tại phòng, bấm nút 'Hoàn tất xử lý' -> Nhập giải pháp sửa chữa và số tiền linh kiện vật tư thay thế -> Bấm 'Xác nhận hoàn tất' (COMPLETED).
3. **Bước 3 - Ghi chỉ số công tơ điện nước:** Vào menu 'Ghi chỉ số điện nước' (/admin/meter-readings). Thực hiện ghi mốc bàn giao phòng khách mới ở tab 'Bàn giao khách mới' hoặc ghi chỉ số tiêu thụ tháng ở tab 'Chốt chỉ số tháng'.
4. **Bước 4 - Truy cập danh sách Nhân sự và nhắn tin nội bộ:** Vào menu 'Nhân sự' (/admin/staff) (Kỹ thuật đã được mở quyền truy cập menu này). Xem danh sách nhân viên tòa nhà, bấm nút 'Nhắn tin' cạnh Quản lý hoặc Sale để trao đổi về tình trạng vật tư kỹ thuật.

#### ✅ Kết quả mong đợi (Expected Results):

- Kỹ thuật viên quản lý độc lập các phiếu bảo trì được giao, cập nhật tiến độ và chi phí vật tư rõ ràng.
- Nhập chỉ số điện nước nhanh chóng, đúng quy trình phân tách bàn giao và chốt tháng.
- Có quyền truy cập trang Nhân sự và Tin nhắn để phối hợp công việc nội bộ thuận tiện.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 12.1]`: *Danh sách công việc sửa chữa bảo trì được giao cho thợ kỹ thuật.*
> - `[Ảnh 12.2]`: *Modal kỹ thuật cập nhật tiến độ xử lý, phương án khắc phục và chi phí vật tư.*
> - `[Ảnh 12.3]`: *Giao diện kỹ thuật viên nhập số công tơ điện nước phòng.*
> - `[Ảnh 12.4]`: *Kỹ thuật viên truy cập trang Quản lý nhân sự và mở chat trao đổi nội bộ.*

---

### LUỒNG 13: CỔNG DỊCH VỤ CƯ DÂN TRỰC TUYẾN (ROLE KHÁCH THUÊ - CUSTOMER PORTAL)

**🎯 Mục tiêu kiểm thử:** Kiểm tra toàn bộ trải nghiệm số của Cư dân lưu trú tại chuỗi phòng trọ: Tra cứu thông tin phòng đang ở, tải file hợp đồng điện tử có đầy đủ chữ ký 2 bên, xem hóa đơn và lịch sử thanh toán, theo dõi tiến độ sửa chữa thiết bị, quản lý bài đăng ở ghép và trung tâm thông báo.

**🏢 Dữ liệu / Tiền điều kiện:** Khách thuê đang ở phòng A104: seed.active@test.local.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Khách thuê đang ở (Chủ phòng)** | `seed.active@test.local` | `Test@12345` | Đăng nhập /my-home -> Kiểm tra thông tin phòng A104, hợp đồng điện tử, hóa đơn tháng, phiếu sửa chữa, bài đăng ở ghép, chuông thông báo. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Kiểm tra trang chủ cư dân (/my-home):** Đăng nhập seed.active@test.local. Màn hình tự động điều hướng đến /my-home. Quan sát tổng quan thông tin phòng A104: Tòa nhà Seed House A, giá thuê, chu kỳ thanh toán, ngày bắt đầu hợp đồng và danh sách các thành viên đang ở chung.
2. **Bước 2 - Tra cứu hợp đồng điện tử pháp lý:** Tại mục 'Hợp đồng thuê', bấm nút 'Xem chi tiết hợp đồng'. Kiểm tra nội dung hợp đồng: Thời hạn 12 tháng, thông tin Bên A Chủ nhà Nguyễn Văn An, Bên B khách thuê, số tiền cọc. Bấm nút 'Tải PDF hợp đồng' -> File PDF hiển thị đầy đủ chữ ký vẽ tay của cả hai bên.
3. **Bước 3 - Quản lý hóa đơn và thanh toán trực tuyến:** Tại mục 'Hóa đơn & Chi phí', xem danh sách các kỳ hóa đơn. Xem chi tiết từng khoản mục: Tiền phòng, tiền điện (chỉ số cũ, chỉ số mới, số tiêu thụ), tiền nước, internet. Khách có thể bấm nút thanh toán trực tuyến qua VNPay Sandbox hoặc quét VietQR.
4. **Bước 4 - Theo dõi yêu cầu bảo trì và bài đăng ở ghép:** Kiểm tra danh sách các phiếu báo hỏng đã gửi và kết quả nghiệm thu của thợ. Kiểm tra bài đăng tìm bạn ở ghép và danh sách hồ sơ ứng viên nộp vào phòng.
5. **Bước 5 - Trung tâm thông báo hệ thống (Notification Bell):** Bấm vào biểu tượng quả chuông ở góc trên bên phải màn hình. Xem danh sách thông báo hệ thống được phân loại theo màu sắc: Thông báo phát hành hóa đơn mới, thông báo thợ đã hoàn tất sửa chữa, nhắc nhở đóng tiền nhà.

#### ✅ Kết quả mong đợi (Expected Results):

- Cổng cư dân cung cấp đầy đủ thông tin phòng, hợp đồng, hóa đơn và sự cố một cách trực quan, minh bạch.
- Hợp đồng PDF tải về có đầy đủ chữ ký điện tử vector của cả 2 bên.
- Chuông thông báo cập nhật realtime các sự kiện quan trọng của phòng.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 13.1]`: *Bảng tổng quan phòng đang ở (/my-home) của cư dân.*
> - `[Ảnh 13.2]`: *Chi tiết hợp đồng thuê điện tử và xem file PDF có 2 chữ ký.*
> - `[Ảnh 13.3]`: *Danh sách hóa đơn tiền nhà và các tùy chọn thanh toán trực tuyến.*
> - `[Ảnh 13.4]`: *Lịch sử các phiếu yêu cầu sửa chữa bảo trì của phòng.*
> - `[Ảnh 13.5]`: *Quản lý bài đăng tìm bạn ở ghép và danh sách ứng viên.*
> - `[Ảnh 13.6]`: *Trung tâm thông báo hệ thống (Notification Bell) với các sự kiện realtime.*

---

### LUỒNG 14: BỘ KIỂM THỬ NGOẠI LỆ & XỬ LÝ LỖI ÂM (NEGATIVE TEST CASES)

**🎯 Mục tiêu kiểm thử:** Kiểm thử độ vững chắc của hệ thống đối với 5 kịch bản ngoại lệ quan trọng: Chặn người ở ghép bấm trả phòng; Router Guard chặn khách thường gõ URL quản trị; Chặn Sale duyệt hợp đồng trái thẩm quyền; Chặn nộp hồ sơ dưới 18 tuổi hoặc sai CCCD; Cô lập dữ liệu theo tòa nhà (BuildingScope).

**🏢 Dữ liệu / Tiền điều kiện:** Các tài khoản vi phạm biên quy tắc nghiệp vụ.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Thành viên ở ghép (Roommate)** | `seed.mate@test.local` | `Test@12345` | Thử bấm 'Yêu cầu trả phòng' -> Hệ thống hiển thị cảnh báo chặn quyền rõ ràng. |
| **Khách hàng thông thường** | `seed.new@test.local` | `Test@12345` | Thử gõ trực tiếp URL /admin/finance trên thanh địa chỉ trình duyệt -> Bị Route Guard chặn. |
| **Nhân viên Sale** | `seed.sale.a@test.local` | `Test@12345` | Kiểm tra giao diện không có nút Duyệt hợp đồng; gọi API duyệt bị trả mã 403 Forbidden. |
| **Quản lý Tòa A** | `seed.manager.a@test.local` | `Test@12345` | Kiểm tra BuildingScope: Tuyệt đối không nhìn thấy phòng hay hợp đồng của Tòa B. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Ca 1 - Chặn người ở ghép gửi yêu cầu trả phòng:** Đăng nhập tài khoản bạn cùng phòng seed.mate@test.local (ở ghép tại phòng A104). Vào /my-home -> Thử tìm hoặc bấm nút 'Yêu cầu trả phòng'. Hệ thống hiển thị thông báo lỗi hoặc vô hiệu hóa nút: 'Chỉ người đại diện hợp đồng mới có quyền gửi yêu cầu trả phòng'.
2. **Ca 2 - Route Guard chặn khách hàng truy cập trái phép trang quản trị:** Đăng nhập tài khoản khách hàng thông thường seed.new@test.local. Trên thanh địa chỉ URL của trình duyệt, gõ trực tiếp đường dẫn quản trị: https://.../admin/finance hoặc /admin/staff rồi nhấn Enter. Hệ thống tự động chặn lại thông qua Route Guard và chuyển hướng người dùng về trang chủ kèm thông báo không có quyền truy cập.
3. **Ca 3 - Chặn nhân viên Sale phê duyệt hợp đồng:** Đăng nhập nhân viên Sale seed.sale.a@test.local. Truy cập danh sách hợp đồng hoặc Leads. Xác nhận trên giao diện hoàn toàn không có nút 'Duyệt hợp đồng' (chỉ có nút Xác minh hồ sơ CCCD). Nếu cố tình gửi request POST duyệt hợp đồng qua API, Backend trả về mã lỗi 403 Forbidden.
4. **Ca 4 - Chặn nộp hồ sơ người thuê dưới 18 tuổi hoặc sai định dạng CCCD:** Khách hàng mở form nộp hồ sơ thuê phòng:
- Trường hợp 1: Nhập ngày sinh tính đến hiện tại chưa đủ 18 tuổi -> Hệ thống hiển thị thông báo lỗi màu đỏ cạnh ô nhập: 'Người thuê phòng phải đủ từ 18 tuổi trở lên' và khóa nút Tiếp tục.
- Trường hợp 2: Nhập số CCCD chỉ có 9 số hoặc chứa chữ cái -> Hệ thống báo lỗi: 'Số CCCD phải bao gồm đúng 12 chữ số'.
5. **Ca 5 - Cô lập dữ liệu theo tòa nhà (BuildingScope Isolation):** Đăng nhập Quản lý Tòa A seed.manager.a@test.local. Vào danh mục phòng (/admin/rooms), hợp đồng (/admin/contracts) và bảo trì (/admin/maintenance). Kiểm tra danh sách hiển thị: Tất cả các dòng dữ liệu 100% đều thuộc Tòa Seed House A, tuyệt đối KHÔNG xuất hiện bất kỳ phòng hay hợp đồng nào của Tòa B (B202, B204, B205...) hay Mộc Studio.

#### ✅ Kết quả mong đợi (Expected Results):

- Hệ thống kiểm soát quyền hạn chặt chẽ ở cả Frontend (Route Guard, UI disable) và Backend (Spring Security 403).
- Dữ liệu giữa các tòa nhà được cô lập tuyệt đối theo phân quyền của nhân sự.
- Thông báo lỗi bằng tiếng Việt chuẩn xác, giải thích rõ nguyên nhân cho người dùng.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 14.1]`: *Hệ thống hiển thị cảnh báo chặn người ở ghép gửi yêu cầu trả phòng.*
> - `[Ảnh 14.2]`: *Bộ lọc Route Guard chặn cư dân truy cập trái phép vào các đường dẫn quản trị /admin.*
> - `[Ảnh 14.3]`: *Giao diện Sale không có nút Duyệt hợp đồng (chỉ có nút Xác minh hồ sơ).*
> - `[Ảnh 14.4]`: *Form đăng ký chặn nộp hồ sơ khi chưa đủ 18 tuổi hoặc sai định dạng số CCCD.*
> - `[Ảnh 14.5]`: *Quản lý Tòa A hoàn toàn không thấy hợp đồng hay dữ liệu của Tòa B (BuildingScope).*

---

### LUỒNG 15: HỆ THỐNG NHẮN TIN NỘI BỘ NHÂN SỰ (STAFF CHAT) & CHAT TRỰC TIẾP VỚI KHÁCH THUÊ (LIVE CHAT)

**🎯 Mục tiêu kiểm thử:** Kiểm tra hệ thống trao đổi tin nhắn nội bộ đa chiều: (15A) Nhân sự các vai trò Quản lý, Sale, Kỹ thuật, Admin có thể bấm nút 'Nhắn tin' trực tiếp từ danh mục Nhân sự (/admin/staff) để mở hội thoại nội bộ (loại STAFF) 1-1 với nhau; (15B) Khách thuê nhắn tin trực tiếp với nhân viên qua bong bóng chat nổi (Chat Dock) ở góc màn hình, và kiểm tra tính năng tự động ẩn bong bóng chat khi mở Drawer đăng ký thuê để tránh che nút Tiếp tục.

**🏢 Dữ liệu / Tiền điều kiện:** Hệ thống tin nhắn chat-service với WebSocket/REST API. Nhân sự Tòa A và khách thuê.

#### 🔑 Bảng tài khoản thực hiện luồng này:

| Vai Trò (Role) | Tài Khoản (Username) | Mật Khẩu | Hành Động Cụ Thể Trong Luồng |
| :--- | :--- | :--- | :--- |
| **Quản lý Tòa A** | `seed.manager.a@test.local` | `Test@12345` | Vào /admin/staff -> Bấm nút 'Nhắn tin' cạnh Nhân viên Sale -> Mở chat gửi tin nhắn trao đổi công việc. |
| **Nhân viên Sale Tòa A** | `seed.sale.a@test.local` | `Test@12345` | Vào /admin/messages -> Nhận tin nhắn từ Quản lý và phản hồi lại -> Vào /admin/staff nhắn tin cho Kỹ thuật. |
| **Kỹ thuật viên Tòa A** | `seed.tech.a@test.local` | `Test@12345` | Vào /admin/staff -> Bấm 'Nhắn tin' trao đổi với Quản lý về vật tư sửa chữa. |
| **Khách thuê phòng** | `seed.active@test.local` | `Test@12345` | Bấm bong bóng chat nổi ở góc dưới -> Nhắn tin hỏi đáp ban quản lý -> Mở Drawer thuê phòng kiểm tra bong bóng chat tự động ẩn. |

#### 📋 Các bước thực hiện bằng lời chi tiết:

1. **Bước 1 - Quản lý mở chat nội bộ từ trang Quản lý nhân sự:** Đăng nhập Quản lý Tòa A seed.manager.a@test.local. Truy cập menu 'Nhân sự' (/admin/staff). Tại bảng danh sách nhân viên, tìm đến hàng của Nhân viên Sale seed.sale.a@test.local. Quan sát thấy nút bấm màu xanh 'Nhắn tin' có icon chat. Bấm vào nút 'Nhắn tin'.
2. **Bước 2 - Quản lý gửi tin nhắn nội bộ cho Sale:** Hệ thống tự động khởi tạo hoặc mở hội thoại nội bộ (context type STAFF) và chuyển hướng Quản lý đến trang Tin nhắn (/admin/messages). Quản lý nhập tin nhắn: 'Chào bạn, hôm nay có khách nào hẹn xem phòng A104 không?' rồi nhấn nút Gửi (hoặc phím Enter).
3. **Bước 3 - Sale nhận tin nhắn và phản hồi realtime:** Đăng xuất. Đăng nhập Nhân viên Sale seed.sale.a@test.local. Truy cập menu 'Tin nhắn' (/admin/messages). Nhìn thấy cuộc trò chuyện với Quản lý hiển thị badge tin nhắn mới. Bấm vào xem và gõ câu trả lời: 'Dạ chào anh, chiều nay lúc 14h có 1 khách đến xem phòng A104 ạ!' -> Bấm gửi. Tin nhắn xuất hiện ngay trong cửa sổ chat.
4. **Bước 4 - Kỹ thuật viên nhắn tin nội bộ cho Quản lý:** Đăng nhập Kỹ thuật viên seed.tech.a@test.local. Vào menu /admin/staff -> Bấm nút 'Nhắn tin' cạnh Quản lý seed.manager.a@test.local -> Mở khung chat gửi tin nhắn: 'Em đã thay xong vòi nước phòng A104 rồi anh nhé!'.
5. **Bước 5 - Khách thuê sử dụng bong bóng chat nổi (Chat Dock):** Đăng nhập khách thuê seed.active@test.local. Ở góc dưới cùng bên phải màn hình, quan sát thấy nút bong bóng chat tròn màu xanh đậm với icon trò chuyện. Bấm vào nút bong bóng chat -> Cửa sổ chat nổi (Chat Dock) mở ra mượt mà ở góc phải, cho phép khách gửi câu hỏi trực tiếp cho ban quản lý tòa nhà.
6. **Bước 6 - Kiểm tra tự động ẩn nút chat khi mở Drawer thuê phòng (Fix UI Collision):** Khách hàng vào trang Khám phá (/listings), chọn một phòng trống bất kỳ và bấm 'Đăng ký thuê phòng'. Khi Drawer 'Hồ sơ đăng ký thuê phòng' trượt ra, kiểm tra góc dưới cùng bên phải màn hình: Nút bong bóng chat ĐÃ TỰ ĐỘNG ẨN HOÀN TOÀN, không che mất nút 'Tiếp tục' hay nút 'Hủy' ở footer của Drawer.

#### ✅ Kết quả mong đợi (Expected Results):

- Tất cả các vai trò nhân sự (Quản lý, Sale, Kỹ thuật, Admin) đều có nút 'Nhắn tin' trên từng hàng nhân viên trong trang /admin/staff.
- Cơ chế dedup key hội thoại nội bộ STAFF đảm bảo không bao giờ bị tạo trùng lặp cuộc trò chuyện 1-1 giữa 2 người.
- Khách thuê có Floating Chat Dock tiện lợi để liên hệ ban quản lý ở mọi trang.
- Bong bóng chat tự động ẩn khi mở Drawer hoặc Modal, khắc phục triệt để lỗi che đè nút bấm hành động.

#### 📸 Khung dành cho thành viên nhóm dán ảnh minh chứng:

> **[KHUNG DÀNH CHO BẠN CHỤP VÀ DÁN ẢNH MINH CHỨNG VÀO ĐÂY]**
> - `[Ảnh 15.1]`: *Nút 'Nhắn tin' màu xanh hiển thị trên từng hàng nhân viên trong trang Quản lý nhân sự (/admin/staff).*
> - `[Ảnh 15.2]`: *Giao diện Quản lý mở khung chat nội bộ và gửi tin nhắn cho Sale (/admin/messages).*
> - `[Ảnh 15.3]`: *Nhân viên Sale nhận tin nhắn và phản hồi lại cho Quản lý.*
> - `[Ảnh 15.4]`: *Kỹ thuật viên nhắn tin nội bộ trao đổi công việc với Ban quản lý.*
> - `[Ảnh 15.5]`: *Khách thuê mở cửa sổ chat nổi (Chat Dock) từ bong bóng chat ở góc màn hình.*
> - `[Ảnh 15.6]`: *Drawer đăng ký thuê mở ra, kiểm tra nút bong bóng chat tự động ẩn không che nút Tiếp tục.*

---

## PHẦN 4: HƯỚNG DẪN THỰC HIỆN & PHÂN CÔNG KIỂM THỬ CHO NHÓM

### 4.1. Gợi ý phân chia công việc cho nhóm (Ví dụ nhóm 4-5 người):
- **Thành viên 1:** Kiểm thử Luồng 1, 2, 3 (Thuê phòng, lịch xem, hủy lịch/yêu cầu).
- **Thành viên 2:** Kiểm thử Luồng 4, 5, 6 (Ở ghép, báo hỏng thiết bị, trả phòng & hoàn cọc).
- **Thành viên 3:** Kiểm thử Luồng 7, 8 (Ghi chỉ số điện nước & Thanh toán hóa đơn qua VNPay Sandbox / VietQR).
- **Thành viên 4:** Kiểm thử Luồng 9, 10, 11, 12 (Các màn hình chuyên môn: Admin, Quản lý, Sale, Kỹ thuật).
- **Thành viên 5 (hoặc Trưởng nhóm):** Kiểm thử Luồng 13, 14, 15 (Cổng cư dân, Negative Test ngoại lệ, Chat nội bộ nhân sự & Chat khách hàng).

### 4.2. Quy chuẩn chụp ảnh minh chứng:
1. Chụp ảnh thấy rõ thanh URL trình duyệt và tên tài khoản đang đăng nhập ở góc trên bên phải.
2. Chụp rõ ràng thông báo thành công (Toast notification), badge trạng thái màu sắc và dữ liệu bảng.
3. Đặt tên file ảnh theo đúng cú pháp: `LuongX_AnhY.png` (Ví dụ: `Luong2_Anh1.png`).

### 4.3. Mẫu báo cáo lỗi khi phát hiện bug (Bug Logging Template):
Nếu gặp lỗi trong quá trình kiểm thử, hãy ghi chú vào bảng bên dưới:

| STT | Mã Luồng | Bước thực hiện | Mô tả lỗi thực tế | Kết quả mong đợi | Mức độ (Nghiêm trọng/Vừa/Nhẹ) | Người phát hiện |
| :---: | :---: | :--- | :--- | :--- | :---: | :---: |
| 1 | TC-02 | Bước 4: Tải ảnh CCCD | Báo lỗi định dạng dù là file png | Chấp nhận file png/jpg | Vừa | Nguyễn Văn A |
| 2 | ... | ... | ... | ... | ... | ... |

