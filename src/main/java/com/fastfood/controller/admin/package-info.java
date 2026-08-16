/**
 * Servlet cho <b>quản trị viên</b> — năm màn hình dưới {@code /admin/*}.
 * <p>
 * {@link com.fastfood.controller.admin.AuditServlet} cố ý <b>chỉ có {@code doGet}</b> và sẽ mãi
 * như vậy: một nhật ký kiểm toán mà quản trị viên sửa được thì không còn giá trị làm bằng chứng.
 * Đó cũng là lý do chỉ tiêu doanh thu được gắn vào bảng điều khiển — xem
 * {@code service.admin.RevenueTargetService}.
 */
package com.fastfood.controller.admin;
