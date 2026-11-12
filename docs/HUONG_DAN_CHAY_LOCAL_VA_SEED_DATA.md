Máy tính của bạn bạn bắt buộc phải có **3 điều kiện tiên quyết**:
1. **Node.js (Phiên bản >= 18, khuyến nghị Node 20+ LTS):**  
   File `start-fresh-system.bat` thực chất sẽ gọi các script `manual-test-seed.mjs` và `upload-missing-images.mjs`. Các script này viết bằng JavaScript và chạy trực tiếp bằng lệnh `node`. Nếu máy chưa cài Node.js, lệnh seed sẽ dừng lại và báo lỗi ngay lập tức. Ngoài ra, giao diện Frontend (`rental-system-frontend`) cũng chạy bằng Node.js + Vite.
2. **Docker Desktop:**  
   Backend của chúng ta là hệ thống **Microservices** (chạy PostgreSQL 8 database, Kafka, MinIO lưu trữ ảnh, Discovery Server, API Gateway và 7 services nghiệp vụ). Toàn bộ hạ tầng này chạy trong Docker containers. Do đó Docker Desktop **bắt buộc phải cài và đang được BẬT (Engine running)**.
3. **Cấu hình file `.env` chuẩn Local (Không dùng URL ngrok):**  
   Nếu bạn copy file `.env` đang chứa đường dẫn `ngrok` từ máy bạn sang máy khác thì trên máy bạn ấy **hình ảnh sẽ bị lỗi không hiển thị được**, vì ngrok của máy bạn đã đóng. Phải trỏ URL ảnh về `http://localhost:9000`.

Dưới đây là hướng dẫn chi tiết từng bước, bạn hãy gửi tài liệu này cho các bạn trong nhóm làm theo từ đầu đến cuối!

---

## 🛠️ BƯỚC 1: Kiểm Tra & Cài Đặt Node.js

Node.js là môi trường bắt buộc để:
- Chạy script seed nạp dữ liệu mẫu tự động (tài khoản, phòng trọ, hóa đơn, hợp đồng).
- Upload toàn bộ ảnh phòng/tòa nhà từ thư mục `pictures/` lên MinIO Object Storage.
- Cài đặt thư viện và chạy giao diện Frontend.

### 1.1. Lệnh kiểm tra
Mở **Command Prompt (CMD)** hoặc **PowerShell** trên máy tính (bấm phím `Windows`, gõ `cmd`, bấm `Enter`), sau đó gõ:
```bash
node -v
npm -v
```

---

### 1.2. Các trường hợp xảy ra và cách xử lý

#### 🔴 Trường hợp 1: Máy CHƯA cài Node.js
- **Màn hình hiển thị lỗi:**
  ```text
  'node' is not recognized as an internal or external command, operable program or batch file.
  ```
  *(hoặc: Lệnh 'node' không được nhận diện là một lệnh nội bộ hay bên ngoài...)*
