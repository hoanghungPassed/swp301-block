/**
 * Servlet cho <b>khách hàng</b>.
 * <p>
 * Hai trong số đó — {@link com.fastfood.controller.customer.MenuServlet} và
 * {@link com.fastfood.controller.customer.ProductDetailServlet} — là <b>trang công khai</b>, nằm
 * ngoài {@code AuthenticationFilter}. Mọi yêu cầu gửi tới đó đều lọt qua, kể cả yêu cầu ghi dữ
 * liệu của người chưa đăng nhập, nên chúng dùng {@code BaseServlet.userOrLogin} thay vì
 * {@code requireUser}: khách được đưa sang trang đăng nhập rồi trả về đúng chỗ đang xem, chứ
 * không nhận một lỗi 401 cho việc hoàn toàn hợp lệ mà chỉ làm sớm một bước.
 */
package com.fastfood.controller.customer;
