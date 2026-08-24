# Plan xây dựng Database — Fast Food Pre-order Pickup & POS (V6)

DBMS: **Microsoft SQL Server**. Nguồn sự thật: `docs/preview-2 (1).html` (Baseline V6).
Tài liệu này là **kế hoạch thi công**, không phải script. Script nằm ở `database/`.

---

## PHẦN A — QUYẾT ĐỊNH CHUẨN (chốt trước khi viết dòng SQL đầu tiên)

Mọi quyết định dưới đây phải chốt trước, vì sửa sau sẽ kéo theo sửa toàn bộ DAO.

### A1. Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ |
|---|---|---|
| Bảng | PascalCase, **số ít** | `Product`, `OrderItem` |
| Cột | snake_case | `pickup_time`, `released_to_kds_at` |
| Khóa chính | `<table>_id`, `INT IDENTITY(1,1)` | `order_id` |
| Khóa ngoại | `FK_<Child>_<Parent>` | `FK_OrderItem_Orders` |
| CHECK | `CK_<Table>_<field>` | `CK_Orders_status` |
| UNIQUE | `UQ_<Table>_<field>` | `UQ_Payment_attempt` |
| Index thường | `IX_<Table>_<mục đích>` | `IX_Orders_release` |
| Unique index (filtered) | `UX_<Table>_<field>` | `UX_Orders_pickupCode` |
| View | `vw_<Nội dung>` | `vw_OnTimeReady` |

**Ngoại lệ bắt buộc — 3 bảng phải đổi tên vì trùng từ khóa SQL Server:**

| Entity trong tài liệu | Tên bảng thực tế | Lý do |
|---|---|---|
| `User` | **`Users`** | `USER` là reserved keyword |
| `Order` | **`Orders`** | `ORDER` là reserved keyword |
| `Transaction` | **`PaymentTransaction`** | `TRANSACTION` là reserved keyword |

> Java entity vẫn giữ tên `User`, `Order`, `Transaction` đúng tài liệu; ánh xạ tên bảng chỉ nằm trong DAO.

### A2. Kiểu dữ liệu — chuẩn hóa toàn hệ thống

| Nhóm | Kiểu chốt | Lý do |
|---|---|---|
| Khóa chính | `INT IDENTITY(1,1)` (riêng `AuditLog` là `BIGINT`) | AuditLog ghi nhiều nhất |
| Tiền | `DECIMAL(12,2)` | Không dùng `FLOAT`/`MONEY`; tránh sai số cộng dồn doanh thu |
| Thời gian | `DATETIME2(0)` | Đủ độ chính xác giây; nhẹ hơn `DATETIME2(7)` |
| Text tiếng Việt | `NVARCHAR(n)` | `name`, `description`, `full_name` |
| Mã / enum / email / url | `VARCHAR(n)` | Chỉ ASCII, tiết kiệm nửa dung lượng |
| Cờ boolean | `BIT` | `is_available` |
| Payload thô | `NVARCHAR(MAX)` | `raw_reference`, `old_value`, `new_value` |

**Quy ước thời gian (quan trọng, dễ sai nhất):**
- Toàn bộ so sánh `now` vs `kitchen_release_at` / `pickup_time` phải dùng **một nguồn thời gian duy nhất**.
- Chốt: **application sinh mọi timestamp nghiệp vụ** (`LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))`), truyền xuống qua parameter. `DEFAULT SYSDATETIME()` chỉ là lưới an toàn cho cột `created_at`.
- Lý do: nếu Tomcat và SQL Server khác timezone/lệch đồng hồ, scheduler sẽ release sai giờ → vi phạm NFR-03 mà rất khó debug.
- Kiểm tra khi cài đặt: `SELECT SYSDATETIME()` phải khớp giờ máy chạy Tomcat (chênh < 5 giây).

### A3. Enum lưu thế nào

Chốt: **`VARCHAR` + `CHECK` constraint**, không tạo bảng lookup — trừ `Role`.

- Lý do: 8 tập giá trị đã bị **khóa** ở mục 18 (không phát sinh giá trị mới trong MVP); đọc dữ liệu thô khi debug thấy ngay `PENDING_PAYMENT` thay vì `1`; khớp 1-1 với enum Java đã tạo ở `common/constant`.
- Riêng `Role` giữ dạng bảng vì có FK từ `Users` và có cột mô tả, đúng mô tả mục 10 ("1 Role – N User").
- **Rủi ro phải chấp nhận:** thêm giá trị mới = `ALTER TABLE ... DROP/ADD CONSTRAINT`. Chấp nhận được vì V6 đã khóa.

### A4. Collation & cấu hình database

| Cấu hình | Giá trị | Lý do |
|---|---|---|
| Collation | `Vietnamese_CI_AS` | Sắp xếp/tìm kiếm tên món tiếng Việt đúng; không phân biệt hoa thường khi search menu (CUS-01) |
| `READ_COMMITTED_SNAPSHOT` | **ON** | KDS poll mỗi 2 giây (NFR-04) đọc liên tục; bật RCSI để reader không chặn writer của bếp |
| `ALLOW_SNAPSHOT_ISOLATION` | ON | Dự phòng cho báo cáo dài |
| Recovery model | `SIMPLE` (môi trường demo) | Không cần point-in-time restore |

### A5. Nguyên tắc "rule nằm ở đâu"

| Loại rule | Nơi enforce | Ví dụ |
|---|---|---|
| Ràng buộc trong 1 dòng | **CHECK constraint** | `pickup_time` bắt buộc khi `order_source='ONLINE_PREORDER'` |
| Tính duy nhất | **UNIQUE / filtered unique index** | `external_transaction_id` |
| Chống race condition | **Câu UPDATE có điều kiện + `@@ROWCOUNT`** | release KDS 1 lần |
| Rule liên bảng đơn giản, nguy cơ tài chính | **Trigger** (phòng vệ lớp 2) | Online không được CASH |
| Rule cần đọc nhiều bảng / thời gian / quyền | **Service layer** | BR-05, BR-12, BR-15 |

> Nguyên tắc: **rule tài chính và rule chống trùng phải có ít nhất 1 lớp bảo vệ ở DB.** Không tin tưởng riêng tầng ứng dụng, vì scheduler + callback + user thao tác đồng thời.

---

## PHẦN B — DANH SÁCH BẢNG & DATA DICTIONARY

13 bảng, chia 5 nhóm theo thứ tự phụ thuộc (cũng là thứ tự tạo bảng):

