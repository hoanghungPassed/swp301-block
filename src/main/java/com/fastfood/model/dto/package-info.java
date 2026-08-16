/**
 * Đối tượng truyền dữ liệu sang tầng hiển thị. Chỉ tạo DTO khi cần gộp dữ liệu từ nhiều bảng
 * hoặc cần tính sẵn giá trị; còn lại dùng thẳng entity.
 * <p>
 * {@link com.fastfood.model.dto.TemplateApplyResult} là ví dụ cho vế thứ hai: nạp mẫu đặt nhanh
 * trả về cả phần <b>không</b> nạp được, vì đó mới là phần khách cần biết ngay thay vì tự phát
 * hiện ở bước chọn giờ đến lấy.
 */
package com.fastfood.model.dto;
