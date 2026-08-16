package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Một lần gửi thư xác thực địa chỉ email. Ánh xạ tới bảng EmailVerificationToken.
 * <p>
 * Giống {@link PasswordResetToken} từng thuộc tính một, và cố ý không rút thành lớp cha chung.
 * Hai loại mã này chỉ tình cờ có cùng hình dáng chứ không cùng ý nghĩa: một mã mở đường đổi
 * mật khẩu, một mã bật một cờ. Gộp lại thì mỗi lần sửa một luồng phải nghĩ hộ cả luồng kia, và
 * chỗ dễ nhầm nhất — nhầm loại mã — lại thành chỗ trình biên dịch không còn nhìn thấy được.
 * <p>
 * Không có thuộc tính nào giữ mã gốc: mã chỉ tồn tại trong liên kết gửi đi, ở đây chỉ có bản
 * băm của nó. Xem ghi chú ở bảng trong {@code database/FastFoodPreorder.sql}.
 */
public class EmailVerificationToken {

    private long tokenId;
    private int userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private String requestedIp;
    private LocalDateTime createdAt;

    public long getTokenId() { return tokenId; }
    public void setTokenId(long tokenId) { this.tokenId = tokenId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public String getRequestedIp() { return requestedIp; }
    public void setRequestedIp(String requestedIp) { this.requestedIp = requestedIp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Đã dùng rồi thì không dùng lại được, kể cả khi vẫn còn trong hạn. */
    public boolean isUsed() { return usedAt != null; }

    public boolean isExpired(LocalDateTime now) { return expiresAt != null && !now.isBefore(expiresAt); }

    /** Còn xác thực được bằng mã này không. */
    public boolean isUsable(LocalDateTime now) { return !isUsed() && !isExpired(now); }
}
