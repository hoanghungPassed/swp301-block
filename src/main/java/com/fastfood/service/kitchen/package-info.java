/**
 * Nghiệp vụ của <b>bếp</b>, hai nhánh tách hẳn nhau:
 * <ul>
 *   <li>{@link com.fastfood.service.kitchen.KitchenService} — việc bắt nguồn từ đơn hàng: hàng
 *       chờ, nhận việc, báo món xong, bàn giao ra quầy, sự cố bếp. Ngoài
 *       {@code controller.kitchen}, nhánh này còn được màn hình Quầy giao nhận của thu ngân đọc
 *       vào: món bếp vừa đưa ra và sự cố bếp đều là thứ thu ngân cần thấy. Đó là lý do lớp này
 *       nằm ở {@code kitchen} chứ không bị chẻ đôi — nó mô tả trạng thái của bếp, ai đọc cũng
 *       là trạng thái ấy.</li>
 *   <li>{@link com.fastfood.service.kitchen.PrepService} — kế hoạch chuẩn bị sẵn trong ca. Phần
 *       việc duy nhất của bếp <b>không bắt nguồn từ đơn nào</b>, và cũng không làm đổi trạng
 *       thái đơn nào. Hiển thị cùng màn hàng chờ vì đầu bếp cần nhìn hai thứ một lúc mới quyết
 *       được nên làm sẵn thêm hay dừng lại.</li>
 *   <li>{@link com.fastfood.service.kitchen.KitchenNoteService} — ghi chú chế biến theo món và
 *       sổ bàn giao ca. Cố ý <b>không</b> dùng lại sự cố bếp: số sự cố đang mở điều khiển bốn
 *       chỗ cảnh báo đỏ trên màn hình thu ngân, nên một dòng "khách dặn ít cay" đi vào đó sẽ
 *       hiện thành sự cố chưa xử lý và làm cảnh báo mất ý nghĩa.</li>
 * </ul>
 */
package com.fastfood.service.kitchen;
