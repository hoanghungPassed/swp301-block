/**
 * Tầng nghiệp vụ — nơi đặt toàn bộ quy tắc kinh doanh và ranh giới giao dịch.
 * <p>
 * Chia theo <b>vai trò người dùng</b>, cùng trục với {@code com.fastfood.controller}: mở
 * {@code service.staff} là thấy trọn phần nghiệp vụ của thu ngân, không phải lần qua một lớp
 * chung khổng lồ. {@link com.fastfood.service.Tx} nằm ở gốc gói vì nó không thuộc vai trò nào —
 * giống {@code controller.BaseServlet}.
 *
 * <table border="1">
 *   <caption>Sáu gói con</caption>
 *   <tr><th>Gói</th><th>Lớp</th><th>Servlet gọi vào</th></tr>
 *   <tr><td>{@code customer}</td><td>CustomerOrderService · CartService</td>
 *       <td>{@code controller.customer}</td></tr>
 *   <tr><td>{@code staff}</td><td>StaffOrderService</td>
 *       <td>{@code controller.staff}</td></tr>
 *   <tr><td>{@code kitchen}</td><td>KitchenService</td>
 *       <td>{@code controller.kitchen} và màn hình Quầy giao nhận</td></tr>
 *   <tr><td>{@code admin}</td><td>AdminService · ReportService</td>
 *       <td>{@code controller.admin}</td></tr>
 *   <tr><td>{@code auth}</td><td>AuthService</td>
 *       <td>{@code controller.auth} và trang cá nhân của khách</td></tr>
 *   <tr><td>{@code shared}</td><td>OrderCoreService · PaymentService · MenuService ·
 *       ScheduleService · NotificationService · AuditService</td>
 *       <td>nhiều vai trò cùng dùng</td></tr>
 * </table>
 *
 * <p><b>Vì sao có {@code shared} chứ không nhân bản ra từng vai trò.</b> Sáu lớp trong đó phục
 * vụ nhiều vai trò cùng lúc: khách trả tiền và thu ngân xem lại đều đi qua {@code PaymentService},
 * trạng thái đơn thì do bếp làm đổi nhưng khách và thu ngân cùng đọc. Chép mỗi lớp thành một bản
 * cho mỗi vai trò nghĩa là sửa một quy tắc phải sửa nhiều chỗ, và chỉ cần quên một chỗ là hai
 * vai trò cư xử khác nhau trên cùng một đơn hàng.
 *
 * <p><b>Ba lớp đáng đọc trước:</b> {@code customer.CustomerOrderService} (đặt trước),
 * {@code staff.StaffOrderService} (bán tại quầy và giao món),
 * {@code shared.OrderCoreService} (trạng thái đơn được suy ra từ trạng thái các món).
 */
package com.fastfood.service;
