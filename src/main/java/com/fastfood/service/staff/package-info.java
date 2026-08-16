/**
 * Nghiệp vụ của <b>thu ngân</b>, năm nhánh:
 * <ul>
 *   <li>{@link com.fastfood.service.staff.StaffOrderService} — bán tại quầy, điều phối đơn, nhận
 *       món từ bếp, giao món cho khách.</li>
 *   <li>{@link com.fastfood.service.staff.ShiftService} — ca làm việc và đối soát tiền mặt. Vá
 *       lỗ hổng mà tài liệu đã tự chỉ ra: khoản thu tiền mặt trước đây không có gì để đối chiếu.</li>
 *   <li>{@link com.fastfood.service.staff.OrderNoteService} — ghi chú điều phối trên đơn.</li>
 *   <li>{@link com.fastfood.service.staff.CounterRejectService} — từ chối nhận món bếp đưa ra
 *       quầy. Vá đường đi còn thiếu: trước đây {@code receiveAtCounter} chỉ có một lối là nhận.</li>
 *   <li>{@link com.fastfood.service.staff.PosHoldService} — phiếu treo tại quầy, cất giỏ đang dở
 *       lại để phục vụ khách tiếp theo.</li>
 * </ul>
 *
 * <p><b>Phiếu treo không phá vỡ nguyên tắc "giỏ POS không ghi xuống cơ sở dữ liệu".</b> Giỏ tạm
 * vẫn nằm trong phiên làm việc; treo đơn là một thao tác cố ý, thu ngân phải đặt tên cho phiếu
 * thì mới treo được. Bấm nhầm không sinh ra bản ghi nào.
 *
 * <p>Đường đặt trước của khách nằm ở {@code service.customer}, phần dùng chung của hai đường
 * nằm ở {@code service.shared.OrderCoreService}.
 */
package com.fastfood.service.staff;
