# Cấu trúc dự án — Fast Food Pre-order Pickup & POS

Java Servlet + JSTL + SQL Server, kiến trúc MVC ba tầng.
Nguồn nghiệp vụ: [preview-2 (1).html](preview-2%20(1).html) · Thiết kế dữ liệu: [DATABASE-PLAN.md](DATABASE-PLAN.md)

---

## 1. Kiến trúc

```
Trình duyệt · Máy bán hàng tại quầy · Màn hình bếp
        │  HTTP
        ▼
┌──────────────────────────────────────────────────┐
│ BỘ LỌC  Bảng mã → Đăng nhập → CSRF → Phân quyền  │  com.fastfood.filter
├──────────────────────────────────────────────────┤
│ CONTROLLER  Servlet: đọc tham số, gọi Service,   │  com.fastfood.controller
│             chọn trang hiển thị                  │
├──────────────────────────────────────────────────┤
│ SERVICE     Toàn bộ quy tắc nghiệp vụ và         │  com.fastfood.service
│             ranh giới giao dịch                  │
├──────────────────────────────────────────────────┤
│ DAO         Chỉ câu lệnh SQL, nhận sẵn Connection│  com.fastfood.dao
├──────────────────────────────────────────────────┤
│ SQL Server — FastFoodPreorder                    │
└──────────────────────────────────────────────────┘
        ▲                          ▲
   BỘ HẸN GIỜ                 TÍCH HỢP NGOÀI
   com.fastfood.scheduler     com.fastfood.integration

HIỂN THỊ: JSP + JSTL trong /WEB-INF/views — không mở trực tiếp từ trình duyệt được
```

**Ba nguyên tắc xuyên suốt**

1. **Controller không viết SQL, DAO không chứa quy tắc nghiệp vụ.** Service là nơi duy nhất
   biết cả hai, và cũng là nơi mở/đóng giao dịch.
2. **Mọi trang JSP nằm trong `WEB-INF`.** Không có đường vào nào bỏ qua được chuỗi bộ lọc.
3. **Quy tắc chống trùng lặp nằm trong chính câu lệnh SQL**, không phải ở kiểm tra trước khi ghi.

### Vì sao dùng lớp cụ thể thay vì giao diện + lớp cài đặt

DAO và Service viết thẳng thành lớp cụ thể. Mỗi lớp chỉ có một cách cài đặt, nên thêm một
giao diện chỉ làm tăng số tệp phải mở khi lần theo một luồng nghiệp vụ.

Chỗ **có** dùng giao diện là nơi thật sự cần thay thế được: `PaymentGateway` và
`NotificationSender`. Mỗi cái có hai bản cài đặt thật sự khác nhau về cách hoạt động, chọn
bằng đúng một dòng trong `app.properties`:

- `payment.gateway.provider` — `PayOsGateway` (mặc định, gọi API payOS xin liên kết trả tiền
  — xem §3.6) hoặc `SePayGateway` (chuyển khoản thật qua mã VietQR — xem §3.7),
  chọn ở `PaymentGateways`.
- `notification.channel` — `MockNotificationSender` ghi ra log hoặc `SmtpNotificationSender`
  gửi thư thật, chọn ở `NotificationSenders` — xem §3.9.

---

## 2. Cây thư mục

```
swp301-block/
├── pom.xml                          Maven, đóng gói WAR
├── database/FastFoodPreorder.sql    Tệp DB duy nhất: bảng, chỉ mục, view, dữ liệu mẫu, tự kiểm tra
├── docs/                            Tài liệu phân tích và thiết kế
└── src/main/
    ├── java/com/fastfood/
    │   ├── common/constant/    1  Constants — 8 kiểu liệt kê + AuditAction + BusinessRule
    │   ├── common/exception/   1  AppException — cả họ ngoại lệ, xem §2.4
    │   ├── common/util/       11  Tiện ích, đáng chú ý là DateTimeUtil, ViewFunctions, CsrfUtil
    │   ├── config/             2  Kết nối cơ sở dữ liệu và tham số vận hành
    │   ├── model/entity/       4  27 lớp ánh xạ đúng 27 bảng, gom theo nhóm — xem §2.4
    │   ├── model/dto/          1  Dtos — 9 lớp dữ liệu đã gộp sẵn cho tầng hiển thị
    │   ├── dao/               25  24 lớp truy vấn chia theo vai trò + JdbcSupport, xem §2.2
    │   ├── service/           28  27 lớp nghiệp vụ chia theo vai trò + Tx, xem §2.1
    │   ├── integration/       11  Cổng thanh toán (payOS, SePay), kênh gửi tin (log và SMTP)
    │   ├── filter/             5  Bốn bộ lọc chạy theo thứ tự + RequestPath dùng chung
    │   ├── listener/           2  Vòng đời ứng dụng
    │   ├── scheduler/          2  Hai công việc chạy nền
    │   └── controller/        36  34 servlet chia theo vai trò + BaseServlet + PayOsCallbacks
    ├── resources/                  db.properties · app.properties
    └── webapp/
        ├── assets/css/main.css
        ├── META-INF/context.xml    SameSite cho cookie phiên — xem §5
        └── WEB-INF/
            ├── web.xml · fastfood.tld
            ├── tags/pager.tag       Thanh chuyển trang dùng chung cho mọi màn hình danh sách
            └── views/               29 trang + 7 tệp bố cục dùng chung + 1 tệp khối chỉ tiêu
```

### 2.1 Tầng Service chia theo vai trò

Cùng trục với `controller/` và với `WEB-INF/views/`: mở một thư mục là thấy trọn phần
nghiệp vụ của một vai trò, không phải lần qua một lớp chung khổng lồ.

```
service/
├── Tx.java                 Mở/đóng giao dịch — ở gốc gói vì không thuộc vai trò nào,
│                           giống controller/BaseServlet
├── customer/   CustomerOrderService   Đặt trước, xem lại lịch sử đơn
│            CartService            Giỏ hàng
│            FavouriteService       Món quen kèm ghi chú riêng
│            OrderTemplateService   Mẫu đặt nhanh, nạp lại vào giỏ
│            ReviewService          Đánh giá món — chỉ khách đã nhận mới viết được
├── staff/      StaffOrderService      Bán tại quầy, điều phối, nhận món, giao món
│            OrderNoteService       Ghi chú điều phối trên đơn
│            CounterRejectService   Từ chối nhận món bếp đưa ra quầy
├── kitchen/    KitchenService         Hàng chờ, nhận việc, báo xong, bàn giao, sự cố
│            PrepService            Kế hoạch chuẩn bị sẵn trong ca
│            KitchenNoteService     Ghi chú chế biến và sổ bàn giao ca
├── admin/      AdminService           Món, danh mục, tài khoản
│            ReportService          Báo cáo doanh thu
│            RevenueTargetService   Chỉ tiêu doanh thu theo kỳ
├── auth/       AuthService            Đăng nhập, đăng ký, đổi mật khẩu
│            PasswordResetService   Quên mật khẩu: cấp và tiêu mã đặt lại
│            LoginThrottle          Đếm số lần sai, tạm khoá cửa
│            SessionGuard           Đồng bộ phiên với trạng thái tài khoản thật
└── shared/     OrderCoreService       Nạp đơn, xác nhận sau thanh toán,
    │                                  suy ra trạng thái đơn từ trạng thái các món
    │           PaymentService         Thu tiền và nhận kết quả cổng gọi về
    │           MenuService            Thực đơn — khách xem, quầy cũng dùng
    │           ScheduleService        Hai công việc chạy nền
    │           NotificationService    Gửi tin cho khách, và hộp thông báo họ đọc lại
    └           AuditService           Nhật ký thao tác
```

**Vì sao `OrderService` cũ bị chẻ làm ba.** Nó dài 803 dòng với 21 phương thức công khai, và
phục vụ cả bốn vai trò cùng lúc — muốn đọc phần thu ngân thì phải lướt qua cả code của khách.
Ba mảnh mới bám đúng ranh giới người dùng:

| Lớp mới | Việc | Dòng |
|---|---|---|
| `customer/CustomerOrderService` | Đặt trước, kiểm giờ hẹn, lịch sử đơn | ~260 |
| `staff/StaffOrderService` | Bán tại quầy, 4 tab điều phối, tra mã, nhận món, giao món | ~310 |
| `shared/OrderCoreService` | Phần cả hai đường cùng đi qua: nạp đơn, xác nhận sau thanh toán, suy ra trạng thái đơn | ~175 |

**Vì sao có `shared/` chứ không nhân bản ra từng vai trò.** Sáu lớp trong đó phục vụ nhiều vai
trò cùng lúc: khách trả tiền và thu ngân xem lại đều đi qua `PaymentService`; trạng thái đơn do
bếp làm đổi nhưng khách và thu ngân cùng đọc. Chép mỗi lớp thành một bản cho mỗi vai trò nghĩa
là sửa một quy tắc phải sửa nhiều chỗ — quên một chỗ là hai vai trò cư xử khác nhau trên cùng
một đơn hàng.

Hai chỗ cố tình để nguyên chứ không chẻ thêm:

- **`KitchenService` nằm ở `kitchen/` dù màn hình Quầy giao nhận của thu ngân cũng đọc.** Nó mô
  tả trạng thái của bếp; ai đọc thì cũng là trạng thái ấy. Chẻ đôi chỉ tạo ra hai lớp cùng nói
  về một thứ.
- **`AuthService` nằm ở `auth/` chứ không thuộc vai trò nào.** Đăng nhập là việc chung của cả
  bốn vai trò, đúng như `controller/auth`.

### 2.2 Tầng DAO chia theo vai trò sở hữu bảng

Cùng trục với `controller/` và `service/`. `JdbcSupport` ở gốc gói vì nó không truy vấn bảng nào
— giống `Tx` và `BaseServlet`.

```
dao/
├── JdbcSupport.java     Nhận diện lỗi trùng khoá, đọc khoá vừa sinh
├── staff/     OrderNoteDAO         → OrderNote
├── customer/  CartDAO              → Cart · CartItem
│            FavouriteDAO         → Favourite
│            OrderTemplateDAO     → OrderTemplate · OrderTemplateItem
│            ReviewDAO            → Review
├── kitchen/   KitchenIssueDAO      → KitchenIssue
│            PrepTaskDAO          → PrepTask
│            KitchenNoteDAO       → OrderItemNote · KitchenNote
├── admin/     ReportDAO            → truy vấn tổng hợp doanh thu
│            RevenueTargetDAO     → RevenueTarget
└── shared/    OrderDAO             → Orders            PaymentDAO      → Payment
               OrderItemDAO         → OrderItem         TransactionDAO  → PaymentTransaction
               ProductDAO           → Product           UserDAO         → Users
               CategoryDAO          → Category          RoleDAO         → Role
               NotificationDAO      → Notification      AuditLogDAO     → AuditLog
```

**Vì sao 12 trong 22 lớp nằm ở `shared/`.** Mười lớp chia được
vì bảng của chúng chỉ một vai trò đụng tới: giỏ hàng, món quen, mẫu đặt nhanh và đánh giá là của
khách; sự cố bếp và kế hoạch chuẩn bị là của bếp; ghi chú điều phối là của thu ngân; báo
cáo doanh thu và chỉ tiêu là của quản trị. Số còn lại gắn với bảng mà nhiều vai trò cùng dùng:

| Lớp | Vai trò đi qua nó |
|---|---|
| `OrderDAO` · `OrderItemDAO` | khách đặt · bếp nấu · thu ngân giao · quản trị xem báo cáo |
| `ProductDAO` · `CategoryDAO` | khách xem thực đơn · quầy bán · bếp tra món · quản trị sửa món |
| `PaymentDAO` · `TransactionDAO` | khách · thu ngân |
| `UserDAO` · `RoleDAO` · `PasswordResetTokenDAO` | đăng nhập · quản trị |
| `NotificationDAO` · `AuditLogDAO` | mọi luồng đều ghi vào |

