package com.fastfood.model.entity;

import com.fastfood.common.constant.Constants.RoleName;
import java.time.LocalDateTime;

public final class UserEntities {

    private UserEntities() {
    }

    public static class Role {
        private int roleId;
        private String name;
        private String description;

        public int getRoleId() { return roleId; }
        public void setRoleId(int roleId) { this.roleId = roleId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public RoleName toEnum() { return RoleName.from(name); }
    }

    public static class User {
        private int userId;
        private String fullName;
        private String email;
        private String phone;
        private String passwordHash;
        private int roleId;
        private String status;
        private boolean mustChangePassword;
        private boolean emailVerified;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private String roleName;

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

        public int getRoleId() { return roleId; }
        public void setRoleId(int roleId) { this.roleId = roleId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }

        public boolean isActive() { return "ACTIVE".equals(status); }

        public boolean isMustChangePassword() { return mustChangePassword; }
        public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

        public boolean isEmailVerified() { return emailVerified; }
        public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    }

    public static class EmailVerificationToken {

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

        public boolean isUsed() { return usedAt != null; }

        public boolean isExpired(LocalDateTime now) { return expiresAt != null && !now.isBefore(expiresAt); }

        public boolean isUsable(LocalDateTime now) { return !isUsed() && !isExpired(now); }
    }

    public static class PasswordResetToken {

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

        public boolean isUsed() { return usedAt != null; }

        public boolean isExpired(LocalDateTime now) { return expiresAt != null && !now.isBefore(expiresAt); }

        public boolean isUsable(LocalDateTime now) { return !isUsed() && !isExpired(now); }
    }

    public static class Notification {

        private int notificationId;
        private Integer userId;
        private int orderId;
        private String channel;
        private String eventType;
        private String content;
        private String status;
        private LocalDateTime sentAt;
        private LocalDateTime readAt;

        public int getNotificationId() { return notificationId; }
        public void setNotificationId(int notificationId) { this.notificationId = notificationId; }

        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }

        public int getOrderId() { return orderId; }
        public void setOrderId(int orderId) { this.orderId = orderId; }

        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getSentAt() { return sentAt; }
        public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

        public LocalDateTime getReadAt() { return readAt; }
        public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

        public boolean isUnread() { return readAt == null; }

        public boolean isFailed() { return "FAILED".equals(status); }
    }

}