```
Nhóm 1 — Danh mục & người dùng   : Role → Users → Category → Product
Nhóm 2 — Giỏ hàng                : Cart → CartItem
Nhóm 3 — Đơn hàng (lõi)          : Orders → OrderItem
Nhóm 4 — Thanh toán              : Payment → PaymentTransaction
Nhóm 5 — Vận hành & audit        : Notification, KitchenIssue, AuditLog
```

### B1. `Role`

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `role_id` | INT IDENTITY | PK | |
| `name` | VARCHAR(20) | NOT NULL, UNIQUE | CHECK ∈ {CUSTOMER, CASHIER, KITCHEN, ADMIN} |
| `description` | NVARCHAR(200) | NULL | |

MVP: **1 User = 1 Role** → không có bảng `UserRole` trung gian.

### B2. `Users`

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| `user_id` | INT IDENTITY | PK | |
| `full_name` | NVARCHAR(100) | NOT NULL | |
| `email` | VARCHAR(150) | NOT NULL, UNIQUE | Dùng làm username đăng nhập |
| `phone` | VARCHAR(20) | NULL | |
| `password_hash` | VARCHAR(255) | NOT NULL | **bcrypt** (NFR-01). Độ dài 255 để đổi thuật toán sau này |
| `role_id` | INT | NOT NULL, FK→Role | |
| `status` | VARCHAR(20) | NOT NULL, DEF `'ACTIVE'` | ACTIVE / LOCKED. ADM-04 dùng "Lock", **không delete** |
| `must_change_password` | BIT | NOT NULL, DEF `0` | **BR-23**. Admin reset password hộ → bật cờ; user tự đổi → tắt cờ |
| `email_verified` | BIT | NOT NULL, DEF `0` | Chủ tài khoản đã bấm liên kết trong thư xác thực chưa. Khách tự đăng ký bắt đầu ở `0`; nhân viên do Admin tạo được đặt `1` ngay |
| `created_at` | DATETIME2(0) | NOT NULL | |
| `updated_at` | DATETIME2(0) | NULL | |

`must_change_password` được enforce ở `AuthenticationFilter`, không ở từng servlet: chỉ cần
sót một màn hình là rào chắn mất tác dụng. Hai đường duy nhất còn mở là `/profile` và
`/logout` — thiếu `/logout` thì người dùng bị kẹt hẳn, không đổi được mà cũng không thoát ra được.

`email_verified` thì ngược lại: enforce ở **tầng Service**, tại `CustomerOrderService.createOnlineOrder`.
Nó không chặn cả một vùng màn hình như cờ trên mà chỉ chặn đúng một thao tác — đặt đơn online,
nơi email thật sự cần thiết vì tin "món đã sẵn sàng" và mã nhận hàng đi qua đó. Chưa xác thực
vẫn đăng nhập và xem thực đơn được. Xem `docs/STRUCTURE.md` §3.9.

Hai bảng mã dùng một lần — `PasswordResetToken` và `EmailVerificationToken` — có cùng hình dáng
(băm SHA-256, `expires_at`, `used_at`, `requested_ip`, `created_at`) và cố ý tách đôi thay vì
gộp một bảng kèm cột phân loại: gộp lại thì mọi câu lệnh của cả hai luồng đều phải nhớ kèm điều
kiện lọc, quên đúng một chỗ là mã xác thực email đổi được mật khẩu. Chi tiết ở đầu mỗi bảng
trong `database/FastFoodPreorder.sql`.

### B3. `Category` / B4. `Product`

`Category`: `category_id`, `name` NVARCHAR(100), `status` (ACTIVE/INACTIVE), `display_order` INT.

`Product`: `product_id`, `category_id` FK, `name` NVARCHAR(150), `description` NVARCHAR(500),
`price` DECIMAL(12,2) CHECK ≥ 0, `image_url` VARCHAR(255), `is_available` BIT, `status` (ACTIVE/INACTIVE),
`created_at`, `updated_at`.

**Điểm cần chú ý:** BR-01 yêu cầu *"Product active + available, thuộc Category active"* → có **3 điều kiện** ở 2 bảng, không enforce được bằng CHECK. Đây là **join bắt buộc trong mọi query menu**:

```sql
FROM Product p JOIN Category c ON c.category_id = p.category_id
WHERE p.status='ACTIVE' AND p.is_available=1 AND c.status='ACTIVE'
```

Tách 2 cột `is_available` và `status` là cố ý: `status` = còn bán trên menu hay đã ngừng kinh doanh (Admin), `is_available` = tạm hết hàng trong ngày.

### B5. `Cart` / B6. `CartItem`

`Cart`: `cart_id`, `user_id` **UNIQUE** (1 Customer = 1 giỏ), `updated_at`.
`CartItem`: `cart_item_id`, `cart_id` FK **ON DELETE CASCADE**, `product_id` FK, `quantity` CHECK > 0,
`UNIQUE(cart_id, product_id)`.

- Cart chỉ dành cho **Customer Online đã login** (BR-04). POS dựng cart tạm trong session/JS, **không ghi DB** — tránh rác dữ liệu cho khách walk-in.
- `UNIQUE(cart_id, product_id)`: thêm lại món đã có thì `UPDATE quantity`, không tạo dòng thứ hai.
- Đây là bảng **duy nhất** được phép CASCADE DELETE, vì cart không phải dữ liệu giao dịch (không dính BR-20).

### B7. `Orders` — bảng phức tạp nhất

| Cột | Kiểu | Null | Rule |
|---|---|---|---|
| `order_id` | INT IDENTITY | PK | |
| `customer_id` | INT | **NULL** | FK→Users. NULL cho POS walk-in guest |
| `created_by_user_id` | INT | NULL | FK→Users. Cashier tạo POS order |
| `order_source` | VARCHAR(20) | NOT NULL | CHECK ∈ {ONLINE_PREORDER, POS} — **BR-03** |
| `total_amount` | DECIMAL(12,2) | NOT NULL | = SUM(OrderItem.unit_price × quantity) |
| `order_status` | VARCHAR(20) | NOT NULL | CHECK 7 giá trị mục 7.1 |
| `idempotency_key` | VARCHAR(64) | NULL | **NFR-07** |
| `pickup_time` | DATETIME2(0) | NULL | **BR-05** ≥ checkout + 30' |
| `kitchen_release_at` | DATETIME2(0) | NULL | **BR-08** = pickup_time − 20' |
| `released_to_kds_at` | DATETIME2(0) | NULL | **BR-09** ghi đúng 1 lần |
| `pickup_code` | VARCHAR(20) | NULL | **BR-15**, chỉ Online |
| `ready_at` | DATETIME2(0) | NULL | Aggregate (BR-11), dùng cho On-time Ready Rate |
| `picked_up_at` | DATETIME2(0) | NULL | **BR-16** |
| `handoff_by_user_id` | INT | NULL | **BR-16** FK→Users |
| `created_at` / `completed_at` / `expired_at` | DATETIME2(0) | | Mốc vòng đời |

