/**
 * Entity ánh xạ 1-1 với bảng trong {@code database/FastFoodPreorder.sql} — <b>25 lớp, 25 bảng</b>.
 * Con số phải luôn bằng nhau: một lớp không có bảng là mã chết, một bảng không có lớp là bảng
 * không ai đọc.
 *
 * <table>
 *   <caption>Bảng theo nhóm</caption>
 *   <tr><td>Nền</td><td>Role · User · Category · Product</td></tr>
 *   <tr><td>Giỏ hàng</td><td>Cart · CartItem</td></tr>
 *   <tr><td>Đơn hàng</td><td>Shift · Order · OrderItem · OrderNote</td></tr>
 *   <tr><td>Thanh toán</td><td>Payment · Transaction</td></tr>
 *   <tr><td>Vận hành</td><td>Notification · KitchenIssue · AuditLog</td></tr>
 *   <tr><td>Bếp</td><td>PrepTask · OrderItemNote · KitchenNote</td></tr>
 *   <tr><td>Quầy</td><td>PosHold · PosHoldItem</td></tr>
 *   <tr><td>Quản trị</td><td>RevenueTarget</td></tr>
 *   <tr><td>Của riêng khách</td><td>Favourite · OrderTemplate · OrderTemplateItem · Review</td></tr>
 * </table>
 *
 * <p><b>Ba tên khác tài liệu vì trùng từ khoá SQL Server:</b> {@code User → Users},
 * {@code Order → Orders}, {@code Transaction → PaymentTransaction}. Lớp Java giữ tên theo tài
 * liệu; ánh xạ chỉ nằm trong tầng DAO.
 *
 * <p><b>Giá không bao giờ được lưu ở bảng nháp.</b> {@code CartItem}, {@code PosHoldItem} và
 * {@code OrderTemplateItem} chỉ giữ mã món và số lượng — giá đọc mới từ bảng món mỗi lần hiển
 * thị. Chỉ {@code OrderItem} chép giá lại, vì đó là lúc giá được chốt.
 */
package com.fastfood.model.entity;
