# Cấu hình payOS

Tài liệu này đi từ lúc chưa có tài khoản payOS đến lúc một đơn hàng thật chuyển sang **PAID**.
Luồng bên trong hệ thống (bảng biểu, trạng thái, chốt chặn) nằm ở [`STRUCTURE.md`](STRUCTURE.md)
§3.7 — ở đây chỉ nói phần **cấu hình**.

> **payOS không có máy chủ sandbox.** Khoá lấy về là khoá thật và tiền chuyển là tiền thật.
> Muốn chạy thử thì đặt vài món rẻ nhất rồi tự chuyển khoản cho chính mình.

---

## 1. Lấy ba khoá

1. Đăng ký tài khoản ở [my.payos.vn](https://my.payos.vn) và liên kết tài khoản ngân hàng nhận tiền.
2. Vào **Kênh thanh toán** → chọn kênh vừa tạo → mục **Thông tin kết nối**.
3. Chép ba giá trị:

| Khoá | Đi đâu | Vai trò |
|---|---|---|
| **Client ID** | tiêu đề `x-client-id` | mã cửa hàng, không bí mật lắm nhưng vẫn không nên khoe |
| **API Key** | tiêu đề `x-api-key` | khoá để **gọi** API payOS: xin liên kết trả tiền, tra cứu trạng thái |
| **Checksum Key** | *không đi đâu cả* | chuỗi bí mật để **ký** và để **kiểm chữ ký** payOS gửi về |

Checksum Key không bao giờ được gửi lên đường truyền. Lộ nó nghĩa là người khác giả được
webhook "đã nhận tiền" cho đơn của bạn.

---

## 2. Điền khoá vào đúng chỗ

Có hai tệp, và chọn nhầm tệp là cách phổ biến nhất để đẩy khoá thật lên GitHub:

| Tệp | Vào kho git? | Chứa gì |
|---|---|---|
| `src/main/resources/app.properties` | **có** | tham số nghiệp vụ, và ba dòng khoá **để trống** |
| `src/main/resources/app.local.properties` | **không** (`.gitignore` bỏ qua) | khoá thật của riêng máy này |

`app.local.properties` được nạp **sau** nên mọi khoá trùng tên đều đè lên `app.properties`.
Không có tệp này thì ứng dụng vẫn chạy, chỉ là chưa cấu hình cổng.

Tạo `src/main/resources/app.local.properties`:

```properties
payment.gateway.provider=PAYOS
payment.payos.clientId=<Client ID>
payment.payos.apiKey=<API Key>
payment.payos.checksumKey=<Checksum Key>
```

Khởi động lại Tomcat rồi mở `catalina.out`, phải thấy:

```
AppConfig: da nap app.properties
AppConfig: da nap de app.local.properties
```

Thiếu dòng thứ hai nghĩa là tệp chưa được đóng vào WAR — dịch lại (`mvn package`) rồi chép
lại WAR sang `webapps`.

> **Đã lỡ commit khoá thật?** Coi như khoá đó **đã lộ**, kể cả khi commit sau xoá đi: nó vẫn
> nằm trong lịch sử. Việc phải làm là vào my.payos.vn **thu hồi và cấp lại khoá**, rồi mới dọn
> lịch sử. Xem [`GIT.md`](GIT.md) §3.

---

## 3. Các tham số còn lại

Ba dòng dưới đây nằm trong `app.properties` (không phải khoá bí mật, cứ để trong kho):

```properties
payment.payos.returnUrl=
payment.payos.baseUrl=
payment.payos.orderCodeOffset=0
```

- **`returnUrl`** — để trống thì địa chỉ quay về tự dựng từ nơi khách đang truy cập, ví dụ
  `http://localhost:8080/fastfood/payment/payos/return`. Chỉ điền khi máy chủ nằm sau proxy và
  tự nó không biết tên miền thật bên ngoài. Địa chỉ này dùng luôn làm `cancelUrl`.
- **`baseUrl`** — để trống là dùng `https://api-merchant.payos.vn`. Chỉ đổi khi trỏ vào một máy
  chủ giả để chạy kiểm thử.
- **`orderCodeOffset`** — xem §6.

---

## 4. Hai đường payOS báo kết quả về

Cả hai cùng đổ vào `PaymentService.handleCallback`, và đều đã nối sẵn:

| Đường | Địa chỉ | Có chữ ký? | Khi nào chạy |
|---|---|---|---|
| Khách quay lại | `/payment/payos/return` | **không** | luôn chạy, kể cả trên `localhost`; nhưng khách đóng tab giữa chừng thì không bao giờ chạy |
| Webhook | `/payment/payos/webhook` | **có** | đường chắc chắn tới; đòi máy chủ có địa chỉ **https** công khai và phải khai báo trong my.payos.vn |

**payOS không ký các tham số trên đường khách quay lại.** Tin thẳng `status=PAID` đọc từ thanh
địa chỉ nghĩa là ai gõ tay được địa chỉ ấy cũng tự cho đơn mình là đã trả tiền. Vì vậy
`PayOsReturnServlet` chỉ lấy `orderCode` rồi **gọi ngược sang payOS** hỏi trạng thái thật bằng
API Key của cửa hàng.

Bật cả hai không thu tiền hai lần: cả hai cùng dựng mã giao dịch từ mã tham chiếu ngân hàng nên
lần thứ hai đụng ràng buộc duy nhất trên bảng giao dịch và bị bỏ qua (NFR-06).

### Khai báo webhook

Chỉ làm được khi máy chủ ra được Internet bằng **https**. Trên máy cá nhân thì cần một đường
hầm (ngrok, Cloudflare Tunnel).

1. my.payos.vn → **Kênh thanh toán** → **Webhook**.
2. Điền `https://<tên miền của bạn>/fastfood/payment/payos/webhook`.
3. Bấm **Kiểm tra** — payOS gửi thử một gói; phải trả về 200.

Chạy thử trên máy cá nhân mà bỏ qua bước này vẫn đi hết luồng được, chỉ dựa vào đường khách
quay lại.

---

## 5. Kiểm lại bằng một đơn thật

1. Đặt một đơn online, chọn thanh toán → bị đẩy sang trang payOS.
2. Quét VietQR bằng ứng dụng ngân hàng, chuyển đúng số tiền hiện trên màn hình.
3. Bấm quay lại — đơn phải chuyển sang **PAID** và xuất hiện ở màn hình bếp đúng lịch.

Ở quầy thì đường đi là **Khách quét mã QR** trên màn POS: `/staff/pos/qr` hỏi cổng lấy chỗ trả
tiền rồi vẽ QR ngay tại máy chủ, trang tự cập nhật khi tiền tới nơi. Mở lại trang không sinh
thêm liên kết mới — payOS nhận ra mã đơn đã có và trả về đúng liên kết cũ.

---

## 6. Khi có trục trặc

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| *"Cổng thanh toán chưa được cấu hình"* | thiếu một trong ba khoá | kiểm `app.local.properties` đã nạp chưa (§2) |
| *"Không mở được cổng thanh toán PayOS: …"* | lời gọi API hỏng — sai Client ID/API Key, hoặc máy chủ không ra được Internet | đọc nguyên văn lý do trong log; payOS nói khá rõ |
| Tiền vào tài khoản mà đơn **không nhúc nhích**, log có dòng `Tu choi webhook PayOS` | **sai `checksumKey`** | chép lại Checksum Key; đây là chỗ đầu tiên nên ngó |
| Webhook không bao giờ tới | chưa khai báo, hoặc địa chỉ không phải https công khai | xem §4 |
| *"Đơn thanh toán đã tồn tại"* sau khi nạp lại cơ sở dữ liệu | payOS không cho dùng lại một `orderCode`, mà mã khoản thu thì quay về đếm từ 1 mỗi lần chạy lại `FastFoodPreorder.sql` | tăng `payment.payos.orderCodeOffset` vượt qua số khoản thu đã từng tạo — `1000`, lần sau `2000`… Số này cộng vào lúc gửi đi và trừ ra lúc đọc kết quả về |
| Tên cửa hàng trong nội dung chuyển khoản bị cắt | payOS đưa mô tả vào nội dung chuyển khoản, mà ngân hàng chỉ nhận 25 ký tự | không sửa được ở phía mình |

---

## 7. Đổi sang cổng khác

`payment.gateway.provider` nhận đúng hai giá trị:

- **`PAYOS`** — mặc định, luồng mô tả ở trên.
- **`SEPAY`** — chuyển khoản VietQR thẳng vào tài khoản ngân hàng, không qua trang trung gian.
  Cần khối `payment.sepay.*`; `payment.sepay.apiKey` cũng là khoá thật nên cũng đặt ở
  `app.local.properties`. Xem [`README.md`](../README.md).

Không còn cổng giả lập trong ứng dụng. Nó từng có nút "thành công" bấm là đơn thành đã trả —
tiện lúc trình bày, nhưng cũng có nghĩa bất kỳ ai đăng nhập được cũng tự cho đơn của mình là đã
trả tiền, và luồng thật thì chưa từng được chạy lần nào.
