/**
 * Nghiệp vụ mà <b>nhiều vai trò cùng dùng</b> — không thuộc riêng vai trò nào.
 * <ul>
 *   <li>{@link com.fastfood.service.shared.OrderCoreService} — nạp đơn, xác nhận sau thanh toán,
 *       hoàn tiền, suy ra trạng thái đơn từ trạng thái các món.</li>
 *   <li>{@link com.fastfood.service.shared.PaymentService} — khách trả tiền, thu ngân xem lại,
 *       cổng thanh toán gọi về.</li>
 *   <li>{@link com.fastfood.service.shared.MenuService} — thực đơn, khách xem và máy bán hàng
 *       tại quầy cũng dùng.</li>
 *   <li>{@link com.fastfood.service.shared.ScheduleService} — hai công việc chạy nền, không có
 *       người dùng nào đứng sau.</li>
 *   <li>{@link com.fastfood.service.shared.NotificationService} ·
 *       {@link com.fastfood.service.shared.AuditService} — gửi tin và ghi nhật ký, xuyên suốt
 *       mọi luồng.</li>
 * </ul>
 */
package com.fastfood.service.shared;
