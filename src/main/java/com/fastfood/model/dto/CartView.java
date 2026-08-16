package com.fastfood.model.dto;

import com.fastfood.model.entity.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Giỏ hàng đã tính sẵn tổng tiền và đánh dấu món không còn đặt được. */
public class CartView {

    private int cartId;
    private List<CartItem> items = new ArrayList<>();

    public int getCartId() { return cartId; }
    public void setCartId(int cartId) { this.cartId = cartId; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public BigDecimal getTotalAmount() {
        return items.stream()
                .filter(CartItem::isOrderable)
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalQuantity() {
        return items.stream().filter(CartItem::isOrderable).mapToInt(CartItem::getQuantity).sum();
    }

    /**
     * Giỏ không có món nào.
     * <p>
     * Tên là {@code isEmptyCart} chứ không {@code isEmpty}: thuộc tính EL suy ra từ
     * {@code isEmpty} là {@code empty}, mà {@code ${cart.empty}} không phân tích cú pháp được
     * vì {@code empty} là toán tử của chính EL — trang sẽ đổ lỗi lúc chạy. Quy tắc này có
     * {@code BeanNamingTest} canh.
     */
    public boolean isEmptyCart() { return items.isEmpty(); }

    /** Có món trong giỏ đã hết hàng hoặc ngừng bán — chặn thanh toán cho tới khi khách bỏ ra. */
    public boolean isHasUnavailable() {
        return items.stream().anyMatch(i -> !i.isOrderable());
    }

    /** Giỏ có ít nhất một món đặt được, đủ điều kiện sang bước thanh toán. */
    public boolean isCheckoutable() {
        return !isEmptyCart() && !isHasUnavailable();
    }
}