**CHECK constraint bắt buộc:**

| Tên | Nội dung | Chặn được lỗi gì |
|---|---|---|
| `CK_Orders_source` | ∈ {ONLINE_PREORDER, POS} | Lọt kênh DELIVERY (BR-03) |
| `CK_Orders_status` | ∈ 7 giá trị | Trạng thái tự chế |
| `CK_Orders_pendingOnlineOnly` | `status<>'PENDING_PAYMENT' OR source='ONLINE_PREORDER'` | POS lọt vào PENDING_PAYMENT — POS không có trạng thái này (mục 7.1) |
| `CK_Orders_pickupTime` | Online ⇒ `pickup_time IS NOT NULL`; POS ⇒ `pickup_time IS NULL` | Online thiếu giờ hẹn / POS bị gán nhầm giờ hẹn |
| `CK_Orders_onlineCustomer` | `order_source <> 'ONLINE_PREORDER' OR customer_id IS NOT NULL` | Đơn Online mồ côi: không tra được lịch sử, không gửi được notification, không check được ownership (BR-21) |

**Ba trường "3 mốc thời gian" phải hiểu đúng — đây là lõi của V6:**

```
kitchen_release_at  = KẾ HOẠCH   (tính 1 lần lúc auto-confirm)
released_to_kds_at  = THỰC TẾ    (scheduler ghi, NULL = chưa release)
pickup_time         = CAM KẾT với khách
```
Chênh lệch `released_to_kds_at − kitchen_release_at` chính là thước đo NFR-03 (≤ 60 giây).

**Không tạo cột `release_state`/`is_overdue`** — mục 7.2 và BR-17 nói rõ đây là giá trị **suy ra**, để tránh phình state machine và tránh dữ liệu không đồng bộ. Suy ra bằng view (xem D1).

### B8. `OrderItem`

| Cột | Kiểu | Rule |
|---|---|---|
| `order_item_id` | INT IDENTITY PK | |
| `order_id` | INT NOT NULL FK→Orders | **KHÔNG cascade delete** (BR-20) |
| `product_id` | INT NOT NULL FK→Product | Giữ FK để truy vết best-seller |
| `product_name_snapshot` | NVARCHAR(150) NOT NULL | **BR-02** |
| `unit_price` | DECIMAL(12,2) NOT NULL | **BR-02** |
| `quantity` | INT NOT NULL CHECK > 0 | |
| `item_status` | VARCHAR(20) DEF `'WAITING'` | CHECK ∈ {WAITING, PREPARING, READY} |
| `assigned_to_user_id` | INT NULL FK→Users | Kitchen claim (UC-11) |
| `started_at`, `ready_at` | DATETIME2(0) NULL | |
| `handed_over_at` | DATETIME2(0) NULL | **BR-25**. Bếp đặt món lên quầy (UC-12a) |
| `handed_over_by` | INT NULL FK→Users | Ghi được ai đưa ra, để đối chiếu khi thiếu món |
| `received_at` | DATETIME2(0) NULL | **BR-25**. Thu ngân cầm món (UC-12b) |
| `received_by` | INT NULL FK→Users | |
| `CK_OrderItem_handover` | CHECK | `received_at IS NULL OR handed_over_at IS NOT NULL` |

**Vì sao vừa có FK `product_id` vừa có snapshot:** FK phục vụ báo cáo best-seller (gom theo sản phẩm); snapshot phục vụ tính toàn vẹn lịch sử khi Admin đổi tên/giá (BR-02). Thiếu snapshot → hóa đơn cũ đổi giá theo. Thiếu FK → không gom nhóm được báo cáo.

**BR-18** (chỉ READY khi toàn bộ quantity xong): **không** tách thành nhiều dòng theo từng cái. Một `OrderItem` là một task nguyên khối trên KDS — không có trạng thái "xong 2/3". Không cần cột thêm.

#### Vì sao bàn giao là bốn cột chứ không phải hai bậc của `item_status`

Cách rẻ nhất trông có vẻ là nối dài chuỗi trạng thái:

```
WAITING → PREPARING → READY → HANDED_OVER → RECEIVED     ← KHÔNG chọn
```

Nó hỏng ở chỗ **đổi nghĩa của `READY`**. Hàng chục chỗ trong mã nguồn đang đếm "món chưa xong"
bằng `item_status <> 'READY'`: tổng hợp trạng thái đơn (BR-11), chỉ số đúng hẹn, màn hình lịch
sử bếp, điều kiện đưa đơn xuống bếp. Thêm hai bậc thì mọi câu đó phải sửa thành
`item_status NOT IN ('READY','HANDED_OVER','RECEIVED')` — sửa nhiều chỗ mà **nghĩa không hề đổi**,
và chỉ cần sót một chỗ là đơn đã nấu xong bị đếm nhầm thành chưa xong.

Bốn cột timestamp giữ `READY` nguyên nghĩa "bếp đã nấu xong", và mô tả một **trục song song**:
món ở đâu về mặt vật lý.

```
item_status : WAITING ──→ PREPARING ──→ READY ─────────────────────→ READY
vị trí món  : trong bếp    trong bếp     trong bếp → trên quầy → tay thu ngân
cột         :                            (handed_over_at)  (received_at)
```

Chọn timestamp thay vì cờ BIT vì **khoảng giữa hai mốc mới là thứ đáng nhìn**: món nằm chờ trên
quầy bao lâu. Cờ `is_handed_over` trả lời được "đã đưa chưa" nhưng không trả lời được "đưa từ bao
giờ", mà đó mới là câu hỏi khi khách phàn nàn món nguội.

Ràng buộc `CK_OrderItem_handover` chặn thứ tự ngược ngay ở tầng dữ liệu — nhận món chưa được bàn
giao là chuyện vô nghĩa, và chặn ở đây thì kể cả sửa thẳng bằng câu lệnh SQL cũng không lọt.
Chiều còn lại **cố ý không chặn**: bếp bàn giao mà quầy chưa nhận là trạng thái bình thường, chính
là hàng chờ của màn hình `/staff/counter`.

**Bất đối xứng về người thực hiện** — hai vế ràng buộc khác nhau, và đó là chủ ý:

| Vế | Ai làm được | Vì sao |
|---|---|---|
| `handed_over_at` | **Chỉ** người có `assigned_to_user_id` | Người nấu món mới biết món nào là món của đơn nào; ai cũng bàn giao được thì mất luôn dấu vết trách nhiệm |
| `received_at` | **Bất kỳ** Cashier nào | Quầy đổi ca giữa chừng, và người cầm món không nhất thiết là người sẽ giao cho khách |

