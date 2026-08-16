package com.fastfood.model.entity;

import java.time.LocalDateTime;

/** Giỏ hàng của khách đặt trước. Khách mua tại quầy không có giỏ trong cơ sở dữ liệu. */
public class Cart {
    private int cartId;
    private int userId;
    private LocalDateTime updatedAt;

    public int getCartId() { return cartId; }
    public void setCartId(int cartId) { this.cartId = cartId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