Thu ngân trước đây không có bảng riêng nên `dao/staff/` không tồn tại. Ghi chú điều phối trên
đơn là thứ thuộc về riêng họ, và thư mục đó ra đời cùng với nó.
Cùng lý do, `dao/customer/` từ một lớp nay lên bốn: món quen, mẫu đặt nhanh và đánh giá đều là
dữ liệu chỉ khách sở hữu.

Số lớp dùng chung **không** bị nhân bản vào từng vai trò, vì hai lối kia đều tệ hơn: chép
`OrderDAO` thành bốn bản SQL trên cùng một bảng thì sửa lược đồ phải sửa bốn chỗ, còn nhét vào
một thư mục vai trò rồi ba thư mục kia import chéo sang thì tên thư mục nói dối về việc ai đang
dùng nó.

Ranh giới vai trò thật sự vẫn nằm ở tầng Service: **không controller nào import DAO.** Muốn đọc
trọn một vai trò từ địa chỉ URL xuống tới tên bảng thì dùng bản đồ ở §4.1.

### 2.3 Ghi chú thiết kế theo gói

Những quyết định chỉ đọc ra được khi mở đúng gói đó, gom lại một chỗ.

**`controller/customer` — vì sao dùng `userOrLogin` chứ không `requireUser`.** Hai trong số các
servlet ở đây (`MenuServlet`, `ProductDetailServlet`) phục vụ **trang công khai**, nằm ngoài
`AuthenticationFilter`. Mọi yêu cầu gửi tới đó đều lọt qua, kể cả yêu cầu ghi dữ liệu của người
chưa đăng nhập. `userOrLogin` đưa khách sang trang đăng nhập rồi trả về đúng chỗ đang xem, thay
vì ném lỗi 401 cho một việc hoàn toàn hợp lệ mà chỉ làm sớm một bước.

**`service/customer` — vì sao ba lớp cuối nhận `Integer` chứ không `int`.** `FavouriteService`,
`ReviewService` và `OrderTemplateService` phục vụ hai trang công khai nói trên, nơi
`WebUtil.currentUser` có thể trả về null. Các phương thức chỉ đọc vì vậy nhận `Integer` và trả về
rỗng khi chưa đăng nhập: người xem không đăng nhập là chuyện bình thường trên trang công khai,
không phải một lỗi.

**`service/kitchen` — vì sao ghi chú không dùng lại sự cố bếp.** Số sự cố đang mở điều khiển bốn
chỗ cảnh báo đỏ trên màn hình thu ngân. Một dòng "khách dặn ít cay" đi vào bảng `KitchenIssue` sẽ
hiện thành sự cố chưa xử lý và làm cảnh báo mất ý nghĩa — nên `KitchenNoteService` đứng riêng.
`PrepService` cũng đứng riêng vì đó là phần việc duy nhất của bếp **không bắt nguồn từ đơn nào**
và cũng không làm đổi trạng thái đơn nào.

**`dao/customer` — vì sao `hasCompletedPurchase` nằm ở `ReviewDAO`.** Truy vấn này ghép qua
`Orders` và `OrderItem` chứ không thuộc bảng `Review`. Nó ở đây vì chỉ đánh giá cần tới nó, và vì
ràng buộc "đã mua và đã nhận" không đặt được ở tầng dữ liệu: `CHECK` trong SQL Server không nhìn
sang bảng khác.

**`dao/kitchen` — ghi chú là dữ liệu duy nhất ngoài giỏ hàng được xoá hẳn.** Nó không dính tiền,
không đổi trạng thái đơn, và không có dòng nhật ký nào trỏ về nó. `PrepTaskDAO` cũng đáng chú ý:
bảng duy nhất của bếp **không tham chiếu `OrderItem`** — bếp làm sẵn theo dự đoán chứ không đợi
đơn, nên nó trỏ thẳng tới `Product`.

**`dao/admin` — hai lớp cố ý không gọi lẫn nhau.** Mức đã đạt của một chỉ tiêu do
`RevenueTargetService` lấy từ `ReportService`, để cả hệ thống chỉ có **một** công thức tính doanh
thu thuần. Viết thêm một câu tính doanh thu trong `RevenueTargetDAO` sẽ đặt hai con số cạnh nhau
trên cùng màn hình và sớm muộn chúng lệch nhau.

**`controller/admin` — `AuditServlet` chỉ có `doGet`, và sẽ mãi như vậy.** Một nhật ký kiểm toán
mà quản trị viên sửa được thì không còn giá trị làm bằng chứng. Đó cũng là lý do chỉ tiêu doanh
thu được gắn vào bảng điều khiển: đó là màn hình duy nhất của quản trị viên còn chỗ cho một thực
thể mới.

**`filter` — chuỗi bộ lọc chạy theo thứ tự tên:** bảng mã → đăng nhập → CSRF → phân quyền. Toàn
bộ trang JSP nằm trong `WEB-INF` nên không mở trực tiếp được; mọi đường vào đều phải qua servlet
và do đó qua chuỗi này.

**`common/util` — `DateTimeUtil` là nguồn thời gian duy nhất** của hệ thống, dùng thay cho đồng
hồ của SQL Server để lịch vào bếp không sai khi hai máy chủ lệch giờ.

### 2.4 Lớp lồng: bốn nhóm gom vào ít tệp

Bốn nhóm dưới đây gồm toàn lớp nhỏ, mỗi lớp vài chục dòng, và luôn được đọc theo cụm chứ không
đọc lẻ. Để mỗi lớp một tệp thì riêng chúng đã chiếm gần một nửa số tệp Java của dự án.

| Tệp | Chứa | Trước |
|---|---|---|
| `common/exception/AppException.java` | `ValidationException` · `AccessDeniedException` · `NotFoundException` · `BusinessException` · `DataAccessException` | 6 tệp |
| `common/constant/Constants.java` | 8 kiểu liệt kê + `AuditAction` + `BusinessRule` | 11 tệp |
| `model/dto/Dtos.java` | 9 lớp DTO | 9 tệp |
| `model/entity/*Entities.java` | 27 entity chia 4 nhóm: `UserEntities` (5) · `MenuEntities` (4) · `OrderEntities` (10) · `OperationEntities` (8) | 27 tệp |

Chúng là **lớp lồng tĩnh**, nên nơi dùng vẫn viết tên đơn giản y như cũ — chỉ dòng `import` đổi:

```java
import com.fastfood.common.constant.Constants.OrderStatus;   // trước: ...constant.OrderStatus
import com.fastfood.model.entity.OrderEntities.Order;        // trước: ...entity.Order
import com.fastfood.common.exception.AppException.NotFoundException;

if (order.getStatus() == OrderStatus.CONFIRMED) { ... }      // thân hàm không đổi một chữ
```

**Phân tầng MVC không đổi.** Gộp chỉ xảy ra trong cùng một tầng và giữa các lớp cùng loại —
không có servlet nào bị gộp với service, không có service nào bị gộp với DAO. `controller/`,
`service/` và `dao/` vẫn tách theo vai trò đúng như §2.1 và §2.2.

**Vì sao không gộp tiếp thành "một tệp cho một màn hình".** Tầng service và DAO dùng chung quá
nhiều để chia được theo màn: `ProductDAO` bị 14 lớp gọi, `KitchenService` 15, `AuditService` 20,
`Tx` 27. Ép mỗi màn một tệp thì hoặc phải nhân bản chúng ra nhiều bản — sửa một quy tắc phải sửa
nhiều chỗ, đúng thứ §2.1 đã bác — hoặc phải để servlet gọi thẳng xuống DAO, phá nguyên tắc số 1
ở §1.

**`BeanNamingTest` đi xuống lớp lồng** khi quét quy ước đặt tên, nên `getX`/`isX` của entity và
DTO vẫn bị canh y như trước.

---

## 3. Ba luồng nghiệp vụ đáng đọc trước

### 3.1 Đặt trước — giữ đơn tới sát giờ mới đưa xuống bếp

```
Khách chọn giờ → CartServlet (action=placeOrder)                      [customer]
     → CustomerOrderService.createOnlineOrder   đọc lại giá và tình trạng từng món
                                                tạo đơn ở trạng thái chờ thanh toán
     → PaymentService.startOnlinePayment        tạo bản ghi thanh toán, chuyển sang cổng
     ← PayOsWebhookServlet / PayOsReturnServlet payOS gửi kết quả về (§3.6)
     → PaymentService.handleCallback            kiểm chữ ký → ghi mã giao dịch (chống trùng)
                                                → đối chiếu số tiền (§3.5)
                                                → ghi nhận tiền → xác nhận đơn
     → OrderCoreService.confirmOnlineAfterPaid  sinh mã nhận hàng, chốt giờ vào bếp
                                                = giờ hẹn trừ 20 phút

     ... đơn NẰM CHỜ, bếp chưa nhìn thấy ...

     → KitchenReleaseScheduler (mỗi 30 giây)
     → ScheduleService.releaseDueOrders         đưa xuống bếp đúng một lần
     → KitchenService.claim / markReady         bếp làm món                    [kitchen]
     → OrderCoreService.recalculateStatus       món cuối xong → đơn sẵn sàng → báo khách
     → KitchenService.handOverToCounter         bếp đưa món ra quầy
     → StaffOrderService.receiveAtCounter       thu ngân xác nhận đã cầm món      [staff]
     → StaffOrderService.handoff                thu ngân đối chiếu mã rồi giao món
```

Ba nhãn bên phải là **thư mục service** chứa lớp đó — một đơn hàng đi qua ba vai trò, và giờ
mỗi chặng nằm đúng thư mục của người thực hiện chặng ấy.

**Vì sao có hai bước bàn giao ở cuối.** "Đơn sẵn sàng" chỉ nghĩa là bếp đã nấu xong — món vẫn
có thể còn nằm trong bếp. Trước đây khoảng đó không ai nhìn thấy: bếp nấu xong là món biến mất
khỏi mọi danh sách, và nó chỉ lộ ra khi khách hỏi. Nay bếp phải bấm bàn giao, quầy phải bấm
nhận, và `handoff` từ chối giao cho khách chừng nào còn món chưa được quầy nhận.

Hai mốc chứ không phải một, vì đó là hành động của hai người khác nhau; khoảng giữa chúng
chính là lúc món nằm chờ trên quầy — và đó mới là thứ màn hình quầy cần hiện ra. Cả hai mốc
nằm ở cột riêng của `OrderItem`, không thêm bậc vào `item_status`: món vẫn ở trạng thái `READY`
suốt cả hai bước, nên mọi chỗ đang đếm "món chưa xong" giữ nguyên ý nghĩa.

**Quầy từ chối thì món đi ngược lại đúng một bậc.** `CounterRejectService.reject` vừa lập phiếu
vừa **xoá hai mốc bàn giao**, nên món quay lại danh sách "chờ bàn giao ra quầy" của chính người
đã nấu nó. Chỉ lập phiếu mà để nguyên mốc thì món kẹt giữa hai màn hình: bếp không còn thấy nó
ở danh sách việc nào (`handOverToCounter` đòi `handed_over_at IS NULL`), quầy vẫn thấy nó nằm
chờ, và cả đơn không giao được cho khách vì `countNotReceived` còn đếm nó — lối thoát duy nhất
khi ấy là bấm nhận đúng món vừa bị chê. Thu hồi phiếu thì **không** kéo món ngược về quầy: món
đã ở trong bếp thật, đường ra vẫn là bếp bàn giao lần nữa.

### 3.2 Bán tại quầy — gọn trong một giao dịch

`PosServlet` giữ phiếu tạm trong phiên của thu ngân, không ghi xuống cơ sở dữ liệu.
Bấm thu tiền thì `StaffOrderService.createPosOrder` làm liền một mạch: lập đơn → ghi nhận
tiền → xác nhận → đưa xuống bếp. Không có giờ hẹn, không có mã nhận hàng.

