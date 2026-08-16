package com.fastfood.model.dto;

import java.math.BigDecimal;

/**
 * Một dòng trên phiếu tính tiền của màn hình bán tại quầy.
 * <p>
 * Giỏ POS chỉ giữ mã món và số lượng trong phiên làm việc; tên và giá đọc mới từ cơ sở dữ liệu
 * mỗi lần dựng trang, cùng nguyên tắc với {@link com.fastfood.model.entity.PosHoldItem}.
 * <p>
 * <b>Vì sao có cờ "còn bán được".</b> Món có thể ngừng bán hoặc hết hàng ngay trong lúc nó đang
 * nằm trong giỏ. Bản trước ghép giỏ với danh sách thực đơn nên dòng đó <i>biến mất khỏi phiếu</i>
 * mà vẫn còn trong giỏ: tổng tiền trên màn hình thiếu đi một món, và thu ngân chỉ biết có chuyện
 * khi bấm thu tiền và nhận về câu "món đã chọn hiện không còn phục vụ" — không nói rõ món nào,
 * cũng không có nút nào để bỏ nó ra. Giữ dòng lại và đánh dấu thì thu ngân thấy ngay món nào
 * hỏng và bỏ được nó ra bằng chính ô số lượng đang có.
 */
public class PosCartLine {

    private final int productId;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final boolean orderable;

    public PosCartLine(int productId, String productName, BigDecimal unitPrice,
                       int quantity, boolean orderable) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.orderable = orderable;
    }

    /**
     * Dòng trỏ tới một món không còn trong cơ sở dữ liệu.
     * <p>
     * Hiếm — món chỉ được ngừng bán chứ không xoá — nhưng giỏ nằm trong phiên làm việc và phiên
     * sống lâu hơn nhiều thao tác của quản trị viên. Bỏ lặng dòng này đi thì giỏ có một món ma
     * không bao giờ thanh toán được và cũng không hiện ra để mà xoá.
     */
    public static PosCartLine missing(int productId, int quantity) {
        return new PosCartLine(productId, "Món không còn trong hệ thống",
                BigDecimal.ZERO, quantity, false);
    }

    public int getProductId() { return productId; }

    public String getProductName() { return productName; }

    public BigDecimal getUnitPrice() { return unitPrice; }

    public int getQuantity() { return quantity; }

    /** Cùng định nghĩa với {@code Product.isOrderable()}, kể cả khi nhóm món đã bị tắt. */
    public boolean isOrderable() { return orderable; }

    public BigDecimal getLineTotal() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