### B9. `Payment`

| Cột | Kiểu | Rule |
|---|---|---|
| `payment_id` | INT IDENTITY PK | |
| `order_id` | INT NOT NULL FK→Orders | **1 Order – N Payment** (BR-14) |
| `method` | VARCHAR(20) | CHECK ∈ {ONLINE_GATEWAY, CASH} |
| `amount` | DECIMAL(12,2) | |
| `payment_status` | VARCHAR(20) | CHECK ∈ {UNPAID, PENDING, PAID, FAILED} |
| `attempt_no` | INT DEF 1 | `UNIQUE(order_id, attempt_no)` |
| `created_at`, `paid_at` | DATETIME2(0) | `paid_at` là mốc tính doanh thu |

**Rủi ro concurrency của `attempt_no`** (xem C4): tính `MAX(attempt_no)+1` bị race nếu khách bấm thanh toán 2 tab.

**Trigger `TR_Payment_OnlineGatewayOnly`** — chặn `method='CASH'` khi Order là ONLINE_PREORDER (BR-04). Không thể dùng CHECK vì phải đọc bảng `Orders`. Đây là rule tài chính → bắt buộc có lớp bảo vệ ở DB.

### B10. `PaymentTransaction`

| Cột | Kiểu | Rule |
|---|---|---|
| `transaction_id` | INT IDENTITY PK | |
| `payment_id` | INT NOT NULL FK→Payment | |
| `gateway` | VARCHAR(50) | |
| `external_transaction_id` | VARCHAR(100) **NOT NULL UNIQUE** | **Cột quan trọng nhất về mặt tài chính** — NFR-06 |
| `status` | VARCHAR(20) | |
| `raw_reference` | NVARCHAR(MAX) | Lưu nguyên payload callback để đối soát |
| `created_at` | DATETIME2(0) | |

`UNIQUE` trên `external_transaction_id` là cơ chế chặn **duplicate revenue** ở mức thấp nhất: callback lặp → INSERT lỗi 2627 → bỏ qua. Không dựa vào `SELECT` kiểm tra trước rồi mới `INSERT` (vẫn race).

**Bảng này phục vụ hai nguồn tiền, không phải một** (BR-22):

| `gateway` | Nguồn | `external_transaction_id` đến từ đâu |
|---|---|---|
| `MOCK` (hoặc tên cổng thật) | Online Pre-order | Callback của cổng thanh toán |
| `POS_TERMINAL` | POS thu bằng mã QR, thu ngân xác nhận tay | Mã do hệ thống sinh theo khoản thu (`POS-QR-<payment_id>`) |

Không tách thành hai bảng, vì cả hai trả lời đúng một câu hỏi: *khoản tiền này đối chiếu với sao kê bằng mã nào?* Gộp lại thì báo cáo đối soát chỉ đọc một nơi, và `UNIQUE` bảo vệ cả hai đường như nhau — một lần thu ngân xác nhận tay không ghi thành hai dòng, đúng như một callback không ghi nhận được hai lần.

POS thu **tiền mặt** thì không có dòng nào ở đây: không có bên thứ ba nào để đối chiếu.

### B11–B13. `Notification`, `KitchenIssue`, `AuditLog`

- `Notification`: `user_id` **NULL được** (POS guest không có tài khoản), `order_id`, `channel`, `event_type` ∈ {ORDER_CONFIRMED, ORDER_READY}, `content` NVARCHAR(MAX) (chứa pickup_time + code), `status` ∈ {PENDING, SENT, FAILED}, `sent_at`.
- `KitchenIssue`: `order_item_id` FK, `created_by` FK, `issue_type`, `description`, `status` ∈ {OPEN, RESOLVED}, `created_at`, `resolved_at`. Chạy **song song** state machine — không ảnh hưởng `item_status` (BR-19).
- `AuditLog`: `audit_id` BIGINT, `actor_id` **NULL = System/Scheduler**, `entity_type`, `entity_id` VARCHAR(50), `action`, `old_value`/`new_value` NVARCHAR(MAX), `created_at`. Thiết kế **generic** (không FK tới từng bảng) để ghi được mọi loại entity theo NFR-08.

### B14. Bảng KHÔNG tạo — và lý do

| Bảng bị loại | Lý do |
|---|---|
| `DeliveryAddress`, `Shipment`, `Shipper` | Mục 17: Delivery ngoài phạm vi MVP |
| `Branch` / `Store` | Mục 17: MVP một cửa hàng |
| `Inventory`, `Supplier`, `PurchaseOrder` | Mục 17 |
| `Voucher`, `Promotion`, `LoyaltyPoint`, `Review` | Mục 17 |
| `OrderStatusHistory` | Đã có `AuditLog` generic; tạo thêm là trùng lặp |
| `PickupCode` (bảng riêng) | Chỉ 1 code/đơn → là cột của `Orders` |
| `TimeSlot` / `SlotCapacity` | Mục 17: capacity optimization là Future Enhancement |
| `AppSetting` | *Cân nhắc* — mục 4 nhắc Admin có thể chỉnh lead time, nhưng BR-08 đã khóa 20 phút cố định. **Chốt: không tạo**, để trong `app.properties` |

---

## PHẦN C — CHỐNG RACE CONDITION (phần khó nhất, làm kỹ nhất)

Hệ thống có **4 tác nhân ghi đồng thời**: Customer, Cashier, Kitchen, Scheduler + callback từ Gateway. Đây là nơi dự án dễ sai nhất.

### C1. Release KDS đúng 1 lần — BR-09, NFR-05

**Sai (đừng làm):**
```sql
SELECT released_to_kds_at FROM Orders WHERE order_id=@id;  -- app kiểm tra NULL
UPDATE Orders SET released_to_kds_at=@now WHERE order_id=@id;
```
Hai lần scheduler chạy chồng nhau đều đọc thấy NULL → release 2 lần → bếp làm 2 phần.

**Đúng — dồn kiểm tra vào chính câu UPDATE:**
```sql
UPDATE Orders
   SET released_to_kds_at = @now
 WHERE order_id = @id
   AND released_to_kds_at IS NULL      -- điều kiện idempotent
   AND order_status = 'CONFIRMED';
-- @@ROWCOUNT = 1 → mới thật sự release: ghi AuditLog KDS_RELEASE, đẩy KDS
-- @@ROWCOUNT = 0 → đã release rồi: bỏ qua im lặng, KHÔNG lỗi
```

### C2. Callback thanh toán lặp — BR-14, NFR-06