Quầy có đúng hai đường thu tiền, và chúng khác nhau ở chỗ **lúc bấm nút thì tiền đã về chưa**:

| | Cách xác nhận | Đơn xuống bếp lúc nào |
|---|---|---|
| Tiền mặt | Thu ngân đếm tiền | Ngay lúc bấm — tiền đã nằm trong két |
| Mã QR | Cổng thanh toán báo về, hoặc thu ngân bấm **Xong** khi nhìn thấy khách trả xong | Chỉ khi tiền được xác nhận, ở `confirmQrPayment` |

Tiền mặt là đường duy nhất mà người bấm nút cũng chính là người cầm tiền, nên `createPosOrder`
ghi khoản thu PAID và mở đường xuống bếp trong cùng một giao dịch. Mã QR thì tiền đi qua bên
thứ ba: đơn lập ra ở trạng thái chờ, bếp chưa thấy gì, và khách bỏ đi giữa chừng thì tác vụ nền
dọn đơn đi sau 15 phút — không ai làm món cho một đơn chưa trả tiền.

**Phiếu tính tiền nói đúng thứ sắp xảy ra.** `StaffOrderService.describeCart` dựng phiếu bằng
đúng câu truy vấn mà `createPosOrder` dùng lúc thu tiền (`findForCheckout`, tính cả nhóm món
đã tắt). Món vừa ngừng bán vì vậy **ở lại phiếu và được đánh dấu** thay vì lặng lẽ rơi ra:
màn hình giấu nút thu tiền, nói rõ vì sao, và thu ngân bỏ món đó ra bằng chính ô số lượng.
Bản trước ghép giỏ với danh sách thực đơn nên dòng đó biến mất khỏi phiếu mà vẫn nằm trong
giỏ — tổng tiền trên màn hình thiếu một món, và lỗi chỉ nổ ra lúc khách đã đưa tiền.

### 3.3 Ba chỗ chống trùng lặp

| Tình huống | Cách xử lý | Nằm ở |
|---|---|---|
| Bộ hẹn giờ chạy lại, đưa đơn xuống bếp hai lần | `UPDATE ... WHERE released_to_kds_at IS NULL` rồi kiểm số dòng | `OrderDAO.markReleasedToKds` |
| Cổng thanh toán gửi kết quả về hai lần | Ghi mã giao dịch có ràng buộc duy nhất; lần hai bị từ chối | `TransactionDAO.insertIfNew` |
| Khách bấm đặt hàng hai lần | Khoá chống trùng; hai lần bấm sát nhau thì lần sau bắt lỗi trùng khoá rồi trả về đơn đã tạo | `CustomerOrderService.createOnlineOrder` |
| Hai đầu bếp nhận cùng một món | `UPDATE ... WHERE item_status = 'WAITING'` rồi kiểm số dòng | `OrderItemDAO.claim` |
| Hai đầu bếp chia nhau hai món của cùng một đơn | Khoá dòng đơn, đọc lại mọi món rồi từ chối nếu đơn đã có người bếp khác — mỗi đơn chỉ một người, kể cả khi nhận lẻ từng món | `KitchenService.requireNobodyElseHoldsOrder` |
| Bếp nhận món của đơn chưa tới lượt vào bếp | Cùng câu lệnh còn kiểm `released_to_kds_at IS NOT NULL` — trùng khít điều kiện của truy vấn hàng chờ | `OrderItemDAO.claim` |
| Hai món cuối xong cùng lúc, đơn kẹt mãi | Khoá dòng đơn trước khi đếm món chưa xong | `OrderCoreService.recalculateStatus` |
| Khách bấm thanh toán ở hai tab | `(order_id, attempt_no)` duy nhất; đọc lại số thứ tự rồi thử tiếp | `PaymentService.startOnlinePayment` |
| Hai người cùng lúc làm phát sinh giỏ hàng | `user_id` duy nhất; trùng thì đọc lại giỏ vừa tạo | `CartDAO.getOrCreateCartId` |
| Mã nhận hàng sinh trùng mã đã có | Sinh lại mã khác, tối đa 5 lần, ngay trong giao dịch thanh toán | `OrderCoreService.confirmOnlineAfterPaid` |

### 3.4 Tiền về sau khi đơn đã hết hiệu lực

Khách để trang thanh toán mở quá 15 phút, bộ hẹn giờ cho đơn hết hiệu lực, rồi khách mới bấm
trả tiền. Tiền lúc đó đã thật sự vào, nhưng đơn không còn để xác nhận.

`PaymentService.handleCallback` nhận ra `confirmOnlineAfterPaid` trả về false và đi vào nhánh
`orphan`: khoản thu **vẫn được ghi `PAID`**, kèm một dòng `PAYMENT_ORPHANED` trong nhật ký, một
tin cho khách, một cảnh báo mức `SEVERE` trong log máy chủ, và mã trả về `ORDER_GONE`.

Ghi `PAID` chứ không phải `FAILED` là có chủ đích: ngân hàng đã trừ tiền thật, nên ghi ngược lại
sẽ làm sổ sách lệch với sao kê — đúng thứ mà đối soát cần tìm ra thì lại bị giấu đi. Hệ thống
không có đường hoàn tiền tự động (§8), nên việc duy nhất làm được là để dấu vết thật rõ cho
người trực xử lý tay qua cổng thanh toán.

Nhánh này còn bắt một trường hợp thứ hai: đơn tại quầy bị bộ hẹn giờ đóng vì quá 15 phút không
ai trả tiền, khoản thu bị đánh dấu `FAILED` theo, rồi tiền mới về. Lúc đó `markPaid` không ghi
được gì (nó chỉ nhận `PENDING`/`UNPAID`), và nếu chỉ nhìn số dòng bị ảnh hưởng thì chuyện này
trông y hệt một callback trùng lặp. `markPaidLate` tách hai chuyện đó ra.

### 3.5 Tiền về không đúng số tiền của đơn

Với cổng thu bằng chuyển khoản, **số tiền do chính khách gõ vào ứng dụng ngân hàng**. Mã QR
điền sẵn 200.000đ không ngăn được ai đó sửa thành 10.000đ rồi chuyển với đúng nội dung ấy. Nếu
"đã có tiền về" là đủ để xác nhận đơn thì đó là cách mua hàng với giá bao nhiêu cũng được.

Vì vậy `PaymentService.handleCallback` đối chiếu số tiền trước khi ghi nhận, và trả về
`AMOUNT_MISMATCH`. Đơn **không** được xác nhận và cũng **không** tự hoàn: số tiền lệch thì máy
không đoán được ý khách — chuyển thiếu chờ bù nốt, chuyển thừa xin lại phần dư, hay gõ nhầm
sang đơn khác — cả ba đều cần người xử lý. Khoản tiền vẫn nằm lại trong `PaymentTransaction`
dưới trạng thái `MISMATCH` để đối soát, và đơn hết hiệu lực theo bộ hẹn giờ như mọi đơn không
ai trả tiền.

### 3.6 payOS — cổng gọi API, và hai đường báo kết quả về

Cổng mặc định. Khác SePay ở chỗ khách **rời ứng dụng**; khác một cổng chuyển hướng thuần như
VNPAY ở chỗ mình không tự dựng được địa chỉ trả tiền mà phải **gọi API xin** một liên kết.

```
Khách bấm thanh toán → PaymentStartServlet
     → PaymentService.startOnlinePayment        tạo bản ghi thanh toán như thường
     → PayOsGateway.initiate                    POST /v2/payment-requests, kèm chữ ký
                                                ← checkoutUrl + qrCode (VietQR) + paymentLinkId
                                                → chuyển hướng khách ra ngoài

     ... khách quét VietQR bằng ứng dụng ngân hàng, hoặc trả bằng thẻ, trên trang payOS ...

     ← PayOsWebhookServlet  /payment/payos/webhook   payOS gọi thẳng vào máy chủ (CÓ chữ ký)
         → PayOsCallbacks.fromWebhook           trải khối data ra để còn kiểm chữ ký
         → PayOsGateway.verifySignature         ký lại khối data, so với signature

     ← PayOsReturnServlet   /payment/payos/return    khách quay lại (KHÔNG có chữ ký)
         → PayOsGateway.lookup                  GET /v2/payment-requests/{orderCode}
         → PayOsCallbacks.fromLookup            dựng callback, đánh dấu trusted

     → PaymentService.handleCallback            từ đây trở đi giống hệt mọi cổng khác
```

**Đường khách quay lại không có chữ ký.** Đây là khác biệt lớn nhất so với bản trước, hồi còn
chạy VNPAY — ở đó cả gói tham số quay về đều nằm dưới một chữ ký HMAC. payOS gắn
`code`, `id`, `cancel`, `status`, `orderCode` vào địa chỉ quay về rồi thôi — không ký gì cả.
Tin thẳng `status=PAID` đọc từ thanh địa chỉ nghĩa là bất kỳ ai gõ tay được địa chỉ ấy cũng tự
cho đơn của mình là đã trả tiền, không mất đồng nào.

Vì vậy `PayOsReturnServlet` chỉ lấy đúng **một** thứ từ địa chỉ — `orderCode`, để biết phải hỏi
về khoản nào — rồi tự gọi ngược sang payOS bằng khoá API của cửa hàng. Trạng thái ghi vào cơ sở
dữ liệu là trạng thái payOS trả lời trong lời gọi đó. Một `orderCode` bịa ra thì không tra được
gì; một `orderCode` thật của người khác thì chỉ đọc ra đúng trạng thái thật của nó.

Kết quả lấy về kiểu ấy mang cờ `GatewayCallback.trusted`, và `handleCallback` bỏ qua bước kiểm
chữ ký cho nó. Đây **không** phải lối tắt cho tiện: chữ ký sinh ra để biết dữ liệu đi qua tay
khách có bị sửa không, còn ở đây dữ liệu không đi qua tay khách — nó là câu trả lời của một lời
gọi HTTPS do chính máy chủ phát ra tới `api-merchant.payos.vn`, kèm khoá API. Đúng một chỗ
trong toàn hệ thống bật cờ này, và nó nằm ngay cạnh lời gọi ấy.

**Hai đường báo về, cùng một chỗ xử lý.** Webhook chắc chắn tới kể cả khi khách đóng tab, nhưng
đòi máy chủ có địa chỉ **https** công khai và phải khai báo trong my.payos.vn. Đường khách quay
lại thì ngược lại: chạy được ở mọi nơi kể cả `localhost`, nhưng khách đóng tab giữa chừng là
mất. Bật cả hai không thu tiền hai lần — xem ngay dưới đây.

**Nối lệnh gọi về với đơn nào**: qua `orderCode`, một con số. payOS đòi nó **duy nhất trên toàn
tài khoản merchant và không bao giờ dùng lại**, kể cả sau khi liên kết đã huỷ. Ở đây lấy thẳng
`payment_id` cộng `payment.payos.orderCodeOffset`, nên đọc ngược ra được mà không phải lưu thêm
bảng tra nào. Cái giá: nạp lại cơ sở dữ liệu thì `payment_id` quay về đếm từ 1 và đụng nguyên
vào những mã đã tiêu ở lần cài trước — lúc ấy tăng `orderCodeOffset` lên là xong (README §*Nối
payOS vào*).

**Chống thu tiền hai lần nằm ở mã giao dịch.** Cả hai đường đều dựng
`external_transaction_id = "PAYOS-" + reference`, với `reference` là mã tham chiếu ngân hàng —
cùng một lần trả tiền thì webhook và lượt tra cứu cho ra **cùng một chuỗi**, nên lần thứ hai
đụng ràng buộc duy nhất và trả về `DUPLICATE` (§3.3). Dựng lệch nhau ở hai chỗ là cùng một
khoản tiền được ghi nhận hai lần mà không có gì báo động, nên `PayOsCallbacksTest` canh riêng
đúng bất biến đó.

