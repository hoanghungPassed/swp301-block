package com.fastfood.model.dto;

import java.math.BigDecimal;

/** Các con số trên bảng điều khiển của quản trị viên. */
public class DashboardKpi {

    private BigDecimal grossRevenue = BigDecimal.ZERO;
    private BigDecimal netRevenue = BigDecimal.ZERO;
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    private int onlineOrderCount;
    private int posOrderCount;
    private int completedOrderCount;
    private int cancelledOrderCount;
    private int expiredOrderCount;
    private int readyOrderCount;
    private int overduePickupCount;
    private int onTimeReadyCount;
    private int totalReadyMeasured;
    private Double avgPrepLeadMinutes;

    /** Tiền thật sự thu được trong kỳ, chưa trừ hoàn. Tính theo paid_at. */
    public BigDecimal getGrossRevenue() { return grossRevenue; }
    public void setGrossRevenue(BigDecimal grossRevenue) { this.grossRevenue = grossRevenue; }

    /** Doanh thu thuần = đã thu trong kỳ trừ đã hoàn trong kỳ (tính theo refunded_at). */
    public BigDecimal getNetRevenue() { return netRevenue; }
    public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public int getOnlineOrderCount() { return onlineOrderCount; }
    public void setOnlineOrderCount(int onlineOrderCount) { this.onlineOrderCount = onlineOrderCount; }

    public int getPosOrderCount() { return posOrderCount; }
    public void setPosOrderCount(int posOrderCount) { this.posOrderCount = posOrderCount; }

    public int getCompletedOrderCount() { return completedOrderCount; }
    public void setCompletedOrderCount(int completedOrderCount) { this.completedOrderCount = completedOrderCount; }

    public int getCancelledOrderCount() { return cancelledOrderCount; }
    public void setCancelledOrderCount(int cancelledOrderCount) { this.cancelledOrderCount = cancelledOrderCount; }

    public int getExpiredOrderCount() { return expiredOrderCount; }
    public void setExpiredOrderCount(int expiredOrderCount) { this.expiredOrderCount = expiredOrderCount; }

    public int getReadyOrderCount() { return readyOrderCount; }
    public void setReadyOrderCount(int readyOrderCount) { this.readyOrderCount = readyOrderCount; }

    public int getOverduePickupCount() { return overduePickupCount; }
    public void setOverduePickupCount(int overduePickupCount) { this.overduePickupCount = overduePickupCount; }

    public int getOnTimeReadyCount() { return onTimeReadyCount; }
    public void setOnTimeReadyCount(int onTimeReadyCount) { this.onTimeReadyCount = onTimeReadyCount; }

    public int getTotalReadyMeasured() { return totalReadyMeasured; }
    public void setTotalReadyMeasured(int totalReadyMeasured) { this.totalReadyMeasured = totalReadyMeasured; }

    public Double getAvgPrepLeadMinutes() { return avgPrepLeadMinutes; }
    public void setAvgPrepLeadMinutes(Double avgPrepLeadMinutes) { this.avgPrepLeadMinutes = avgPrepLeadMinutes; }

    public int getTotalOrderCount() { return onlineOrderCount + posOrderCount; }

    /** Tỷ lệ đơn đặt trước có món sẵn sàng trước giờ hẹn — chỉ số vận hành riêng của kênh đặt trước. */
    public double getOnTimeReadyRate() {
        return totalReadyMeasured == 0 ? 0 : (100.0 * onTimeReadyCount / totalReadyMeasured);
    }
}
