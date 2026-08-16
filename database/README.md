# Database

Toàn bộ database nằm trong **một file duy nhất**: [`FastFoodPreorder.sql`](FastFoodPreorder.sql)

```bash
sqlcmd -S localhost -U sa -P '<password>' -C -i FastFoodPreorder.sql
```

Hoặc mở file trong SSMS rồi nhấn F5. Yêu cầu SQL Server 2016 trở lên.

> **File xoá và tạo lại toàn bộ bảng mỗi lần chạy.** Thiết kế như vậy để luôn cho ra
> một database sạch, không phụ thuộc trạng thái trước đó. Đừng chạy trên dữ liệu thật.

Không có file migration đi kèm. Sửa lược đồ thì sửa thẳng vào file này rồi chạy lại — mỗi
lần chạy đều dựng lại từ đầu nên không có bước nâng cấp nào để phải viết riêng.

Chạy xong, file tự in ra 10 bảng kiểm tra ở cuối — đối chiếu để biết database đã sẵn sàng:

| Mục | Kỳ vọng |
|---|---|
| 8.1 Số bản ghi | cả **13 bảng** đều khác 0 |
| 8.2 Menu | đúng 10 món (3 món bị loại vì hết hàng / ngừng bán / danh mục tắt) |
| 8.3 Đơn hàng | 11 đơn, đủ 7 trạng thái, có 1 đơn quá hạn nhận |
| 8.4 Tỷ lệ đúng hẹn | 4 đơn, đúng hẹn 3 → 75% |
| 8.5 Đối soát tiền | **không có dòng nào** |
| 8.6 Vị trí món | cả **bốn** mức đều khác 0 |
| 8.7 Thứ tự bàn giao | **không có dòng nào** |
| 8.8 Tin đã gửi | đủ **bốn** loại sự kiện (13 tin) |
| 8.9 Giỏ hàng | 2 giỏ, `dat_hang_duoc` có cả `1` và `0` |
| 8.10 Giờ SQL Server | khớp giờ máy chạy Tomcat, lệch dưới 5 giây |

Mục 8.6 đáng nhìn kỹ: mức nào bằng 0 thì màn hình tương ứng mở ra trống trơn, không có gì để
thử. Bốn mức là cố ý — mỗi mức mở khoá một nút ở một màn hình khác nhau:

| Mức | Nút mở khoá được | Ở màn hình |
|---|---|---|
| 1 · chưa nấu xong | Nhận việc · Báo xong | `/kitchen/queue` |
| 2 · xong, còn trong bếp | **Bàn giao ra quầy** | `/kitchen/queue` |
| 3 · đang chờ ở quầy | **Nhận món** | `/staff/counter` |
| 4 · quầy đã nhận | Giao cho khách | `/staff/order/detail` |

Mục 8.9 kiểm tra hai giỏ hàng dựng sẵn: giỏ của `customer1` đặt được ngay, giỏ của
`customer2` cố ý có một món vừa hết hàng để thấy dải cảnh báo và nút *bỏ món hết hàng ra*
ở `/cart` — nhánh đó không tự xuất hiện nếu mọi món trong giỏ đều còn bán.

## Nội dung file

13 bảng · 17 index · 2 view · 6 trigger · dữ liệu mẫu (7 user, 13 món, 2 giỏ hàng, 11 đơn).

Tin báo cho khách và nhật ký thao tác không viết tay theo từng đơn mà **suy ra** từ chính
các mốc thời gian đã ghi — nhờ vậy không sót đơn nào và không bao giờ mâu thuẫn với dữ liệu
mà chúng mô tả.

Ba tên bảng khác tài liệu phân tích vì trùng từ khoá SQL Server:

| Tài liệu | Bảng thực tế |
|---|---|
| `User` | `Users` |
| `Order` | `Orders` |
| `Transaction` | `PaymentTransaction` |

Java entity vẫn giữ tên theo tài liệu; ánh xạ chỉ nằm trong tầng DAO.

## Tài khoản mẫu

Mật khẩu tất cả: **`123456`**

| Vai trò | Email |
|---|---|
| CUSTOMER | customer1@gmail.com · customer2@gmail.com |
| CASHIER | cashier1@fastfood.vn · cashier2@fastfood.vn |
| KITCHEN | kitchen1@fastfood.vn · kitchen2@fastfood.vn |
| ADMIN | admin@fastfood.vn |

Hash bcrypt dùng tiền tố `$2a$` vì jBCrypt 0.4 chỉ chấp nhận `$2$` và `$2a$` —
hash sinh bằng `htpasswd` ra `$2y$` thì phải đổi tiền tố, phần còn lại giữ nguyên.

## Thiết kế

Lý do đằng sau từng quyết định: [../docs/DATABASE-PLAN.md](../docs/DATABASE-PLAN.md)