**Thành công phải đúng cả hai mức**: `success` ở ngoài nói lời gọi webhook có hợp lệ không, còn
`data.code` mới nói giao dịch có thành công không. Tin mỗi mức ngoài là ghi nhận một lần trả
tiền hỏng.

**Hai kiểu chữ ký, đừng lẫn** — cả hai đều HMAC-SHA256 với `checksumKey`:

| | Chuỗi đem ký | Dùng ở đâu |
|---|---|---|
| Lời xin liên kết | Đúng năm trường, thứ tự **cố định**: `amount`, `cancelUrl`, `description`, `orderCode`, `returnUrl` | `signPaymentRequest` |
| Kết quả gửi về | **Toàn bộ** khối `data`, khoá xếp theo bảng chữ cái, null → chuỗi rỗng | `signData` |

Hai chỗ dễ sai khi ký khối `data`, cả hai đều làm mọi webhook thật bị từ chối:

| | Phải làm | Sai thì |
|---|---|---|
| Trường null | Thành chuỗi rỗng, **vẫn nằm** trong chuỗi ký | Lệch ở đúng những giao dịch thiếu thông tin người chuyển |
| Số | Giữ nguyên chữ số payOS gửi | Đọc thành số rồi in lại ra `3000.0` là lệch trên mọi giao dịch |

`PayOsGatewayTest` neo hai giá trị chữ ký **tính tay ngoài Java** — đó là chỗ duy nhất trong bộ
test bắt được lỗi sai khuôn chuỗi ký, vì mọi chỗ khác đều so chữ ký do cùng một hàm sinh ra.

**Mở lại trang không đẻ thêm liên kết.** Màn hình quầy (§3.8) dựng lại mã QR mỗi lần mở. payOS
từ chối tạo hai liên kết cùng một `orderCode`, và `PayOsGateway.initiate` bắt lấy lời từ chối
đó rồi tra cứu trả về đúng liên kết cũ. Nếu mỗi lần mở lại sinh ra một liên kết nữa thì cùng
một đơn có mấy chỗ trả tiền còn sống cùng lúc, mà hệ thống không có đường hoàn tiền tự động.
Nhánh này **không bám vào một mã lỗi cụ thể** của payOS — danh sách mã của họ có thể đổi, còn
câu hỏi "liên kết cho `orderCode` này có sẵn chưa" thì đúng bất kể họ đánh số thế nào. Liên kết
cũ đã huỷ, hoặc mang số tiền khác, thì **không** dùng lại: số tiền lệch là dấu hiệu mã đơn đang
đụng dữ liệu của lần cài trước.

**Mở cổng là một lời gọi mạng**, khác hẳn VNPAY nơi mọi thứ là phép tính cục bộ. Mất mạng ra
Internet thì không có liên kết nào để đưa khách đi, và `initiate` hỏng ngay tại chỗ kèm lý do
đọc được. `HttpPayOsApi` đặt hạn 5 giây để nối và 15 giây cho cả lời gọi: nó nằm trên đường
khách bấm nút, nên payOS chậm là khách ngồi nhìn trang trắng.

Đường truyền tách sau `PayOsApi` để bài kiểm tra thay được bằng bản giả (`FakePayOs`). Không có
chỗ nối ấy thì mọi bài kiểm tra chạm tới thanh toán đều đòi mạng và đòi khoá thật — tức là trên
thực tế không ai chạy chúng. `PaymentService` có thêm một hàm dựng nhận cổng từ ngoài vào chính
vì việc này.

**Không đòi đăng nhập ở đường quay lại.** Khách vừa đi vòng qua trang của payOS, và ở vài trình
duyệt hoặc ứng dụng ngân hàng thì lần quay lại này không mang theo cookie phiên. Mở công khai
không mở thêm đường nào cho ai tự khai là đã trả tiền, vì thứ quyết định là câu trả lời của
payOS chứ không phải tham số trên địa chỉ.

**Webhook luôn trả 2xx sau khi chữ ký đã qua.** payOS coi mã ngoài 2xx là "chưa nhận được" và
gọi lại nhiều lần; trả lỗi vì một chuyện bên mình (đơn không tìm thấy, cơ sở dữ liệu trục trặc)
chỉ đổi một sự cố im lặng thành một trận dội webhook. Chuyện hỏng đi vào log mức `SEVERE`.

**Không còn cổng giả lập.** Bản trước có một trang trong ứng dụng với nút "Thanh toán thành
công" bấm là đơn thành đã trả. Nó tiện lúc trình bày nhưng đổi lại hai thứ: bất kỳ ai đăng nhập
được cũng tự cho đơn của mình là đã trả tiền, và luồng thật chưa từng được chạy lần nào nên
không có gì bảo đảm nó đúng. Lưu ý payOS **không có sandbox**: khoá lấy về là khoá thật và tiền
chuyển là tiền thật, nên chạy thử thì đặt món rẻ nhất rồi tự chuyển khoản cho chính mình.

### 3.7 SePay — thu tiền bằng chuyển khoản có mã VietQR

Bật bằng `payment.gateway.provider=SEPAY` trong `app.properties`. Mặc định là `PAYOS` (§3.6).

**SePay không phải cổng thanh toán kiểu chuyển hướng**, và điều đó đổi hình dạng cả luồng.
Nó không giữ tiền và không có trang thanh toán riêng: tiền đi thẳng từ tài khoản khách vào tài
khoản ngân hàng của cửa hàng, việc duy nhất SePay làm là đọc biến động số dư rồi gọi về báo.

```
Khách bấm thanh toán → PaymentStartServlet
     → PaymentService.startOnlinePayment        tạo bản ghi thanh toán như thường
     → SePayGateway.initiate                    KHÔNG chuyển ra ngoài; trả về địa chỉ
                                                /payment/sepay ngay trong ứng dụng
     → SePayCheckoutServlet                     hiện mã VietQR đã điền sẵn số tiền và
                                                nội dung "FF<payment_id>"

     ... khách quét mã, chuyển khoản từ ứng dụng ngân hàng ...

     ← SePayWebhookServlet                      SePay gọi vào /payment/sepay/webhook
     → SePayGateway.verifySignature             khoá API ở header Authorization
     → SePayGateway.paymentIdFrom               đọc mã thanh toán từ nội dung chuyển khoản
     → PaymentService.handleCallback            từ đây trở đi giống hệt cổng chuyển hướng
```

Ba điểm khác biệt đáng nhớ:

| | Cổng chuyển hướng | SePay |
|---|---|---|
| Khách đi đâu | Sang trang của bên thứ ba | Ở lại ứng dụng, quét mã QR |
| Nối lệnh gọi về với đơn nào | Cổng gửi kèm `paymentId` | **Nội dung chuyển khoản** `FF<payment_id>` |
| Bằng chứng dữ liệu là thật | Chữ ký từ khoá dùng chung | Khoá API ở header `Authorization: Apikey <khoá>` |

**Không có "thanh toán thất bại".** Khách hoặc chuyển tiền, hoặc không — và không chuyển thì
không có lệnh gọi về nào cả, đơn tự hết hiệu lực theo bộ hẹn giờ.

**Lệnh gọi về không đi qua trình duyệt của khách**, nên màn hình của khách không tự biết tiền
đã tới. Trang `/payment/sepay` hỏi lại `/api/order/status` mỗi 5 giây và rời trang ngay khi đơn
thoát khỏi trạng thái chờ thanh toán.

Vì sao tiền tố `FF` phải chốt hai đầu khi đọc ngược: SePay tự chèn mã của họ vào nội dung
chuyển khoản (kiểu `SEVN63DC8E5C`), mà mã đó là chữ số mười sáu nên hoàn toàn có thể chứa đúng
hai chữ `FF` ở giữa. Khớp lỏng thì `SEVNFF12ABCD` bị đọc thành mã thanh toán 12 và tiền được
ghi cho đơn của người khác — `SePayGatewayTest` canh đúng chuyện này.

### 3.8 Hộp thông báo — nơi khách đọc lại những gì đã xảy ra với đơn

`NotificationService` sinh tin cho ba sự kiện: đơn được xác nhận, món sẵn sàng, đơn hết hiệu
lực. Tin đi qua `NotificationSender`, mà kênh mặc định vẫn là lớp giả lập —
**không có bức thư nào thật sự tới hộp thư của khách cho tới khi bật SMTP** (§3.9). Vì vậy
`/notifications` là nơi duy nhất họ đọc được những tin đó, và nó không phải một tiện ích thêm
vào cho đẹp: tình huống ở §3.4 kết thúc bằng một khoản tiền cần đối chiếu, mà nếu khách đã đóng
trang thanh toán thì cả câu chuyện chỉ còn nằm ở đây. Kể cả khi đã bật thư thật, hộp trong ứng
dụng vẫn cần: thư rơi vào mục rác hoặc gửi hỏng, và `status = 'FAILED'` chỉ có ý nghĩa nếu có
chỗ để khách đọc bù.

Ba mảnh ghép lại thành một mạch:

| Chỗ | Việc |
|---|---|
| Cột `Notification.read_at` | NULL nghĩa là chưa đọc — toàn bộ cơ sở của huy hiệu |
| `BaseServlet.forward` | Đếm tin chưa đọc cho **mọi** trang, vì thanh điều hướng có ở mọi trang |
| `/order/track` | Mở đơn ra là đọc tin của riêng đơn đó, nên huy hiệu không nói dối |

Đánh dấu đã đọc **không** chạy khi khách chỉ mở `/notifications`: danh sách dài hơn một màn
hình thì mở trang không có nghĩa là đã đọc hết, và tin quan trọng nhất — báo có khoản tiền cần
đối chiếu — lại hay nằm dưới cùng. Ở đó có nút bấm tay, còn việc tự đánh dấu chỉ xảy ra tại trang theo dõi đơn,
nơi tin thật sự hiện ra trước mắt.

### 3.9 Xác thực email — chứng minh địa chỉ có thật trước khi nhận đơn

Trước đây ô email lúc đăng ký chỉ được kiểm đúng một điều: chuỗi gõ vào **có hình dáng** của
một địa chỉ. Không có gì buộc địa chỉ ấy tồn tại, và cũng không có gì buộc nó thuộc về người
đang gõ. Ba hậu quả, xếp theo mức độ:

| Chuyện xảy ra | Hậu quả |
|---|---|
| Gõ nhầm một chữ | Ngày quên mật khẩu, liên kết lấy lại tài khoản gửi tới hộp thư không ai mở. Email là danh tính đăng nhập và **không sửa được**, nên không còn đường tự cứu |
| Địa chỉ không tồn tại | Tin "đơn đã xác nhận" và "món đã sẵn sàng, mã nhận hàng là…" rơi vào hư không. Khách tới quầy tay không có mã |
| Gõ email của người khác | `Users.email` là UNIQUE, nên chủ thật sau này gõ đúng địa chỉ của họ sẽ nhận được "Email này đã được đăng ký". Một lần chiếm là chiếm vĩnh viễn |

Luồng đi qua bốn chỗ:

| Chỗ | Việc |
|---|---|
| `AuthService.register` | Tạo tài khoản với `email_verified = 0`, cấp mã **trong cùng giao dịch**, gửi thư **sau khi** giao dịch chốt |
| `EmailVerificationService` | Cấp mã, gửi lại, xác nhận. Băm · dùng một lần · hạn 24 giờ · có trần số lần xin |
| `/verify-email` | GET là bấm liên kết trong thư, POST là xin gửi lại. Nằm trong danh sách trang công khai |
| `CustomerOrderService.createOnlineOrder` | Chốt chặn thật: chưa xác thực thì không tạo được đơn online |

