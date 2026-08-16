package com.fastfood.model.dto;

import java.math.BigDecimal;

/** Một dòng trong báo cáo dạng bảng: món bán chạy, doanh thu theo phương thức... */
public class ReportRow {

    private String label;
    private String subLabel;
    private long quantity;
    private BigDecimal amount = BigDecimal.ZERO;

    public ReportRow() {
    }

    public ReportRow(String label, long quantity, BigDecimal amount) {
        this.label = label;
        this.quantity = quantity;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getSubLabel() { return subLabel; }
    public void setSubLabel(String subLabel) { this.subLabel = subLabel; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
