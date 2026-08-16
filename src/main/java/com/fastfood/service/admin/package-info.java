/**
 * Nghiệp vụ của <b>quản trị viên</b> — quản lý món, danh mục, tài khoản, báo cáo doanh thu và
 * chỉ tiêu doanh thu.
 *
 * <p><b>Vì sao chỉ tiêu doanh thu nằm ở bảng điều khiển.</b> Bảng điều khiển là màn hình duy
 * nhất của quản trị viên còn chỗ cho một thực thể mới: nhật ký thao tác phải giữ nguyên trạng
 * chỉ đọc, vì một nhật ký kiểm toán mà quản trị viên sửa được thì mất đúng thứ nó sinh ra để
 * làm. {@link com.fastfood.service.admin.RevenueTargetService} cũng cố ý là thứ <b>chỉ được
 * đọc</b> ở phía dưới — nó không chen vào bất kỳ phép tính tiền nào, nên đặt sai chỉ tiêu chỉ
 * sai một dòng so sánh trên màn hình chứ không sai sổ sách.
 *
 * <p>Gọi vào từ {@code controller.admin}. Nhật ký thao tác mà trang quản trị hiển thị nằm ở
 * {@code service.shared.AuditService} vì mọi vai trò đều ghi vào đó.
 */
package com.fastfood.service.admin;