**Chặn tới đâu, và vì sao không chặn hơn.** Tài khoản chưa xác thực vẫn đăng nhập, vẫn xem thực
đơn, vẫn thêm món vào giỏ — chỉ không đặt được đơn online. Chặn ngay ở cửa đăng nhập thì thư
không tới (rơi vào mục rác, SMTP chưa cấu hình, đang chạy kênh giả lập) là người dùng kẹt hoàn
toàn, mà lỗi không phải của họ. Chặn ở đúng chỗ email thật sự cần thiết thì lý do hiện ra đúng
lúc nó có nghĩa, và phần còn lại của ứng dụng vẫn dùng được.

Chốt chặn nằm ở tầng Service chứ không chỉ ở chỗ ẩn nút: nút bị ẩn vẫn gửi được yêu cầu bằng
tay. Giao diện có hai nơi nói trước lý do — dải nhắc kèm nút "Gửi lại thư xác thực" trong
`layout/page-start.jspf` (có mặt trên mọi trang) và phần chọn giờ trong `customer/cart.jsp`.

**Ba kết quả chứ không phải hai.** `confirm` trả về `VERIFIED`, `ALREADY_VERIFIED` hoặc
`INVALID`. Trình duyệt và bộ quét liên kết của các dịch vụ thư đều hay tải trước đường dẫn nằm
trong thư, nên lượt bấm thật của người dùng rất thường là lượt **thứ hai** — báo "liên kết
không hợp lệ" ở đúng lượt đó là dựng ra một lỗi không có thật, và người dùng sẽ đi xin thư mới
cho một việc đã xong.

Tài khoản nhân viên do quản trị viên tạo có `email_verified = 1` ngay từ đầu: địa chỉ đã được
xác nhận bằng đường khác, và cả bốn màn hình nội bộ đều không đi qua chốt chặn nói trên.

**Kênh gửi thư.** `NotificationSenders.fromConfig()` chọn kênh theo `notification.channel`:

| Giá trị | Hành vi |
|---|---|
| `MOCK` (mặc định) | Thư ghi ra log máy chủ. Máy demo không có SMTP, hoặc mất mạng giữa buổi bảo vệ, thì mọi luồng vẫn đi hết được: mở `catalina.out` lấy liên kết, bấm vào chạy tiếp bình thường |
| `SMTP` | Gửi thư thật qua `SmtpNotificationSender`. Gmail cần **App Password**, không dùng được mật khẩu đăng nhập thường |

Đặt `SMTP` mà thiếu `notification.mail.username/password` thì hệ thống tự quay về `MOCK` và ghi
một dòng `SEVERE` — đổi một chức năng chạy được thành một chức năng báo lỗi thì tệ hơn.

---

## 4. Bản đồ theo vai trò

### 4.1 Đọc một vai trò từ địa chỉ xuống tới bảng dữ liệu

Bảng này dẫn trọn một mạch từ đường dẫn URL xuống tới tên bảng. Cả ba tầng đều đã chia theo vai
trò, nhưng một vai trò vẫn đi qua vài gói `shared/` — bảng này cho biết chính xác là những gói
nào, để không phải mở từng file ra dò.

| Vai trò | Thư mục controller | Thư mục service | DAO đi qua | Bảng |
|---|---|---|---|---|
| **Khách hàng** | `controller/customer` | `service/customer`<br>+ `shared` (thanh toán, thực đơn) | `dao/customer` CartDAO · FavouriteDAO · OrderTemplateDAO · ReviewDAO<br>`dao/shared` OrderDAO · OrderItemDAO · ProductDAO · CategoryDAO · PaymentDAO · TransactionDAO · NotificationDAO | Cart · CartItem · Favourite · OrderTemplate · OrderTemplateItem · Review · Orders · OrderItem · Product · Category · Payment · PaymentTransaction · Notification |
| **Thu ngân** | `controller/staff` | `service/staff`<br>+ `kitchen` (quầy giao nhận)<br>+ `shared` | `dao/staff` OrderNoteDAO<br>`dao/kitchen` KitchenIssueDAO<br>`dao/shared` OrderDAO · OrderItemDAO · PaymentDAO · TransactionDAO · ProductDAO · AuditLogDAO | OrderNote · KitchenIssue · Orders · OrderItem · Payment · PaymentTransaction · Product · AuditLog |
| **Bếp** | `controller/kitchen`<br>`controller/api` (KDS) | `service/kitchen` | `dao/kitchen` KitchenIssueDAO · PrepTaskDAO · KitchenNoteDAO<br>`dao/shared` OrderItemDAO · OrderDAO · ProductDAO | KitchenIssue · PrepTask · OrderItemNote · KitchenNote · OrderItem · Orders · Product |
| **Quản trị** | `controller/admin` | `service/admin`<br>+ `shared` (nhật ký) | `dao/admin` ReportDAO · RevenueTargetDAO<br>`dao/shared` ProductDAO · CategoryDAO · UserDAO · RoleDAO · AuditLogDAO | RevenueTarget · Product · Category · Users · Role · AuditLog |
| **Đăng nhập** | `controller/auth` | `service/auth` | `dao/shared` UserDAO · RoleDAO · PasswordResetTokenDAO | Users · Role · PasswordResetToken |
| **Chạy nền** | `scheduler/` | `service/shared` | `dao/shared` OrderDAO · NotificationDAO · AuditLogDAO | Orders · Notification · AuditLog |

Cột "DAO đi qua" chỉ liệt kê chỗ vai trò đó **đọc** dữ liệu. `AuditLogDAO` và `NotificationDAO`
thì mọi luồng đều **ghi** vào — liệt kê lặp ở cả sáu dòng chỉ làm bảng nhiễu. Bảng `Notification`
vừa là dấu vết của việc gửi tin, vừa là **hộp thông báo** khách đọc ở `/notifications` — xem §3.8.

Đọc theo chiều dọc một dòng là thấy trọn phần của một vai trò. Đọc theo chiều ngang cột "DAO đi
qua" là thấy vì sao `dao/shared` lại đông đến vậy: `OrderDAO` xuất hiện ở bốn dòng, `ProductDAO`
ở bốn dòng — đó là những bảng mà nhiều vai trò cùng đi qua, không tách rời được.

### 4.2 Bảng địa chỉ

Servlet khai báo bằng `@WebServlet`. Trang JSP nằm trong `/WEB-INF/views/`.

Danh sách chia theo **loại**, không gộp tất cả vào "màn hình": trang có giao diện, endpoint
chỉ để hành động rồi chuyển hướng, và endpoint cho máy gọi vào là ba thứ khác nhau — khác cả
về cách kiểm tra quyền lẫn cách viết ca kiểm thử.

### Trang công khai
| Địa chỉ | Servlet | Trang |
|---|---|---|
| `/menu` | MenuServlet | customer/menu.jsp — lọc theo nhóm/từ khoá, sắp xếp theo giá hoặc đánh giá, **khối giới thiệu ba bước** cho khách chưa đăng nhập và **món quen** cho khách đã đăng nhập |
| `/product/detail` | ProductDetailServlet | customer/product-detail.jsp — kèm **đánh giá món** và nút **món quen** |
| `/login` `/register` | LoginServlet · RegisterServlet | auth/ |
| `/forgot-password` `/reset-password` | ForgotPasswordServlet · ResetPasswordServlet | auth/ — luồng quên mật khẩu, xem §5 |
| `/verify-email` | VerifyEmailServlet | Không có trang. GET là bấm liên kết trong thư, POST là xin gửi lại. Công khai vì người ta hay bấm từ điện thoại, nơi không có phiên đăng nhập — xem §3.9 |
| `/logout` | LogoutServlet | Không có trang. **Chỉ nhận POST**; gõ thẳng vào trình duyệt thì chỉ đưa về trang chủ |

### Khách hàng — 7 trang
| Địa chỉ | Servlet | Trang |
|---|---|---|
| `/cart` | CartServlet | customer/cart.jsp — giỏ hàng **và** chọn giờ đến lấy |
| `/payment/sepay` | SePayCheckoutServlet | customer/payment-sepay.jsp — **mã VietQR chuyển khoản**, chỉ sống khi `payment.gateway.provider=SEPAY`; cổng mặc định payOS đưa khách ra ngoài nên không có trang nào trong ứng dụng. Xem §3.7 |
| `/order/track` | OrderTrackingServlet | customer/order-tracking.jsp |
| `/order/history` | OrderHistoryServlet | customer/order-history.jsp — kèm **mẫu đặt nhanh**, lọc theo trạng thái và khoảng ngày |
| `/notifications` | NotificationServlet | customer/notifications.jsp — **hộp thông báo**, xem §3.8 |
| `/profile` | ProfileServlet | customer/profile.jsp |

Cộng hai trang công khai `/menu` và `/product/detail` là **8 màn hình** khách hàng đi qua.
Trang thanh toán chỉ có mặt khi chạy SePay: cổng mặc định payOS đẩy khách sang trang của bên
thứ ba, còn `/payment/sepay` trả 404.

### Thu ngân — 5 trang, `/staff/*`
| Địa chỉ | Servlet | Trang |
|---|---|---|
| `/staff/pos` | PosServlet | staff/pos.jsp — thu **tiền mặt** hoặc sinh **mã QR** cho khách quét |
| `/staff/orders` | OrderDashboardServlet | staff/order-dashboard.jsp — kèm ô **tra mã nhận hàng** và **ghi chú điều phối** |
| `/staff/order/detail` | OrderDetailServlet | staff/order-detail.jsp — kèm **hoá đơn in** |
| `/staff/counter` | CounterServlet | staff/counter.jsp — nhận **hoặc từ chối** món bếp đưa ra, và xem sự cố bếp |
| `/staff/history` | StaffHistoryServlet | staff/history.jsp — tra lại đơn đã bán, kèm **nhật ký thao tác** |

### Bếp — 4 trang, `/kitchen/*`
| Địa chỉ | Servlet | Trang |
|---|---|---|
| `/kitchen/queue` | KdsQueueServlet | kitchen/kds-queue.jsp — bốn ô chỉ báo tự cập nhật, hàng chờ, việc đang làm dở, và **kế hoạch chuẩn bị sẵn** nằm chung |
| `/kitchen/item` | KitchenItemServlet | kitchen/item-detail.jsp — kèm **ghi chú chế biến** |
| `/kitchen/issue` | KitchenIssueServlet | kitchen/issue.jsp — chọn món trong danh sách món còn trong bếp, không gõ mã |
| `/kitchen/history` | KitchenHistoryServlet | kitchen/history.jsp — lọc **cả bếp / món tôi làm**, kèm **sổ bàn giao ca** |

### Quản trị — 5 trang, `/admin/*`
| Địa chỉ | Servlet | Trang |
|---|---|---|
| `/admin/dashboard` | AdminDashboardServlet | admin/dashboard.jsp — kèm **chỉ tiêu doanh thu** và ba khoảng xem nhanh |
| `/admin/products` | ProductManageServlet | admin/product.jsp — lọc theo nhóm, từ khoá, **trạng thái bán** và **tình trạng hàng**, có phân trang |
| `/admin/categories` | CategoryManageServlet | admin/category.jsp |
| `/admin/users` | UserManageServlet | admin/user.jsp — lọc theo vai trò, từ khoá và **trạng thái tài khoản**, có phân trang |
| `/admin/audit` | AuditServlet | admin/audit.jsp |

Ba màn hình có danh sách dài đều **giữ bộ lọc qua mọi thao tác**: liên kết Sửa, thanh chuyển
trang và cả nút ghi dữ liệu đều mang theo chuỗi lọc hiện tại, nên khoá một tài khoản ở trang 3
của danh sách đang lọc thì xong việc vẫn đứng nguyên chỗ đó. Thiếu điều này, mỗi lần bấm một
nút là phải gõ lại bộ lọc và lật lại từng trang — thao tác quản trị thường đi thành chuỗi
chứ hiếm khi chỉ có một lần.

