/**
 * Tầng truy cập dữ liệu. Mỗi lớp phụ trách một bảng, chỉ chứa câu lệnh SQL, không chứa
 * quy tắc nghiệp vụ. Mọi phương thức nhận sẵn Connection để tầng Service gom nhiều thao tác
 * vào chung một giao dịch.
 * <p>
 * Các phương thức đổi trạng thái gộp điều kiện kiểm tra vào chính câu lệnh cập nhật và trả về
 * số dòng bị ảnh hưởng — đây là cách chống trùng lặp khi nhiều luồng cùng chạy.
 *
 * <p>Chia thành gói con theo <b>vai trò sở hữu bảng</b>, cùng trục với {@code controller} và
 * {@code service}. {@link com.fastfood.dao.JdbcSupport} ở gốc gói vì nó không truy vấn bảng nào —
 * giống {@code service.Tx} và {@code controller.BaseServlet}.
 *
 * <table border="1">
 *   <caption>Bốn gói con</caption>
 *   <tr><th>Gói</th><th>Lớp</th><th>Bảng</th></tr>
 *   <tr><td>{@code customer}</td><td>CartDAO</td><td>Cart · CartItem</td></tr>
 *   <tr><td>{@code kitchen}</td><td>KitchenIssueDAO</td><td>KitchenIssue</td></tr>
 *   <tr><td>{@code admin}</td><td>ReportDAO</td><td>truy vấn tổng hợp doanh thu</td></tr>
 *   <tr><td>{@code shared}</td><td>OrderDAO · OrderItemDAO · ProductDAO · CategoryDAO ·
 *       PaymentDAO · TransactionDAO · UserDAO · RoleDAO · NotificationDAO · AuditLogDAO</td>
 *       <td>Orders · OrderItem · Product · Category · Payment · PaymentTransaction ·
 *       Users · Role · Notification · AuditLog</td></tr>
 * </table>
 *
 * <p><b>Vì sao 10 trong 14 lớp nằm ở {@code shared} và {@code staff} thì không có lớp nào.</b>
 * Ba lớp chia được vì bảng của chúng chỉ một vai trò đụng tới: giỏ hàng là của khách, sự cố bếp
 * là của bếp, báo cáo doanh thu là của quản trị. Số còn lại gắn với bảng mà nhiều vai trò cùng
 * dùng — {@code OrderDAO} bị cả bốn vai trò đọc, {@code ProductDAO} cũng vậy. Thu ngân không có
 * bảng riêng: mọi thứ họ đọc đều là bảng dùng chung.
 *
 * <p>Chúng nằm ở {@code shared} chứ không bị nhân bản vào từng vai trò, vì hai lối kia đều tệ
 * hơn: chép {@code OrderDAO} thành bốn bản SQL trên cùng một bảng thì sửa lược đồ phải sửa bốn
 * chỗ, còn nhét vào một thư mục vai trò rồi ba thư mục kia import chéo sang thì tên thư mục nói
 * dối về việc ai đang dùng nó.
 *
 * <p>Ranh giới vai trò thật sự nằm ở tầng Service: không controller nào import DAO. Muốn đọc
 * trọn một vai trò từ địa chỉ URL xuống tới tên bảng thì xem mục <b>Bản đồ theo vai trò</b>
 * trong {@code docs/STRUCTURE.md}.
 */
package com.fastfood.dao;
