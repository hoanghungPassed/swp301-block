package com.fastfood.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu treo tại quầy — giỏ hàng POS được cất lại để phục vụ khách tiếp theo trước.
 * <p>
 * Đây là <b>bản nháp</b>, không phải đơn hàng: chưa có mã đơn, chưa thu tiền, bếp chưa thấy gì.
 * Nó chỉ thành đơn khi thu ngân lấy phiếu ra và bấm thu tiền như bình thường.
 */
public class PosHold {

    private int holdId;
    private int cashierId;
    private String label;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<PosHoldItem> items = new ArrayList<>();

    public int getHoldId() { return holdId; }
    public void setHoldId(int holdId) { this.holdId = holdId; }

    public int getCashierId() { return cashierId; }
    public void setCashierId(int cashierId) { this.cashierId = cashierId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<PosHoldItem> getItems() { return items; }
    public void setItems(List<PosHoldItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public int getLineCount() { return items.size(); }

    public int getTotalQuantity() {
        int sum = 0;
        for (PosHoldItem item : items) {
            sum += item.getQuantity();
        }
        return sum;
    }

    public BigDecimal getTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PosHoldItem item : items) {
            sum = sum.add(item.getLineTotal());
        }
        return sum;
    }

    /**
     * Phiếu có món đã ngừng bán hoặc vừa hết hàng.
     * <p>
     * Tính ở đây để màn hình cảnh báo được <b>trước khi</b> thu ngân lấy phiếu ra: treo từ sáng
     * mà trưa bếp báo hết món thì lúc bấm thu tiền mới biết là đã muộn — khách đang đứng ở quầy.
     */
    public boolean isAnyUnavailable() {
        for (PosHoldItem item : items) {
            if (!item.isOrderable()) {
                return true;
            }
        }
        return false;
    }
}