### Phân trang — một thẻ dùng chung cho mọi danh sách

Mọi màn hình có danh sách đều phân trang bằng cùng một thẻ `WEB-INF/tags/pager.tag`, nhận vào
đối tượng `Dtos.Page` và tự dựng chuỗi truy vấn cho các liên kết. Hai cách lấy trang:

* **Cắt dưới SQL** (`OFFSET … FETCH NEXT`) cho bảng lớn tra cứu theo bộ lọc: thực đơn, món và
  tài khoản ở màn quản trị, nhóm món, lịch sử đơn, nhật ký thao tác, hộp thông báo.
* **Cắt trên danh sách đã nạp** (`Dtos.Page.of`) cho màn hình vận hành — hàng chờ bếp, quầy
  giao nhận, sự cố: những màn này vốn phải đọc trọn danh sách để đếm và tô màu
  cảnh báo, hỏi thêm một câu `COUNT` nữa chỉ tốn thêm một vòng đi về cơ sở dữ liệu.

Trang nào có nhiều bảng thì mỗi bảng mang **tên tham số riêng** (`issuePage`, `prepPage`,
`readyPage`…) kèm một mỏ neo `#id`, nên lật bảng này không kéo bảng kia về đầu và bấm xong thì
màn hình dừng lại đúng chỗ vừa bấm. Mọi nút ghi dữ liệu trên các màn hình đó đều gửi kèm
`returnTo` là địa chỉ hiện tại, để nhận một món ở trang 3 xong vẫn còn đứng ở trang 3.

Riêng hàng chờ bếp tự làm mới bằng JavaScript: máy chủ dựng đúng trang đang xem, còn mỗi lần
hỏi lại `/api/kds/queue` thì trình duyệt cắt lại đúng khoảng đó. Thẻ món tự cập nhật được,
nhưng thanh chuyển trang thì do máy chủ dựng nên không — nên nó chỉ nhắc tải lại khi **số
trang đổi thật** hoặc khi món đã trôi hết khỏi trang đang xem; hàng chờ nhích vài món trong
cùng một trang thì im lặng, vì nhắc liên tục là kiểu cảnh báo mà bếp sẽ học cách bỏ qua. Hết
sạch hàng chờ thì thanh bị giấu đi, để không có cảnh khung báo "hết món" nằm ngay trên dòng
"đang xem 1–12 trong 12 đơn".

### Không phải trang — endpoint hành động và tích hợp
| Địa chỉ | Servlet | Bản chất |
|---|---|---|
| `/payment/start` | PaymentStartServlet | Lập một lần thanh toán rồi chuyển hướng sang địa chỉ payOS trả về. Không hiển thị gì. Chỉ chủ đơn gọi được |
| `/payment/payos/return` | PayOsReturnServlet | payOS đưa khách quay lại sau khi trả tiền, có thể không kèm phiên đăng nhập. Các tham số trên địa chỉ **không có chữ ký** nên không được tin: chỉ lấy `orderCode` rồi gọi ngược sang payOS hỏi trạng thái thật. Xem §3.6 |
| `/payment/payos/webhook` | PayOsWebhookServlet | payOS gọi thẳng vào máy chủ, không qua trình duyệt. Kiểm chữ ký trên khối `data` rồi đi vào cùng `PaymentService.handleCallback`. Luôn trả 2xx sau khi chữ ký đã qua, kể cả khi xử lý hỏng — trả lỗi chỉ làm payOS gọi lại nhiều lần. Xem §3.6 |
| `/payment/sepay/webhook` | SePayWebhookServlet | SePay báo có tiền về, không có phiên đăng nhập. Kiểm khoá API ở header `Authorization`, đọc mã thanh toán từ nội dung chuyển khoản, rồi đi vào đúng `PaymentService.handleCallback` như trên. Xem §3.7 |
| `/api/kds/queue` | KdsApiServlet | JSON cho màn hình bếp, hỏi lại mỗi 5 giây — chỉ vai trò Bếp và Quản trị. Chỉ trả dữ liệu bếp cần, không kèm thông tin khách hay thanh toán |
| `/api/order/status` | OrderStatusApiServlet | JSON cho trang theo dõi đơn, hỏi lại mỗi 10 giây |

Hỏi lại theo chu kỳ là lựa chọn có ý thức: chạy được trên Servlet thuần, không cần thêm hạ
tầng. Đánh đổi là độ trễ tối đa đúng bằng chu kỳ hỏi. WebSocket hay SSE nằm ngoài phạm vi.

### Không phải trang — công việc chạy nền
| Lớp | Chu kỳ | Việc |
|---|---|---|
| `KitchenReleaseScheduler` | 30 giây | Đưa đơn tới giờ xuống bếp, đúng một lần cho mỗi đơn |
| `PaymentExpiryScheduler` | 30 giây | Cho hết hiệu lực đơn quá hạn thanh toán 15 phút |

Cả hai ghi nhật ký với người thực hiện để trống — đó là dấu hiệu nhận biết việc do hệ thống
tự làm, không phải do ai bấm.

### Năm màn hình đã gộp

Mỗi vai trò giữ trong khoảng bốn tới sáu màn hình. Năm trang dưới đây từng đứng riêng nhưng
không tự mình làm xong việc gì — người dùng luôn phải đi tiếp sang trang khác — nên chúng
được đưa về đúng chỗ đang cần chúng:

| Trang cũ | Nay nằm ở | Vì sao |
|---|---|---|
| `/checkout` | `/cart`, phần dưới giỏ hàng | Trang chọn giờ vẫn phải liệt kê lại toàn bộ giỏ; sửa số lượng thì phải quay ngược về trang trước |
| `/staff/pickup/verify` | `/staff/orders`, ô tra ở đầu trang | Tra xong vẫn phải mở đơn mới giao được món — hai lần chuyển trang cho một việc lúc khách đứng ở quầy |
| `/staff/receipt` | `/staff/order/detail`, khối `print-only` | Cả trang chỉ để chứa một nút in; nay khối hoá đơn ẩn trên màn hình và hiện ra khi in |
| `/kitchen/my-tasks` | `/kitchen/queue`, khối trên cùng | Đầu bếp phải nhìn việc của mình và hàng chờ cùng lúc mới quyết định được nhận món tiếp theo, và món đã xong thì không có chỗ nào nhắc |
| `/staff/issues` | `/staff/counter` | Sự cố bếp và việc nhận món từ bếp trả lời chung một câu hỏi: món của đơn này đang ở đâu |

Riêng dòng cuối là **đổi vai chứ không bị xoá**: `/staff/issues` trước đây chỉ đọc, chỉ hiện sự
cố bếp. Nay thành `/staff/counter` — **Quầy giao nhận**, nơi thu ngân nhận món bếp vừa đưa ra và
thấy đơn nào đã đủ món để gọi khách. Sự cố bếp vẫn ở đó, thành một trong ba khối.

### 4.3 Màn hình đủ CRUD theo vai trò

Mỗi vai trò có **ít nhất bốn màn hình làm đủ cả bốn thao tác** trên một thực thể. Bảng này liệt
kê đúng những màn hình đó, kèm thực thể chính và cách thao tác Xoá được hiểu.

| Vai trò | Màn hình | Thực thể | Xoá là gì |
|---|---|---|---|
| **Khách hàng** | `/cart` | CartItem | Xoá hẳn — giỏ là bản nháp |
| | `/menu` | Favourite | Xoá hẳn — dữ liệu riêng của khách |
| | `/order/history` | OrderTemplate · OrderTemplateItem | Xoá hẳn, dòng món đi theo bằng cascade |
| | `/product/detail` | Review | Xoá hẳn, điểm trung bình tự tính lại |
| **Thu ngân** | `/staff/orders` | OrderNote | Xoá hẳn |
| | `/staff/counter` | KitchenIssue loại `COUNTER_REJECT` | Mềm: `status = CANCELLED` |
| **Bếp** | `/kitchen/queue` | PrepTask | Mềm: `status = CANCELLED` |
| | `/kitchen/item` | OrderItemNote | Xoá hẳn |
| | `/kitchen/issue` | KitchenIssue | Mềm: `status = CANCELLED` |
| | `/kitchen/history` | KitchenNote | Xoá hẳn |
| **Quản trị** | `/admin/products` | Product | Mềm: `status = INACTIVE`, nút **Ngừng bán** riêng |
| | `/admin/categories` | Category | Mềm: `status = INACTIVE`, nút **Ẩn nhóm** riêng |
| | `/admin/users` | Users | Mềm: `status = LOCKED` |
| | `/admin/dashboard` | RevenueTarget | Xoá hẳn — con số cũ còn trong nhật ký |

**Xoá luôn có đường đi riêng, không nấp trong form Sửa.** Hai màn danh mục của quản trị viên
từng vi phạm điều này: trạng thái kinh doanh nằm trong một ô tick của chính form Sửa, nên
"xoá" thực chất là một lần Sửa. Hai hậu quả, và cái thứ hai nặng hơn:

- Mọi lần lưu nội dung đều ghi đè trạng thái. Sửa mô tả một món mà quên tick là món rời thực
  đơn; sửa tên một nhóm mà quên tick là cả dòng sản phẩm biến mất.
- Nhật ký không phân biệt được. Cả hai việc đều ra `PRODUCT_CHANGED`, nên câu hỏi hay gặp nhất
  khi rà thực đơn — "món này ai gỡ, lúc nào" — phải lọc tay giữa hàng trăm dòng sửa giá.

Nay `setProductStatus` / `setCategoryStatus` là thao tác riêng, ô tick đã gỡ khỏi form Sửa, và
tầng dịch vụ **giữ nguyên trạng thái cũ** kể cả khi form gửi lên giá trị khác. Nhật ký có bốn
mã riêng: `PRODUCT_RETIRED` · `PRODUCT_RESTORED` · `CATEGORY_RETIRED` · `CATEGORY_RESTORED`,
mỗi dòng mang cả trạng thái cũ lẫn mới.

Đừng nhầm nút **Ngừng bán** với nút **Còn hàng / Tạm hết** ngay cạnh nó trên cùng một dòng:
cái sau là `is_available`, tình trạng trong ngày, hết buổi lại bật lên. Cái trước là gỡ món
khỏi thực đơn. Gộp hai khái niệm này thì mỗi lần bếp báo hết nguyên liệu là món bị xoá.

**Ranh giới giữa xoá hẳn và xoá mềm** không tuỳ tiện: xoá hẳn dành cho bản nháp và nội dung
riêng của người dùng — không có đồng tiền nào đi qua, không bản ghi nào trỏ tới. Xoá mềm dành
cho thứ đã có vết ở nơi khác: mã sự cố đã nằm trong nhật ký thao tác, còn
món và tài khoản thì các đơn cũ vẫn đang tham chiếu tới.

`RevenueTarget` là ngoại lệ đáng nói: nó **có** dòng nhật ký trỏ tới nhưng vẫn xoá hẳn, vì dòng
`TARGET_DELETED` mang theo con số cũ trong `old_value` — bản thân dòng nhật ký đã đủ, không cần
giữ lại một bản ghi rỗng.

**Hai màn hình cố ý không có CRUD**, và đó là quyết định chứ không phải thiếu sót:

- **`/admin/audit` phải mãi mãi chỉ đọc.** Một nhật ký kiểm toán mà quản trị viên sửa được thì
  mất đúng thứ nó sinh ra để làm. Đây là lý do màn hình thứ tư của quản trị buộc phải là bảng
  điều khiển.
- **`/staff/order/detail` và `/order/track`** là màn hình tra cứu và thao tác trạng thái, không
  sở hữu thực thể nào của riêng chúng.

---

## 5. Bảo mật

