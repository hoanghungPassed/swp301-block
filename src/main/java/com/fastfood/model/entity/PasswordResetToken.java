package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Một lần xin đặt lại mật khẩu. Ánh xạ tới bảng PasswordResetToken.
 * <p>
 * Không có thuộc tính nào giữ mã gốc: mã chỉ tồn tại trong liên kết gửi cho người dùng, còn
 * ở đây chỉ có bản băm của nó. Xem ghi chú ở bảng trong {@code database/FastFoodPreorder.sql}.
 */
public class PasswordResetToken {

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

    /** Còn đổi được mật khẩu bằng mã này không. */
    public boolean isUsable(LocalDateTime now) { return !isUsed() && !isExpired(now); }
}
