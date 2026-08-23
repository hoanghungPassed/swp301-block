# Fast Food Pre-order Pickup & POS

Hệ thống đặt món trước có hẹn giờ và bán hàng tại quầy cho cửa hàng đồ ăn nhanh.
Đồ án môn SWP301.

Điểm khác biệt so với một trang bán hàng thông thường: đơn đặt trước **không** được đưa
xuống bếp ngay khi thanh toán. Hệ thống giữ đơn lại và chỉ đẩy xuống bếp trước giờ khách
hẹn 20 phút — đủ để món vừa xong khi khách tới, không sớm tới mức nguội.

| Tài liệu | Nội dung |
|---|---|
| [docs/preview-2 (1).html](docs/preview-2%20(1).html) | Phân tích nghiệp vụ |
| [docs/STRUCTURE.md](docs/STRUCTURE.md) | Kiến trúc, luồng nghiệp vụ, bảng địa chỉ |
| [docs/DATABASE-PLAN.md](docs/DATABASE-PLAN.md) | Thiết kế cơ sở dữ liệu và lý do từng quyết định |
| [database/README.md](database/README.md) | Cách dựng cơ sở dữ liệu |
| [docs/GIT.md](docs/GIT.md) | Quy tắc làm việc trên Git — đọc trước khi commit |

## Công nghệ

Java 17 · Servlet 4 · JSTL · SQL Server (JDBC thuần) · Tomcat 9 · Maven · HikariCP · bcrypt · ZXing

Không dùng framework: toàn bộ tầng điều khiển, truy cập dữ liệu và giao diện viết tay
để thấy rõ cách MVC ba tầng vận hành.

## Chạy thử

```bash
# 1. Dựng cơ sở dữ liệu (tạo bảng + dữ liệu mẫu, chạy lại được nhiều lần)
#    sqlcmd.exe cũ trên Windows phải thêm -f 65001, không thì chữ tiếng Việt vào database
#    ở dạng hỏng — xem database/README.md
sqlcmd -S localhost -U sa -P '<mật khẩu>' -C -i database/FastFoodPreorder.sql

# 2. Sửa src/main/resources/db.properties cho khớp máy chủ SQL Server của bạn

# 3. Đóng gói và triển khai
mvn clean package
cp target/fastfood.war $TOMCAT_HOME/webapps/
```

Mở http://localhost:8080/fastfood/

## Tài khoản mẫu

Mật khẩu tất cả: **`123456`**

| Vai trò | Email | Vào được gì |
|---|---|---|
| Khách hàng | customer1@gmail.com | Thực đơn, giỏ hàng, đặt trước, theo dõi đơn |
| Thu ngân | cashier1@fastfood.vn | Bán tại quầy, điều phối đơn, giao món |
| Bếp | kitchen1@fastfood.vn | Hàng chờ, nhận món, báo sự cố |
| Quản trị | admin@fastfood.vn | Báo cáo, quản lý món và tài khoản, nhật ký |

## Đường đi thử nhanh

1. Đăng nhập `customer1`, thêm món vào giỏ, đặt trước với giờ hẹn **sau 35 phút**.
2. Ở trang thanh toán giả lập, bấm **Thanh toán thành công** → đơn được xác nhận và
   sinh mã nhận hàng. Bấm nút đó **lần nữa** để thấy hệ thống nhận ra giao dịch trùng
   và không ghi nhận tiền thêm lần thứ hai.
3. Đăng nhập `kitchen1` → **hàng chờ trống**, vì đơn chưa tới giờ vào bếp.
   Đây chính là cơ chế giữ cho món không bị làm sớm.
4. Muốn thấy ngay không phải chờ: đặt một đơn với giờ hẹn sau đúng 35 phút, rồi đợi
   khoảng 15 phút — hoặc chỉnh `business.kitchen.prepLeadMinutes` trong
   `src/main/resources/app.properties` lên 30 rồi khởi động lại.
5. Bếp nhận món → đánh dấu xong → đơn tự chuyển sang sẵn sàng và khách được báo.
6. Đăng nhập `cashier1` → tab **Chờ khách tới lấy** → mở đơn, nhập mã khách đưa để giao món.
   Thử nhập sai mã: hệ thống từ chối và ghi lại lần thử sai vào nhật ký.

Dữ liệu mẫu có sẵn 11 đơn phủ đủ bảy trạng thái, gồm cả đơn khách đến muộn và đơn món
ra trễ hẹn, kèm giỏ hàng và tin đã gửi cho khách — cả 13 bảng đều có dữ liệu, nên mọi
màn hình đều có thứ để xem ngay từ lần chạy đầu.

### Thử luồng xác thực email

Bảy tài khoản mẫu đều đã xác thực sẵn nên đường đi ở trên không vướng gì. Muốn xem chốt
chặn thì **tự đăng ký một tài khoản mới**: đăng ký xong vào thẳng thực đơn được, thêm món
được, nhưng ở giỏ hàng sẽ không có phần chọn giờ — kèm dải nhắc và nút **Gửi lại thư xác
thực** trên đầu mọi trang.

Kênh gửi thư mặc định là giả lập, nên lá thư nằm trong log máy chủ chứ không tới hộp thư:

```
[THONG BAO] den=ban@gmail.com | Xác thực địa chỉ email | ...
http://localhost:8080/fastfood/verify-email?token=xY3k...
```

Chép liên kết đó dán vào trình duyệt là xác thực xong, và đặt đơn được ngay. Muốn thư
thật sự đi ra ngoài thì điền `notification.mail.username` / `password` (Gmail phải là
**App Password**) rồi đổi `notification.channel=SMTP` trong `src/main/resources/app.properties`.

### Thu tiền thật bằng chuyển khoản (SePay)

Cổng mặc định là bản giả lập ở trên. Muốn nhận tiền thật thì điền khối `payment.sepay.*`
trong `src/main/resources/app.properties` rồi đổi `payment.gateway.provider=SEPAY`:

```properties
payment.gateway.provider=SEPAY
payment.sepay.accountNumber=0011223344
payment.sepay.bank=Vietcombank
payment.sepay.accountName=CUA HANG ABC
payment.sepay.apiKey=<khoá tự đặt trong bảng điều khiển SePay>
```

Khi đó trang thanh toán giả lập đóng lại (trả 404) và thay bằng trang mã VietQR: khách quét
bằng ứng dụng ngân hàng, tiền vào **thẳng tài khoản của cửa hàng**, SePay chỉ đọc biến động
số dư rồi báo về. Trang tự cập nhật khi tiền tới nơi, khách không phải bấm gì.

Ở phía SePay cần khai báo webhook trỏ về `https://<tên miền của bạn>/fastfood/payment/sepay/webhook`,
kiểu xác thực **API Key**, và khoá phải khớp đúng `payment.sepay.apiKey` — lệch một ký tự thì
mọi lần báo có tiền đều bị từ chối và ghi một dòng `SEVERE` trong log. Địa chỉ này phải ra được
Internet, nên lúc chạy trên máy cá nhân cần một đường hầm (ngrok, Cloudflare Tunnel) chứ
`localhost` thì SePay không gọi tới được.

Chi tiết luồng và các chốt chặn: xem `docs/STRUCTURE.md` §3.6.

## Quy mô

| Phần | Số tệp | Số dòng |
|---|---|---|
| Java | 198 | ~18.800 |
| JSP | 39 | ~4.700 |
| SQL | 1 | ~2.000 |

## Ngoài phạm vi

Không giao hàng tận nơi, không nhiều chi nhánh, không quản lý kho, không mã giảm giá,
không tích điểm, không hoàn tiền một phần.