| Lớp | Thực hiện | Chặn được gì |
|---|---|---|
| `AuthenticationFilter` | Liệt kê trang công khai, còn lại bắt đăng nhập | Thêm màn hình mới mà quên khai báo thì bị bắt đăng nhập thừa, không lộ dữ liệu |
| `CsrfFilter` | Mọi yêu cầu ghi phải mang mã dùng theo phiên; mặc định là từ chối | Một trang bất kỳ dựng biểu mẫu tự gửi tới `/admin/users` và khoá tài khoản dưới danh nghĩa người quản trị đang đăng nhập |
| `RoleAuthorizationFilter` | Phân quyền theo tiền tố địa chỉ, gồm cả `/api/kds/*` | Gõ thẳng `/admin/...`, hoặc gọi địa chỉ JSON của bếp bằng tài khoản khách |
| `SessionGuard` | Soi lại tài khoản trong cơ sở dữ liệu theo nhịp 30 giây, **và ngay lập tức ở mọi yêu cầu ghi** | Khoá hay hạ quyền một tài khoản mà tới 60 phút sau mới có tác dụng — đúng khoảng thời gian người vừa bị khoá còn đang thao tác |
| `LoginThrottle` | Năm lần sai trên cùng cặp email + máy thì khoá cửa 15 phút, kiểm **trước** khi băm mật khẩu | Quét danh sách mật khẩu thông dụng. Kiểm trước khi băm để mỗi lần thử không còn tốn công máy chủ |
| Kiểm tra chủ sở hữu | Điều kiện `customer_id` ngay trong câu truy vấn | Khách xem đơn của người khác. Trả về "không tìm thấy" để không lộ mã đơn nào có thật |
| Mật khẩu | bcrypt cost 10; tối thiểu 8 ký tự, có cả chữ và số, chặn danh sách phổ biến, **giới hạn 72 byte** | Lộ cơ sở dữ liệu vẫn không đọc được mật khẩu. Giới hạn 72 byte vì bcrypt cắt cụt im lặng ở đó |
| Câu lệnh SQL | `PreparedStatement` toàn bộ | Chèn mã SQL qua ô tìm kiếm |
| Hiển thị | `<c:out>` cho mọi dữ liệu do người dùng nhập | Chèn mã kịch bản qua tên tài khoản hay tên món — nguy nhất là khi trang quản trị mở lên và chạy mã của khách |
| Địa chỉ quay về | `WebUtil.safeRedirect` chỉ nhận đường dẫn nội bộ | Liên kết đăng nhập của chính cửa hàng nhưng đẩy sang trang giả mạo sau khi đăng nhập |
| Phiên đăng nhập | Cấp phiên mới sau khi đăng nhập **và sau khi đăng ký** | Chiếm phiên đã biết trước |
| Nội dung phiên | `WebUtil.putCurrentUser` gỡ bản băm mật khẩu trước khi cất vào phiên | Một dòng `${me.passwordHash}` gõ nhầm ở bất kỳ trang nào in bản băm ra HTML |
| Đăng xuất | Chỉ nhận POST, kèm mã chống giả mạo | Trang khác đá người dùng ra bằng một thẻ `<img src=".../logout">` rồi mời họ đăng nhập lại trên trang giả |
| Mật khẩu đặt hộ | Quản trị viên đặt lại thì máy chủ **sinh mật khẩu tạm ngẫu nhiên**, và tài khoản bị giữ ở trang tài khoản tới khi tự đổi | Tài khoản chạy tiếp bằng mật khẩu mà ít nhất hai người biết. Trước đây mọi lần đặt lại đều về cùng một chuỗi nằm sẵn trong mã trang |
| Quên mật khẩu | Mã 32 byte ngẫu nhiên, **chỉ lưu bản băm**, dùng một lần, hạn 15 phút, tối đa 3 lần xin mỗi 15 phút | Đọc được bảng cũng không dựng lại được liên kết. Xin mã cho email người khác cũng không dội thư được vào hộp thư họ |
| Dò email | Trang quên mật khẩu trả về **cùng một câu** cho mọi email, kể cả khi định dạng sai hay hệ thống lỗi | Dùng trang này để kiểm tra một địa chỉ có phải khách của cửa hàng không |
| Xác thực email | Mã 32 byte ngẫu nhiên, **chỉ lưu bản băm**, dùng một lần, hạn 24 giờ, tối đa 5 lần xin mỗi 15 phút. Chưa xác thực thì không đặt được đơn online — xem §3.9 | Đăng ký bằng email của người khác rồi chiếm vĩnh viễn địa chỉ đó (cột `email` là UNIQUE), và đơn hàng gắn với một hộp thư không ai mở |
| Chữ ký cổng thanh toán | Kiểm tra trước khi ghi nhận tiền | Gọi thẳng địa chỉ nhận kết quả để tự xác nhận đơn |
| Tự hạ quyền | Chặn quản trị viên tự khoá và tự đổi vai trò của chính mình | Mất đường vào khu vực quản trị mà không tự sửa lại được |
| Gán vai trò | `AdminService` đọc lại vai trò trong cùng giao dịch: mã lạ bị từ chối, và màn hình tạo nhân viên không nhận vai trò Khách hàng | Ô chọn trên biểu mẫu đã ẩn mục Khách hàng, nhưng ẩn một mục trong thẻ `select` không ngăn được ai gửi thẳng mã vai trò khác lên — rào chắn phải nằm sau giao diện |
| Nhật ký đăng nhập | `LOGIN_SUCCESS` · `LOGIN_FAILED` · `LOGIN_BLOCKED` · `LOGOUT` · `PASSWORD_RESET_*` · `EMAIL_VERIFY_SENT` · `EMAIL_VERIFIED`, ghi bằng **giao dịch riêng** | Ghi cùng giao dịch với thao tác bị từ chối là tự xoá bằng chứng: giao dịch huỷ thì dòng nhật ký đáng chú ý nhất huỷ theo |

**Thứ tự bốn bộ lọc** khai báo trong `web.xml` chứ không bằng `@WebFilter`: đặc tả Servlet không
bảo đảm thứ tự của bộ lọc khai báo bằng annotation, mà thứ tự thì quan trọng ở cả bốn vị trí.

```
01-Encoding → 02-Authentication → 03-Csrf → 04-RoleAuthorization
```

- **Encoding chạy trước hết**: bảng mã bị chốt ngay khi có ai đó đọc tham số đầu tiên, mà chính
  `CsrfFilter` là kẻ đọc tham số đầu tiên. Chốt sai thì tên tiếng Việt gửi lên thành ký tự lỗi.
- **Csrf sau Authentication**: lý do khiến một yêu cầu thiếu mã, phần lớn các lần, là phiên hết
  hạn khi trang còn mở. Để bộ lọc đăng nhập gặp trước thì người dùng được đưa về trang đăng nhập
  kèm đường quay lại; đảo lại thì họ nhận một trang 403 không nói được gì.
- **Csrf trước RoleAuthorization**: một yêu cầu chưa chứng minh được là do chính người dùng phát
  ra thì không đáng đem đi xét quyền.

**Cả ba bộ lọc có kiểm tra đều đọc đường dẫn qua `RequestPath`**, không tự cắt chuỗi từ `getRequestURI()`.
Chuỗi thô còn mang tham số đường dẫn — phần sau dấu chấm phẩy như `/menu;jsessionid=ABC` — mà
máy chủ thì bỏ qua phần đó khi chọn servlet. Lệch kiểu này vô hại với danh sách trang công khai
(chỉ bắt đăng nhập thừa) nhưng nguy hiểm với danh sách **miễn trừ** của `CsrfFilter`.

**Hai thuộc tính cookie không khai báo được trong `web.xml`** nên nằm ở `META-INF/context.xml`:
`SameSite=Lax` (Servlet 4.0 ra đời trước thuộc tính này) và `Secure` — cố ý **để tắt**, vì demo
chạy trên `http://localhost` và bật lên thì trình duyệt vứt cookie phiên đi. Triển khai thật
sau tên miền https thì thêm `secure="true"` vào thẻ `CookieProcessor`.

**Mật khẩu tài khoản mẫu vẫn là `123456`.** Chính sách mới chỉ áp dụng lúc **đặt** mật khẩu, nên
các tài khoản đã seed vẫn đăng nhập bình thường — cố tình giữ vậy để người chấm không phải tra
mật khẩu mới. Đổi chính sách mà khoá luôn cửa với tài khoản cũ là một cách siết sai.

---

## 6. Cách chạy

```bash
# 1. Cơ sở dữ liệu
#    sqlcmd.exe cũ trên Windows phải thêm -f 65001; đừng nạp bằng SSMS F5
#    — xem database/README.md, mục bảng mã
sqlcmd -S localhost -U sa -P '<mật khẩu>' -C -i database/FastFoodPreorder.sql

# 2. Sửa src/main/resources/db.properties cho khớp máy chủ SQL Server

# 3. Đóng gói và triển khai
mvn clean package
cp target/fastfood.war $TOMCAT_HOME/webapps/
```

Mở `http://localhost:8080/fastfood/` — mật khẩu mọi tài khoản mẫu là `123456`.

Cấu hình hiện tại dùng **Tomcat 9** (`javax.servlet`). Chạy Tomcat 10 trở lên phải đổi
phụ thuộc sang `jakarta.*` và đổi toàn bộ lệnh `import javax.servlet.*`.

---

## 7. Bộ kiểm thử

```bash
mvn test          # 560 bài
```

Chia hai nhóm bằng đuôi tên lớp:

| Nhóm | Đuôi | Chạy ở đâu | Số bài |
|---|---|---|---|
| Không chạm cơ sở dữ liệu | `*Test` | Mọi máy, không cần gì thêm | 220 |
| Chạy thật xuống cơ sở dữ liệu | `*IT` | Cần SQL Server ở `localhost:1433` | 340 |

Nhóm đầu gồm 213 bài logic thuần (`BusinessMathTest`, `PickupCodeGeneratorTest`,
`OrderStateTest`, `PasswordPolicyTest`, `SafeRedirectTest`, `LoginThrottleTest`,
`CsrfTokenTest`, bốn bài về cổng thanh toán: `PayOsGatewayTest` và `PayOsCallbacksTest`
— xem §3.6 — cùng `SePayGatewayTest` và `SePayWebhookServletTest` — xem §3.7, và bốn bài về bộ lọc:
`RoleAuthorizationFilterTest`, `CsrfFilterTest`, `RequestPathTest`, `RoutePolicyTest`)
và 7 bài dựng
tầng hiển thị (`BeanNamingTest`, `CsrfTokenPresenceTest`, `JspCompileTest` — chạy Jasper biên
dịch toàn bộ JSP để một lỗi cú pháp trong trang không phải đợi tới lúc mở trình duyệt mới
lộ ra).

Nhóm `*IT` **tự bỏ qua** khi không có máy chủ, không báo đỏ: màu đỏ phải có nghĩa là mã nguồn
sai, không phải là máy chạy test thiếu thứ gì đó.

Nhưng chỉ đúng hai chuyện đó mới được bỏ qua. **Kết nối được mà tệp lược đồ chạy hỏng thì
báo đỏ**, vì đó là lỗi mã nguồn: quên một dòng `DROP`, sai thứ tự khoá ngoại, viết hỏng ràng
buộc. Trước đây hai tình huống này gộp làm một, nên thêm một bảng mà quên dòng `DROP` khiến
toàn bộ 111 bài tích hợp im lặng biến khỏi lượt chạy trong khi kết quả vẫn ghi BUILD SUCCESS —
xem `TestDatabase.ensureReady`.

**Database test là một database riêng.** `src/test/resources/db.properties` trỏ vào
`FastFoodPreorder_Test`, và `TestDatabase` xoá rồi dựng lại toàn bộ bảng một lần trước cả
lượt chạy — trỏ vào database đang phát triển là mất sạch dữ liệu đang thử tay. Schema dùng
đúng file `database/FastFoodPreorder.sql` chứ không chép ra bản riêng cho test: chép ra thì
hai bản lệch nhau lúc nào không ai biết, và bộ test sẽ xanh trên một schema không tồn tại ở
đâu cả.

