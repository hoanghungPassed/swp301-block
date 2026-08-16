package com.fastfood.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả nạp một mẫu đặt nhanh vào giỏ hàng.
 * <p>
 * Có riêng một kiểu trả về thay vì trả số món đã thêm, vì phần đáng nói nhất là phần <b>không</b>
 * thêm được: mẫu lưu từ tháng trước có thể chứa món đã ngừng bán. Nạp thiếu trong im lặng thì
 * khách chỉ phát hiện ra ở bước cuối, lúc đã chọn xong giờ đến lấy.
 */
public class TemplateApplyResult {

    private int addedCount;
    private final List<String> skippedNames = new ArrayList<>();

    public int getAddedCount() { return addedCount; }

    public void countAdded() { this.addedCount++; }

    public List<String> getSkippedNames() { return skippedNames; }

    public void skip(String productName) { this.skippedNames.add(productName); }

    public boolean isAnythingAdded() { return addedCount > 0; }

    public boolean isAnythingSkipped() { return !skippedNames.isEmpty(); }

    /** Tên các món bị bỏ qua, nối lại để đưa thẳng vào câu thông báo. */
    public String getSkippedText() {
        return String.join(", ", skippedNames);
    }
}
