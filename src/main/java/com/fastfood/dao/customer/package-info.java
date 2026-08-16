/**
 * Truy vấn các bảng của riêng <b>khách hàng</b>:
 * <ul>
 *   <li>{@link com.fastfood.dao.customer.CartDAO} — giỏ hàng (Cart · CartItem).</li>
 *   <li>{@link com.fastfood.dao.customer.FavouriteDAO} — món quen kèm ghi chú riêng.</li>
 *   <li>{@link com.fastfood.dao.customer.OrderTemplateDAO} — mẫu đặt nhanh
 *       (OrderTemplate · OrderTemplateItem).</li>
 *   <li>{@link com.fastfood.dao.customer.ReviewDAO} — đánh giá món.</li>
 * </ul>
 *
 * <p><b>Ba bảng dưới cùng nguyên tắc với giỏ hàng: không lưu giá.</b> Giá luôn đọc mới từ bảng
 * món, nên một mẫu lưu từ tháng trước không bao giờ đưa giá cũ vào đơn mới.
 *
 * <p>{@code ReviewDAO} có một truy vấn không thuộc bảng của nó —
 * {@code hasCompletedPurchase} ghép qua Orders và OrderItem. Nó nằm ở đây vì chỉ đánh giá cần
 * tới nó, và vì ràng buộc "đã mua và đã nhận" không đặt được ở tầng dữ liệu: {@code CHECK}
 * trong SQL Server không nhìn sang bảng khác.
 *
 * <p>Ngoài {@code service.customer}, gói này còn được {@code service.shared.OrderCoreService}
 * dùng để dọn giỏ sau khi tiền về — vẫn là giỏ của khách, chỉ do một lớp dùng chung thao tác hộ.
 */
package com.fastfood.dao.customer;