| Lớp | Kiểm chứng điều gì |
|---|---|
| `RevenueReportIT` | Doanh thu chia kỳ theo `paid_at` — xem ghi chú bên dưới |
| `SchemaConstraintIT` | 21 ràng buộc và trigger, ghi thẳng bằng SQL để **cố tình bỏ qua** tầng Service |
| `OnlinePreorderFlowIT` | Cả vòng đời đơn đặt trước, chạy qua tầng Service thật — kể cả khi tiền về không đúng số tiền của đơn (§3.5) |
| `PosOrderIT` | Bán tại quầy bằng tiền mặt: đơn xuống bếp ngay, và phiếu tính tiền đọc giá mới nhất |
| `KitchenFlowIT` | Bếp làm món, trạng thái đơn tự suy ra, bàn giao ra quầy, sự cố bếp |
| `OrderDashboardIT` | Bốn tab phủ kín mọi đơn chưa kết thúc |
| `CounterQueueIT` | Hàng chờ của quầy giao nhận — kể cả món của đơn đã đóng |
| `KitchenPrepIT` | Kế hoạch chuẩn bị sẵn: một món một dòng mỗi ngày, dòng đã chốt thì đóng băng, chỉ người lập mới thu hồi |
| `AdminCatalogIT` | Đủ bốn thao tác trên món và nhóm món, và chứng minh Xoá đi đường riêng: sửa nội dung không được đụng tới trạng thái kinh doanh |
| `AdminListingIT` | Hai danh sách quản trị: câu đếm dùng chung mệnh đề lọc với câu lấy dữ liệu, trang 2 không lặp trang 1, giá trị lọc lạ hiểu thành không lọc, và nhóm đang ẩn vẫn nằm trong ô chọn nhóm |
| `KitchenNoteIT` | Ghi chú chế biến và sổ bàn giao — và bằng chứng ghi chú **không** làm tăng số sự cố đang mở |
| `CounterNoteRejectIT` | Ghi chú điều phối, và từ chối nhận món chỉ trong khoảng bếp đã bàn giao mà quầy chưa nhận |
| `AdminAccountIT` | Mật khẩu đặt hộ, chống tự hạ quyền |
| `AdminRoleAssignmentIT` | Vai trò gán cho tài khoản: mã vai trò lạ bị chặn ở tầng dịch vụ chứ không đợi khoá ngoại, không tạo được tài khoản khách qua màn hình nhân viên dù gửi thẳng mã vai trò, và nhật ký ghi tên vai trò chứ không ghi mã |
| `AuthFlowIT` | Đăng nhập, khoá cửa sau nhiều lần sai, và trọn vòng đời mã quên mật khẩu |
| `EmailVerificationIT` | Xác thực email: mã dùng một lần và có hạn, bấm lại lá thư cũ **không** bị báo lỗi oan, và chốt chặn đặt đơn nằm ở tầng Service chứ không chỉ ẩn nút |
| `AuthGuardIT` | Bộ lọc đăng nhập chạy thật: khoá tài khoản là chấm dứt phiên đang mở — xem ghi chú bên dưới |
| `RevenueTargetIT` | Chỉ tiêu doanh thu: dùng lại đúng doanh thu thuần của báo cáo, và mốc cuối kỳ không lấn sang kỳ sau |
| `CartIT` | Giỏ hàng: cộng dồn không vượt được trần số lượng, món ngừng bán không vào giỏ, không sửa được dòng trong giỏ người khác |
| `FavouriteIT` | Món quen: ghi chú riêng, món hết hàng vẫn đánh dấu được, khách chưa đăng nhập nhận tập rỗng |
| `MenuBrowseIT` | Duyệt thực đơn: hai chiều sắp xếp theo giá, món chưa ai chấm nằm cuối khi xếp theo đánh giá, mã sắp xếp lạ rơi về mặc định thay vì lọt vào câu lệnh |
| `OrderTemplateIT` | Mẫu đặt nhanh: nạp xong mẫu vẫn còn, món đã ngừng bán bị bỏ qua và **được gọi tên** |
| `ReviewIT` | Đánh giá món: chỉ khách đã nhận mới viết được, mỗi khách một đánh giá cho một món, và thực đơn không mất món nào khi nối bảng điểm |
| `NotificationInboxIT` | Hộp thông báo: dấu đã đọc không dời mốc cũ, mở một đơn không đọc hộ đơn khác, và không ai xoá được dấu chưa đọc của người khác |
| `OrderHistoryFilterIT` | Bộ lọc lịch sử đơn: câu đếm dùng chung mệnh đề lọc với câu lấy dữ liệu, ngày kết thúc phủ hết ngày, trạng thái lạ không thành lỗi |
| `JspCompileTest` | Dịch thử cả 27 trang JSP bằng bộ dịch của Tomcat — xem ghi chú bên dưới |
| `BeanNamingTest` | Tên phương thức đọc phải là thứ EL gọi được: không `hasX`, không trùng từ khoá |
| `CsrfTokenPresenceTest` | Cả 83 biểu mẫu POST đều mang ô ẩn `_csrf` — xem ghi chú bên dưới |
| `RoutePolicyTest` | Ranh giới quyền theo địa chỉ: mọi màn hình đặc quyền đều nằm sau bộ lọc phân quyền, mọi trang của khách đều bắt đăng nhập trừ hai trang công khai cố ý |
| `RoutePolicyTest` | Ranh giới quyền theo địa chỉ, đọc từ `@WebServlet` + danh sách công khai + `web.xml` |
| `CsrfFilterTest` · `RoleAuthorizationFilterTest` | Hành vi thật của hai bộ lọc, chạy qua yêu cầu giả (`FakeHttp`) |
| `PasswordPolicyTest` · `SafeRedirectTest` · `LoginThrottleTest` · `CsrfTokenTest` · `RequestPathTest` | Các rào chắn của phần xác thực, kiểm bằng logic thuần |
| `OrderStateTest` · `BusinessMathTest` · `PickupCodeGeneratorTest` | Giá trị suy ra và các phép tính nhỏ |

**Vì sao có ba bài test cho tầng hiển thị.** JSP là tầng duy nhất không được biên dịch trong
`mvn test`: mã Java sai thì Maven báo đỏ ngay, còn một thẻ quên đóng chỉ lộ ra khi có người mở
đúng trang đó trên trình duyệt. Ba bài này chặn ba loại lỗi khác nhau, và **cả ba loại đều đã
thật sự xảy ra** trong dự án:

| Lỗi | Hậu quả | Ai bắt |
|---|---|---|
| `isShort()` → `${s.short}` | `short` là từ khoá, biểu thức không phân tích được, **đổ cả trang** | `JspCompileTest` |
| `hasVariance()` → `${s.hasVariance}` | EL chỉ thấy `getX`/`isX`, nên biểu thức thành rỗng và **một nhánh giao diện lặng lẽ không bao giờ hiện** | `BeanNamingTest` |
| Biểu mẫu POST quên ô ẩn `_csrf` | Bấm nút thì ra trang 403 — với 83 biểu mẫu trải khắp bốn vai trò, "sẽ có người bấm thử" không phải điều chắc chắn xảy ra trước buổi trình bày | `CsrfTokenPresenceTest` |

Loại thứ hai nguy hiểm hơn vì nó không báo gì cả. `CartView.isEmpty()` là quả mìn cùng loại đã
được gỡ nhân dịp này: `${cart.empty}` cũng không phân tích được vì `empty` là toán tử của EL —
chưa trang nào gọi tới, nên nó vẫn đang chờ người đầu tiên viết dòng đó.

Loại thứ ba từng suýt lọt theo một đường khác: kịch bản chèn ô ẩn hàng loạt đặt nhầm nó **vào
giữa thẻ mở** của một biểu mẫu có chú thích JSP xen giữa các thuộc tính. Trang vẫn dịch được,
nút vẫn hiện ra, và chỉ hỏng khi có người bấm. Vì vậy `CsrfTokenPresenceTest` bỏ qua chú thích
JSP khi tìm chỗ kết thúc thẻ, và kiểm cả **giá trị** của ô ẩn chứ không chỉ sự tồn tại của nó.

**Vì sao `AuthGuardIT` phải chạm cơ sở dữ liệu** trong khi hai bài về bộ lọc kia thì không.
Điều đáng kiểm nhất ở đó là `SessionGuard`: bản chụp người dùng trong phiên có được đối chiếu
lại với hàng thật hay không. Thay cơ sở dữ liệu bằng vật giả thì bài test chỉ chứng minh được
rằng mã có gọi một hàm nào đó, chứ không chứng minh được rằng **khoá một tài khoản là chặn được
người đó ngay**. Phiên sống 60 phút còn quyền thì đọc một lần lúc đăng nhập — không có bài test
ở đây thì câu trên chỉ là một câu nói.

Bài đó cũng ghim chặt cả hai vế của một đánh đổi có ý thức: trong cửa sổ 30 giây, một lần **xem**
trang vẫn đi qua; còn một thao tác **ghi** thì không bao giờ. Viết ra thành hai khẳng định cạnh
nhau để lần sau ai đó chỉnh nhịp soi lại sẽ thấy ngay mình đang đánh đổi cái gì.

**Yêu cầu và phản hồi giả nằm ở `testsupport/FakeHttp`**, dựng bằng lớp uỷ nhiệm động thay vì
thêm một thư viện giả lập vào `pom.xml`. Phương thức chưa dựng thì ném lỗi kèm đúng tên nó, chứ
không lặng lẽ trả `null` — bài test sau này cần thêm gì sẽ đọc được ngay, thay vì đi tìm một giá
trị rỗng không rõ từ đâu ra.

**Vì sao `RevenueReportIT` đáng đọc trước.** Mốc chia kỳ của doanh thu là `Payment.paid_at`,
không phải `Orders.created_at` — và hai cái đó lệch nhau đúng ở chỗ khó thấy nhất: đơn lập cuối
tháng 3, khách trả tiền đầu tháng 4. Lấy nhầm mốc thì từng kỳ trông vẫn hợp lý, chỉ có tổng các
kỳ là không khớp tổng toàn thời gian. Bộ test này canh cả hai điều đó: khoản thu rơi vào đúng kỳ
của `paid_at`, và tháng 3 cộng tháng 4 bằng đúng con số tính gộp.

---

## 8. Ngoài phạm vi — không có trong mã nguồn

Không giao hàng tận nơi, không nhiều chi nhánh, không quản lý kho và nhà cung cấp,
không mã giảm giá, không tích điểm, **không huỷ đơn và không hoàn tiền**,
không đặt trước mà không đăng nhập, không trả tiền mặt cho đơn đặt trước.

Đơn đã lập chỉ đi tới `COMPLETED`, hoặc dừng ở `EXPIRED` khi khách không hoàn tất thanh toán
trong 15 phút. Khách muốn bỏ đơn chưa trả tiền thì cứ để nguyên cho nó hết hạn; mọi trường hợp
cần trả lại tiền đều xử lý tay ngoài hệ thống, qua cổng thanh toán.

**Đánh giá món** trước đây nằm trong danh sách này, nay đã có: viết ở `/product/detail`, và điểm
trung bình hiện trên từng thẻ món ở `/menu`.

Phép gộp điểm chạy **một lần cho cả bảng** rồi mới nối vào thực đơn bằng `LEFT JOIN`, chứ không
phải một truy vấn con chạy lại cho từng dòng — chỉ mục `IX_Review_product` đã mang sẵn cột
`rating` nên phép gộp không phải mở tới bảng gốc. Dấu `LEFT` là chỗ chết người: đổi thành `JOIN`
thuần sẽ làm biến mất mọi món chưa ai chấm, tức là gần hết thực đơn của một cửa hàng mới mở, nên
có bài kiểm thử riêng canh đúng điều đó.
