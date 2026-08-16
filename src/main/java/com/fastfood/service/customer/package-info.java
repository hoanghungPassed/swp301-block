/**
 * Nghiệp vụ của <b>khách hàng</b>, năm nhánh:
 * <ul>
 *   <li>{@link com.fastfood.service.customer.CustomerOrderService} — đặt trước, xem lại, tự huỷ.</li>
 *   <li>{@link com.fastfood.service.customer.CartService} — giỏ hàng.</li>
 *   <li>{@link com.fastfood.service.customer.FavouriteService} — món quen kèm ghi chú riêng.</li>
 *   <li>{@link com.fastfood.service.customer.OrderTemplateService} — mẫu đặt nhanh, nạp lại vào giỏ.</li>
 *   <li>{@link com.fastfood.service.customer.ReviewService} — đánh giá món, chỉ khách đã nhận
 *       mới viết được.</li>
 * </ul>
 *
 * <p><b>Ba lớp cuối phục vụ hai trang công khai</b> ({@code /menu} và {@code /product/detail}),
 * nơi {@code WebUtil.currentUser} có thể trả về null. Vì vậy các phương thức chỉ đọc ở đây nhận
 * {@code Integer} thay vì {@code int} và trả về rỗng khi chưa đăng nhập — người xem không đăng
 * nhập là chuyện bình thường trên trang công khai, không phải một lỗi.
 *
 * <p>Gọi vào từ {@code controller.customer} và {@code controller.api.OrderStatusApiServlet}.
 * Phần dùng chung với thu ngân nằm ở {@code service.shared.OrderCoreService}.
 */
package com.fastfood.service.customer;
