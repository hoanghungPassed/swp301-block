/**
 * Truy vấn hai bảng của riêng <b>bếp</b>:
 * <ul>
 *   <li>{@link com.fastfood.dao.kitchen.KitchenIssueDAO} — sự cố bếp (KitchenIssue). Chỉ bếp ghi
 *       vào. Thu ngân có đọc ở màn hình Quầy giao nhận, nhưng đọc qua
 *       {@code service.kitchen.KitchenService} chứ không gọi thẳng xuống đây.</li>
 *   <li>{@link com.fastfood.dao.kitchen.PrepTaskDAO} — kế hoạch chuẩn bị sẵn (PrepTask). Bảng
 *       duy nhất của bếp <b>không tham chiếu OrderItem</b>: bếp làm sẵn theo dự đoán chứ không
 *       đợi đơn, nên nó trỏ thẳng tới Product.</li>
 *   <li>{@link com.fastfood.dao.kitchen.KitchenNoteDAO} — hai bảng ghi chú: OrderItemNote (ghi
 *       chú chế biến theo món) và KitchenNote (sổ bàn giao ca). Gộp một lớp vì hai bảng cùng
 *       hình dạng và cùng quy tắc "chỉ người viết mới sửa hoặc xoá được".</li>
 * </ul>
 *
 * <p>Ghi chú là dữ liệu <b>duy nhất ngoài giỏ hàng được xoá hẳn</b> khỏi cơ sở dữ liệu: nó không
 * dính tiền, không đổi trạng thái đơn, và không có dòng nhật ký nào trỏ về nó.
 */
package com.fastfood.dao.kitchen;
