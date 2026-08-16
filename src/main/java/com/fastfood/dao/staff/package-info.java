/**
 * Truy vấn các bảng của riêng <b>thu ngân</b>:
 * <ul>
 *   <li>{@link com.fastfood.dao.staff.ShiftDAO} — ca làm việc và số liệu đối soát tiền mặt.
 *       Bảng duy nhất trong hệ thống mà {@code Orders} trỏ tới thay vì ngược lại.</li>
 *   <li>{@link com.fastfood.dao.staff.OrderNoteDAO} — ghi chú điều phối gắn với đơn hàng.</li>
 *   <li>{@link com.fastfood.dao.staff.PosHoldDAO} — phiếu treo tại quầy
 *       (PosHold · PosHoldItem).</li>
 * </ul>
 *
 * <p>Trước khi có ca làm việc, gói này không tồn tại: mọi thứ thu ngân đọc đều là bảng dùng
 * chung. Ca làm việc là thứ đầu tiên thuộc về riêng họ, phiếu treo là thứ thứ hai.
 */
package com.fastfood.dao.staff;
