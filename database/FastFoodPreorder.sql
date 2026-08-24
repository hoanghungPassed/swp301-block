/* =============================================================================================
   FAST FOOD PRE-ORDER PICKUP & POS  —  DATABASE (SQL Server)
   Dự án SWP301 · Requirements Baseline V6 · docs/preview-2 (1).html

   Hai kênh đặt hàng:
     ONLINE_PREORDER — đặt từ xa, hẹn giờ đến lấy, thanh toán online trước
     POS             — khách walk-in đặt tại quầy
   MVP KHÔNG có Delivery/Shipper.

   ---------------------------------------------------------------------------------------------
   YÊU CẦU : SQL Server 2016 trở lên (dùng DROP ... IF EXISTS)
   CÁCH CHẠY: mở file trong SSMS -> Execute (F5). Hoặc:
              sqlcmd -S localhost -U sa -P '<password>' -C -i FastFoodPreorder.sql

   *** CẢNH BÁO: file này XOÁ và TẠO LẠI toàn bộ bảng mỗi lần chạy. ***
   Thiết kế như vậy để luôn cho ra một database sạch, không phụ thuộc trạng thái trước đó.
   Đừng chạy trên môi trường có dữ liệu thật.

   ĐÂY LÀ FILE DATABASE DUY NHẤT của dự án — lược đồ, dữ liệu demo và bộ kiểm tra nằm chung
   một chỗ, chạy một lần là xong. Không có file migration đi kèm: sửa lược đồ thì sửa thẳng
   vào đây rồi chạy lại, vì mỗi lần chạy đều dựng lại từ đầu nên không có bước nâng cấp nào
   để phải viết riêng.
   ---------------------------------------------------------------------------------------------

   NỘI DUNG
     1. Tạo database
     2. Xoá đối tượng cũ
     3. 24 bảng          (nhóm: danh mục · giỏ hàng · đơn hàng · thanh toán · vận hành ·
                          bếp · quản trị · của riêng khách)
     4. 27 index
     5. 2 view           (suy ra trạng thái release & KPI đúng hẹn)
     6. 6 trigger        (BR-04 + chặn hard-delete BR-20)
     7. Dữ liệu mẫu     (7 user · 13 món · 2 giỏ hàng · 11 đơn phủ đủ 7 trạng thái · chỉ tiêu
                          doanh thu · món quen, mẫu đặt nhanh và đánh giá của khách · tin báo
                          và nhật ký thao tác suy ra từ chính các mốc thời gian)
     8. Kiểm tra sau khi chạy — 10 bảng đối chiếu, in ra ngay sau khi chạy xong

   MỖI BẢNG MỚI PHẢI CÓ DÒNG DROP Ở MỤC 2. Quên dòng đó thì file chạy được đúng một lần: từ
   lần thứ hai, DROP bảng cha vấp phải khoá ngoại và cả phần dựng lược đồ dừng giữa chừng.
   Số dòng DROP ở mục 2 không bao giờ ít hơn số bảng ở mục 3 — hiện 24 bảng và 27 dòng DROP.
   Ba dòng dư là bảng của bản cũ (Shift, PosHold, PosHoldItem): tính năng đã bỏ nhưng dòng
   DROP phải ở lại, nếu không thì cơ sở dữ liệu dựng bằng bản trước sẽ kẹt khi chạy lại file.

   Hai trong số 24 bảng — PasswordResetToken và EmailVerificationToken — cố ý rỗng sau khi
   chạy file: mã chỉ sinh ra khi có người bấm "Quên mật khẩu" hay "Gửi lại thư xác thực".
   Vì vậy bảng đối chiếu ở mục 8.1 chỉ liệt kê 22 bảng còn lại.

   BA TÊN BẢNG KHÁC TÀI LIỆU — vì trùng từ khoá SQL Server:
     User -> Users        Order -> Orders        Transaction -> PaymentTransaction
   Java entity vẫn giữ tên theo tài liệu; ánh xạ chỉ nằm trong tầng DAO.

   TÀI KHOẢN MẪU — mật khẩu tất cả: 123456
     customer1@gmail.com  customer2@gmail.com          (CUSTOMER)
     cashier1@fastfood.vn cashier2@fastfood.vn         (CASHIER)
     kitchen1@fastfood.vn kitchen2@fastfood.vn         (KITCHEN)
     admin@fastfood.vn                                 (ADMIN)
   ============================================================================================= */


/* =============================================================================================
   1. TẠO DATABASE
   ============================================================================================= */
USE master;
GO

IF DB_ID('FastFoodPreorder') IS NULL
    /* Collation Vietnamese_CI_AS: sắp xếp và tìm kiếm tên món tiếng Việt đúng,
       không phân biệt hoa thường khi khách search menu. Phải đặt ngay lúc tạo DB. */
    CREATE DATABASE FastFoodPreorder COLLATE Vietnamese_CI_AS;
GO

/* KDS poll dữ liệu mỗi 2 giây (NFR-04). Không bật snapshot thì các câu SELECT liên tục
   của màn hình bếp sẽ chặn thao tác ghi của chính bếp. */
IF EXISTS (SELECT 1 FROM sys.databases WHERE name='FastFoodPreorder' AND is_read_committed_snapshot_on = 0)
    ALTER DATABASE FastFoodPreorder SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO

USE FastFoodPreorder;
GO


/* =============================================================================================
   2. XOÁ ĐỐI TƯỢNG CŨ  — theo chiều ngược phụ thuộc khoá ngoại
   (DROP TABLE không bị trigger chặn hard-delete cản, nên file luôn chạy lại được)
   ============================================================================================= */
DROP VIEW  IF EXISTS dbo.vw_OnTimeReady;
DROP VIEW  IF EXISTS dbo.vw_OrderReleaseState;

DROP TABLE IF EXISTS dbo.AuditLog;
DROP TABLE IF EXISTS dbo.PasswordResetToken;
DROP TABLE IF EXISTS dbo.EmailVerificationToken;
/* Năm bảng dưới đây đều trỏ tới Users và/hoặc Product, nên phải xoá trước hai bảng đó.
   Bảng con xoá trước bảng cha ngay cả khi đã có ON DELETE CASCADE: cascade chỉ áp dụng cho
   việc xoá DÒNG, không áp dụng cho DROP TABLE. */
DROP TABLE IF EXISTS dbo.Review;
DROP TABLE IF EXISTS dbo.OrderTemplateItem;
DROP TABLE IF EXISTS dbo.OrderTemplate;
DROP TABLE IF EXISTS dbo.Favourite;
DROP TABLE IF EXISTS dbo.RevenueTarget;
-- Hai bảng của bản cũ, đã bỏ cùng tính năng treo phiếu. Vẫn phải xoá ở đây, cùng lý do với
-- Shift bên dưới: cơ sở dữ liệu dựng bằng bản trước còn khoá ngoại PosHold → Users.
DROP TABLE IF EXISTS dbo.PosHoldItem;
DROP TABLE IF EXISTS dbo.PosHold;
/* Ba bảng của bếp. PrepTask trỏ tới Product và Users, OrderItemNote trỏ tới OrderItem, nên cả
   ba phải xoá trước những bảng chúng tham chiếu. Thêm bảng mới mà quên dòng DROP ở đây thì tệp
   này không chạy lại được lần thứ hai — xem TestDatabase.ensureReady, nơi lỗi đó bị bắt. */
DROP TABLE IF EXISTS dbo.KitchenNote;
DROP TABLE IF EXISTS dbo.OrderItemNote;
DROP TABLE IF EXISTS dbo.PrepTask;
DROP TABLE IF EXISTS dbo.KitchenIssue;
DROP TABLE IF EXISTS dbo.Notification;
DROP TABLE IF EXISTS dbo.PaymentTransaction;
DROP TABLE IF EXISTS dbo.Payment;
DROP TABLE IF EXISTS dbo.OrderNote;
DROP TABLE IF EXISTS dbo.OrderItem;
DROP TABLE IF EXISTS dbo.Orders;
-- Bảng của bản cũ, đã bỏ cùng tính năng giao ca. Vẫn phải xoá ở đây, nếu không thì cơ sở dữ
-- liệu dựng bằng bản trước sẽ kẹt: khoá ngoại Shift → Users chặn lệnh xoá Users bên dưới.
DROP TABLE IF EXISTS dbo.Shift;
DROP TABLE IF EXISTS dbo.CartItem;
DROP TABLE IF EXISTS dbo.Cart;
DROP TABLE IF EXISTS dbo.Product;
DROP TABLE IF EXISTS dbo.Category;
DROP TABLE IF EXISTS dbo.Users;
DROP TABLE IF EXISTS dbo.Role;
GO


/* =============================================================================================
   3. BẢNG

   Quy ước chung
     · Khoá chính  : <bảng>_id, INT IDENTITY (riêng AuditLog dùng BIGINT vì ghi nhiều nhất)
     · Tiền        : DECIMAL(12,2) — không dùng FLOAT/MONEY để tránh sai số khi cộng doanh thu
     · Thời gian   : DATETIME2(0) — đủ độ chính xác tới giây
     · Tiếng Việt  : NVARCHAR   ·  Mã / enum / email: VARCHAR
     · Enum        : VARCHAR + CHECK, không tạo bảng lookup, vì các tập giá trị đã được
                     khoá ở mục 18 và khớp 1-1 với enum Java trong com.fastfood.common.constant

   QUAN TRỌNG — thời gian: mọi mốc thời gian nghiệp vụ do TẦNG ỨNG DỤNG sinh và truyền xuống.
   DEFAULT SYSDATETIME() chỉ là lưới an toàn. Nếu Tomcat và SQL Server lệch giờ thì Scheduler
   sẽ đưa món vào bếp sai thời điểm và toàn bộ KPI đúng hẹn sai theo — lỗi rất khó truy.
   ============================================================================================= */

/* ------------------------------- NHÓM 1 — DANH MỤC & NGƯỜI DÙNG --------------------------- */

CREATE TABLE dbo.Role (
    role_id     INT IDENTITY(1,1) NOT NULL,
    name        VARCHAR(20)       NOT NULL,
    description NVARCHAR(200)     NULL,
    CONSTRAINT PK_Role      PRIMARY KEY (role_id),
    CONSTRAINT UQ_Role_name UNIQUE (name),
    CONSTRAINT CK_Role_name CHECK (name IN ('CUSTOMER','CASHIER','KITCHEN','ADMIN'))
);
GO

CREATE TABLE dbo.Users (
    user_id       INT IDENTITY(1,1) NOT NULL,
    full_name     NVARCHAR(100)     NOT NULL,
    email         VARCHAR(150)      NOT NULL,      -- dùng làm username đăng nhập
    phone         VARCHAR(20)       NULL,
    password_hash VARCHAR(255)      NOT NULL,      -- bcrypt; để 255 để sau này đổi thuật toán không phải sửa bảng
    role_id       INT               NOT NULL,      -- MVP: 1 User = 1 Role
    status        VARCHAR(20)       NOT NULL CONSTRAINT DF_Users_status    DEFAULT ('ACTIVE'),
    /* Bật khi quản trị viên đặt lại mật khẩu hộ. Mật khẩu lúc đó ít nhất hai người biết,
       nên tài khoản chỉ vào được trang tài khoản cho tới khi chủ nhân tự đặt lại. */
    must_change_password BIT        NOT NULL CONSTRAINT DF_Users_mustChangePw DEFAULT (0),
    /* Chủ tài khoản đã mở được hộp thư này hay chưa — bật lên khi họ bấm liên kết trong thư
       xác thực. Mặc định 0: ai đăng ký cũng chỉ mới GÕ ra một địa chỉ, chưa chứng minh gì.

       Vì sao cần cột này chứ không tin luôn địa chỉ đã gõ. Đơn đặt trước sống bằng email —
       báo đơn đã xác nhận, báo món đã sẵn sàng, và gửi liên kết lấy lại mật khẩu. Địa chỉ gõ
       nhầm một chữ thì cả ba đường đó rơi vào hư không, mà người dùng không hề biết cho tới
       lúc cần lấy lại tài khoản. Nặng hơn: gõ email của NGƯỜI KHÁC thì tài khoản này chiếm
       mất địa chỉ đó — chủ thật sau này đăng ký sẽ bị báo trùng email.

       Đây là trạng thái của tài khoản chứ không phải của một lần gửi thư, nên nó nằm ở đây
       chứ không nằm trong bảng mã. Xoá hết mã trong EmailVerificationToken cũng không làm
       một tài khoản đã xác thực quay về chưa xác thực. */
    email_verified BIT       NOT NULL CONSTRAINT DF_Users_emailVerified DEFAULT (0),
    created_at    DATETIME2(0)      NOT NULL CONSTRAINT DF_Users_createdAt DEFAULT (SYSDATETIME()),
    updated_at    DATETIME2(0)      NULL,
    CONSTRAINT PK_Users        PRIMARY KEY (user_id),
    CONSTRAINT UQ_Users_email  UNIQUE (email),
    CONSTRAINT FK_Users_Role   FOREIGN KEY (role_id) REFERENCES dbo.Role(role_id),
    -- Admin khoá tài khoản chứ không xoá, để giữ nguyên lịch sử đơn của người đó
    CONSTRAINT CK_Users_status CHECK (status IN ('ACTIVE','LOCKED'))
);
GO

