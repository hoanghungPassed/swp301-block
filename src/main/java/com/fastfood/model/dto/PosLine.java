package com.fastfood.model.dto;

/** Một dòng món do thu ngân nhập trên màn hình bán tại quầy. */
public class PosLine {

    private int productId;
    private int quantity;

    public PosLine(int productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public int getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}
