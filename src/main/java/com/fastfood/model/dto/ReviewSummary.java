package com.fastfood.model.dto;

import com.fastfood.common.util.StarRating;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Điểm trung bình và số lượt đánh giá của một món. */
public class ReviewSummary {

    private BigDecimal average = BigDecimal.ZERO;
    private int count;

    public BigDecimal getAverage() { return average; }
    public void setAverage(BigDecimal average) {
        this.average = average == null ? BigDecimal.ZERO : average;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public boolean isEmptySummary() { return count == 0; }

    /** Điểm trung bình làm tròn một chữ số, ví dụ {@code 4.3}. */
    public BigDecimal getAverageRounded() {
        return average.setScale(1, RoundingMode.HALF_UP);
    }

    /** Sao đặc theo điểm trung bình, làm tròn tới sao gần nhất. */
    public String getStars() {
        return StarRating.of(average);
    }
}
