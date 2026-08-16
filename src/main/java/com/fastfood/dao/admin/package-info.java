/**
 * Truy vấn của riêng <b>quản trị viên</b>:
 * <ul>
 *   <li>{@link com.fastfood.dao.admin.ReportDAO} — các câu tổng hợp doanh thu cho trang báo cáo.
 *       Khác mọi lớp DAO còn lại ở chỗ nó không ánh xạ một bảng nào — nó gộp nhiều bảng lại
 *       thành số liệu. Đó cũng là lý do nó không dùng chung được với vai trò nào khác.</li>
 *   <li>{@link com.fastfood.dao.admin.RevenueTargetDAO} — chỉ tiêu doanh thu theo kỳ.</li>
 * </ul>
 *
 * <p>Hai lớp này cố ý <b>không</b> gọi lẫn nhau: mức đã đạt của một chỉ tiêu do
 * {@code service.admin.RevenueTargetService} lấy từ {@code ReportService}, để cả hệ thống chỉ
 * có <b>một</b> công thức tính doanh thu thuần. Viết thêm một câu tính doanh thu trong
 * {@code RevenueTargetDAO} sẽ đặt hai con số cạnh nhau trên cùng màn hình và sớm muộn chúng
 * lệch nhau.
 */
package com.fastfood.dao.admin;