Thứ tự bắt buộc trong **một transaction**:
1. `INSERT PaymentTransaction(external_transaction_id, ...)` — nếu ném lỗi **2627/2601** (unique violation) → callback trùng → `ROLLBACK`, ghi `CALLBACK_IGNORED`, trả HTTP 200 cho gateway.
2. `UPDATE Payment SET payment_status='PAID' WHERE payment_id=@p AND payment_status='PENDING'` → kiểm `@@ROWCOUNT`.
3. `UPDATE Orders SET order_status='CONFIRMED', pickup_code=@code, kitchen_release_at=DATEADD(MINUTE,-20,pickup_time) WHERE order_id=@o AND order_status='PENDING_PAYMENT'` → BR-07 + BR-08.
4. `INSERT AuditLog` (PAYMENT_PAID, AUTO_CONFIRM).
5. `COMMIT`.

Đặt INSERT transaction **trước tiên** để unique index làm "cửa vào" duy nhất.

### C3. Hai đầu bếp claim cùng một món — UC-11

```sql
UPDATE OrderItem
   SET assigned_to_user_id=@uid, item_status='PREPARING', started_at=@now
 WHERE order_item_id=@id
   AND item_status='WAITING'
   AND assigned_to_user_id IS NULL;
-- @@ROWCOUNT = 0 → báo "món đã có người nhận", không ghi đè
```

Câu lệnh này mới chỉ giữ cho **một món** không có hai chủ. Nó không cản được hai đầu bếp
chia nhau hai món khác nhau của cùng một đơn — mỗi người ghi một dòng, cả hai `@@ROWCOUNT`
đều bằng 1. Mà đơn hai chủ thì mỗi người xong một nửa, không ai bàn giao được trọn đơn ra
quầy, và màn hình quầy nhận đơn nham nhở.

**Đúng:** khoá dòng Order rồi đọc lại mọi món của đơn trước khi ghi; đơn đã mang tên người
bếp khác thì từ chối. Phải khoá trước khi đọc, vì hai người bấm cùng lúc thì chỉ khoá mới
xếp được ai đọc trước ai đọc sau.

```sql
BEGIN TRAN;
  SELECT order_id FROM Orders WITH (UPDLOCK, ROWLOCK) WHERE order_id=@o;
  -- có món nào mang assigned_to_user_id khác @uid → ROLLBACK, báo "đơn đã có người nhận"
  UPDATE OrderItem SET ... ;
COMMIT;
```

Nằm ở `KitchenService.requireNobodyElseHoldsOrder`, gọi từ **cả hai** lối vào: nhận trọn đơn
và nhận lẻ từng món.

### C4. Sinh `attempt_no` — BR-14

`SELECT MAX(attempt_no)+1` rồi INSERT sẽ race khi khách bấm thanh toán ở 2 tab → cả hai ra cùng số → vi phạm `UQ_Payment_attempt`.

Ba hướng, chọn **hướng 1**:
1. ✅ **Bắt lỗi unique và retry** vòng lặp tối đa 3 lần. Đơn giản, không cần đổi schema.
2. Lock dòng Order: `SELECT ... FROM Orders WITH (UPDLOCK, HOLDLOCK) WHERE order_id=@o` trước khi tính MAX. Đúng nhưng làm nghẽn.
3. Bỏ `UQ_Payment_attempt`, sắp thứ tự bằng `created_at`. Mất khả năng đánh số attempt rõ ràng.

### C5. Aggregate trạng thái Order từ OrderItem — BR-11

Hai đầu bếp mark READY hai món cuối cùng gần như đồng thời → cả hai đều thấy "còn 1 món chưa xong" → Order không bao giờ chuyển READY.

**Đúng:** trong cùng transaction với `UPDATE OrderItem`, khóa dòng Order rồi mới đếm:
```sql
BEGIN TRAN;
  UPDATE OrderItem SET item_status='READY', ready_at=@now
   WHERE order_item_id=@id AND item_status='PREPARING';
  IF @@ROWCOUNT = 0 BEGIN ROLLBACK; RETURN; END

  SELECT @dummy = order_id FROM Orders WITH (UPDLOCK) WHERE order_id=@o;  -- serialize

  IF NOT EXISTS (SELECT 1 FROM OrderItem WHERE order_id=@o AND item_status<>'READY')
      UPDATE Orders SET order_status='READY', ready_at=@now
       WHERE order_id=@o AND order_status IN ('CONFIRMED','PREPARING');
  ELSE
      UPDATE Orders SET order_status='PREPARING'
       WHERE order_id=@o AND order_status='CONFIRMED';
COMMIT;
```

`ready_at` của Order phải ghi **đúng lúc món cuối xong** — đây là mẫu số của On-time Ready Rate; ghi sai làm hỏng toàn bộ KPI mục 13.

### C6. Bảng tổng hợp cơ chế

| Rule | Cơ chế | Vị trí |
|---|---|---|
| BR-09 / NFR-05 | `UPDATE ... WHERE released_to_kds_at IS NULL` | SQL |
| BR-14 / NFR-06 | UNIQUE `external_transaction_id` + bắt lỗi 2627 | SQL + Service |
| NFR-07 | Filtered unique index `idempotency_key` | SQL |
| BR-11 | `WITH (UPDLOCK)` + đếm trong transaction | Service |
| UC-11 | `UPDATE ... WHERE item_status='WAITING'` | SQL |
| BR-04 | Trigger + validate Service | Cả hai |
| BR-12 | So `now < kitchen_release_at` trong transaction | Service |

---

## PHẦN D — INDEX & VIEW

### D1. View

| View | Phục vụ | Nội dung |
|---|---|---|
| `vw_OrderReleaseState` | Mục 7.2, BR-17 | Suy ra `release_state` (NOT_RELEASED / SCHEDULED / RELEASED_TO_KDS) và cờ `is_overdue` từ timestamps |
| `vw_OnTimeReady` | Mục 13 | `is_on_time` = `ready_at ≤ pickup_time`; `prep_lead_minutes` = `ready_at − released_to_kds_at` |

> Không dùng `INDEXED VIEW` (materialized): các view này chứa `SYSDATETIME()` nên không deterministic.

### D2. Index — mỗi index gắn với một truy vấn có thật

