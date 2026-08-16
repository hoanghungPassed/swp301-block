package com.fastfood.model.dto;

import java.util.List;

/**
 * Một trang kết quả kèm thông tin để vẽ thanh chuyển trang.
 * <p>
 * Trước đây các màn hình lịch sử đều cắt cứng bằng {@code SELECT TOP (n)}: dữ liệu bị bỏ
 * bớt mà giao diện không hề nói ra, nên người xem tưởng đó là toàn bộ và kết luận sai về
 * khoảng thời gian mình đang lọc. Có tổng số bản ghi thì trang mới nói được "đang xem
 * 21–40 trong 214".
 * <p>
 * Số trang đếm từ 1 vì con số này hiện thẳng ra cho người dùng đọc.
 */
public class Page<T> {

    /** Số dòng mỗi trang. Vừa đủ một màn hình mà không phải cuộn nhiều. */
    public static final int SIZE = 20;

    /** Chặn trên cho số dòng mỗi trang, phòng khi tham số đến từ địa chỉ do người dùng sửa. */
    public static final int MAX_SIZE = 200;

    private final List<T> items;
    private final int pageNo;
    private final int pageSize;
    private final long totalItems;

    public Page(List<T> items, int pageNo, int pageSize, long totalItems) {
        this.items = items;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    public List<T> getItems() { return items; }
    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public long getTotalItems() { return totalItems; }

    /**
     * Không đặt tên là {@code isEmpty}: EL có sẵn toán tử {@code empty}, nên viết
     * {@code ${pageData.empty}} trong trang JSP sẽ không dịch được.
     */
    public boolean isEmptyPage() { return items == null || items.isEmpty(); }

    public int getTotalPages() {
        if (totalItems <= 0) {
            return 1;
        }
        return (int) ((totalItems + pageSize - 1) / pageSize);
    }

    public boolean isFirst() { return pageNo <= 1; }
    public boolean isLast() { return pageNo >= getTotalPages(); }

    public int getPrevPage() { return Math.max(1, pageNo - 1); }
    public int getNextPage() { return Math.min(getTotalPages(), pageNo + 1); }

    /** Thứ tự của dòng đầu tiên trên trang, đếm từ 1. Trang rỗng thì trả 0. */
    public long getFirstIndex() {
        return isEmptyPage() ? 0 : (long) (pageNo - 1) * pageSize + 1;
    }

    /** Thứ tự của dòng cuối cùng trên trang. */
    public long getLastIndex() {
        return isEmptyPage() ? 0 : getFirstIndex() + items.size() - 1;
    }

    /** Cần thanh chuyển trang hay không — một trang duy nhất thì vẽ ra chỉ tổ rối. */
    public boolean isPaged() { return getTotalPages() > 1; }

    // ---------------------------------------------------------------- tiện ích

    /**
     * Ép số trang về khoảng hợp lệ. Địa chỉ có thể do người dùng gõ tay, nên
     * {@code ?page=0} hay {@code ?page=-5} phải hiểu thành trang đầu chứ không được
     * biến thành số âm rồi đi thẳng vào OFFSET của câu lệnh SQL.
     */
    public static int safePage(int requested) {
        return Math.max(1, requested);
    }

    public static int safeSize(int requested) {
        if (requested <= 0) {
            return SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }

    /** Số dòng phải bỏ qua để tới đầu trang. */
    public static int offset(int pageNo, int pageSize) {
        return (safePage(pageNo) - 1) * safeSize(pageSize);
    }
}
