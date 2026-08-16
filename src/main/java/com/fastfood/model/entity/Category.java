package com.fastfood.model.entity;

/** Nhóm món trên thực đơn. */
public class Category {
    private int categoryId;
    private String name;
    private String status;
    private int displayOrder;

    /** Số món đang có trong nhóm — dùng ở màn hình quản trị. */
    private int productCount;

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public int getProductCount() { return productCount; }
    public void setProductCount(int productCount) { this.productCount = productCount; }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