| Index | Truy vấn phục vụ | Vì sao cần |
|---|---|---|
| `IX_Product_category(category_id, status, is_available)` | Menu CUS-01 | Query chạy mỗi lần mở menu |
| `UX_Orders_idempotency` (filtered) | Checkout | NFR-07, filtered để bỏ qua hàng NULL |
| `UX_Orders_pickupCode` (filtered) | Verify pickup STF-03 | Tra code phải nhanh và duy nhất |
| `IX_Orders_release(order_status, kitchen_release_at) WHERE released_to_kds_at IS NULL` | **Scheduler mỗi 30 giây** | Filtered index chỉ chứa đơn *chưa* release → luôn nhỏ, đây là index quan trọng nhất cho NFR-03 |
| `IX_Orders_status_source(order_status, order_source, pickup_time)` | Dashboard STF-02 (4 tab) | |
| `IX_Orders_customer(customer_id, created_at DESC)` | Lịch sử CUS-05 | Kèm BR-21 lọc ownership |
| `IX_OrderItem_order(order_id)` | Chi tiết đơn + aggregate | Chạy mỗi lần đổi item status |
| `IX_OrderItem_kds(item_status, assigned_to_user_id)` | KIT-01 | **Poll mỗi 2 giây** (NFR-04) |
| `IX_OrderItem_counter(handed_over_at, order_item_id) WHERE received_at IS NULL` | STF-04 + huy hiệu đếm trên thanh điều hướng | Cùng nguyên tắc lọc như `IX_Orders_release`: index chỉ chứa món đang thật sự nằm chờ trên quầy nên không lớn lên theo số món đã bán. Đáng có index riêng vì con số này chạy ở **mọi** trang thu ngân, không riêng STF-04 |
| `IX_Payment_order(order_id, payment_status)` | Kiểm tra PAID trước handoff | BR-15 |
| `IX_Payment_paidAt(paid_at)` | Doanh thu | Quét theo khoảng ngày; tiền chỉ đi một chiều nên một mốc là đủ |
| `IX_Audit_entity(entity_type, entity_id, created_at DESC)` | STF-05 / ADM-05 | |

**Nguyên tắc:** không tạo index "cho chắc". Mỗi index làm chậm INSERT/UPDATE — mà `Orders`/`OrderItem` là hai bảng bị ghi nhiều nhất.

### D3. Truy vấn báo cáo — mục 13

| Metric | Nguồn | Lưu ý |
|---|---|---|
| Doanh thu | `SUM(amount WHERE paid_at ∈ kỳ)` | Mốc là `paid_at`, không phải `created_at`. Xem cảnh báo ngay dưới bảng |
| Order Count by Channel | `COUNT` group by `order_source` | Lọc `created_at` |
| Completed Sales | `SUM(total_amount)` where `COMPLETED` | Loại `EXPIRED`; lọc `completed_at` |
| Best-selling Product | `SUM(oi.quantity)` join Order COMPLETED | Group theo `product_id` (không theo snapshot name) |
| **On-time Ready Rate** | `vw_OnTimeReady` | `AVG(is_on_time*1.0)` — KPI đặc trưng của V6 |
| Average Preparation Lead | `AVG(prep_lead_minutes)` | Chỉ đơn scheduled đã completed |
| Overdue Pickup Count | `vw_OrderReleaseState WHERE is_overdue=1` | BR-17 |
| Payment Summary | Group `method` × `payment_status` | |

**Cái bẫy của doanh thu — đọc kỹ trước khi viết câu SQL.**

Mốc để chia kỳ là **`paid_at`**, không phải `Orders.created_at`. Cách viết trông tự nhiên
nhất lại sai:

```sql
-- SAI: đơn lập cuối tháng 3, khách trả tiền đầu tháng 4 vẫn bị tính vào tháng 3
SELECT SUM(p.amount) FROM dbo.Payment p
JOIN dbo.Orders o ON o.order_id = p.order_id
WHERE o.created_at BETWEEN @from AND @to AND p.payment_status = 'PAID'
```

```sql
-- ĐÚNG: kỳ nào nhận tiền thì kỳ đó ghi nhận
SELECT SUM(p.amount) FROM dbo.Payment p WHERE p.paid_at BETWEEN @from AND @to
```

Cách đúng giữ được tính chất quan trọng nhất: **tổng các kỳ luôn khớp tổng toàn thời gian**.
Lọc theo `payment_status = 'PAID'` là thừa — chỉ khoản đã thu mới có `paid_at`.

---

## PHẦN E — LỘ TRÌNH THI CÔNG

### E1. Cấu trúc file script (tách từ `01_schema.sql` hiện tại)

| Thứ tự | File | Nội dung |
|---|---|---|
| 1 | `00_create_database.sql` | CREATE DATABASE, collation, RCSI |
| 2 | `01_tables.sql` | 13 CREATE TABLE + PK/FK/CHECK |
| 3 | `02_indexes.sql` | Toàn bộ index & filtered unique index |
| 4 | `03_views.sql` | 2 view |
| 5 | `04_triggers.sql` | `TR_Payment_OnlineGatewayOnly` + trigger chặn hard-delete |
| 6 | `05_seed_master.sql` | Role, Users, Category, Product |
| 7 | `06_seed_demo.sql` | Đơn demo đủ 7 trạng thái (để test UI ngay) |
| 8 | `99_drop_all.sql` | Reset môi trường dev |
| 9 | `verify/*.sql` | Script kiểm chứng BR/NFR (F1) |

Mỗi file **idempotent**: bọc `IF OBJECT_ID(...) IS NULL` / `DROP ... IF EXISTS` để chạy lại nhiều lần không lỗi.

### E2. Các giai đoạn

| GĐ | Việc | Đầu ra | Tiêu chí hoàn thành |
|---|---|---|---|
| **0** | Chốt Phần A | Quy ước đặt tên, kiểu dữ liệu, timezone | Cả nhóm thống nhất, ghi vào tài liệu |
| **1** | Nhóm 1+2 (Role→Product, Cart) | `01_tables.sql` phần đầu | Seed được menu, query BR-01 chạy đúng |
| **2** | `Orders` + `OrderItem` + 4 CHECK | Phần lõi | Insert thử: POS có `pickup_time` phải **bị chặn** |
| **3** | `Payment` + `PaymentTransaction` + trigger BR-04 | Nhóm tài chính | Insert CASH cho Online phải **bị chặn** |
| **4** | `Notification`, `KitchenIssue`, `AuditLog` | Nhóm vận hành | |
| **5** | `02_indexes.sql` + `03_views.sql` | Index & view | `SET STATISTICS IO ON` xác nhận scheduler dùng `IX_Orders_release` |
| **6** | Seed master + demo | Dữ liệu chạy được | Login 4 role, menu hiện 10 món |
| **7** | Script verify | `verify/*.sql` | Toàn bộ mục F1 pass |
| **8** | Rà đối chiếu | Checklist F2 | 25 BR + 10 NFR đều có nơi enforce |

