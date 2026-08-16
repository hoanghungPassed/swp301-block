/**
 * Truy vấn các bảng mà <b>nhiều vai trò cùng dùng</b>.
 * <p>
 * Đây là phần lớn tầng DAO, và đó là điều bình thường: một đơn hàng đi qua tay khách, bếp và
 * thu ngân, nên bảng {@code Orders} không thuộc riêng ai. Bảng {@code Product} thì khách xem,
 * quầy bán, bếp tra và quản trị sửa.
 *
 * <table border="1">
 *   <caption>Ai đi qua lớp nào</caption>
 *   <tr><th>Lớp</th><th>Vai trò</th></tr>
 *   <tr><td>OrderDAO · OrderItemDAO</td><td>khách · bếp · thu ngân · quản trị</td></tr>
 *   <tr><td>ProductDAO · CategoryDAO</td><td>khách · thu ngân · bếp · quản trị</td></tr>
 *   <tr><td>PaymentDAO · TransactionDAO</td><td>khách · thu ngân</td></tr>
 *   <tr><td>UserDAO · RoleDAO</td><td>đăng nhập · quản trị</td></tr>
 *   <tr><td>NotificationDAO · AuditLogDAO</td><td>mọi luồng đều ghi vào</td></tr>
 * </table>
 */
package com.fastfood.dao.shared;