- **Cách khắc phục:**
  1. Mở trình duyệt web, truy cập trang chủ Node.js: **[https://nodejs.org/](https://nodejs.org/)**
  2. Bấm vào nút tải bản **LTS (Long Term Support)** (ví dụ: `v20.x` hoặc `v22.x`).
  3. Mở file cài đặt `.msi` vừa tải về, bấm **Next -> Next -> Install** theo mặc định cho đến khi hoàn thành (**Finish**).
  4. **Quan trọng:** Tắt cửa sổ CMD/PowerShell hiện tại đi và mở lại một cửa sổ mới (để Windows nhận diện PATH).
  5. Gõ lại `node -v` để xác nhận thành công.

---

#### 🟡 Trường hợp 2: Đã có Node.js nhưng phiên bản QUÁ CŨ (< 18)
- **Màn hình hiển thị:**
  ```text
  v14.17.0   (hoặc v16.14.0)
  ```
- **Tại sao phải nâng cấp?**  
  Script seed dữ liệu sử dụng các tính năng mới của JavaScript hiện đại gồm `fetch()`, `FormData`, `Blob` có sẵn từ Node 18 trở lên. Đồng thời Frontend dùng **React 19** và **Vite 8**. Nếu dùng Node cũ sẽ gặp lỗi crash:
  `ReferenceError: fetch is not defined` hoặc `FormData is not defined`.
- **Cách khắc phục:**  
  Vào lại **[https://nodejs.org/](https://nodejs.org/)**, tải bản LTS mới nhất và cài đè lên bản cũ, sau đó mở lại terminal.

---

#### 🟢 Trường hợp 3: Đã cài Node.js hợp lệ
- **Màn hình hiển thị:**
  ```text
  v18.20.0   (hoặc v20.18.0, v22.14.0, v24.x...)
  10.8.2     (phiên bản npm)
  ```
- **Kết luận:** Đạt chuẩn 100%! Bạn chuyển tiếp sang **Bước 2**.

---

## 🐳 BƯỚC 2: Kiểm Tra & Bật Docker Desktop

Docker Desktop là hạ tầng vận hành toàn bộ Backend Microservices bao gồm:
- PostgreSQL (lưu 8 database nghiệp vụ độc lập).
- Apache Kafka (hệ thống Message Broker trao đổi sự kiện).
- MinIO Object Storage (máy chủ lưu trữ ảnh phòng trọ và chứng từ).
- Discovery Server (Eureka Registry).
- API Gateway (cổng tập trung tiếp nhận request tại cổng `8080`).
- 7 Microservices nghiệp vụ Java Spring Boot.

### 2.1. Lệnh kiểm tra
Trong cửa sổ terminal, gõ:
```bash
docker --version
docker ps
```

---

### 2.2. Các trường hợp xảy ra và cách xử lý

#### 🔴 Trường hợp 1: Máy CHƯA cài Docker Desktop
- **Màn hình hiển thị lỗi:**
  ```text
  'docker' is not recognized as an internal or external command...
  ```
- **Cách khắc phục:**
  1. Truy cập trang chủ Docker: **[https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/)**
  2. Tải bản **Docker Desktop for Windows** (khuyến nghị dùng cấu hình WSL 2).
  3. Cài đặt và khởi động lại máy tính (Restart PC) nếu trình cài đặt yêu cầu.

---

#### 🟡 Trường hợp 2: Đã cài Docker nhưng CHƯA BẬT (Lỗi phổ biến nhất!)
- **Màn hình hiển thị lỗi:**
  ```text
  failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is running: open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
  ```
- **Ý nghĩa:** Bạn đã cài Docker nhưng chưa mở ứng dụng Docker Desktop, dịch vụ Docker Engine chưa chạy.
- **Cách khắc phục:**
  1. Bấm phím `Windows`, gõ tìm **Docker Desktop** và bấm mở ứng dụng lên.
  2. Nhìn xuống khay hệ thống góc dưới bên phải màn hình (hoặc góc dưới bên trái cửa sổ ứng dụng Docker), đợi biểu tượng con cá voi chuyển sang trạng thái:  
     🟢 **Engine running** (hoặc biểu tượng cá voi ngừng nhấp nháy).
  3. Quay lại terminal gõ lệnh:
     ```bash
     docker ps
     ```
     Màn hình hiển thị bảng `CONTAINER ID   IMAGE   COMMAND ...` (dù chưa có container nào chạy) là đã sẵn sàng!

---

#### 🟢 Trường hợp 3: Docker đang chạy bình thường
- Lệnh `docker ps` thực thi trơn tru mà không có lỗi kết nối -> Chuyển tiếp sang **Bước 3**.

---

## 📂 BƯỚC 3: Clone Mã Nguồn Dự Án Về Máy

Mở terminal tại thư mục bạn muốn lưu dự án (ví dụ `D:\Projects` hoặc `F:\`) và chạy:

```bash
git clone <URL_REPO_CUA_BAN> rental-systemFinal
cd rental-systemFinal
```

Cấu trúc thư mục dự án sau khi tải về sẽ trông như sau:
```text
rental-systemFinal/
├── rental-system-backend/        # Thư mục mã nguồn Backend Microservices
│   ├── api-gateway/              # Cổng API Gateway (Port 8080)
│   ├── auth-service/             # Xác thực & Phân quyền JWT
│   ├── properties-service/       # Quản lý tòa nhà, phòng trọ, tiện ích
│   ├── listing-booking-service/  # Tin đăng, lịch hẹn xem phòng
│   ├── contract-tenant-service/  # Hợp đồng thuê phòng, khách thuê
│   ├── billing-utility-service/  # Hóa đơn, chỉ số điện nước, thanh toán VNPay
│   ├── asset-maintenance-service/# Tài sản, thiết bị, báo hỏng sửa chữa
│   ├── chat-service/             # Nhắn tin tư vấn trực tuyến
│   ├── infra/                    # Cấu hình docker-compose, postgres init
│   ├── pictures/                 # Kho ảnh thực tế tòa nhà & phòng trọ
│   ├── seed/                     # Script reset & nạp dữ liệu mẫu
│   ├── .env.example              # File cấu hình mẫu Backend
│   └── compose.yaml              # Docker Compose tổng
├── rental-system-frontend/       # Thư mục giao diện Web Frontend (React + Vite)
│   ├── src/                      # Source code React components, pages
│   ├── .env.example              # File cấu hình mẫu Frontend
│   └── package.json              # Khai báo thư viện Frontend
├── pictures/                     # Thư mục chứa hơn 40 ảnh mẫu chất lượng cao
├── seed/                         # Script chạy seed test toàn diện
├── start-fresh-system.bat        # File chạy 1-click khởi động & seed Backend
└── start-fresh-system.ps1        # PowerShell script tự động hóa kiểm tra & chạy
```

---

## ⚙️ BƯỚC 4: Chuẩn Bị File Cấu Hình `.env`

File `.env` chứa mật khẩu và license MinIO nên mặc định được Git loại trừ (`.gitignore`), máy mới clone về sẽ chưa có sẵn.

### 4.1. File `.env` của Backend (`rental-system-backend/.env`)
- **Trường hợp A — Bạn nhận được file `.env` từ trưởng nhóm (Khuyên dùng):**  
  Copy trực tiếp file `.env` đó vào đúng đường dẫn **`rental-system-backend/.env`**.
- **Trường hợp B — Tự tạo từ `.env.example`:**  
  Copy `rental-system-backend/.env.example` thành `rental-system-backend/.env`, sau đó **BẮT BUỘC điền `MINIO_LICENSE_TOKEN=...`** (đăng ký miễn phí tại `subnet.min.io`, vì `infra/docker-compose.yml` yêu cầu biến này khác rỗng khi khởi động container MinIO AIStor).

⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG VỀ ĐƯỜNG DẪN ẢNH VÀ THANH TOÁN VNPAY:**  
Mở file `rental-system-backend/.env` lên và chắc chắn các biến sau được thiết lập đúng chuẩn Local:
```env
# 1. MinIO trỏ về localhost để không bị vỡ ảnh trên máy local
MINIO_PUBLIC_ENDPOINT=http://localhost:9000
PROPERTY_IMAGE_PUBLIC_BASE_URL=http://localhost:9000/property-images
R2_BUCKET=property-images
PROPERTY_IMAGE_BUCKET=property-images

# 2. Whitelist cổng Frontend local 8443/5173/3000 để khi test thanh toán VNPay không bị lỗi 400
VNPAY_ALLOWED_RETURN_URLS=http://localhost:8443/payment/vnpay-return,http://localhost:5173/payment/vnpay-return,http://localhost:3000/payment/vnpay-return,https://rental-system-frontend-ten.vercel.app/payment/vnpay-return
```
*Lý do:* MinIO chạy trong container máy bạn và mở cổng `9000`. Khi trỏ về `localhost:9000`, trình duyệt web của bạn sẽ tải trực tiếp ảnh từ container máy bạn mà không cần internet hay tunnel ngrok. Cổng `8443` là cổng mặc định của Frontend Vite. (Không cần set `MINIO_ENDPOINT` / `R2_ENDPOINT` trong `.env` để Docker compose tự dùng `http://minio:9000`, còn chạy lẻ từ IntelliJ tự dùng `http://localhost:9000`.)

### 4.2. File `.env` của Frontend (`rental-system-frontend/.env`)
Copy file `.env` từ trưởng nhóm hoặc copy từ `rental-system-frontend/.env.example` thành `rental-system-frontend/.env`:
```env
VITE_API_URL=http://localhost:8080
```
*Lý do:* Mọi request từ giao diện người dùng sẽ gửi tới API Gateway Backend tại cổng `8080`.

---

## 🚀 BƯỚC 5: Khởi Động Backend & Tự Động Seed Dữ Liệu Mẫu (Kèm Ảnh)

Đây là bước quan trọng nhất! Bạn chỉ cần thực hiện đúng **1 thao tác**:

### 5.1. Chạy File Khởi Động
- **Cách 1 (Giao diện Windows):**  
  Mở thư mục `rental-systemFinal`, tìm file **`start-fresh-system.bat`** và **nhấp đúp chuột (Double Click)** vào file đó.
- **Cách 2 (Dùng Terminal):**  
  Mở PowerShell tại thư mục `rental-systemFinal` và gõ:
  ```powershell
  .\start-fresh-system.bat
  ```

---

### 5.2. Quá trình tự động diễn ra như thế nào?
Cửa sổ màu đen sẽ mở ra và tự động thực hiện tuần tự:

1. **`[BƯỚC 0/3] Kiểm tra điều kiện tiên quyết:`**
   - Tự động kiểm tra Node.js (nếu thiếu hoặc version < 18 sẽ dừng và hướng dẫn cài).
   - Tự động kiểm tra Docker Desktop (nếu Docker chưa bật sẽ dừng và nhắc bạn mở Docker Desktop lên).
   - Tự động tạo file `.env` nếu bạn chưa tạo sẵn.
   - **Tự động kiểm tra & biên dịch file `.jar`:** Vì file `.jar` được Git loại trừ, máy mới clone về sẽ chưa có. Script sẽ tự động phát hiện và biên dịch mã nguồn Backend ra 10 file `.jar` bằng Maven (hoặc tự động gọi Docker Maven `maven:3.9-eclipse-temurin-21` nếu máy bạn chưa cài Java/Maven) mà bạn không cần phải làm thủ công!
2. **`[BƯỚC 1/3] Khởi động các container Backend:`**
   - Chạy lệnh `docker compose up -d` để khởi động:
     + `rental-postgres`: Cơ sở dữ liệu PostgreSQL.
     + `rental-kafka`: Message broker sự kiện.
     + `rental-minio`: Máy chủ lưu trữ ảnh (Port `9000`, Console `9001`).
     + `rental-discovery`: Eureka Discovery Server (Port `8761`).
     + `rental-gateway`: API Gateway (Port `8080`).
     + Các dịch vụ: auth, properties, listing, contract, billing, asset, chat.
   - Chờ hệ thống database và các container đạt trạng thái sẵn sàng (healthy).
3. **`[BƯỚC 2/3] Dọn sạch dữ liệu cũ & Nạp dữ liệu mới:`**
   - Xóa và tạo mới 8 database độc lập: `auth_db`, `user_db`, `property_db`, `listing_db`, `contract_db`, `billing_db`, `asset_db`, `chat_db`.
   - Flyway tự động khởi tạo các bảng và ràng buộc khóa ngoại mới nhất.
   - Nạp danh mục địa lý hành chính (tỉnh/thành phố, quận/huyện, phường/xã chuẩn).
   - Tạo tài khoản quản trị hệ thống Bootstrap Admin.
   - Chạy script `manual-test-seed.mjs`: Tự động nạp kịch bản kinh doanh thực tế bao gồm tòa nhà mẫu, các hạng phòng, giá thuê, hợp đồng thuê phòng, khách thuê, người ở cùng, phiếu yêu cầu sửa chữa, hóa đơn tiền phòng & tiền điện nước hàng tháng.
   - Chạy script `upload-missing-images.mjs`: Tự động nạp hơn **40 bức ảnh thật** (ngoại thất tòa nhà, phòng tiêu chuẩn, phòng studio có gác lửng, đồ nội thất) từ thư mục `pictures/` lên MinIO và gắn vào từng phòng trọ tương ứng.

---

### 5.3. Kết quả thành công
Khi màn hình xuất hiện thông báo màu **XANH LÁ CÂY**:

```text
======================================================================
   HE THONG BACKEND DA DUOC KHOI DONG VA SEED DATA TEST THANH CONG!   
======================================================================

  📌 TÀI KHOẢN ĐĂNG NHẬP KIỂM THỬ (Mật khẩu: Test@12345):
     - Admin Bootstrap : e2e.admin@example.com / E2ePass123!
     - Admin Seed      : seed.admin@test.local / Test@12345
     - Quản lý Tòa A/B : seed.manager.a@test.local | seed.manager.b@test.local
     - Sale / Kỹ thuật : seed.sale.a@test.local | seed.tech.a@test.local
     - Khách đang thuê : seed.active@test.local (hợp đồng & hóa đơn mẫu)
     - Khách tìm phòng : seed.new@test.local | seed.appt@test.local

  🚀 BƯỚC TIẾP THEO: KHỞI ĐỘNG FRONTEND
     1. Mở một cửa sổ Terminal/PowerShell mới.
     2. Gõ lệnh: cd rental-system-frontend
     3. Gõ lệnh: npm install
     4. Gõ lệnh: npm run dev
     5. Truy cập trình duyệt tại: http://localhost:8443
======================================================================
```
👉 Bạn hãy giữ nguyên cửa sổ này (không tắt) hoặc thu nhỏ lại.

---

## 💻 BƯỚC 6: Khởi Động Giao Diện Web Frontend

Bây giờ bạn mở một cửa sổ **Terminal/PowerShell MỚI** để chạy giao diện người dùng:

### 6.1. Di chuyển vào thư mục Frontend
```bash
cd rental-systemFinal/rental-system-frontend
```

### 6.2. Cài đặt các gói thư viện
```bash
npm install
```
*(Chờ 1 - 2 phút để npm tải các thư viện React, Vite, Tailwind CSS về máy)*.

### 6.3. Khởi động Web Dev Server
```bash
npm run dev
```

Màn hình sẽ hiển thị:
```text
  VITE v8.0.5  ready in 450 ms

  ➜  Local:   http://localhost:8443/
  ➜  Network: use --host to expose
```

### 6.4. Mở trình duyệt Web
Mở Chrome, Edge hoặc trình duyệt yêu thích của bạn và truy cập:  
👉 **`http://localhost:8443`**

Trang chủ hệ thống tìm kiếm phòng trọ với giao diện hiện đại, bản đồ tương tác và các phòng mẫu đầy đủ hình ảnh sẽ xuất hiện!

---

## 👥 BƯỚC 7: Danh Sách Tài Khoản Mẫu Để Kiểm Thử Mọi Tính Năng

Tất cả các tài khoản seed (`seed.*@test.local`) đều có chung mật khẩu: **`Test@12345`**

| Vai trò (Role) | Email đăng nhập | Mật khẩu | Kịch bản kiểm thử có thể thực hiện |
|---|---|---|---|
| **Admin Hệ Thống** | `seed.admin@test.local` | `Test@12345` | • Xem toàn bộ thống kê doanh thu, quản lý **Seed House A**, **Seed House B** và tất cả phòng (`A101..A108`, `B201..B205`).<br>• Thu tiền mặt hóa đơn, quản lý tài khoản & phân quyền nhân sự. |
| **Admin Bootstrap** | `e2e.admin@example.com` | `E2ePass123!` | Tài khoản quản trị gốc tạo sẵn lúc bootstrap. |
| **Quản lý Tòa A / B** | `seed.manager.a@test.local`<br>`seed.manager.b@test.local` | `Test@12345` | • Phụ trách riêng **Seed House A** hoặc **Seed House B**.<br>• Duyệt yêu cầu thuê, tạo/gửi ký hợp đồng, duyệt trả phòng/gia hạn, phân công bảo trì, thu tiền mặt hóa đơn. |
| **Sale Tòa A / B** | `seed.sale.a@test.local`<br>`seed.sale.b@test.local` | `Test@12345` | • Quản lý tin đăng phòng, lịch hẹn xem phòng, xác minh hồ sơ yêu cầu thuê trong tòa được phân công. |
| **Kỹ thuật Tòa A / B** | `seed.tech.a@test.local`<br>`seed.tech.b@test.local` | `Test@12345` | • Chốt chỉ số điện nước hàng tháng, tiếp nhận & hoàn tất phiếu sửa chữa và lịch bảo trì định kỳ. |
| **Khách thuê (có hợp đồng ACTIVE & hóa đơn)** | `seed.active@test.local` | `Test@12345` | • Đang ở phòng thuộc hợp đồng hiệu lực.<br>• Test xem "Phòng của tôi", hóa đơn & thanh toán **VNPay Sandbox**, báo hỏng thiết bị, nhắn tin chat với quản lý. |
| **Khách thuê (ở ghép / gia hạn / trả phòng)** | `seed.mate@test.local`<br>`seed.renew@test.local`<br>`seed.moveout@test.local`<br>`seed.old@test.local` | `Test@12345` | • Kiểm thử hợp đồng ở ghép, luồng gia hạn hợp đồng, trả phòng & hoàn cọc, hóa đơn tổng phòng (`B201`, `B203`...). |
| **Khách vãng lai / tìm phòng** | `seed.new@test.local`<br>`seed.appt@test.local`<br>`seed.draft@test.local` | `Test@12345` | • Tìm kiếm phòng trên bản đồ, đặt lịch xem phòng, gửi yêu cầu thuê, ký hợp đồng nháp, chat tư vấn. |

---

## 🔍 BƯỚC 8: Kiểm Tra & Nghiệm Thu Hệ Thống (Verification)

Sau khi chạy xong, bạn có thể kiểm tra các cổng dịch vụ sau để đảm bảo mọi thành phần đều "sống":

1. **Giao diện người dùng Frontend (`http://localhost:8443`):**
   - Đăng nhập thử với `seed.admin@test.local` / `Test@12345` → vào **Tòa nhà** (`Seed House A`, `Seed House B`) và **Phòng & Tin đăng** để kiểm tra ảnh phòng/tòa nhà hiển thị sắc nét từ `http://localhost:9000/property-images/...`.
   - Thử đăng nhập `seed.active@test.local` / `Test@12345` → kiểm tra trang chủ, phòng của tôi, thanh toán VNPay Sandbox.
2. **Cổng API Gateway & Swagger UI tập trung (`http://localhost:8080`):**
   - Truy cập **`http://localhost:8080`** (tự chuyển về `/swagger-ui.html`) để xem đầy đủ tài liệu Swagger của cả **8 microservices** phân nhóm rõ ràng (`auth-service`, `user-notification-service`, `property-service`, `listing-booking-service`, `chat-service`, `contract-tenant-service`, `billing-utility-service`, `asset-maintenance-service`).
   - Lưu ý chuẩn Microservices: các cổng nội bộ `8081`–`8088` không mở ra ngoài khi chạy Docker; mọi request từ FE/Swagger đều đi qua cổng duy nhất `8080`.
   - Kiểm tra health: `http://localhost:8080/actuator/health` → `{"status":"UP"}`.
3. **Bảng điều khiển Eureka Discovery Server (`http://localhost:8761`):**
   - Trong bảng `Instances currently registered with Eureka`, phải thấy đủ 9 service `UP`:
     + `API-GATEWAY`
     + `AUTH-SERVICE`
     + `USER-NOTIFICATION-SERVICE`
     + `PROPERTY-SERVICE`
     + `LISTING-BOOKING-SERVICE`
     + `CONTRACT-TENANT-SERVICE`
     + `BILLING-UTILITY-SERVICE`
     + `ASSET-MAINTENANCE-SERVICE`
     + `CHAT-SERVICE`
4. **Máy chủ lưu trữ ảnh MinIO Web Console (`http://localhost:9001`):**
   - Đăng nhập `minioadmin` / `change_me_in_dot_env` → vào **Buckets** → `property-images` sẽ thấy các ảnh `building/` và `room/` đã được upload đủ.
5. **Nếu muốn chạy từng service trực tiếp bằng IntelliJ (thay vì container service Docker):**
   - Giữ các container hạ tầng đang chạy (`postgres`, `kafka`, `minio`, `discovery-server`), sau đó **Stop** container của riêng service bạn muốn debug (ví dụ `docker stop rental-auth`) để không trùng cổng, rồi bấm Run `*Application` trong IntelliJ (các `application.yml` đã cấu hình sẵn fallback `localhost:5432`, `localhost:9000`, `localhost:9092`, `localhost:8761`).

---

## ❓ BƯỚC 9: Bảng Khắc Phục Các Sự Cố Thường Gặp (Troubleshooting)

### 1. Lỗi: `ExecutionPolicy` trong PowerShell chặn không cho chạy file `.ps1`
- **Hiện tượng:** Khi chạy `start-fresh-system.bat` hoặc lệnh PowerShell, máy báo:
  `...cannot be loaded because running scripts is disabled on this system.`
- **Khắc phục:** Mở PowerShell bằng quyền Admin (Run as Administrator) và gõ lệnh sau:
  ```powershell
  Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned -Force
  ```
  Sau đó chạy lại file `.bat`.

### 2. Lỗi: Xung đột cổng (Port is already in use)
- **Hiện tượng:** Docker hoặc Vite báo cổng `8080`, `5432`, `9000` hoặc `8443` đã bị một chương trình khác chiếm dụng.
- **Khắc phục:**
  - Kiểm tra xem bạn có đang bật một bản PostgreSQL cài trực tiếp trên Windows hay không (PostgreSQL cài bằng installer thường chạy ngầm ở cổng 5432). Hãy vào `services.msc` tìm dịch vụ `postgresql-x64` và bấm **Stop**.
  - Kiểm tra xem cổng 8080 hoặc 8443 có chương trình nào đang dùng không bằng lệnh:
    ```cmd
    netstat -ano | findstr :8080
    netstat -ano | findstr :8443
    ```
    Nếu có PID chiếm giữ, bạn có thể tắt nó trong Task Manager hoặc đổi cổng dev server của Frontend bằng cách chạy:
    ```bash
    npm run dev -- --port 3000
    ```

### 3. Lỗi: Hình ảnh phòng trọ bị vỡ hoặc không hiển thị
- **Nguyên nhân:** File `rental-system-backend/.env` đang để URL ảnh trỏ về domain `ngrok` cũ của người khác.
- **Khắc phục:** Mở file `rental-system-backend/.env`, tìm và sửa thành:
  ```env
  MINIO_PUBLIC_ENDPOINT=http://localhost:9000
  PROPERTY_IMAGE_PUBLIC_BASE_URL=http://localhost:9000/property-images
  ```
  Sau đó chạy lại `start-fresh-system.bat` để script seed cập nhật lại link ảnh mới vào cơ sở dữ liệu.

### 4. Lỗi: Muốn reset sạch toàn bộ dữ liệu để test lại từ đầu thì làm thế nào?
- Bạn **KHÔNG CẦN** xóa thư mục hay xóa Docker thủ công.
- Chỉ cần **chạy lại file `start-fresh-system.bat` một lần nữa**. Script sẽ tự động xóa sạch 8 database cũ, tạo mới cấu trúc bảng và nạp lại toàn bộ dữ liệu seed và ảnh mẫu như mới trong vòng 2-3 phút!

---

*Chúc nhóm bạn cài đặt thành công và hoàn thành xuất sắc đợt kiểm thử hệ thống! Nếu gặp bất kỳ vấn đề nào phát sinh trong quá trình chạy, hãy kiểm tra lại mục 9 hoặc liên hệ nhóm để được hỗ trợ.*