Đường găng: **GĐ 2 → 3 → 5**. Ba giai đoạn này quyết định tính đúng đắn; các giai đoạn khác có thể làm song song.

---

## PHẦN F — KIỂM CHỨNG

### F1. Test case DB (viết thành `verify/*.sql`, chạy sau mỗi lần đổi schema)

| # | Rule | Thao tác test | Kết quả mong đợi |
|---|---|---|---|
| T-01 | BR-03 | INSERT `order_source='DELIVERY'` | Lỗi CHECK |
| T-02 | Mục 7.1 | INSERT POS + `PENDING_PAYMENT` | Lỗi `CK_Orders_pendingOnlineOnly` |
| T-03 | BR-05 | INSERT Online không `pickup_time` | Lỗi `CK_Orders_pickupTime` |
| T-04 | BR-03 | INSERT POS có `pickup_time` | Lỗi `CK_Orders_pickupTime` |
| T-05 | BR-04 | INSERT Payment CASH cho Order Online | Lỗi từ trigger |
| T-06 | BR-14 | INSERT 2 transaction cùng `external_transaction_id` | Lần 2 lỗi 2627 |
| T-07 | NFR-07 | INSERT 2 Order cùng `idempotency_key` | Lần 2 lỗi unique |
| T-08 | BR-09 | Chạy câu UPDATE release 2 lần | Lần 1 `@@ROWCOUNT=1`, lần 2 `=0` |
| T-09 | UC-11 | Chạy câu claim 2 lần | Lần 2 `@@ROWCOUNT=0` |
| T-10 | BR-08 | Order Online PAID | `kitchen_release_at = pickup_time − 20'` |
| T-11 | BR-17 | Order READY, `pickup_time` lùi 40' | `vw_OrderReleaseState.is_overdue = 1` |
| T-12 | Mục 13 | Order `ready_at ≤ pickup_time` | `vw_OnTimeReady.is_on_time = 1` |
| T-13 | BR-20 | DELETE một dòng `Orders` | Bị chặn (trigger) |
| T-14 | BR-01 | Đặt Category về INACTIVE | Product của nó biến mất khỏi query menu |
| T-15 | BR-02 | Admin đổi giá Product | `OrderItem.unit_price` đơn cũ **không đổi** |
| T-16 | NFR-03 | 500 đơn chờ release | Query scheduler dùng `IX_Orders_release`, < 50ms |
| T-19 | BR-22 | INSERT 2 PaymentTransaction cùng mã xác nhận POS | Lần 2 lỗi 2627 → lần ghi nhận thứ hai bị từ chối |
| T-20 | BR-21 | INSERT Order Online với `customer_id = NULL` | Lỗi `CK_Orders_onlineCustomer` |
| T-21 | Mục 13 | Đơn lập tháng 1, `paid_at` tháng 2 | Tháng 2 ghi doanh thu; tháng 1 không tính |
| T-22 | Mục 13 | Cộng doanh thu tháng 1 + tháng 2 | Bằng đúng doanh thu tính gộp cả hai tháng |
| T-23 | BR-25 | UPDATE `received_at` khi `handed_over_at` còn NULL | Lỗi `CK_OrderItem_handover` |
| T-24 | BR-25 | Handoff cho khách khi đơn còn món chưa `received_at` | Bị từ chối; đơn giữ nguyên READY, không ghi `picked_up_at` |

### F2. Checklist đối chiếu 22 Business Rules

| BR | Nơi enforce | Có ràng buộc DB? |
|---|---|---|
| BR-01 Product/Category active | Query menu (join 3 điều kiện) | Không (liên bảng) |
| BR-02 Snapshot name/price | Cột `product_name_snapshot`, `unit_price` | ✅ cột NOT NULL |
| BR-03 Chỉ 2 order_source | `CK_Orders_source` | ✅ |
| BR-04 Online phải ONLINE_GATEWAY | Trigger + Service | ✅ trigger |
| BR-05 pickup ≥ +30' | Service (cần "now" của app) | ❌ có chủ đích |
| BR-06 Revalidate trước payment | Service | ❌ |
| BR-07 Auto confirm sau PAID | Service, trong transaction callback | ❌ |
| BR-08 release = pickup − 20' | Service khi auto-confirm | ❌ |
| BR-09 Release 1 lần | `UPDATE ... WHERE released_to_kds_at IS NULL` | ✅ |
| BR-10 POS release ngay | Service | ❌ |
| BR-11 Aggregate status | Service + `UPDLOCK` | ❌ |
| BR-13 Expire 15' | `PaymentExpiryScheduler` | ❌ |
| BR-14 N attempt, callback idempotent | UNIQUE `external_transaction_id`, `UQ_Payment_attempt` | ✅ |
| BR-15 Handoff cần READY+PAID+code | Service + `UX_Orders_pickupCode` | Một phần |
| BR-16 COMPLETED sau handoff | Cột `picked_up_at`, `handoff_by_user_id` | Một phần |
| BR-17 OVERDUE là UI flag | `vw_OrderReleaseState` | ✅ view |
| BR-18 READY khi đủ quantity | Thiết kế OrderItem nguyên khối | ✅ theo thiết kế |
| BR-19 Issue song song | Bảng `KitchenIssue` tách riêng | ✅ |
| BR-20 Không hard-delete | Trigger chặn DELETE + không CASCADE | ✅ |
| BR-21 Ownership | Điều kiện `customer_id` ngay trong câu truy vấn | ❌ |
| BR-22 Tiền POS đi qua bên thứ ba cần mã đối soát | `PaymentTransaction` + UNIQUE `external_transaction_id` | ✅ — nay chỉ còn đường mã QR, máy quẹt thẻ rời đã bỏ |
| BR-23 Reset password → buộc đổi | Cột `must_change_password` + `AuthenticationFilter` | ✅ cột |
| BR-25 Bàn giao bếp → quầy | 4 cột timestamp + `CK_OrderItem_handover` + `IX_OrderItem_counter` | ✅ thứ tự; ❌ điều kiện handoff |

> Các ô ❌ là **có chủ đích**: rule cần "thời điểm hiện tại", quyền người dùng, hoặc logic nhiều bước — thuộc Service layer. Điều quan trọng là **không có rule nào bị bỏ sót cả hai nơi**.

---

## PHẦN G — TRẠNG THÁI TRIỂN KHAI

Plan đã được triển khai thành **một file duy nhất**:
[`database/FastFoodPreorder.sql`](../database/FastFoodPreorder.sql) — 13 bảng · 17 index ·
2 view · 6 trigger · dữ liệu mẫu · 10 truy vấn tự kiểm tra. Không có file migration đi kèm:
mỗi lần chạy đều dựng lại từ đầu nên không có bước nâng cấp nào để phải viết riêng.