CREATE TABLE dbo.Category (
    category_id   INT IDENTITY(1,1) NOT NULL,
    name          NVARCHAR(100)     NOT NULL,
    status        VARCHAR(20)       NOT NULL CONSTRAINT DF_Category_status DEFAULT ('ACTIVE'),
    display_order INT               NOT NULL CONSTRAINT DF_Category_order  DEFAULT (0),
    CONSTRAINT PK_Category        PRIMARY KEY (category_id),
    CONSTRAINT CK_Category_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
GO

/* Hai cột trạng thái tách riêng là CỐ Ý:
     status       — còn kinh doanh hay đã ngừng bán  (Admin quyết định, ít thay đổi)
     is_available — tạm hết hàng trong ngày          (nhân viên bật/tắt thường xuyên)
   Món chỉ được đặt khi ĐỦ CẢ BA: product ACTIVE + available + category ACTIVE.
   Ba điều kiện nằm ở hai bảng nên không CHECK được — mọi truy vấn menu bắt buộc join Category. */
CREATE TABLE dbo.Product (
    product_id   INT IDENTITY(1,1) NOT NULL,
    category_id  INT               NOT NULL,
    name         NVARCHAR(150)     NOT NULL,
    description  NVARCHAR(500)     NULL,
    price        DECIMAL(12,2)     NOT NULL,
    image_url    VARCHAR(255)      NULL,
    is_available BIT               NOT NULL CONSTRAINT DF_Product_available DEFAULT (1),
    status       VARCHAR(20)       NOT NULL CONSTRAINT DF_Product_status    DEFAULT ('ACTIVE'),
    created_at   DATETIME2(0)      NOT NULL CONSTRAINT DF_Product_createdAt DEFAULT (SYSDATETIME()),
    updated_at   DATETIME2(0)      NULL,
    CONSTRAINT PK_Product          PRIMARY KEY (product_id),
    CONSTRAINT FK_Product_Category FOREIGN KEY (category_id) REFERENCES dbo.Category(category_id),
    CONSTRAINT CK_Product_price    CHECK (price >= 0),
    CONSTRAINT CK_Product_status   CHECK (status IN ('ACTIVE','INACTIVE'))
);
GO

/* ------------------------------------ NHÓM 2 — GIỎ HÀNG ----------------------------------- */

/* Chỉ khách Online đã đăng nhập mới có giỏ trong DB.
   POS dựng giỏ tạm trong session của Cashier, không ghi xuống đây — nếu ghi thì mỗi khách
   vãng lai lại sinh một dòng rác. Giỏ ấy sống đúng bằng lần phục vụ một khách: thu tiền xong
   là dọn, thu ngân đóng trình duyệt cũng mất, và không có gì phải dọn rác về sau. */
CREATE TABLE dbo.Cart (
    cart_id    INT IDENTITY(1,1) NOT NULL,
    user_id    INT               NOT NULL,
    updated_at DATETIME2(0)      NOT NULL CONSTRAINT DF_Cart_updatedAt DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_Cart      PRIMARY KEY (cart_id),
    CONSTRAINT UQ_Cart_user UNIQUE (user_id),                    -- mỗi khách đúng một giỏ
    CONSTRAINT FK_Cart_Users FOREIGN KEY (user_id) REFERENCES dbo.Users(user_id)
);
GO

/* Bảng duy nhất trong toàn hệ thống được phép CASCADE DELETE:
   giỏ hàng là dữ liệu nháp, không phải dữ liệu giao dịch cần lưu vết. */
CREATE TABLE dbo.CartItem (
    cart_item_id INT IDENTITY(1,1) NOT NULL,
    cart_id      INT               NOT NULL,
    product_id   INT               NOT NULL,
    quantity     INT               NOT NULL,
    CONSTRAINT PK_CartItem         PRIMARY KEY (cart_item_id),
    CONSTRAINT FK_CartItem_Cart    FOREIGN KEY (cart_id)    REFERENCES dbo.Cart(cart_id) ON DELETE CASCADE,
    CONSTRAINT FK_CartItem_Product FOREIGN KEY (product_id) REFERENCES dbo.Product(product_id),
    CONSTRAINT CK_CartItem_qty     CHECK (quantity > 0),
    -- thêm lại món đã có trong giỏ thì cộng dồn số lượng, không tạo dòng thứ hai
    CONSTRAINT UQ_CartItem         UNIQUE (cart_id, product_id)
);
GO

/* ------------------------------------ NHÓM 3 — ĐƠN HÀNG ----------------------------------- */

/* Ba mốc thời gian dưới đây là phần cốt lõi phân biệt Online Pre-order với POS:

     pickup_time         CAM KẾT với khách — giờ khách sẽ đến lấy
     kitchen_release_at  KẾ HOẠCH          — tính một lần khi đơn được xác nhận, = pickup_time - 20'
     released_to_kds_at  THỰC TẾ           — Scheduler ghi đúng một lần; NULL nghĩa là bếp chưa thấy đơn

   Nhờ tách kế hoạch và thực tế, hệ thống vừa tránh làm món quá sớm, vừa đo được
   Scheduler chạy đúng giờ hay trễ (chênh lệch giữa hai cột).

   Cố ý KHÔNG tạo cột release_state hay is_overdue: hai giá trị đó suy ra được từ timestamps,
   lưu thêm chỉ tạo nguy cơ dữ liệu mâu thuẫn. Xem view ở mục 5. */
CREATE TABLE dbo.Orders (
    order_id           INT IDENTITY(1,1) NOT NULL,
    customer_id        INT               NULL,          -- NULL với khách vãng lai mua tại quầy
    created_by_user_id INT               NULL,          -- Cashier lập đơn POS
    order_source       VARCHAR(20)       NOT NULL,      -- ONLINE_PREORDER | POS
    total_amount       DECIMAL(12,2)     NOT NULL CONSTRAINT DF_Orders_total DEFAULT (0),
    order_status       VARCHAR(20)       NOT NULL,
    idempotency_key    VARCHAR(64)       NULL,          -- chặn tạo trùng đơn khi khách bấm Đặt hàng 2 lần

    -- Nhóm cột lịch hẹn & nhận hàng: chỉ dùng cho ONLINE_PREORDER
    pickup_time        DATETIME2(0)      NULL,
    kitchen_release_at DATETIME2(0)      NULL,
    released_to_kds_at DATETIME2(0)      NULL,
    pickup_code        VARCHAR(10)       NULL,          -- định dạng yyMMdd + 4 ký tự ngẫu nhiên

    -- Mốc vòng đời
    ready_at           DATETIME2(0)      NULL,          -- thời điểm món cuối cùng xong; là mẫu số của KPI đúng hẹn
    picked_up_at       DATETIME2(0)      NULL,
    handoff_by_user_id INT               NULL,          -- Cashier thực hiện giao món
    created_at         DATETIME2(0)      NOT NULL CONSTRAINT DF_Orders_createdAt DEFAULT (SYSDATETIME()),
    completed_at       DATETIME2(0)      NULL,
    expired_at         DATETIME2(0)      NULL,

    CONSTRAINT PK_Orders           PRIMARY KEY (order_id),
    CONSTRAINT FK_Orders_Customer  FOREIGN KEY (customer_id)        REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_Orders_CreatedBy FOREIGN KEY (created_by_user_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_Orders_Handoff   FOREIGN KEY (handoff_by_user_id) REFERENCES dbo.Users(user_id),

    -- chặn mọi kênh ngoài phạm vi MVP lọt vào hệ thống
    CONSTRAINT CK_Orders_source CHECK (order_source IN ('ONLINE_PREORDER','POS')),

    /* Không có trạng thái huỷ: đơn đã lập chỉ đi tới COMPLETED, hoặc dừng ở EXPIRED khi
       khách không hoàn tất thanh toán trong thời gian giữ chỗ. */
    CONSTRAINT CK_Orders_status CHECK (order_status IN
        ('PENDING_PAYMENT','CONFIRMED','PREPARING','READY','COMPLETED','EXPIRED')),

    -- đơn tại quầy thu tiền ngay nên không bao giờ ở trạng thái chờ thanh toán
    CONSTRAINT CK_Orders_pendingOnlineOnly CHECK
        (order_status <> 'PENDING_PAYMENT' OR order_source = 'ONLINE_PREORDER'),

    /* Đơn Online bắt buộc có giờ hẹn; đơn POS không được gán giờ hẹn. Viết theo dạng suy diễn
       như CK_Orders_onlineCustomer bên dưới, mỗi vế chỉ nói về đúng một kênh. Viết gộp thành
       "(là ONLINE và có giờ) OR (là POS và không giờ)" thì một kênh lạ như 'DELIVERY' cũng làm
       vế này sai, và SQL Server báo về giờ hẹn trong khi lỗi thật là kênh ngoài phạm vi. */
    CONSTRAINT CK_Orders_pickupTime CHECK
        ((order_source <> 'ONLINE_PREORDER' OR pickup_time IS NOT NULL)
     AND (order_source <> 'POS'             OR pickup_time IS NULL)),

    /* Đặt trước bắt buộc đăng nhập, nên đơn Online luôn có chủ. Không có ràng buộc này thì
       một đơn Online thiếu customer_id vẫn ghi được, và sau đó không ai tra được lịch sử của
       nó, không gửi được tin báo món sẵn sàng, cũng không kiểm tra được quyền xem đơn.
       Khách vãng lai mua tại quầy thì ngược lại: cố tình để trống, không bắt lập tài khoản. */
    CONSTRAINT CK_Orders_onlineCustomer CHECK
        (order_source <> 'ONLINE_PREORDER' OR customer_id IS NOT NULL),

    -- bắt lỗi tính sai lead time ngay tại DB: kế hoạch vào bếp phải trước giờ hẹn
    CONSTRAINT CK_Orders_releaseBeforePickup CHECK
        (kitchen_release_at IS NULL OR pickup_time IS NULL OR kitchen_release_at < pickup_time),

    -- mã nhận hàng chỉ sinh cho đơn Online; khách tại quầy nhận trực tiếp
    CONSTRAINT CK_Orders_pickupCodeOnline CHECK
        (pickup_code IS NULL OR order_source = 'ONLINE_PREORDER'),

    CONSTRAINT CK_Orders_total CHECK (total_amount >= 0)
);
GO

/* Mỗi dòng là một việc trên màn hình bếp.
   Vừa giữ khoá ngoại product_id vừa lưu bản sao tên và giá là có lý do:
     · thiếu bản sao  -> Admin sửa giá thì hoá đơn cũ đổi giá theo
     · thiếu khoá ngoại -> không gom nhóm được báo cáo món bán chạy
   Một dòng là một việc nguyên khối: không có trạng thái "xong 2/3 phần". */
CREATE TABLE dbo.OrderItem (
    order_item_id         INT IDENTITY(1,1) NOT NULL,
    order_id              INT               NOT NULL,
    product_id            INT               NOT NULL,
    product_name_snapshot NVARCHAR(150)     NOT NULL,
    unit_price            DECIMAL(12,2)     NOT NULL,
    quantity              INT               NOT NULL,
    item_status           VARCHAR(20)       NOT NULL CONSTRAINT DF_OrderItem_status DEFAULT ('WAITING'),
    assigned_to_user_id   INT               NULL,      -- đầu bếp đã nhận việc
    started_at            DATETIME2(0)      NULL,
    ready_at              DATETIME2(0)      NULL,

    /* Bàn giao món từ bếp ra quầy. Hai mốc chứ không phải một: bếp đặt món lên quầy và thu
       ngân cầm lấy là hai hành động của hai người, và khoảng giữa chúng chính là lúc món
       nằm chờ — đó mới là thứ màn hình quầy cần hiện ra.

       Không nhét vào item_status vì đây là trục song song: món vẫn ở trạng thái READY suốt
       cả hai bước. Thêm bậc vào chuỗi WAITING→PREPARING→READY sẽ kéo theo mọi chỗ đang đếm
       "món chưa xong" phải sửa lại, trong khi nghĩa của chúng không hề đổi. */
    handed_over_at        DATETIME2(0)      NULL,      -- bếp đưa món ra quầy
    handed_over_by        INT               NULL,
    received_at           DATETIME2(0)      NULL,      -- thu ngân nhận món tại quầy
    received_by           INT               NULL,

    CONSTRAINT PK_OrderItem          PRIMARY KEY (order_item_id),
    CONSTRAINT FK_OrderItem_Orders   FOREIGN KEY (order_id)            REFERENCES dbo.Orders(order_id),
    CONSTRAINT FK_OrderItem_Product  FOREIGN KEY (product_id)          REFERENCES dbo.Product(product_id),
    CONSTRAINT FK_OrderItem_Assignee FOREIGN KEY (assigned_to_user_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_OrderItem_HandedBy FOREIGN KEY (handed_over_by)      REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_OrderItem_RecvBy   FOREIGN KEY (received_by)         REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_OrderItem_qty      CHECK (quantity > 0),
    CONSTRAINT CK_OrderItem_price    CHECK (unit_price >= 0),
    CONSTRAINT CK_OrderItem_status   CHECK (item_status IN ('WAITING','PREPARING','READY')),
    /* Không thể nhận món chưa được bàn giao. Ràng buộc này chặn đứng thứ tự ngược ngay ở
       tầng dữ liệu, kể cả khi có ai đó sửa thẳng bằng câu lệnh SQL. */
    CONSTRAINT CK_OrderItem_handover CHECK (received_at IS NULL OR handed_over_at IS NOT NULL)
);
GO

/* ----------------------------------- NHÓM 4 — THANH TOÁN ---------------------------------- */

/* Một đơn có thể có nhiều lần thanh toán: khách thất bại rồi thử lại.
   Không ghi đè dòng cũ — mỗi lần thử là một dòng riêng để đối soát được với cổng thanh toán. */
CREATE TABLE dbo.Payment (
    payment_id     INT IDENTITY(1,1) NOT NULL,
    order_id       INT               NOT NULL,
    method         VARCHAR(20)       NOT NULL,     -- ONLINE_GATEWAY | CASH
    amount         DECIMAL(12,2)     NOT NULL,
    payment_status VARCHAR(20)       NOT NULL,     -- UNPAID | PENDING | PAID | FAILED
    attempt_no     INT               NOT NULL CONSTRAINT DF_Payment_attempt   DEFAULT (1),
    created_at     DATETIME2(0)      NOT NULL CONSTRAINT DF_Payment_createdAt DEFAULT (SYSDATETIME()),
    paid_at        DATETIME2(0)      NULL,         -- mốc dùng để tính doanh thu
    CONSTRAINT PK_Payment           PRIMARY KEY (payment_id),
    CONSTRAINT FK_Payment_Orders    FOREIGN KEY (order_id) REFERENCES dbo.Orders(order_id),
    CONSTRAINT CK_Payment_method    CHECK (method IN ('ONLINE_GATEWAY','CASH')),
    CONSTRAINT CK_Payment_status    CHECK (payment_status IN ('UNPAID','PENDING','PAID','FAILED')),
    CONSTRAINT CK_Payment_amount    CHECK (amount >= 0),
    CONSTRAINT CK_Payment_attemptNo CHECK (attempt_no >= 1),
    /* Lưu ý khi lập trình: tính attempt_no bằng MAX(attempt_no)+1 sẽ bị trùng nếu khách
       bấm thanh toán ở hai tab cùng lúc. Bắt lỗi trùng khoá rồi thử lại tối đa 3 lần. */
    CONSTRAINT UQ_Payment_attempt   UNIQUE (order_id, attempt_no)
);
GO

/* Nhật ký giao dịch với cổng thanh toán.
   external_transaction_id UNIQUE là chốt chặn quan trọng nhất về mặt tiền bạc: cổng thanh toán
   có thể gọi callback nhiều lần cho cùng một giao dịch, lần thứ hai sẽ bị lỗi trùng khoá và
   bị bỏ qua. Không kiểm tra bằng SELECT rồi mới INSERT — hai callback vào cùng lúc vẫn lọt. */
CREATE TABLE dbo.PaymentTransaction (
    transaction_id          INT IDENTITY(1,1) NOT NULL,
    payment_id              INT               NOT NULL,
    gateway                 VARCHAR(50)       NOT NULL,
    external_transaction_id VARCHAR(100)      NOT NULL,
    status                  VARCHAR(20)       NOT NULL,
    raw_reference           NVARCHAR(MAX)     NULL,     -- payload gốc, giữ để đối soát khi có tranh chấp
    created_at              DATETIME2(0)      NOT NULL CONSTRAINT DF_Txn_createdAt DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_Transaction         PRIMARY KEY (transaction_id),
    CONSTRAINT UQ_Transaction_extId   UNIQUE (external_transaction_id),
    CONSTRAINT FK_Transaction_Payment FOREIGN KEY (payment_id) REFERENCES dbo.Payment(payment_id)
);
GO

/* --------------------------------- NHÓM 5 — VẬN HÀNH & AUDIT ------------------------------ */

CREATE TABLE dbo.Notification (
    notification_id INT IDENTITY(1,1) NOT NULL,
    user_id         INT               NULL,       -- NULL với khách vãng lai không có tài khoản
    order_id        INT               NOT NULL,
    channel         VARCHAR(20)       NOT NULL CONSTRAINT DF_Notification_channel DEFAULT ('MOCK'),
    event_type      VARCHAR(30)       NOT NULL,   -- xem com.fastfood.common.constant.NotificationEvent
    content         NVARCHAR(MAX)     NULL,       -- tin báo sẵn sàng phải kèm giờ hẹn và mã nhận hàng
    status          VARCHAR(20)       NOT NULL CONSTRAINT DF_Notification_status  DEFAULT ('PENDING'),
    sent_at         DATETIME2(0)      NULL,
    /* Lúc khách mở tin ra đọc. NULL nghĩa là chưa đọc, và đó là toàn bộ cơ sở cho con số trên
       mục "Thông báo" ở thanh điều hướng. Không có cột này thì kênh gửi tin chỉ ghi vào một
       bảng không ai mở: bản chạy thử gửi qua kênh giả lập, nên màn hình trong ứng dụng chính
       là nơi duy nhất khách đọc được tin — mà một danh sách không đánh dấu đã đọc thì không
       nói được tin nào là mới. */
    read_at         DATETIME2(0)      NULL,
    CONSTRAINT PK_Notification         PRIMARY KEY (notification_id),
    CONSTRAINT FK_Notification_Users   FOREIGN KEY (user_id)  REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_Notification_Orders  FOREIGN KEY (order_id) REFERENCES dbo.Orders(order_id),
    /* Ba sự kiện sau đều là chuyện khách cần biết ngay mà không phải tự mở trang ra xem.
       Hai cái đầu là tin vui, cái cuối là tin xấu — thiếu nó thì đơn hết hiệu lực diễn ra
       im lặng, khách cứ đinh ninh mình vẫn còn suất và đến quầy mới biết. */
    CONSTRAINT CK_Notification_event   CHECK (event_type IN
        ('ORDER_CONFIRMED','ORDER_READY','ORDER_EXPIRED')),
    CONSTRAINT CK_Notification_status  CHECK (status  IN ('PENDING','SENT','FAILED')),
    CONSTRAINT CK_Notification_channel CHECK (channel IN ('EMAIL','MOCK'))
);
GO

/* Sự cố bếp chạy song song với trạng thái món, không kéo trạng thái lùi lại.
   Món hỏng phải làm lại vẫn nằm ở PREPARING cho tới khi xong, kèm một sự cố đang mở. */
CREATE TABLE dbo.KitchenIssue (
    issue_id      INT IDENTITY(1,1) NOT NULL,
    order_item_id INT               NOT NULL,
    created_by    INT               NOT NULL,
    issue_type    VARCHAR(30)       NOT NULL,   -- OUT_OF_STOCK | QUALITY | REMAKE | OTHER
    description   NVARCHAR(500)     NULL,
    status        VARCHAR(20)       NOT NULL CONSTRAINT DF_Issue_status    DEFAULT ('OPEN'),
    created_at    DATETIME2(0)      NOT NULL CONSTRAINT DF_Issue_createdAt DEFAULT (SYSDATETIME()),
    resolved_at   DATETIME2(0)      NULL,
    CONSTRAINT PK_KitchenIssue    PRIMARY KEY (issue_id),
    CONSTRAINT FK_Issue_OrderItem FOREIGN KEY (order_item_id) REFERENCES dbo.OrderItem(order_item_id),
    CONSTRAINT FK_Issue_Users     FOREIGN KEY (created_by)    REFERENCES dbo.Users(user_id),
    -- CANCELLED: sự cố báo nhầm, do chính người báo thu hồi khi còn đang mở. Giữ lại dòng
    -- thay vì xoá hẳn để nhật ký thao tác vẫn dẫn được về đúng bản ghi mà nó nhắc tới.
    CONSTRAINT CK_Issue_status    CHECK (status     IN ('OPEN','RESOLVED','CANCELLED')),
    /* COUNTER_REJECT: thu ngân từ chối nhận món bếp đưa ra (sai món, nguội, thiếu phần).
       Dùng chung bảng với sự cố bếp chứ không tạo bảng riêng: cùng là một món có vấn đề, cùng
       vòng đời mở → xử lý xong, và cùng phải hiện lên cả hai màn hình. Khác nhau ở người tạo,
       và cột created_by đã ghi lại điều đó. */
    CONSTRAINT CK_Issue_type      CHECK (issue_type IN ('OUT_OF_STOCK','QUALITY','REMAKE','OTHER','COUNTER_REJECT'))
);
GO

/* Kế hoạch chuẩn bị sẵn trong ca.

   Bếp đồ ăn nhanh không đợi có đơn mới bắt tay vào làm: gà được nướng sẵn theo dự đoán, rau
   cắt sẵn từ đầu ca. Đây là bảng DUY NHẤT mô tả công việc của bếp mà không bắt nguồn từ một
   đơn hàng cụ thể — vì vậy nó tham chiếu thẳng Product chứ không qua OrderItem như KitchenIssue.

   done_qty tách khỏi planned_qty chứ không ghi đè lên: cuối ca cần so kế hoạch với thực tế để
   lần sau đặt số cho sát. Ghi đè thì con số dự đoán biến mất và không còn gì để đối chiếu. */
CREATE TABLE dbo.PrepTask (
    prep_task_id INT IDENTITY(1,1) NOT NULL,
    product_id   INT               NOT NULL,
    prep_date    DATE              NOT NULL,
    planned_qty  INT               NOT NULL,
    done_qty     INT               NOT NULL CONSTRAINT DF_Prep_doneQty   DEFAULT (0),
    note         NVARCHAR(300)     NULL,
    created_by   INT               NOT NULL,
    created_at   DATETIME2(0)      NOT NULL CONSTRAINT DF_Prep_createdAt DEFAULT (SYSDATETIME()),
    updated_at   DATETIME2(0)      NULL,
    status       VARCHAR(20)       NOT NULL CONSTRAINT DF_Prep_status    DEFAULT ('PLANNED'),
    CONSTRAINT PK_PrepTask     PRIMARY KEY (prep_task_id),
    CONSTRAINT FK_Prep_Product FOREIGN KEY (product_id) REFERENCES dbo.Product(product_id),
    CONSTRAINT FK_Prep_Users   FOREIGN KEY (created_by) REFERENCES dbo.Users(user_id),
    -- CANCELLED: kế hoạch lập nhầm, người lập tự thu hồi. Giữ dòng lại thay vì xoá hẳn để
    -- nhật ký thao tác vẫn dẫn được về đúng bản ghi mà nó nhắc tới — cùng cách làm với KitchenIssue.
    CONSTRAINT CK_Prep_status  CHECK (status IN ('PLANNED','DONE','CANCELLED')),
    -- Chặn ngay tại đây chứ không chỉ ở tầng Service: số lượng vô lý lọt xuống sẽ làm báo cáo
    -- so sánh kế hoạch với thực tế trở thành vô nghĩa mà không ai nhận ra.
    CONSTRAINT CK_Prep_planned CHECK (planned_qty BETWEEN 1 AND 999),
    CONSTRAINT CK_Prep_done    CHECK (done_qty BETWEEN 0 AND 999)
);
GO

/* Ghi chú điều phối gắn với một đơn hàng.

   Của thu ngân, khác ghi chú chế biến của bếp ở chỗ nó nói về CẢ ĐƠN chứ không về một món:
   "khách gọi báo đến muộn 20 phút", "đơn này ưu tiên, khách đang đứng đợi". Hiện tại những
   thông tin đó chỉ tồn tại trong đầu người trực quầy — đổi ca là mất.

   Cùng lý do với ghi chú của bếp: không dính tiền, không đổi trạng thái đơn, không có dòng nhật
   ký nào trỏ về, nên xoá hẳn được và không cần ghi vào nhật ký thao tác. */
CREATE TABLE dbo.OrderNote (
    order_note_id INT IDENTITY(1,1) NOT NULL,
    order_id      INT               NOT NULL,
    author_id     INT               NOT NULL,
    content       NVARCHAR(500)     NOT NULL,
    created_at    DATETIME2(0)      NOT NULL CONSTRAINT DF_OrderNote_createdAt DEFAULT (SYSDATETIME()),
    updated_at    DATETIME2(0)      NULL,
    CONSTRAINT PK_OrderNote        PRIMARY KEY (order_note_id),
    CONSTRAINT FK_OrderNote_Order  FOREIGN KEY (order_id)  REFERENCES dbo.Orders(order_id),
    CONSTRAINT FK_OrderNote_Users  FOREIGN KEY (author_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_OrderNote_content CHECK (LEN(LTRIM(RTRIM(content))) > 0)
);
GO

/* Ghi chú chế biến gắn với một món cụ thể.

   Vì sao KHÔNG dùng lại KitchenIssue với issue_type = 'OTHER': số sự cố đang mở điều khiển bốn
   chỗ cảnh báo đỏ trên màn hình thu ngân (dải cảnh báo ở màn điều phối, thẻ đỏ từng món ở màn
   chi tiết đơn, thẻ trên màn bếp, và chữ ký polling của KDS). Một dòng ghi chú thường ngày như
   "khách dặn ít cay" mà đi vào đó sẽ hiện thành sự cố chưa xử lý ở cả bốn chỗ, và thu ngân mất
   niềm tin vào chính con số cảnh báo ấy.

   Sự cố có vòng đời (mở → xử lý xong / thu hồi) vì nó là chuyện phải giải quyết. Ghi chú thì
   không: nó chỉ là thông tin để lại cho người làm ca sau, nên xoá hẳn được và không cần ghi
   vào nhật ký thao tác — nhật ký dành cho việc làm đổi tiền hoặc đổi trạng thái đơn. */
CREATE TABLE dbo.OrderItemNote (
    note_id       INT IDENTITY(1,1) NOT NULL,
    order_item_id INT               NOT NULL,
    author_id     INT               NOT NULL,
    content       NVARCHAR(500)     NOT NULL,
    created_at    DATETIME2(0)      NOT NULL CONSTRAINT DF_ItemNote_createdAt DEFAULT (SYSDATETIME()),
    updated_at    DATETIME2(0)      NULL,
    CONSTRAINT PK_OrderItemNote    PRIMARY KEY (note_id),
    CONSTRAINT FK_ItemNote_Item    FOREIGN KEY (order_item_id) REFERENCES dbo.OrderItem(order_item_id),
    CONSTRAINT FK_ItemNote_Users   FOREIGN KEY (author_id)     REFERENCES dbo.Users(user_id),
    -- Ghi chú rỗng không mang thông tin gì mà vẫn chiếm một dòng trên màn hình.
    CONSTRAINT CK_ItemNote_content CHECK (LEN(LTRIM(RTRIM(content))) > 0)
);
GO

/* Sổ bàn giao ca bếp — thứ ca trước cần nói lại với ca sau.

   Không gắn với đơn hay món nào: nội dung là chuyện của cả ca ("lò số 2 nóng chậm, cần thêm 5
   phút", "hết khay giấy, đã báo quản lý"). Cùng lý do với ghi chú chế biến, bảng này xoá hẳn
   được và không đi vào nhật ký thao tác. */
CREATE TABLE dbo.KitchenNote (
    kitchen_note_id INT IDENTITY(1,1) NOT NULL,
    shift_date      DATE              NOT NULL,
    author_id       INT               NOT NULL,
    content         NVARCHAR(1000)    NOT NULL,
    created_at      DATETIME2(0)      NOT NULL CONSTRAINT DF_KitNote_createdAt DEFAULT (SYSDATETIME()),
    updated_at      DATETIME2(0)      NULL,
    CONSTRAINT PK_KitchenNote      PRIMARY KEY (kitchen_note_id),
    CONSTRAINT FK_KitNote_Users    FOREIGN KEY (author_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_KitNote_content  CHECK (LEN(LTRIM(RTRIM(content))) > 0)
);
GO

/* ------------------------------------ NHÓM 6 — QUẢN TRỊ ----------------------------------- */

/* Chỉ tiêu doanh thu theo kỳ, để bảng điều khiển có con số để so chứ không chỉ báo cáo suông.

   Bảng này CHỈ ĐƯỢC ĐỌC ở phía dưới: bảng điều khiển lấy chỉ tiêu ra rồi đặt cạnh doanh thu
   thuần đã tính sẵn. Không một câu lệnh tính tiền nào của hệ thống đi qua đây, nên đặt sai chỉ
   tiêu thì chỉ sai một dòng so sánh trên màn hình, không sai sổ sách.

   Doanh thu đem ra so PHẢI là netRevenue của DashboardKpi, không viết lại công thức lần hai:
   hai cách tính đặt cạnh nhau trên cùng màn hình sớm muộn cũng lệch nhau — đúng bài học hai mốc
   thời gian của báo cáo doanh thu.

   Xoá hẳn được, khác với đơn hàng. Chỉ tiêu là một dự định chưa thành việc gì: không có tiền đi
   qua, không bản ghi nào trỏ tới. Dấu vết vẫn còn vì dòng nhật ký TARGET_DELETED mang theo con
   số cũ trong old_value — tức là bản thân dòng nhật ký đã đủ, không cần giữ lại bản ghi rỗng. */
CREATE TABLE dbo.RevenueTarget (
    target_id     INT IDENTITY(1,1) NOT NULL,
    period_type   VARCHAR(10)       NOT NULL,   -- DAY | MONTH
    period_start  DATE              NOT NULL,   -- MONTH thì luôn là ngày mùng 1
    target_amount DECIMAL(12,2)     NOT NULL,
    note          NVARCHAR(500)     NULL,
    created_by    INT               NOT NULL,
    created_at    DATETIME2(0)      NOT NULL CONSTRAINT DF_Target_createdAt DEFAULT (SYSDATETIME()),
    updated_at    DATETIME2(0)      NULL,
    CONSTRAINT PK_RevenueTarget    PRIMARY KEY (target_id),
    CONSTRAINT FK_Target_Users     FOREIGN KEY (created_by) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_Target_period    CHECK (period_type IN ('DAY','MONTH')),
    CONSTRAINT CK_Target_amount    CHECK (target_amount > 0),
    /* Kỳ tháng phải bắt đầu từ mùng 1. Không chặn ở đây thì hai chỉ tiêu "tháng 8" đặt lệch
       ngày sẽ cùng tồn tại, và ràng buộc duy nhất bên dưới không bắt được. */
    CONSTRAINT CK_Target_monthStart CHECK (period_type <> 'MONTH' OR DAY(period_start) = 1)
);
GO

/* ------------------------------------ NHÓM 7 — CỦA RIÊNG KHÁCH ---------------------------- */

/* Món quen của khách. Ghi chú riêng là phần làm nên giá trị của bảng này: đánh dấu yêu thích
   thì chỉ có thêm và bỏ, còn "ít cay", "không hành", "nhiều đá" mới là thứ khách muốn lưu và
   muốn sửa lại. */
CREATE TABLE dbo.Favourite (
    favourite_id INT IDENTITY(1,1) NOT NULL,
    customer_id  INT               NOT NULL,
    product_id   INT               NOT NULL,
    note         NVARCHAR(255)     NULL,
    created_at   DATETIME2(0)      NOT NULL CONSTRAINT DF_Fav_createdAt DEFAULT (SYSDATETIME()),
    updated_at   DATETIME2(0)      NULL,
    CONSTRAINT PK_Favourite         PRIMARY KEY (favourite_id),
    CONSTRAINT FK_Fav_Users         FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT FK_Fav_Product       FOREIGN KEY (product_id)  REFERENCES dbo.Product(product_id),
    CONSTRAINT UQ_Fav_customer_prod UNIQUE (customer_id, product_id)
);
GO

/* Mẫu đặt nhanh — khách lưu lại một đơn đã đặt để lần sau nạp thẳng vào giỏ.

   Mẫu chỉ lưu MÃ MÓN và SỐ LƯỢNG, cố ý không lưu giá. Cùng nguyên tắc với giỏ hàng: giá luôn
   đọc mới tại thời điểm nạp, nên mẫu lưu từ tháng trước không bao giờ đưa giá cũ vào đơn mới. */
CREATE TABLE dbo.OrderTemplate (
    template_id INT IDENTITY(1,1) NOT NULL,
    customer_id INT               NOT NULL,
    name        NVARCHAR(100)     NOT NULL,
    created_at  DATETIME2(0)      NOT NULL CONSTRAINT DF_Tpl_createdAt DEFAULT (SYSDATETIME()),
    updated_at  DATETIME2(0)      NULL,
    CONSTRAINT PK_OrderTemplate    PRIMARY KEY (template_id),
    CONSTRAINT FK_Tpl_Users        FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_Tpl_name         CHECK (LEN(LTRIM(RTRIM(name))) > 0),
    -- trùng tên trong cùng một khách thì chính khách đó không phân biệt được hai mẫu
    CONSTRAINT UQ_Tpl_customer_name UNIQUE (customer_id, name)
);
GO

CREATE TABLE dbo.OrderTemplateItem (
    template_item_id INT IDENTITY(1,1) NOT NULL,
    template_id      INT               NOT NULL,
    product_id       INT               NOT NULL,
    quantity         INT               NOT NULL,
    CONSTRAINT PK_OrderTemplateItem   PRIMARY KEY (template_item_id),
    CONSTRAINT FK_TplItem_Template    FOREIGN KEY (template_id) REFERENCES dbo.OrderTemplate(template_id) ON DELETE CASCADE,
    CONSTRAINT FK_TplItem_Product     FOREIGN KEY (product_id)  REFERENCES dbo.Product(product_id),
    CONSTRAINT CK_TplItem_qty         CHECK (quantity > 0),
    CONSTRAINT UQ_TplItem             UNIQUE (template_id, product_id)
);
GO

/* Đánh giá món.

   Điều kiện "đã mua và đã nhận" KHÔNG chặn được ở tầng dữ liệu vì nó cần phép ghép qua Orders
   và OrderItem — ràng buộc CHECK trong SQL Server không nhìn sang bảng khác. Chốt chặn nằm ở
   ReviewService, và có bài kiểm thử riêng cho nó. Ở đây chỉ chặn được hai chuyện: điểm nằm
   trong khoảng 1–5, và mỗi khách một đánh giá cho một món.

   Ràng buộc duy nhất thứ hai mới là chốt chặn thật sự đáng giá: không có nó, một khách bấm gửi
   hai lần sẽ tự đẩy điểm trung bình của món lên. */
CREATE TABLE dbo.Review (
    review_id   INT IDENTITY(1,1) NOT NULL,
    product_id  INT               NOT NULL,
    customer_id INT               NOT NULL,
    rating      TINYINT           NOT NULL,
    comment     NVARCHAR(1000)    NULL,
    created_at  DATETIME2(0)      NOT NULL CONSTRAINT DF_Review_createdAt DEFAULT (SYSDATETIME()),
    updated_at  DATETIME2(0)      NULL,
    CONSTRAINT PK_Review              PRIMARY KEY (review_id),
    CONSTRAINT FK_Review_Product      FOREIGN KEY (product_id)  REFERENCES dbo.Product(product_id),
    CONSTRAINT FK_Review_Users        FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id),
    CONSTRAINT CK_Review_rating       CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT UQ_Review_cust_product UNIQUE (customer_id, product_id)
);
GO


/* Nhật ký thao tác, thiết kế dạng chung để ghi được mọi loại đối tượng mà không cần
   thêm bảng mỗi khi có nghiệp vụ mới. actor_id để trống nghĩa là do hệ thống tự thực hiện.
   Bắt buộc ghi lại: thanh toán thành công, tự động xác nhận, đưa đơn xuống bếp, bắt đầu và
   hoàn thành món, xác minh mã nhận hàng, giao món, huỷ và hoàn tiền, thao tác quản trị. */
CREATE TABLE dbo.AuditLog (
    audit_id    BIGINT IDENTITY(1,1) NOT NULL,
    actor_id    INT           NULL,
    entity_type VARCHAR(50)   NOT NULL,     -- ORDER | ORDER_ITEM | PAYMENT | PRODUCT | USER ...
    entity_id   VARCHAR(50)   NOT NULL,
    action      VARCHAR(50)   NOT NULL,     -- xem com.fastfood.common.constant.AuditAction
    old_value   NVARCHAR(MAX) NULL,
    new_value   NVARCHAR(MAX) NULL,
    created_at  DATETIME2(0)  NOT NULL CONSTRAINT DF_Audit_createdAt DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_AuditLog    PRIMARY KEY (audit_id),
    CONSTRAINT FK_Audit_Users FOREIGN KEY (actor_id) REFERENCES dbo.Users(user_id)
);
GO


/* Mã đặt lại mật khẩu cho luồng "Quên mật khẩu".

   Lưu BẢN BĂM của mã chứ không lưu mã. Mã nằm trong liên kết gửi cho người dùng, nên lưu
   nguyên văn nghĩa là bất kỳ ai đọc được bảng này — bản sao lưu, ảnh chụp màn hình lúc trình
   bày, một truy vấn SELECT vô tình — đều chiếm được tài khoản trong thời gian mã còn hạn.
   Băm rồi thì đọc được bảng cũng không dựng lại được liên kết.

   Ba cột used_at / expires_at / created_at là ba cách một mã hết hiệu lực, và cả ba đều cần:
   dùng rồi thì không dùng lại được (chặn phát lại liên kết trong hộp thư), quá hạn thì hỏng
   (thu hẹp khoảng thời gian một liên kết bị lộ còn giá trị), và created_at để đếm xem một
   tài khoản vừa xin bao nhiêu lần trong ít phút vừa rồi.

   Không xoá dòng khi đã dùng: bảng này chính là chỗ trả lời "tài khoản đó được đặt lại mật
   khẩu lúc nào, từ máy nào". */
CREATE TABLE dbo.PasswordResetToken (
    token_id     BIGINT IDENTITY(1,1) NOT NULL,
    user_id      INT          NOT NULL,
    token_hash   CHAR(64)     NOT NULL,     -- SHA-256 dạng chữ số mười sáu
    expires_at   DATETIME2(0) NOT NULL,
    used_at      DATETIME2(0) NULL,
    requested_ip VARCHAR(45)  NULL,         -- 45 ký tự: đủ cho địa chỉ IPv6 dạng dài nhất
    created_at   DATETIME2(0) NOT NULL CONSTRAINT DF_Reset_createdAt DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_PasswordResetToken PRIMARY KEY (token_id),
    CONSTRAINT UQ_Reset_tokenHash    UNIQUE (token_hash),
    CONSTRAINT FK_Reset_Users        FOREIGN KEY (user_id) REFERENCES dbo.Users(user_id)
);
GO


/* Mã xác thực địa chỉ email, cấp lúc đăng ký và mỗi lần người dùng bấm "gửi lại thư".

   Cùng một hình dáng với PasswordResetToken ở trên — băm, có hạn, dùng một lần, đếm được số
   lần xin — và cố ý KHÔNG gộp chung một bảng với nó dù bảy cột giống hệt nhau. Gộp lại thì
   phải thêm một cột phân loại, và mọi câu lệnh của cả hai luồng đều phải nhớ kèm điều kiện
   lọc theo cột đó. Quên đúng một chỗ là một mã xác thực email đổi được mật khẩu, hoặc ngược
   lại — hỏng theo kiểu im lặng và đúng ở chỗ tai hại nhất. Hai bảng riêng thì kiểu dữ liệu tự
   nó không cho phép nhầm.

   Hạn dài hơn mã đặt lại mật khẩu (24 giờ so với 15 phút) vì hai việc khác nhau: người quên
   mật khẩu đang ngồi chờ ngay đó, còn thư xác thực thì người ta hay để đến tối mới mở hộp
   thư. Đổi lại, mã này chỉ bật được một cờ chứ không mở được đường vào tài khoản, nên một mã
   nằm lâu cũng không đáng ngại bằng.

   Không xoá dòng khi đã dùng: đây là chỗ trả lời "địa chỉ này được xác thực lúc nào, từ máy
   nào" — câu hỏi sẽ được hỏi đúng vào lúc có tranh chấp ai là chủ một địa chỉ. */
CREATE TABLE dbo.EmailVerificationToken (
    token_id     BIGINT IDENTITY(1,1) NOT NULL,
    user_id      INT          NOT NULL,
    token_hash   CHAR(64)     NOT NULL,     -- SHA-256 dạng chữ số mười sáu
    expires_at   DATETIME2(0) NOT NULL,
    used_at      DATETIME2(0) NULL,
    requested_ip VARCHAR(45)  NULL,
    created_at   DATETIME2(0) NOT NULL CONSTRAINT DF_EmailVerify_createdAt DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_EmailVerificationToken PRIMARY KEY (token_id),
    CONSTRAINT UQ_EmailVerify_tokenHash  UNIQUE (token_hash),
    CONSTRAINT FK_EmailVerify_Users      FOREIGN KEY (user_id) REFERENCES dbo.Users(user_id)
);
GO


/* =============================================================================================
   4. INDEX

   Mỗi index dưới đây phục vụ một truy vấn có thật trong ứng dụng. Không tạo thêm "cho chắc":
   Orders và OrderItem là hai bảng bị ghi nhiều nhất, index thừa làm chậm chính luồng bếp.
   ============================================================================================= */

CREATE INDEX IX_Users_role ON dbo.Users(role_id);

-- Đếm số lần một tài khoản vừa xin đặt lại mật khẩu, để chặn kẻ dùng chức năng này làm
-- công cụ dội thư vào hộp thư người khác. Chạy ở mỗi lần bấm "Quên mật khẩu".
CREATE INDEX IX_Reset_user ON dbo.PasswordResetToken(user_id, created_at DESC);
GO

-- Cùng công dụng, cho nút "Gửi lại thư xác thực": đếm số lần vừa xin trong ít phút gần đây.
-- Không có index này thì mỗi lần bấm phải quét toàn bảng — mà bảng chỉ có mỗi lối ghi vào
-- là chính cái nút ấy, nên nó lớn nhanh đúng bằng tốc độ người ta bấm.
CREATE INDEX IX_EmailVerify_user ON dbo.EmailVerificationToken(user_id, created_at DESC);
GO

-- màn hình menu: chạy mỗi lần khách mở trang
CREATE INDEX IX_Product_category ON dbo.Product(category_id, status, is_available) INCLUDE (name, price);
GO

-- chặn tạo trùng đơn khi khách bấm Đặt hàng nhiều lần; lọc NULL để index luôn nhỏ
CREATE UNIQUE INDEX UX_Orders_idempotency ON dbo.Orders(idempotency_key) WHERE idempotency_key IS NOT NULL;
GO

-- Cashier tra mã khi khách đến nhận: vừa đảm bảo mã không trùng, vừa tra tức thì
CREATE UNIQUE INDEX UX_Orders_pickupCode ON dbo.Orders(pickup_code) WHERE pickup_code IS NOT NULL;
GO

/* Index quan trọng nhất của hệ thống.
   Scheduler quét mỗi 30 giây để tìm đơn tới giờ đưa xuống bếp. Nhờ lọc "chưa release",
   index chỉ chứa các đơn đang chờ nên kích thước gần như không đổi dù bảng Orders lớn dần
   theo năm tháng — đây là điều kiện để đưa đơn xuống bếp không trễ quá 60 giây. */
CREATE INDEX IX_Orders_release ON dbo.Orders(order_status, kitchen_release_at) WHERE released_to_kds_at IS NULL;
GO

-- màn hình đơn hàng của Cashier: 4 tab đơn tại quầy / đơn hẹn giờ / sẵn sàng giao / quá hạn
CREATE INDEX IX_Orders_status_source ON dbo.Orders(order_status, order_source, pickup_time);

-- lịch sử đơn của khách, luôn kèm điều kiện lọc theo chính chủ
CREATE INDEX IX_Orders_customer ON dbo.Orders(customer_id, created_at DESC);
GO

-- xem chi tiết đơn và tổng hợp trạng thái: chạy mỗi lần bếp đổi trạng thái một món
CREATE INDEX IX_OrderItem_order ON dbo.OrderItem(order_id) INCLUDE (item_status);

-- hàng chờ và việc đang làm trên màn hình bếp: truy vấn lại mỗi 2 giây
CREATE INDEX IX_OrderItem_kds ON dbo.OrderItem(item_status, assigned_to_user_id) INCLUDE (order_id);

/* Món bếp đã đưa ra quầy mà thu ngân chưa cầm. Cùng nguyên tắc lọc như IX_Orders_release:
   điều kiện WHERE giữ index chỉ chứa những món đang thật sự nằm chờ trên quầy — thường vài
   dòng — nên nó không lớn lên theo số món đã bán từ đầu tới giờ.
   Đáng có index riêng vì con số này còn hiện thành huy hiệu trên thanh điều hướng của thu
   ngân, tức là chạy ở MỌI trang thu ngân mở ra, không riêng màn hình quầy giao nhận. */
CREATE INDEX IX_OrderItem_counter ON dbo.OrderItem(handed_over_at, order_item_id)
    INCLUDE (order_id) WHERE received_at IS NULL;
GO

-- kiểm tra đã thanh toán chưa trước khi giao món
CREATE INDEX IX_Payment_order ON dbo.Payment(order_id, payment_status);

/* Báo cáo doanh thu quét theo khoảng ngày, lấy paid_at làm mốc. Tiền chỉ đi một chiều —
   không có đường hoàn ra — nên một index trên một mốc là đủ. */
CREATE INDEX IX_Payment_paidAt     ON dbo.Payment(paid_at)     INCLUDE (amount, method, payment_status);

CREATE INDEX IX_Transaction_payment ON dbo.PaymentTransaction(payment_id);
GO

CREATE INDEX IX_Notification_order ON dbo.Notification(order_id, event_type);
/* Hộp thông báo của khách: lấy tin mới nhất trước, và đếm số tin chưa đọc. Con số chưa đọc
   được tính lại ở MỌI lượt mở trang của khách để huy hiệu trên thanh điều hướng không bao giờ
   lệch, nên câu đếm đó phải rẻ — read_at nằm trong INCLUDE để đếm xong ngay trên index. */
CREATE INDEX IX_Notification_user  ON dbo.Notification(user_id, notification_id DESC)
    INCLUDE (read_at);
CREATE INDEX IX_Issue_item         ON dbo.KitchenIssue(order_item_id, status);
CREATE INDEX IX_Audit_entity       ON dbo.AuditLog(entity_type, entity_id, created_at DESC);
GO

/* Mỗi món chỉ có một dòng kế hoạch cho mỗi ngày. Hai đầu bếp cùng lập kế hoạch cho gà rán sẽ
   thành hai con số mâu thuẫn mà không ai biết cái nào đúng.

   Lọc bỏ dòng đã huỷ để lập nhầm rồi thu hồi vẫn lập lại được cho cùng món trong cùng ngày —
   không lọc thì một lần bấm nhầm khoá luôn món đó tới hết ngày. */
CREATE UNIQUE INDEX UX_PrepTask_date_product ON dbo.PrepTask(prep_date, product_id)
    WHERE status IN ('PLANNED','DONE');

-- Ghi chú điều phối: đọc cùng lúc với danh sách đơn trên màn điều phối của thu ngân.
CREATE INDEX IX_OrderNote_order ON dbo.OrderNote(order_id, created_at DESC);

-- Ghi chú của một món: đọc mỗi lần mở màn chi tiết món, mới nhất trước.
CREATE INDEX IX_ItemNote_item ON dbo.OrderItemNote(order_item_id, created_at DESC);

-- Sổ bàn giao: luôn đọc theo ngày, và ngày gần nhất là ngày hay mở nhất.
CREATE INDEX IX_KitNote_date ON dbo.KitchenNote(shift_date DESC, created_at DESC);
GO


/* Mỗi kỳ đúng một chỉ tiêu. Không có ràng buộc này thì hai chỉ tiêu cho tháng 8 cùng tồn tại,
   và bảng điều khiển lấy phải cái nào là chuyện ngẫu nhiên. */
CREATE UNIQUE INDEX UX_Target_period ON dbo.RevenueTarget(period_type, period_start);

/* Danh sách món quen của một khách, và câu hỏi "món này tôi đã đánh dấu chưa" chạy cho từng
   dòng trên thực đơn. Ràng buộc UQ_Fav_customer_prod đã sinh sẵn index cho cặp khoá đó, nên
   ở đây chỉ cần thêm cột ngày để danh sách sắp xếp không phải sắp lại. */
CREATE INDEX IX_Fav_customer ON dbo.Favourite(customer_id, created_at DESC) INCLUDE (product_id);

-- Mẫu đặt nhanh của một khách, đọc cùng lúc với lịch sử đơn.
CREATE INDEX IX_Tpl_customer ON dbo.OrderTemplate(customer_id, created_at DESC);

/* Đánh giá của một món: đọc mỗi lần mở trang chi tiết, kèm luôn điểm để tính trung bình mà
   không phải mở tới bảng gốc. */
CREATE INDEX IX_Review_product ON dbo.Review(product_id, created_at DESC) INCLUDE (rating);
GO


/* =============================================================================================
   5. VIEW

   Chỉ tạo view cho những giá trị suy ra mà công thức KHÔNG hiển nhiên, để mọi màn hình dùng
   chung một cách tính. Các báo cáo còn lại (doanh thu, món bán chạy, thống kê thanh toán)
   chỉ là GROUP BY thuần nên viết thẳng trong ReportDAO, không giấu vào DB.
   ============================================================================================= */

/* Trạng thái đưa xuống bếp và cờ quá hạn nhận hàng.
   Cả hai KHÔNG phải trạng thái đơn — chúng được suy ra từ các mốc thời gian.
   Khách đến muộn chỉ bị đánh dấu để nhân viên chú ý, tuyệt đối không tự huỷ hay tự hoàn tiền
   vì đơn đã được trả tiền trước.

   Cột is_overdue dùng đồng hồ của SQL Server nên chỉ dành cho hiển thị; khi tầng Service cần
   mốc thời gian chính xác thì tự tính lại bằng thời gian của ứng dụng. */
CREATE VIEW dbo.vw_OrderReleaseState
AS
SELECT  o.order_id,
        o.order_source,
        o.order_status,
        o.pickup_time,
        o.kitchen_release_at,
        o.released_to_kds_at,

        CASE WHEN o.released_to_kds_at IS NOT NULL THEN 'RELEASED_TO_KDS'
             WHEN o.order_status = 'CONFIRMED'     THEN 'SCHEDULED'
             ELSE 'NOT_RELEASED'
        END AS release_state,

        CASE WHEN o.order_status = 'READY'
              AND o.order_source = 'ONLINE_PREORDER'
              AND SYSDATETIME() > DATEADD(MINUTE, 30, o.pickup_time)
             THEN 1 ELSE 0
        END AS is_overdue,

        -- Scheduler chạy trễ bao nhiêu giây so với kế hoạch (yêu cầu: không quá 60)
        DATEDIFF(SECOND, o.kitchen_release_at, o.released_to_kds_at) AS release_delay_seconds
FROM    dbo.Orders o;
GO

/* Tỷ lệ món sẵn sàng đúng hẹn — chỉ số vận hành riêng của kênh đặt trước.
   Đây chính là thứ chứng minh Online Pre-order không chỉ khác ở giao diện: nó có cam kết
   thời gian đo được bằng số. */
CREATE VIEW dbo.vw_OnTimeReady
AS
SELECT  o.order_id,
        o.pickup_time,
        o.ready_at,
        o.released_to_kds_at,
        o.completed_at,
        CASE WHEN o.ready_at <= o.pickup_time THEN 1 ELSE 0 END AS is_on_time,
        DATEDIFF(MINUTE, o.released_to_kds_at, o.ready_at)        AS prep_lead_minutes
FROM    dbo.Orders o
WHERE   o.order_source = 'ONLINE_PREORDER'
  AND   o.ready_at IS NOT NULL;
GO


/* =============================================================================================
   6. TRIGGER

   Chỉ dùng trigger cho hai loại quy tắc: rule liên bảng có rủi ro tiền bạc, và rule chống
   mất dữ liệu giao dịch. Mọi quy tắc còn lại nằm ở tầng Service — nơi có thông tin về
   thời điểm hiện tại, người đang thao tác, và có thể trả thông báo tiếng Việt cho người dùng.
   ============================================================================================= */

/* Đơn đặt trước bắt buộc thanh toán online, không thu tiền mặt tại quầy.
   Không viết được thành CHECK vì phải đọc bảng Orders. Đây là quy tắc liên quan tới tiền
   nên cần một lớp chặn ngay tại DB, không phó thác hoàn toàn cho tầng ứng dụng. */
CREATE TRIGGER dbo.TR_Payment_OnlineGatewayOnly
ON dbo.Payment
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1
               FROM   inserted i
               JOIN   dbo.Orders o ON o.order_id = i.order_id
               WHERE  o.order_source = 'ONLINE_PREORDER' AND i.method = 'CASH')
    BEGIN
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW 50004, 'Don dat truoc chi cho phep thanh toan online, khong thu tien mat tai quay.', 1;
    END
END
GO

/* Không xoá vĩnh viễn dữ liệu giao dịch — cần giữ để đối soát và truy vết.
   Muốn "xoá" thì đổi trạng thái: đơn thành EXPIRED, thanh toán thành FAILED,
   sản phẩm và tài khoản thành INACTIVE/LOCKED.

   OrderItem cũng được chặn: khoá ngoại chỉ ngăn xoá đơn khi còn món, chứ không ngăn xoá
   chính dòng món — mà xoá món sẽ làm tổng tiền của đơn sai lệch mà không ai biết.

   Lưu ý: DROP TABLE và TRUNCATE không bị các trigger này chặn. Ở môi trường thật cần
   không cấp quyền ALTER cho tài khoản mà ứng dụng dùng để kết nối. */
CREATE TRIGGER dbo.TR_Orders_NoHardDelete ON dbo.Orders INSTEAD OF DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW 50020, 'Khong duoc xoa Orders. Dung order_status = EXPIRED.', 1;
END
GO

CREATE TRIGGER dbo.TR_OrderItem_NoHardDelete ON dbo.OrderItem INSTEAD OF DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW 50021, 'Khong duoc xoa OrderItem — se lam sai tong tien cua don.', 1;
END
GO

CREATE TRIGGER dbo.TR_Payment_NoHardDelete ON dbo.Payment INSTEAD OF DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW 50022, 'Khong duoc xoa Payment. Dung payment_status = FAILED.', 1;
END
GO

CREATE TRIGGER dbo.TR_Transaction_NoHardDelete ON dbo.PaymentTransaction INSTEAD OF DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW 50023, 'Khong duoc xoa PaymentTransaction — mat dau vet doi soat voi cong thanh toan.', 1;
END
GO

CREATE TRIGGER dbo.TR_AuditLog_NoHardDelete ON dbo.AuditLog INSTEAD OF DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW 50024, 'Khong duoc xoa AuditLog.', 1;
END
GO


/* =============================================================================================
   7. DỮ LIỆU MẪU
   ============================================================================================= */
SET NOCOUNT ON;
GO

/* --------------------------------------- Vai trò ------------------------------------------ */
INSERT INTO dbo.Role (name, description) VALUES
 ('CUSTOMER', N'Khách hàng đặt trước qua ứng dụng'),
 ('CASHIER',  N'Nhân viên bán hàng và giao món tại quầy'),
 ('KITCHEN',  N'Nhân viên bếp'),
 ('ADMIN',    N'Quản trị hệ thống');
GO

/* ------------------------------------- Người dùng -----------------------------------------
   Mật khẩu tất cả tài khoản: 123456
   Hash bcrypt cost 10, tiền tố $2a$ — thư viện jBCrypt 0.4 chỉ chấp nhận $2$ và $2a$.
   Nếu tự sinh hash bằng htpasswd (ra $2y$) thì phải đổi tiền tố thành $2a$, phần còn lại giữ nguyên.
   ------------------------------------------------------------------------------------------ */
DECLARE @pw VARCHAR(255) = '$2a$10$Kpqc42/3twhcoaPX6ezdS.HAMJB7suONI3r9eOk6nbhklIHTNqdO6';

/* email_verified = 1 cho cả bảy tài khoản mẫu. Bốn tài khoản nhân viên do quản trị viên tạo
   nên địa chỉ đã được xác nhận từ trước bằng đường khác; hai tài khoản khách coi như đã bấm
   liên kết trong thư từ lâu. Để 0 ở đây thì mọi kịch bản demo đặt hàng đều vấp phải chốt chặn
   "chưa xác thực email" ngay bước đầu, trong khi việc cần thử là luồng đặt hàng chứ không
   phải luồng xác thực — luồng đó thử bằng tài khoản tự đăng ký mới. */
INSERT INTO dbo.Users (full_name, email, phone, password_hash, role_id, status, email_verified)
SELECT v.full_name, v.email, v.phone, @pw, r.role_id, 'ACTIVE', 1
FROM (VALUES
        (N'Nguyễn Văn An',  'customer1@gmail.com',  '0901000001', 'CUSTOMER'),
        (N'Trần Thị Bình',  'customer2@gmail.com',  '0901000002', 'CUSTOMER'),
        (N'Lê Thu Ngân',    'cashier1@fastfood.vn', '0902000001', 'CASHIER'),
        (N'Vũ Minh Quang',  'cashier2@fastfood.vn', '0902000002', 'CASHIER'),
        (N'Phạm Hữu Phước', 'kitchen1@fastfood.vn', '0903000001', 'KITCHEN'),
        (N'Đỗ Thanh Hà',    'kitchen2@fastfood.vn', '0903000002', 'KITCHEN'),
        (N'Quản Trị Viên',  'admin@fastfood.vn',    '0904000001', 'ADMIN')
     ) AS v(full_name, email, phone, role_name)
JOIN dbo.Role r ON r.name = v.role_name;
GO

/* -------------------------------------- Danh mục ------------------------------------------ */
INSERT INTO dbo.Category (name, status, display_order) VALUES
 (N'Burger',       'ACTIVE',   1),
 (N'Gà rán',       'ACTIVE',   2),
 (N'Khoai tây',    'ACTIVE',   3),
 (N'Đồ uống',      'ACTIVE',   4),
 (N'Combo',        'ACTIVE',   5),
 (N'Món theo mùa', 'INACTIVE', 9);   -- danh mục đã tắt: món bên trong không được lên menu
GO

/* -------------------------------------- Sản phẩm ------------------------------------------
   Ba món cuối cố ý không đủ điều kiện lên menu, để kiểm chứng quy tắc lọc món ba tầng:
   tạm hết hàng · đã ngừng bán · thuộc danh mục đã tắt.

   ẢNH MÓN là link ngoài, trỏ thẳng vào ảnh thật đúng với tên món (Unsplash cho món fast food,
   Wikimedia Commons cho bánh trung thu) — không phải ảnh chỗ trống. Dự án không có thư mục
   upload ảnh nên image_url chỉ chứa URL; máy chạy demo cần vào được Internet thì ảnh mới hiện,
   mất mạng thì trang vẫn chạy, chỉ là ô ảnh trống. Tham số ?w=600&h=400&fit=crop bắt Unsplash
   cắt sẵn về đúng khổ thẻ món, khỏi tải ảnh gốc vài MB. Cả cột chỉ có VARCHAR(255) nên khi
   thay ảnh khác phải giữ URL ngắn — link dài hơn sẽ bị cắt cụt và ảnh chết.
   ------------------------------------------------------------------------------------------ */
INSERT INTO dbo.Product (category_id, name, description, price, image_url, is_available, status)
SELECT c.category_id, v.name, v.description, v.price, v.image_url, v.is_available, v.status
FROM (VALUES
    (N'Burger',       N'Burger Bò Phô Mai',   N'Bò nướng, phô mai cheddar, rau tươi',   55000., 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Burger',       N'Burger Gà Giòn',      N'Gà chiên giòn, sốt mayonnaise',         49000., 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Gà rán',       N'Gà Rán 1 Miếng',      N'Gà rán truyền thống',                   35000., 'https://images.unsplash.com/photo-1580217593608-61931cefc821?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Gà rán',       N'Gà Rán 3 Miếng',      N'Phần ba miếng gà rán',                  95000., 'https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Khoai tây',    N'Khoai Tây Chiên (M)', N'Khoai tây chiên cỡ vừa',                25000., 'https://images.unsplash.com/photo-1630431341973-02e1b662ec35?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Khoai tây',    N'Khoai Tây Chiên (L)', N'Khoai tây chiên cỡ lớn',                35000., 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Đồ uống',      N'Pepsi (M)',           N'Nước ngọt có ga',                       15000., 'https://images.unsplash.com/photo-1629203851122-3726ecdf080e?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Đồ uống',      N'Trà Đào Cam Sả',      N'Trà đào cam sả',                        29000., 'https://images.unsplash.com/photo-1499638673689-79a0b5115d87?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Combo',        N'Combo Burger Solo',   N'Burger bò, khoai tây cỡ vừa và Pepsi',  85000., 'https://images.unsplash.com/photo-1550547660-d9450f859349?w=600&h=400&fit=crop',    1, 'ACTIVE'),
    (N'Combo',        N'Combo Gia Đình',      N'Ba gà rán, hai khoai lớn và bốn nước', 259000., 'https://images.unsplash.com/photo-1614398751058-eb2e0bf63e53?w=600&h=400&fit=crop', 1, 'ACTIVE'),
    (N'Gà rán',       N'Gà Rán Cay Hàn Quốc', N'Sốt cay kiểu Hàn — tạm hết hàng',       45000., 'https://images.unsplash.com/photo-1575932444877-5106bee2a599?w=600&h=400&fit=crop', 0, 'ACTIVE'),
    (N'Đồ uống',      N'Nước Cam Ép',         N'Đã ngừng kinh doanh',                   30000., 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=600&h=400&fit=crop', 1, 'INACTIVE'),
    (N'Món theo mùa', N'Bánh Trung Thu',      N'Món theo mùa, ngoài menu thường',       60000., 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/B%C3%A1nh_trung_thu_2.JPG/500px-B%C3%A1nh_trung_thu_2.JPG', 1, 'ACTIVE')
   ) AS v(category_name, name, description, price, image_url, is_available, status)
JOIN dbo.Category c ON c.name = v.category_name;
GO

/* ------------------------------------- Giỏ hàng mẫu ---------------------------------------
   Hai giỏ, mỗi giỏ dựng sẵn một tình huống khác nhau của màn hình /cart:

     customer1 — giỏ sạch, bấm Đặt hàng là đi thẳng sang trang thanh toán.
     customer2 — trong giỏ có món vừa hết hàng. Đây mới là cái đáng dựng sẵn: giỏ hàng đọc
                 kèm is_available và trạng thái danh mục để chặn thanh toán và hiện dải cảnh
                 báo "bỏ món hết hàng ra". Nếu không có giỏ nào ở tình trạng đó thì nhánh
                 xử lý ấy chỉ chạy được sau khi tự tay đi tắt một món trong trang quản trị.

   Chỉ khách Online mới có giỏ trong DB; giỏ của thu ngân nằm trong session nên không ghi
   xuống đây. Giỏ là dữ liệu nháp — không có audit log, không có ràng buộc nào ràng nó với
   đơn hàng, và OrderService dọn sạch giỏ ngay sau khi đặt hàng thành công.
   ------------------------------------------------------------------------------------------ */
INSERT INTO dbo.Cart (user_id, updated_at)
SELECT u.user_id, DATEADD(MINUTE, -20, SYSDATETIME())
FROM   dbo.Users u
WHERE  u.email IN ('customer1@gmail.com', 'customer2@gmail.com');
GO

INSERT INTO dbo.CartItem (cart_id, product_id, quantity)
SELECT c.cart_id, p.product_id, v.quantity
FROM (VALUES
        ('customer1@gmail.com', N'Burger Bò Phô Mai',    1),
        ('customer1@gmail.com', N'Khoai Tây Chiên (M)',  2),
        ('customer2@gmail.com', N'Pepsi (M)',            2),
        -- món tạm hết hàng: chính là thứ làm nút thanh toán của customer2 bị khoá
        ('customer2@gmail.com', N'Gà Rán Cay Hàn Quốc',  1)
     ) AS v(email, product_name, quantity)
JOIN dbo.Users   u ON u.email   = v.email
JOIN dbo.Cart    c ON c.user_id = u.user_id
JOIN dbo.Product p ON p.name    = v.product_name;
GO

/* ------------------------------------- Đơn hàng mẫu ---------------------------------------
   11 đơn phủ đủ sáu trạng thái và các tình huống ngoại lệ quan trọng.
   Mọi mốc thời gian tính tương đối so với lúc chạy file, nên dữ liệu luôn hợp lý:
   đơn hẹn giờ luôn nằm ở tương lai, đơn quá hạn luôn nằm ở quá khứ.

   D1  Online  PENDING_PAYMENT  đang chờ khách thanh toán
   D2  Online  EXPIRED          thanh toán thất bại rồi quá hạn 15 phút
   D3  Online  CONFIRMED        đã trả tiền, đang chờ tới giờ mới đưa xuống bếp
   D4  Online  PREPARING        bếp đang làm dở
   D5  Online  READY            sẵn sàng, đúng hẹn — dùng để demo xác minh mã nhận hàng
   D6  Online  READY            quá giờ hẹn 40 phút mà khách chưa tới
   D7  Online  COMPLETED        đã giao đúng hẹn
   D8  POS     EXPIRED          khách lập đơn quét mã QR rồi bỏ đi, không ai trả tiền
   D9  POS     COMPLETED        khách tại quầy trả tiền mặt
   D10 POS     PREPARING        khách tại quầy quét mã trả tiền, đang có sự cố bếp
   D11 Online  COMPLETED        món xong TRỄ so với giờ hẹn — để tỷ lệ đúng hẹn ra 75%
                                thay vì 100%, mới kiểm chứng được công thức
   ------------------------------------------------------------------------------------------ */
DECLARE @now DATETIME2(0) = SYSDATETIME();
/* CONVERT kiểu 12 cho ra đúng yyMMdd, giống PickupCodeGenerator sinh mã lúc chạy thật.
   KHÔNG dùng FORMAT: đó là hàm CLR, mà Azure SQL Edge (bản chạy được trên máy Mac dùng chip
   ARM) không bật CLR. Khi đó FORMAT trả về NULL, @day thành NULL, và vì NULL nối chuỗi vẫn ra
   NULL nên TOÀN BỘ mã nhận hàng của dữ liệu mẫu biến mất — lặng lẽ, không có lỗi nào báo lên.
   Hậu quả: không đơn đặt trước nào giao được, vì giao món phải đối chiếu mã. */
DECLARE @day VARCHAR(6)   = CONVERT(VARCHAR(6), @now, 12);

DECLARE @cus1  INT = (SELECT user_id FROM dbo.Users WHERE email = 'customer1@gmail.com');
DECLARE @cus2  INT = (SELECT user_id FROM dbo.Users WHERE email = 'customer2@gmail.com');
DECLARE @cash1 INT = (SELECT user_id FROM dbo.Users WHERE email = 'cashier1@fastfood.vn');
DECLARE @kit1  INT = (SELECT user_id FROM dbo.Users WHERE email = 'kitchen1@fastfood.vn');
DECLARE @kit2  INT = (SELECT user_id FROM dbo.Users WHERE email = 'kitchen2@fastfood.vn');

DECLARE @pBurgerBo INT = (SELECT product_id FROM dbo.Product WHERE name = N'Burger Bò Phô Mai');
DECLARE @pBurgerGa INT = (SELECT product_id FROM dbo.Product WHERE name = N'Burger Gà Giòn');
DECLARE @pGa1      INT = (SELECT product_id FROM dbo.Product WHERE name = N'Gà Rán 1 Miếng');
DECLARE @pGa3      INT = (SELECT product_id FROM dbo.Product WHERE name = N'Gà Rán 3 Miếng');
DECLARE @pKhoaiM   INT = (SELECT product_id FROM dbo.Product WHERE name = N'Khoai Tây Chiên (M)');
DECLARE @pKhoaiL   INT = (SELECT product_id FROM dbo.Product WHERE name = N'Khoai Tây Chiên (L)');
DECLARE @pPepsi    INT = (SELECT product_id FROM dbo.Product WHERE name = N'Pepsi (M)');
DECLARE @pTraDao   INT = (SELECT product_id FROM dbo.Product WHERE name = N'Trà Đào Cam Sả');
DECLARE @pSolo     INT = (SELECT product_id FROM dbo.Product WHERE name = N'Combo Burger Solo');
DECLARE @pGiaDinh  INT = (SELECT product_id FROM dbo.Product WHERE name = N'Combo Gia Đình');

DECLARE @o INT, @pay INT, @item INT;

/* -- D1 · Online · chờ thanh toán · 70.000 ------------------------------------------------- */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key, pickup_time, created_at)
VALUES (@cus1, 'ONLINE_PREORDER', 70000, 'PENDING_PAYMENT', 'demo-0001', DATEADD(MINUTE,45,@now), DATEADD(MINUTE,-5,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status) VALUES
 (@o, @pBurgerBo, N'Burger Bò Phô Mai', 55000, 1, 'WAITING'),
 (@o, @pPepsi,    N'Pepsi (M)',         15000, 1, 'WAITING');
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at)
VALUES (@o, 'ONLINE_GATEWAY', 70000, 'PENDING', DATEADD(MINUTE,-5,@now));

/* -- D2 · Online · quá hạn thanh toán · 70.000 --------------------------------------------- */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key, pickup_time, created_at, expired_at)
VALUES (@cus2, 'ONLINE_PREORDER', 70000, 'EXPIRED', 'demo-0002', DATEADD(MINUTE,40,@now), DATEADD(MINUTE,-30,@now), DATEADD(MINUTE,-15,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status)
VALUES (@o, @pGa1, N'Gà Rán 1 Miếng', 35000, 2, 'WAITING');
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at)
VALUES (@o, 'ONLINE_GATEWAY', 70000, 'FAILED', DATEADD(MINUTE,-29,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, raw_reference, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0002-FAIL', 'FAILED', N'{"code":"51","message":"Insufficient funds"}', DATEADD(MINUTE,-29,@now));
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, new_value, created_at)
VALUES (NULL, 'ORDER', CAST(@o AS VARCHAR(50)), 'ORDER_EXPIRED', 'EXPIRED', DATEADD(MINUTE,-15,@now));

/* -- D3 · Online · đã xác nhận, chờ tới giờ vào bếp · 114.000 ------------------------------
   Trạng thái đặc trưng nhất của dự án: tiền đã thu, đơn đã nhận, nhưng bếp CHƯA thấy đơn
   để món không bị làm quá sớm. Giờ hẹn +60 phút nên kế hoạch vào bếp là +40 phút. */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key,
                        pickup_time, kitchen_release_at, pickup_code, created_at)
VALUES (@cus1, 'ONLINE_PREORDER', 114000, 'CONFIRMED', 'demo-0003',
        DATEADD(MINUTE,60,@now), DATEADD(MINUTE,40,@now), @day+'A1C7', DATEADD(MINUTE,-10,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status) VALUES
 (@o, @pSolo,   N'Combo Burger Solo', 85000, 1, 'WAITING'),
 (@o, @pTraDao, N'Trà Đào Cam Sả',    29000, 1, 'WAITING');
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 114000, 'PAID', DATEADD(MINUTE,-10,@now), DATEADD(MINUTE,-9,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, raw_reference, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0003', 'SUCCESS', N'{"code":"00","message":"Success"}', DATEADD(MINUTE,-9,@now));
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, new_value, created_at) VALUES
 (NULL, 'PAYMENT', CAST(@pay AS VARCHAR(50)), 'PAYMENT_PAID', 'PAID',      DATEADD(MINUTE,-9,@now)),
 (NULL, 'ORDER',   CAST(@o   AS VARCHAR(50)), 'AUTO_CONFIRM', 'CONFIRMED', DATEADD(MINUTE,-9,@now));

/* -- D4 · Online · bếp làm xong một món, còn một món · 130.000 ------------------------------
   Đơn hai món với đúng một món đã xong là trạng thái mở khoá nút "bàn giao ra quầy" của bếp —
   khối quan trọng nhất trên màn hình bếp, vì trước khi có nó món nấu xong không còn nằm trong
   danh sách nào. Thiếu đơn này thì khối đó rỗng ngay sau khi cài, không có gì để thử.

   Cả hai món đều của kitchen1, một xong một đang làm. Nhờ vậy màn hình bếp có đủ việc ở cả ba
   khối cùng lúc — thiếu một khối là mất một nút không thử được.

   Đơn vẫn ở PREPARING vì món kia chưa xong (BR-11), và đó chính là điều đáng thấy: món xong
   trước được mang ra quầy ngay thay vì nằm lại chờ món cuối rồi nguội. */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key,
                        pickup_time, kitchen_release_at, released_to_kds_at, pickup_code, created_at)
VALUES (@cus2, 'ONLINE_PREORDER', 130000, 'PREPARING', 'demo-0004',
        DATEADD(MINUTE,15,@now), DATEADD(MINUTE,-5,@now), DATEADD(MINUTE,-5,@now), @day+'B2D8', DATEADD(MINUTE,-30,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at, ready_at) VALUES
 (@o, @pGa3,    N'Gà Rán 3 Miếng',      95000, 1, 'READY',     @kit1, DATEADD(MINUTE,-4,@now), DATEADD(MINUTE,-1,@now)),
 (@o, @pKhoaiL, N'Khoai Tây Chiên (L)', 35000, 1, 'PREPARING', @kit1, DATEADD(MINUTE,-3,@now), NULL);
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 130000, 'PAID', DATEADD(MINUTE,-30,@now), DATEADD(MINUTE,-29,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0004', 'SUCCESS', DATEADD(MINUTE,-29,@now));
/* Chỉ ghi tay sự kiện mức đơn. Sự kiện mức món do khối suy ra ở cuối tệp sinh — viết tay ở
   đây phải tự nhập entity_id, mà mã món chỉ biết được sau khi INSERT nên rất dễ ghi nhầm
   thành mã đơn. Bản trước đã nhầm đúng như vậy: entity_type ghi ORDER_ITEM nhưng entity_id
   lại là mã đơn, bấm vào trong màn hình nhật ký thì ra nhầm bản ghi. */
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, new_value, created_at) VALUES
 (NULL,  'ORDER',      CAST(@o AS VARCHAR(50)), 'KDS_RELEASE', 'RELEASED',  DATEADD(MINUTE,-5,@now));

/* -- D5 · Online · sẵn sàng, đúng hẹn · 98.000 --------------------------------------------- */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key,
                        pickup_time, kitchen_release_at, released_to_kds_at, pickup_code, ready_at, created_at)
VALUES (@cus1, 'ONLINE_PREORDER', 98000, 'READY', 'demo-0005',
        DATEADD(MINUTE,10,@now), DATEADD(MINUTE,-10,@now), DATEADD(MINUTE,-10,@now), @day+'C3E9',
        DATEADD(MINUTE,-2,@now), DATEADD(MINUTE,-40,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at, ready_at)
VALUES (@o, @pBurgerGa, N'Burger Gà Giòn', 49000, 2, 'READY', @kit1, DATEADD(MINUTE,-9,@now), DATEADD(MINUTE,-2,@now));
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 98000, 'PAID', DATEADD(MINUTE,-40,@now), DATEADD(MINUTE,-39,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0005', 'SUCCESS', DATEADD(MINUTE,-39,@now));

/* -- D6 · Online · sẵn sàng nhưng khách chưa tới, đã quá hẹn 40 phút · 259.000 -------------
   Chỉ được đánh dấu để nhân viên chú ý; không tự huỷ, không tự hoàn tiền. */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key,
                        pickup_time, kitchen_release_at, released_to_kds_at, pickup_code, ready_at, created_at)
VALUES (@cus2, 'ONLINE_PREORDER', 259000, 'READY', 'demo-0006',
        DATEADD(MINUTE,-40,@now), DATEADD(MINUTE,-60,@now), DATEADD(MINUTE,-60,@now), @day+'D4F1',
        DATEADD(MINUTE,-45,@now), DATEADD(MINUTE,-90,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at, ready_at)
VALUES (@o, @pGiaDinh, N'Combo Gia Đình', 259000, 1, 'READY', @kit2, DATEADD(MINUTE,-58,@now), DATEADD(MINUTE,-45,@now));
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 259000, 'PAID', DATEADD(MINUTE,-90,@now), DATEADD(MINUTE,-89,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0006', 'SUCCESS', DATEADD(MINUTE,-89,@now));

/* -- D7 · Online · đã giao xong, đúng hẹn · 95.000 ----------------------------------------- */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key,
                        pickup_time, kitchen_release_at, released_to_kds_at, pickup_code,
                        ready_at, picked_up_at, handoff_by_user_id, created_at, completed_at)
VALUES (@cus1, 'ONLINE_PREORDER', 95000, 'COMPLETED', 'demo-0007',
        DATEADD(MINUTE,-120,@now), DATEADD(MINUTE,-140,@now), DATEADD(MINUTE,-140,@now), @day+'E5G2',
        DATEADD(MINUTE,-125,@now), DATEADD(MINUTE,-118,@now), @cash1, DATEADD(MINUTE,-155,@now), DATEADD(MINUTE,-118,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at, ready_at) VALUES
 (@o, @pBurgerBo, N'Burger Bò Phô Mai',   55000, 1, 'READY', @kit1, DATEADD(MINUTE,-138,@now), DATEADD(MINUTE,-127,@now)),
 (@o, @pKhoaiM,   N'Khoai Tây Chiên (M)', 25000, 1, 'READY', @kit1, DATEADD(MINUTE,-135,@now), DATEADD(MINUTE,-125,@now)),
 (@o, @pPepsi,    N'Pepsi (M)',           15000, 1, 'READY', @kit1, DATEADD(MINUTE,-134,@now), DATEADD(MINUTE,-130,@now));
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 95000, 'PAID', DATEADD(MINUTE,-155,@now), DATEADD(MINUTE,-154,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0007', 'SUCCESS', DATEADD(MINUTE,-154,@now));
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, new_value, created_at) VALUES
 (@cash1, 'ORDER', CAST(@o AS VARCHAR(50)), 'PICKUP_VERIFY_OK', @day+'E5G2', DATEADD(MINUTE,-119,@now)),
 (@cash1, 'ORDER', CAST(@o AS VARCHAR(50)), 'HANDOFF',          'COMPLETED', DATEADD(MINUTE,-118,@now));

/* -- D8 · Quầy · khách bỏ đi giữa chừng, không ai trả tiền · 85.000 -----------------------
   Đơn tại quầy phải lập TRƯỚC khi có tiền thì mới sinh được mã QR để khách quét, nên nó nằm
   ở trạng thái CONFIRMED chứ không phải PENDING_PAYMENT (CK_Orders_pendingOnlineOnly không
   cho đơn quầy chờ thanh toán). Khách quét xong rồi bỏ đi, quá 15 phút thì bộ hẹn giờ đóng
   đơn lại — xem ScheduleService.expireAbandonedCounterOrders. Không dọn thì đơn này treo
   mãi trong màn hình bán tại quầy mà bếp không bao giờ thấy. */
INSERT INTO dbo.Orders (created_by_user_id, order_source, total_amount, order_status,
                        created_at, expired_at)
VALUES (@cash1, 'POS', 85000, 'EXPIRED',
        DATEADD(MINUTE,-180,@now), DATEADD(MINUTE,-165,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status)
VALUES (@o, @pSolo, N'Combo Burger Solo', 85000, 1, 'WAITING');
/* Không có dòng PaymentTransaction: cổng thanh toán chỉ gọi lại khi khách bấm trả tiền,
   mà ở đây khách bỏ đi trước lúc đó. Khoản thu nằm mãi ở PENDING cho tới khi bộ hẹn giờ
   đánh dấu FAILED. */
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at)
VALUES (@o, 'ONLINE_GATEWAY', 85000, 'FAILED', DATEADD(MINUTE,-180,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, old_value, new_value, created_at) VALUES
 (NULL, 'PAYMENT', CAST(@pay AS VARCHAR(50)), 'PAYMENT_FAILED', 'PENDING', 'FAILED', DATEADD(MINUTE,-165,@now));

/* -- D9 · Quầy · đã giao xong, trả tiền mặt · 85.000 --------------------------------------- */
INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, order_status,
                        released_to_kds_at, ready_at, picked_up_at, handoff_by_user_id, created_at, completed_at)
VALUES (NULL, @cash1, 'POS', 85000, 'COMPLETED',
        DATEADD(MINUTE,-60,@now), DATEADD(MINUTE,-52,@now), DATEADD(MINUTE,-50,@now), @cash1,
        DATEADD(MINUTE,-61,@now), DATEADD(MINUTE,-50,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at, ready_at) VALUES
 (@o, @pGa1,   N'Gà Rán 1 Miếng', 35000, 2, 'READY', @kit2, DATEADD(MINUTE,-59,@now), DATEADD(MINUTE,-52,@now)),
 (@o, @pPepsi, N'Pepsi (M)',      15000, 1, 'READY', @kit2, DATEADD(MINUTE,-59,@now), DATEADD(MINUTE,-58,@now));
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'CASH', 85000, 'PAID', DATEADD(MINUTE,-61,@now), DATEADD(MINUTE,-60,@now));
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, new_value, created_at) VALUES
 (@cash1, 'ORDER', CAST(@o AS VARCHAR(50)), 'POS_CONFIRM', 'CONFIRMED', DATEADD(MINUTE,-60,@now)),
 (@cash1, 'ORDER', CAST(@o AS VARCHAR(50)), 'HANDOFF',     'COMPLETED', DATEADD(MINUTE,-50,@now));

/* -- D10 · Quầy · đang làm, khách quét mã trả tiền, có sự cố bếp · 80.000 ------------------ */
INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, order_status, released_to_kds_at, created_at)
VALUES (NULL, @cash1, 'POS', 80000, 'PREPARING', DATEADD(MINUTE,-6,@now), DATEADD(MINUTE,-7,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at)
VALUES (@o, @pBurgerBo, N'Burger Bò Phô Mai', 55000, 1, 'PREPARING', @kit2, DATEADD(MINUTE,-5,@now));
SET @item = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status)
VALUES (@o, @pKhoaiM, N'Khoai Tây Chiên (M)', 25000, 1, 'WAITING');
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 80000, 'PAID', DATEADD(MINUTE,-7,@now), DATEADD(MINUTE,-6,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0010-QR', 'SUCCESS', DATEADD(MINUTE,-6,@now));
INSERT INTO dbo.KitchenIssue (order_item_id, created_by, issue_type, description, status, created_at)
VALUES (@item, @kit2, 'QUALITY', N'Bánh bị cháy cạnh, cần làm lại', 'OPEN', DATEADD(MINUTE,-3,@now));

/* -- D11 · Online · đã giao xong nhưng món ra TRỄ so với giờ hẹn · 124.000 ----------------- */
INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, idempotency_key,
                        pickup_time, kitchen_release_at, released_to_kds_at, pickup_code,
                        ready_at, picked_up_at, handoff_by_user_id, created_at, completed_at)
VALUES (@cus2, 'ONLINE_PREORDER', 124000, 'COMPLETED', 'demo-0011',
        DATEADD(MINUTE,-165,@now), DATEADD(MINUTE,-185,@now), DATEADD(MINUTE,-185,@now), @day+'G7J4',
        DATEADD(MINUTE,-158,@now), DATEADD(MINUTE,-155,@now), @cash1, DATEADD(MINUTE,-200,@now), DATEADD(MINUTE,-155,@now));
SET @o = SCOPE_IDENTITY();
INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, quantity, item_status, assigned_to_user_id, started_at, ready_at) VALUES
 (@o, @pGa3,    N'Gà Rán 3 Miếng', 95000, 1, 'READY', @kit2, DATEADD(MINUTE,-183,@now), DATEADD(MINUTE,-158,@now)),
 (@o, @pTraDao, N'Trà Đào Cam Sả', 29000, 1, 'READY', @kit2, DATEADD(MINUTE,-182,@now), DATEADD(MINUTE,-176,@now));
INSERT INTO dbo.Payment (order_id, method, amount, payment_status, created_at, paid_at)
VALUES (@o, 'ONLINE_GATEWAY', 124000, 'PAID', DATEADD(MINUTE,-200,@now), DATEADD(MINUTE,-199,@now));
SET @pay = SCOPE_IDENTITY();
INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, status, created_at)
VALUES (@pay, 'MOCK', 'MOCK-TXN-0011', 'SUCCESS', DATEADD(MINUTE,-199,@now));

/* -- Bàn giao món giữa bếp và quầy cho dữ liệu mẫu -----------------------------------------
   Đặt ở cuối, sau khi mọi đơn đã có đủ món, để viết theo trạng thái đơn thay vì phải nhớ
   từng mã đơn. Bốn mức khác nhau là cố ý — mỗi mức mở ra một thao tác để thử.

   Mức "đã xong mà còn trong bếp" KHÔNG nằm ở đây mà nằm ngay tại D4: nó là món đầu tiên của
   một đơn hai món, nên không viết được bằng điều kiện theo trạng thái đơn. Xem lại mục 8.6
   sau khi sửa dữ liệu mẫu — mức nào rơi về 0 là một màn hình mất chỗ để thử. */

/* Đơn đã giao cho khách thì hiển nhiên đã đi qua cả hai bước. */
UPDATE oi
SET    handed_over_at = oi.ready_at, handed_over_by = oi.assigned_to_user_id,
       received_at    = oi.ready_at, received_by    = @cash1
FROM   dbo.OrderItem oi
JOIN   dbo.Orders    o ON o.order_id = oi.order_id
WHERE  o.order_status = 'COMPLETED';

/* Đơn đang chờ khách tới lấy: bếp đã đưa món ra quầy, quầy chưa xác nhận nhận.
   Đây là dữ liệu để thử thao tác "nhận món" ở màn hình quầy. */
UPDATE oi
SET    handed_over_at = oi.ready_at, handed_over_by = oi.assigned_to_user_id
FROM   dbo.OrderItem oi
JOIN   dbo.Orders    o ON o.order_id = oi.order_id
WHERE  o.order_status = 'READY';

/* Riêng D5 thì quầy đã nhận đủ món, nên thao tác giao cho khách thử được ngay mà không phải
   bấm nhận trước. Thiếu đơn này thì màn hình giao món không có gì để thử. */
UPDATE oi
SET    received_at = oi.ready_at, received_by = @cash1
FROM   dbo.OrderItem oi
JOIN   dbo.Orders    o ON o.order_id = oi.order_id
WHERE  o.idempotency_key = 'demo-0005';
GO


/* -- Kế hoạch chuẩn bị sẵn của bếp cho hôm nay ----------------------------------------------
   Ba dòng dựng ra ba tình huống khác nhau để màn hàng chờ có đủ thứ để nhìn: còn thiếu, làm
   dư, và đã chốt. Chỉ có dữ liệu "còn thiếu" thì cột trạng thái chỉ hiện được một màu, và
   người thử không biết hai màu kia trông ra sao.
   ------------------------------------------------------------------------------------------ */
DECLARE @kit1p INT = (SELECT user_id FROM dbo.Users WHERE email = 'kitchen1@fastfood.vn');
DECLARE @kit2p INT = (SELECT user_id FROM dbo.Users WHERE email = 'kitchen2@fastfood.vn');
DECLARE @today DATE = CAST(SYSDATETIME() AS DATE);

INSERT INTO dbo.PrepTask (product_id, prep_date, planned_qty, done_qty, note, created_by, status)
SELECT p.product_id, @today, v.planned, v.done, v.note, v.author, v.status
FROM   (VALUES
          (N'Gà Rán 3 Miếng', 40, 22, N'Nướng sẵn trước 11h, giữ trong tủ ấm', @kit1p, 'PLANNED'),
          (N'Trà Đào Cam Sả', 30, 34, N'Pha dư một mẻ vì trưa nay đông',        @kit2p, 'PLANNED'),
          (N'Khoai Tây Chiên (M)', 25, 25, N'Xong từ đầu ca',                    @kit1p, 'DONE')
       ) AS v(ten_mon, planned, done, note, author, status)
JOIN   dbo.Product p ON p.name = v.ten_mon;

/* -- Ghi chú của bếp -----------------------------------------------------------------------
   Một ghi chú gắn với món, hai dòng bàn giao ca của hai ngày khác nhau. Ngoài việc cho hai màn
   hình có nội dung ngay khi cài xong, chúng còn dựng ra phép so sánh đáng nhìn nhất: ghi chú
   KHÔNG làm tăng số cảnh báo đỏ trên màn thu ngân, khác hẳn sự cố bếp.
   ------------------------------------------------------------------------------------------ */
INSERT INTO dbo.OrderItemNote (order_item_id, author_id, content)
SELECT TOP 1 oi.order_item_id, @kit1p, N'Khách dặn ít cay, đã làm theo'
FROM   dbo.OrderItem oi
JOIN   dbo.Orders    o ON o.order_id = oi.order_id
WHERE  o.order_status = 'COMPLETED'
ORDER BY oi.order_item_id;

INSERT INTO dbo.KitchenNote (shift_date, author_id, content) VALUES
 (@today, @kit1p, N'Lò số 2 nóng chậm, mỗi mẻ gà cần thêm khoảng 5 phút. Đã báo quản lý.'),
 (DATEADD(DAY,-1,@today), @kit2p, N'Hết khay giấy cỡ lớn từ giữa ca chiều, tạm dùng khay nhỏ.');

/* Tên món gõ sai thì phép JOIN ở trên lặng lẽ bỏ qua dòng đó và không ai biết — đúng loại lỗi
   mà mục 8 sinh ra để bắt. Chặn ngay tại đây thay vì đợi tới lúc mở màn hình mới thấy thiếu. */
IF (SELECT COUNT(*) FROM dbo.PrepTask)       <> 3
OR (SELECT COUNT(*) FROM dbo.OrderItemNote)  <> 1
OR (SELECT COUNT(*) FROM dbo.KitchenNote)    <> 2
    THROW 50005, 'Du lieu mau cua bep thieu dong: kiem tra lai ten mon va dieu kien JOIN.', 1;
GO


/* -- Ghi chú điều phối của thu ngân ---------------------------------------------------------
   Các màn hình của thu ngân mở ra sẽ trống trơn nếu không có khối này.
   ------------------------------------------------------------------------------------------ */
DECLARE @cash1s INT = (SELECT user_id FROM dbo.Users WHERE email = 'cashier1@fastfood.vn');
DECLARE @adminId INT = (SELECT user_id FROM dbo.Users WHERE email = 'admin@fastfood.vn');
DECLARE @bay_gio DATETIME2(0) = SYSDATETIME();

INSERT INTO dbo.OrderNote (order_id, author_id, content)
SELECT TOP 1 o.order_id, @cash1s, N'Khách gọi báo đến muộn khoảng 15 phút, giữ món giúp.'
FROM   dbo.Orders o WHERE o.order_status = 'READY' ORDER BY o.order_id;

INSERT INTO dbo.OrderNote (order_id, author_id, content)
SELECT TOP 1 o.order_id, @cash1s, N'Đã gọi khách hai lần chưa được, thử lại sau 10 phút.'
FROM   dbo.Orders o WHERE o.order_status = 'READY' ORDER BY o.order_id DESC;

/* -- Chỉ tiêu doanh thu ---------------------------------------------------------------------
   Một chỉ tiêu tháng và một chỉ tiêu ngày, để bảng điều khiển hiện được cả hai mức cùng lúc.
   ------------------------------------------------------------------------------------------ */
INSERT INTO dbo.RevenueTarget (period_type, period_start, target_amount, note, created_by) VALUES
 ('MONTH', DATEFROMPARTS(YEAR(@bay_gio), MONTH(@bay_gio), 1), 150000000,
  N'Chỉ tiêu tháng, đã tính cả tuần cao điểm cuối tháng.', @adminId),
 ('DAY',   CAST(@bay_gio AS DATE), 5000000, N'Chỉ tiêu ngày thường trong tuần.', @adminId);

/* -- Của riêng khách: món quen, mẫu đặt nhanh, đánh giá -------------------------------------
   Hai dòng dưới đây cố ý trỏ vào món KHÔNG bán được, và đó là phần đáng giá nhất của khối này:
     · "Gà Rán Cay Hàn Quốc" đang tạm hết hàng  → dựng sẵn trạng thái "món quen đang hết"
     · "Nước Cam Ép" đã ngừng kinh doanh        → dựng sẵn cảnh báo khi nạp mẫu vào giỏ
   Toàn món còn bán thì hai nhánh cảnh báo đó không có gì để xem, và lỗi ở chúng sẽ chỉ lộ ra
   khi có khách thật gặp phải.
   ------------------------------------------------------------------------------------------ */
INSERT INTO dbo.Favourite (customer_id, product_id, note)
SELECT u.user_id, p.product_id, v.note
FROM  (VALUES
         ('customer1@gmail.com', N'Gà Rán Cay Hàn Quốc', N'Ít cay thôi, xin thêm một gói tương'),
         ('customer1@gmail.com', N'Trà Đào Cam Sả',      N'Không đá'),
         ('customer2@gmail.com', N'Combo Gia Đình',      NULL)
      ) AS v(email, ten_mon, note)
JOIN  dbo.Users   u ON u.email = v.email
JOIN  dbo.Product p ON p.name  = v.ten_mon;

INSERT INTO dbo.OrderTemplate (customer_id, name)
SELECT u.user_id, v.ten
FROM  (VALUES
         ('customer1@gmail.com', N'Bữa trưa quen'),
         ('customer1@gmail.com', N'Đặt cho cả phòng')
      ) AS v(email, ten)
JOIN  dbo.Users u ON u.email = v.email;

INSERT INTO dbo.OrderTemplateItem (template_id, product_id, quantity)
SELECT t.template_id, p.product_id, v.qty
FROM  (VALUES
         (N'Bữa trưa quen',    N'Burger Gà Giòn',      1),
         (N'Bữa trưa quen',    N'Khoai Tây Chiên (M)', 1),
         (N'Bữa trưa quen',    N'Pepsi (M)',           1),
         (N'Đặt cho cả phòng', N'Combo Gia Đình',      2),
         (N'Đặt cho cả phòng', N'Nước Cam Ép',         3)
      ) AS v(ten_mau, ten_mon, qty)
JOIN  dbo.OrderTemplate t ON t.name = v.ten_mau
JOIN  dbo.Product       p ON p.name = v.ten_mon;

/* Đánh giá SUY RA từ đơn đã hoàn tất chứ không viết tay, để dữ liệu mẫu tuân đúng chính quy tắc
   mà ReviewService áp: chỉ khách đã mua và đã nhận món mới đánh giá được. Viết tay thì rất dễ
   tạo ra một đánh giá mà chính hệ thống sẽ từ chối nếu người dùng thật bấm gửi. */
;WITH da_mua AS (
    SELECT DISTINCT o.customer_id, oi.product_id
    FROM   dbo.Orders    o
    JOIN   dbo.OrderItem oi ON oi.order_id = o.order_id
    WHERE  o.order_status = 'COMPLETED' AND o.customer_id IS NOT NULL
), xep AS (
    SELECT customer_id, product_id,
           ROW_NUMBER() OVER (ORDER BY customer_id, product_id) AS stt
    FROM   da_mua
)
INSERT INTO dbo.Review (product_id, customer_id, rating, comment)
SELECT x.product_id, x.customer_id,
       CASE x.stt WHEN 1 THEN 5 WHEN 2 THEN 4 ELSE 3 END,
       CASE x.stt WHEN 1 THEN N'Món nóng giòn, lấy đúng giờ hẹn. Sẽ đặt lại.'
                  WHEN 2 THEN N'Ngon, nhưng hôm nay mặn hơn mọi khi một chút.'
                  ELSE       N'Bình thường, được cái đặt trước nên không phải xếp hàng.' END
FROM   xep x WHERE x.stt <= 3;

/* Cùng loại chốt chặn với THROW 50005 ở trên. Dòng cuối kiểm thứ mà phép đếm thuần không
   thấy: đánh giá có sinh ra được từ đơn đã hoàn tất không — chỗ mà một thay đổi ở dữ liệu
   đơn hàng sẽ âm thầm làm rỗng. */
IF (SELECT COUNT(*) FROM dbo.OrderNote)         <> 2
OR (SELECT COUNT(*) FROM dbo.RevenueTarget)     <> 2
OR (SELECT COUNT(*) FROM dbo.Favourite)         <> 3
OR (SELECT COUNT(*) FROM dbo.OrderTemplate)     <> 2
OR (SELECT COUNT(*) FROM dbo.OrderTemplateItem) <> 5
OR (SELECT COUNT(*) FROM dbo.Review)             < 3
    THROW 50006, 'Du lieu mau cua thu ngan / quan tri / khach hang thieu dong: kiem tra lai ten mon, email va dieu kien JOIN.', 1;
GO


/* -- Tin đã gửi cho khách ------------------------------------------------------------------
   Suy ra từ các mốc thời gian của đơn, cùng lý do như khối nhật ký ngay bên dưới: viết tay
   ở từng đơn thì luôn sót. Bản trước chỉ có hai dòng cho mười một đơn — trong khi hai loại
   tin xấu (đơn bị huỷ, đơn hết hiệu lực) không có lấy một dòng nào, dù đó chính là hai loại
   tin mà thiếu chúng khách sẽ mất tiền hoặc mất đơn trong im lặng.

   Ba điều kiện dưới đây khớp đúng với NotificationService: chỉ đơn ONLINE_PREORDER và chỉ
   khi đơn có chủ. Khách mua tại quầy đứng ngay đó nên không có tin nào, và đó là lý do
   Notification.user_id để NULL được dù bảng vẫn bắt buộc có order_id.

   Nội dung dựng đúng theo mẫu câu của NotificationService — kèm số tiền, giờ hẹn và mã nhận
   hàng. MVP chưa có màn hình nào hiển thị các tin này (kênh gửi là MOCK, nội dung chỉ ra
   log), nên chúng phục vụ việc đối soát: tra thẳng bằng SQL để kiểm chứng rằng mỗi bước
   trong vòng đời đơn đều đã báo cho khách, và báo bằng đúng câu chữ mà mã nguồn sinh ra.
   ------------------------------------------------------------------------------------------ */
;WITH don AS (
    SELECT  o.order_id, o.customer_id, o.pickup_time, o.ready_at, o.expired_at,
            ma_nhan = ISNULL(o.pickup_code, '---'),
            /* 259000.00 -> "259.000 đ", giống MoneyUtil.format.
               Không dùng FORMAT vì Azure SQL Edge không bật CLR — xem ghi chú ở phần đơn hàng.
               CONVERT kiểu 1 cho ra "259,000.00": cắt ba ký tự cuối rồi đổi dấu phân nhóm. */
            tien = REPLACE(LEFT(CONVERT(VARCHAR(30), CAST(o.total_amount AS MONEY), 1),
                                LEN(CONVERT(VARCHAR(30), CAST(o.total_amount AS MONEY), 1)) - 3),
                           ',', '.') + N' đ',
            -- "dd/MM/yyyy HH:mm", giống DateTimeUtil.format
            gio_hen = CONVERT(VARCHAR(10), o.pickup_time, 103) + ' ' + CONVERT(VARCHAR(5), o.pickup_time, 108),
            da_tra  = (SELECT MIN(p.paid_at) FROM dbo.Payment p
                       WHERE p.order_id = o.order_id AND p.paid_at IS NOT NULL)
    FROM    dbo.Orders o
    WHERE   o.order_source = 'ONLINE_PREORDER' AND o.customer_id IS NOT NULL
), tin AS (
    /* Tiền vào là đơn được xác nhận (BR-07) — tin này mang mã nhận hàng tới cho khách */
    SELECT d.customer_id, d.order_id, 'ORDER_CONFIRMED' AS event_type, d.da_tra AS sent_at,
           N'Đơn #' + CAST(d.order_id AS NVARCHAR(20)) + N' đã thanh toán thành công ' + d.tien
         + N'. Giờ nhận hàng: ' + d.gio_hen + N'. Mã nhận hàng: ' + d.ma_nhan + N'.' AS content
    FROM   don d WHERE d.da_tra IS NOT NULL
    UNION ALL
    /* Món xong — nhắc lại giờ hẹn và mã, vì đây là tin khách mở ra lúc đang trên đường tới */
    SELECT d.customer_id, d.order_id, 'ORDER_READY', d.ready_at,
           N'Món của bạn đã sẵn sàng. Vui lòng đến quầy trước ' + d.gio_hen
         + N' và đưa mã ' + d.ma_nhan + N' để nhận hàng.'
    FROM   don d WHERE d.ready_at IS NOT NULL
    UNION ALL
    /* Hết hiệu lực vì quá hạn thanh toán — chưa từng thu tiền nên không có chuyện hoàn */
    SELECT d.customer_id, d.order_id, 'ORDER_EXPIRED', d.expired_at,
           N'Đơn #' + CAST(d.order_id AS NVARCHAR(20))
         + N' không được thanh toán trong thời gian giữ chỗ nên đã hết hiệu lực. '
         + N'Bạn không bị trừ tiền. Vui lòng đặt lại nếu vẫn muốn dùng bữa.'
    FROM   don d WHERE d.expired_at IS NOT NULL
)
INSERT INTO dbo.Notification (user_id, order_id, channel, event_type, content, status, sent_at)
SELECT t.customer_id, t.order_id, 'MOCK', t.event_type, t.content, 'SENT', t.sent_at
FROM   tin t
WHERE  t.sent_at IS NOT NULL;

/* Tin của đơn đã khép lại thì coi như khách đã đọc, tin của đơn còn đang chạy thì để nguyên
   chưa đọc. Đánh dấu đã đọc tất cả sẽ dựng ra một hộp thông báo phẳng lì: mở lên không thấy
   huy hiệu, không thấy dòng nào nổi bật, và cũng không thấy nút "đánh dấu đã đọc hết" làm gì.
   Để nguyên chưa đọc tất cả thì ngược lại — huy hiệu đếm cả những tin từ đơn đã giao xong từ
   đời nào. Chia theo trạng thái đơn cho ra đúng cảnh mà một khách dùng thật sẽ thấy. */
UPDATE  n
SET     n.read_at = DATEADD(MINUTE, 4, n.sent_at)
FROM    dbo.Notification n
JOIN    dbo.Orders o ON o.order_id = n.order_id
WHERE   o.order_status IN ('COMPLETED','EXPIRED');
GO


/* -- Nhật ký thao tác cho dữ liệu mẫu ------------------------------------------------------
   NFR-08 liệt kê các sự kiện bắt buộc phải có vết. Viết tay từng dòng ở mỗi đơn thì luôn sót:
   ở bản trước, 11 đơn đi hết vòng đời chỉ để lại 12 dòng — năm đơn đã giao khách mà chỉ hai
   dòng HANDOFF, và không dòng nào cho việc bàn giao bếp→quầy.

   Nên phần này SUY RA từ chính các mốc thời gian đã ghi trong đơn. Cách viết đó có hai cái
   được: không sót đơn nào, và nhật ký không bao giờ mâu thuẫn với dữ liệu nó mô tả.

   Chỉ chèn cho sự kiện CHƯA có dòng, để những dòng viết tay ở trên (mang thông tin riêng như
   lý do huỷ) được giữ nguyên. */

DECLARE @cash1 INT = (SELECT user_id FROM dbo.Users WHERE email = 'cashier1@fastfood.vn');

;WITH moi AS (
    /* Thanh toán thành công */
    SELECT NULL AS actor, 'PAYMENT' AS et, p.payment_id AS eid, 'PAYMENT_PAID' AS act,
           NULL AS ov, 'PAID' AS nv, p.paid_at AS t
    FROM dbo.Payment p WHERE p.paid_at IS NOT NULL
    UNION ALL
    /* Hệ thống tự xác nhận đơn đặt trước sau khi tiền vào (BR-07) — actor để trống */
    SELECT NULL, 'ORDER', o.order_id, 'AUTO_CONFIRM', 'PENDING_PAYMENT', 'CONFIRMED', o.created_at
    FROM dbo.Orders o
    WHERE o.order_source = 'ONLINE_PREORDER'
      AND o.order_status NOT IN ('PENDING_PAYMENT','EXPIRED')
    UNION ALL
    /* Thu ngân xác nhận đơn tại quầy (BR-10) */
    SELECT o.created_by_user_id, 'ORDER', o.order_id, 'POS_CONFIRM', NULL, 'CONFIRMED', o.created_at
    FROM dbo.Orders o WHERE o.order_source = 'POS'
    UNION ALL
    /* Bộ hẹn giờ đưa đơn xuống bếp — actor để trống là dấu hiệu việc do hệ thống làm */
    SELECT NULL, 'ORDER', o.order_id, 'KDS_RELEASE', NULL, 'RELEASED', o.released_to_kds_at
    FROM dbo.Orders o WHERE o.released_to_kds_at IS NOT NULL
    UNION ALL
    /* Bếp nhận việc và báo xong từng món */
    SELECT oi.assigned_to_user_id, 'ORDER_ITEM', oi.order_item_id, 'ITEM_START', 'WAITING', 'PREPARING', oi.started_at
    FROM dbo.OrderItem oi WHERE oi.started_at IS NOT NULL
    UNION ALL
    SELECT oi.assigned_to_user_id, 'ORDER_ITEM', oi.order_item_id, 'ITEM_READY', 'PREPARING', 'READY', oi.ready_at
    FROM dbo.OrderItem oi WHERE oi.ready_at IS NOT NULL
    UNION ALL
    /* Hai lần bàn giao của hai người khác nhau (BR-25) */
    SELECT oi.handed_over_by, 'ORDER_ITEM', oi.order_item_id, 'ITEM_HANDED_OVER', 'READY', 'AT_COUNTER', oi.handed_over_at
    FROM dbo.OrderItem oi WHERE oi.handed_over_at IS NOT NULL
    UNION ALL
    SELECT oi.received_by, 'ORDER_ITEM', oi.order_item_id, 'ITEM_RECEIVED', 'AT_COUNTER', 'RECEIVED', oi.received_at
    FROM dbo.OrderItem oi WHERE oi.received_at IS NOT NULL
    UNION ALL
    /* Cả đơn sẵn sàng — do backend tổng hợp từ các món (BR-11) */
    SELECT NULL, 'ORDER', o.order_id, 'ORDER_READY', 'PREPARING', 'READY', o.ready_at
    FROM dbo.Orders o WHERE o.ready_at IS NOT NULL
    UNION ALL
    /* Đối chiếu mã và giao món cho khách */
    SELECT o.handoff_by_user_id, 'ORDER', o.order_id, 'PICKUP_VERIFY_OK', NULL, o.pickup_code, o.picked_up_at
    FROM dbo.Orders o WHERE o.picked_up_at IS NOT NULL AND o.pickup_code IS NOT NULL
    UNION ALL
    SELECT o.handoff_by_user_id, 'ORDER', o.order_id, 'HANDOFF', 'READY', 'COMPLETED', o.picked_up_at
    FROM dbo.Orders o WHERE o.picked_up_at IS NOT NULL
    UNION ALL
    /* Kết thúc bất thường. Đơn đặt trước hết hiệu lực từ PENDING_PAYMENT; đơn tại quầy bị
       bỏ dở thì đi từ CONFIRMED, vì nó phải lập trước khi có tiền mới sinh được mã QR. */
    SELECT NULL, 'ORDER', o.order_id, 'ORDER_EXPIRED',
           CASE WHEN o.order_source = 'POS' THEN 'CONFIRMED' ELSE 'PENDING_PAYMENT' END,
           'EXPIRED', o.expired_at
    FROM dbo.Orders o WHERE o.expired_at IS NOT NULL
    UNION ALL
    /* Sự cố bếp */
    SELECT ki.created_by, 'ORDER_ITEM', ki.order_item_id, 'ISSUE_OPENED', NULL, ki.issue_type, ki.created_at
    FROM dbo.KitchenIssue ki
    UNION ALL
    SELECT ki.created_by, 'ORDER_ITEM', ki.order_item_id, 'ISSUE_RESOLVED', 'OPEN', 'RESOLVED', ki.resolved_at
    FROM dbo.KitchenIssue ki WHERE ki.resolved_at IS NOT NULL
)
INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, old_value, new_value, created_at)
SELECT m.actor, m.et, CAST(m.eid AS VARCHAR(50)), m.act, m.ov, m.nv, m.t
FROM   moi m
WHERE  m.t IS NOT NULL
  AND  NOT EXISTS (SELECT 1 FROM dbo.AuditLog a
                   WHERE a.entity_type = m.et
                     AND a.entity_id   = CAST(m.eid AS VARCHAR(50))
                     AND a.action      = m.act);
GO


/* =============================================================================================
   8. KIỂM TRA SAU KHI CHẠY
   Chạy xong file, đối chiếu các bảng kết quả bên dưới. Nếu khớp là database đã sẵn sàng.
   ============================================================================================= */

/* 8.1 · Số lượng bản ghi từng bảng — kỳ vọng cả 22 bảng dưới đây đều khác 0.
        Bảng nào bằng 0 nghĩa là có một màn hình chưa có dữ liệu để thử.
        Hai bảng mã (PasswordResetToken, EmailVerificationToken) không có mặt ở đây vì rỗng
        là đúng — xem ghi chú ở đầu file. */
SELECT 'Role' AS bang, COUNT(*) AS so_dong FROM dbo.Role
UNION ALL SELECT 'Users',              COUNT(*) FROM dbo.Users
UNION ALL SELECT 'Category',           COUNT(*) FROM dbo.Category
UNION ALL SELECT 'Product',            COUNT(*) FROM dbo.Product
UNION ALL SELECT 'Cart',               COUNT(*) FROM dbo.Cart
UNION ALL SELECT 'CartItem',           COUNT(*) FROM dbo.CartItem
UNION ALL SELECT 'Orders',             COUNT(*) FROM dbo.Orders
UNION ALL SELECT 'OrderItem',          COUNT(*) FROM dbo.OrderItem
UNION ALL SELECT 'OrderNote',          COUNT(*) FROM dbo.OrderNote
UNION ALL SELECT 'Payment',            COUNT(*) FROM dbo.Payment
UNION ALL SELECT 'PaymentTransaction', COUNT(*) FROM dbo.PaymentTransaction
UNION ALL SELECT 'Notification',       COUNT(*) FROM dbo.Notification
UNION ALL SELECT 'KitchenIssue',       COUNT(*) FROM dbo.KitchenIssue
UNION ALL SELECT 'PrepTask',           COUNT(*) FROM dbo.PrepTask
UNION ALL SELECT 'OrderItemNote',      COUNT(*) FROM dbo.OrderItemNote
UNION ALL SELECT 'KitchenNote',        COUNT(*) FROM dbo.KitchenNote
UNION ALL SELECT 'RevenueTarget',      COUNT(*) FROM dbo.RevenueTarget
UNION ALL SELECT 'Favourite',          COUNT(*) FROM dbo.Favourite
UNION ALL SELECT 'OrderTemplate',      COUNT(*) FROM dbo.OrderTemplate
UNION ALL SELECT 'OrderTemplateItem',  COUNT(*) FROM dbo.OrderTemplateItem
UNION ALL SELECT 'Review',             COUNT(*) FROM dbo.Review
UNION ALL SELECT 'AuditLog',           COUNT(*) FROM dbo.AuditLog;

/* 8.2 · Menu hiển thị cho khách — đây chính là truy vấn mà màn hình menu phải dùng.
        Kỳ vọng 10 món: ba món còn lại bị loại vì tạm hết hàng, đã ngừng bán,
        hoặc thuộc danh mục đã tắt. */
SELECT  c.name AS danh_muc, p.name AS mon, p.price AS gia
FROM    dbo.Product  p
JOIN    dbo.Category c ON c.category_id = p.category_id
WHERE   p.status = 'ACTIVE' AND p.is_available = 1 AND c.status = 'ACTIVE'
ORDER BY c.display_order, p.name;

/* 8.3 · Toàn bộ đơn kèm trạng thái đưa xuống bếp — kỳ vọng đủ 11 đơn, 7 trạng thái */
SELECT  o.order_id, o.order_source, o.order_status, rs.release_state, rs.is_overdue,
        o.total_amount, o.pickup_time, o.kitchen_release_at, o.released_to_kds_at, o.pickup_code
FROM    dbo.Orders o
JOIN    dbo.vw_OrderReleaseState rs ON rs.order_id = o.order_id
ORDER BY o.order_id;

/* 8.4 · Tỷ lệ món sẵn sàng đúng hẹn — kỳ vọng 4 đơn, đúng hẹn 3, tỷ lệ 75% */
SELECT  COUNT(*)        AS so_don_da_xong_mon,
        SUM(is_on_time) AS dung_hen,
        CAST(100.0 * SUM(is_on_time) / NULLIF(COUNT(*),0) AS DECIMAL(5,2)) AS ty_le_dung_hen,
        CAST(AVG(CAST(prep_lead_minutes AS FLOAT)) AS DECIMAL(6,2))        AS thoi_gian_lam_trung_binh
FROM    dbo.vw_OnTimeReady;

/* 8.5 · Đối soát tổng tiền: tổng của đơn phải bằng tổng các dòng món.
        Kỳ vọng KHÔNG có dòng nào trả về. */
SELECT  o.order_id, o.total_amount AS tong_luu, SUM(oi.unit_price * oi.quantity) AS tong_tinh_lai
FROM    dbo.Orders o
JOIN    dbo.OrderItem oi ON oi.order_id = o.order_id
GROUP BY o.order_id, o.total_amount
HAVING  o.total_amount <> SUM(oi.unit_price * oi.quantity);

/* 8.6 · Đường đi của món từ bếp ra quầy (BR-25). Dữ liệu mẫu cố ý dựng đủ BỐN mức, mỗi mức
        mở khoá một nút ở một màn hình khác nhau:
          "1 chua nau xong"        → bếp bấm nhận việc / báo xong ở /kitchen/queue
          "2 xong, con trong bep"  → bếp bấm BÀN GIAO ra quầy ở /kitchen/queue
          "3 dang cho o quay"      → thu ngân bấm NHẬN món ở /staff/counter
          "4 quay da nhan"         → giao cho khách ở /staff/order/detail
        Kỳ vọng cả bốn mức đều KHÁC 0. Mức nào bằng 0 thì màn hình tương ứng mở ra trống trơn.

        Tách mức 1 khỏi mức 2 là có lý do: gộp chung thì con số luôn khác 0 nhờ các món còn
        đang chờ nấu, và việc thiếu hẳn món "đã xong mà chưa ai mang ra" bị che mất — đúng
        cái lỗ mà bản kiểm tra đầu tiên đã để lọt. */
SELECT  CASE WHEN oi.received_at     IS NOT NULL   THEN N'4 quay da nhan'
             WHEN oi.handed_over_at  IS NOT NULL   THEN N'3 dang cho o quay'
             WHEN oi.item_status      = 'READY'    THEN N'2 xong, con trong bep'
             ELSE                                       N'1 chua nau xong' END AS vi_tri_mon,
        COUNT(*) AS so_mon
FROM    dbo.OrderItem oi
GROUP BY CASE WHEN oi.received_at     IS NOT NULL   THEN N'4 quay da nhan'
              WHEN oi.handed_over_at  IS NOT NULL   THEN N'3 dang cho o quay'
              WHEN oi.item_status      = 'READY'    THEN N'2 xong, con trong bep'
              ELSE                                       N'1 chua nau xong' END
ORDER BY vi_tri_mon;

/* 8.7 · Không món nào được nhận mà chưa qua bàn giao. CK_OrderItem_handover đã chặn ở tầng
        dữ liệu, câu này chỉ để nhìn thấy điều đó bằng mắt.
        Kỳ vọng KHÔNG có dòng nào trả về. */
SELECT  oi.order_item_id, oi.order_id, oi.handed_over_at, oi.received_at
FROM    dbo.OrderItem oi
WHERE   oi.received_at IS NOT NULL AND oi.handed_over_at IS NULL;

/* 8.8 · Tin đã gửi cho khách, đếm theo loại sự kiện.
        Kỳ vọng đủ CẢ BA loại. Thiếu ORDER_EXPIRED là dấu hiệu dữ liệu mẫu chỉ dựng đường đi
        thuận lợi — nhánh khách mất đơn vì quá hạn thanh toán không có gì để xem. Tin chỉ sinh
        cho đơn đặt trước; đơn tại quầy không có tin nào là đúng.

        Cột chua_doc phải có ít nhất một số khác 0, nếu không thì hộp thông báo của khách mở ra
        không còn tin nào mới và huy hiệu trên thanh điều hướng không bao giờ hiện. */
SELECT  n.event_type AS loai_tin, COUNT(*) AS so_tin,
        SUM(CASE WHEN n.read_at IS NULL THEN 1 ELSE 0 END) AS chua_doc,
        MAX(n.sent_at) AS gui_gan_nhat
FROM    dbo.Notification n
GROUP BY n.event_type
ORDER BY loai_tin;

/* 8.9 · Hai giỏ hàng mẫu — cột dat_hang_duoc phải có cả 1 và 0.
        Giỏ bị chặn (customer2) là giỏ có món vừa hết hàng: nó dựng sẵn dải cảnh báo và nút
        "bỏ món hết hàng ra" ở /cart, thứ không tự xuất hiện nếu mọi món đều còn bán. */
SELECT  u.email,
        COUNT(ci.cart_item_id) AS so_dong,
        SUM(ci.quantity)       AS tong_so_luong,
        SUM(ci.quantity * p.price) AS tam_tinh,
        CASE WHEN SUM(CASE WHEN p.is_available = 1 AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                           THEN 0 ELSE 1 END) = 0
             THEN 1 ELSE 0 END AS dat_hang_duoc
FROM    dbo.Cart     ca
JOIN    dbo.Users    u  ON u.user_id     = ca.user_id
JOIN    dbo.CartItem ci ON ci.cart_id    = ca.cart_id
JOIN    dbo.Product  p  ON p.product_id  = ci.product_id
JOIN    dbo.Category c  ON c.category_id = p.category_id
GROUP BY u.email
ORDER BY u.email;

/* 8.10 · Giờ của SQL Server — phải khớp giờ máy chạy Tomcat, lệch dưới 5 giây.
         Lệch giờ sẽ khiến đơn được đưa xuống bếp sai thời điểm. */
SELECT SYSDATETIME() AS gio_sql_server, DB_NAME() AS database_hien_tai;
GO


/* 8.11 · Bảng mã của chính file này — kỳ vọng 0 dòng và KHÔNG có thông báo lỗi.

   File này là UTF-8 không BOM. Công cụ nào đọc nó theo bảng mã khác thì mọi chuỗi N'...'
   tiếng Việt vào cơ sở dữ liệu ở dạng hỏng, mà máy chủ không hề báo lỗi: cột NVARCHAR nhận
   tuốt. Lỗi chỉ lộ ra sau đó trên trình duyệt, và chỉ trên máy đã nạp sai — cùng một mã
   nguồn, máy này chữ đúng máy kia chữ hỏng, rất mất công truy.

   Cách nạp đã biết là hỏng:
     · SSMS mở file rồi F5 — SSMS đọc file không BOM theo bảng mã ANSI của máy.
     · sqlcmd.exe cũ (bản đi kèm SQL Server) thiếu tham số -f 65001.
   Cách nạp đúng: xem database/README.md.

   Phép thử không so với một chuỗi viết sẵn trong file — đọc sai thì cả dữ liệu lẫn chuỗi
   đem so đều sai giống hệt nhau nên vẫn khớp. Thay vào đó tìm dấu vết chỉ xuất hiện khi
   đọc sai: Ã (195), Ä (196), Æ (198) là ba ký tự đầu của các cặp mà UTF-8 biến thành khi bị
   đọc theo Latin-1/CP1252, và không ký tự nào trong ba cái đó có mặt trong tiếng Việt.
   NCHAR() dựng chúng từ số nên bản thân phép thử miễn nhiễm với chuyện đọc sai. */
WITH chu_tieng_viet AS (
    SELECT 'Product.name'   AS cot, name      AS gia_tri FROM dbo.Product
    UNION ALL SELECT 'Category.name',  name              FROM dbo.Category
    UNION ALL SELECT 'Users.full_name', full_name        FROM dbo.Users
    UNION ALL SELECT 'Role.description', description     FROM dbo.Role
)
SELECT cot, gia_tri
FROM   chu_tieng_viet
WHERE  gia_tri LIKE '%' + NCHAR(195) + '%'
   OR  gia_tri LIKE '%' + NCHAR(196) + '%'
   OR  gia_tri LIKE '%' + NCHAR(198) + '%';

IF EXISTS (SELECT 1 FROM dbo.Product
           WHERE name LIKE '%' + NCHAR(195) + '%'
              OR name LIKE '%' + NCHAR(196) + '%'
              OR name LIKE '%' + NCHAR(198) + '%')
    RAISERROR (N'8.11 HONG BANG MA: file da duoc nap bang cong cu doc sai bang ma, chu tieng Viet trong du lieu mau da hong. Xoa database roi nap lai theo dung huong dan trong database/README.md.', 16, 1);
GO