File **xoá và tạo lại toàn bộ bảng mỗi lần chạy**, nên luôn cho ra database sạch.
`DROP TABLE` không bị các trigger chặn hard-delete cản, vì vậy vẫn giữ được cả hai:
ràng buộc BR-20 lúc vận hành, và khả năng dựng lại DB bất cứ lúc nào lúc phát triển.

| # | Việc | Trạng thái |
|---|---|---|
| 1 | Gộp về một file SQL duy nhất, chạy lại được | ✅ Xong |
| 2 | `COLLATE Vietnamese_CI_AS` lúc CREATE DATABASE | ✅ Xong |
| 3 | Bật `READ_COMMITTED_SNAPSHOT ON` | ✅ Xong |
| 4 | `DATETIME2(0)` cho mọi cột thời gian | ✅ Xong |
| 5 | Trigger chặn hard-delete (5 bảng, có `OrderItem`) | ✅ Xong |
| 6 | Dữ liệu mẫu phủ đủ 7 trạng thái | ✅ Xong — 11 đơn D1–D11, cả 13 bảng đều có dữ liệu |
| 7 | Truy vấn tự kiểm tra sau khi chạy | ✅ Xong — mục 8 trong file, 10 bảng đối chiếu |
| 8 | `pickup_code` định dạng `yyMMdd` + 4 ký tự, `VARCHAR(10)` | ✅ Xong |
| 9 | Cột `row_version ROWVERSION` cho `Orders` | ⬜ Không làm — UPDATE có điều kiện (Phần C) đã đủ cho MVP |

### Khác biệt so với plan ban đầu

| Hạng mục | Plan | Thực tế | Lý do |
|---|---|---|---|
| Số file | 8 file + thư mục `verify/` | **1 file** | Yêu cầu của dự án; một file tự chứa dễ nộp và dễ dựng lại |
| Cách chạy lại | `IF NOT EXISTS` từng đối tượng | `DROP` rồi tạo mới | Bỏ được hàng chục lớp guard, file gọn và kết quả tất định |
| View | 5 view | **2 view** | Chỉ giữ view mã hoá logic suy ra không hiển nhiên (release state, đúng hẹn). Doanh thu và món bán chạy là `GROUP BY` thuần → viết trong `ReportDAO`, không giấu logic báo cáo trong DB |
| Bộ test | 4 file `verify/*.sql`, 18 test | 6 truy vấn kiểm tra ở cuối file | Đánh đổi để giữ đúng một file. Nếu cần bộ test đầy đủ (đặc biệt 4 test chống race condition ở Phần C) thì tách lại thành file riêng |

### Bổ sung ngoài plan ban đầu

| Hạng mục | Lý do thêm |
|---|---|
| `CK_Orders_releaseBeforePickup` | Bắt lỗi tính sai lead time ngay tại DB |
| `CK_Orders_pickupCodeOnline` | Mục 10 nói pickup_code chỉ dùng cho Online |
| `CK_Notification_channel`, `CK_Issue_type` | Khoá tập giá trị, nhất quán với cách làm enum ở A3 |
| Trigger chặn DELETE trên `OrderItem` | FK chỉ chặn xoá `Orders` khi còn item, **không** chặn xoá chính item → xoá item làm `total_amount` sai âm thầm |
| Đơn D11 — món ra trễ hẹn | Không có đơn trễ thì tỷ lệ đúng hẹn luôn ra 100%, không kiểm chứng được công thức KPI |
| 3 món "bẫy" trong dữ liệu mẫu | Cố ý không đủ điều kiện lên menu, để kiểm chứng quy tắc lọc ba tầng của BR-01 |

### Bổ sung ngoài plan ban đầu

| Hạng mục | Lý do thêm |
|---|---|
| `CK_Orders_releaseBeforePickup` | Bắt lỗi tính sai lead time ngay tại DB (test T-17) |
| `CK_Orders_pickupCodeOnline` | Mục 10 nói pickup_code chỉ dùng cho Online (test T-18) |
| `CK_Notification_channel`, `CK_Issue_type` | Khóa tập giá trị, nhất quán với cách làm enum ở A3 |
| Trigger chặn DELETE trên `OrderItem` | FK chỉ chặn xóa `Orders` khi còn item, **không** chặn xóa chính item → xóa item làm `total_amount` sai âm thầm |
| `vw_DailyRevenue`, `vw_BestSellingProduct` | Phục vụ trực tiếp ADM-01 (mục 13) |
| `vw_OrderTotalCheck` | Công cụ đối soát: `total_amount` phải luôn khớp tổng OrderItem; kỳ vọng 0 dòng |
| Test T-10b, T-11b, T-11c, T-13b, T-15b, T-16b | Phủ thêm BR-05, BR-12, BR-20 và tính nhất quán tổng tiền |
| Product/Category "bẫy" trong seed master | 3 món cố ý không đủ điều kiện BR-01 để test T-14 có ý nghĩa |

---

## PHẦN H — RỦI RO

| Rủi ro | Ảnh hưởng | Cách phòng |
|---|---|---|
| Tomcat và SQL Server lệch timezone | Scheduler release sai giờ, KPI on-time sai toàn bộ | A2: app sinh mọi timestamp; kiểm tra lúc cài đặt |
| Quên `@@ROWCOUNT` sau UPDATE có điều kiện | Mất tác dụng chống trùng, bếp làm 2 phần | Code review bắt buộc cho mọi UPDATE ở C1–C5 |
| Test toàn bằng thao tác tay tuần tự | Race condition không bao giờ lộ ra lúc demo | Test T-06 → T-09 bằng 2 kết nối song song |
| Aggregate status ngoài transaction | Order kẹt ở PREPARING dù mọi món đã READY | C5 |
| Seed thiếu đơn ở trạng thái hiếm (EXPIRED, OVERDUE) | Không phát hiện lỗi hiển thị đến khi bảo vệ | `06_seed_demo.sql` phủ đủ 6 trạng thái |
| Sửa CHECK constraint sau khi đã có dữ liệu | `ALTER` fail vì dữ liệu cũ vi phạm | Chốt Phần A trước GĐ 1 |

---

## Thứ tự đọc khi bắt tay làm

1. Phần A — chốt chuẩn (làm 1 lần, cả nhóm cùng thống nhất)
2. Phần B — viết `01_tables.sql`
3. **Phần C — đọc kỹ nhất**, vì đây là nơi quyết định đúng/sai và cũng là phần dễ mất điểm nhất khi phản biện
4. Phần D — index & view
5. Phần F — viết script verify, dùng làm bằng chứng nghiệm thu
