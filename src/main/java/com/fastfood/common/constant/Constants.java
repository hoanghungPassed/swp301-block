package com.fastfood.common.constant;

public final class Constants {

    private Constants() {
    }

    public enum OrderStatus {

        PENDING_PAYMENT,

        CONFIRMED,

        PREPARING,

        READY,

        COMPLETED,

        CANCELLED,

        EXPIRED;

        public boolean isFinal() {
            return this == COMPLETED || this == CANCELLED || this == EXPIRED;
        }
    }

    public enum OrderItemStatus {

        WAITING,
        PREPARING,
        READY;

        public boolean canTransitionTo(OrderItemStatus next) {
            return (this == WAITING && next == PREPARING)
                || (this == PREPARING && next == READY);
        }
    }

    public enum KdsReleaseState {

        NOT_RELEASED,

        SCHEDULED,

        RELEASED_TO_KDS
    }

    public enum OrderSource {

        ONLINE_PREORDER,

        POS;

        public boolean isOnline() {
            return this == ONLINE_PREORDER;
        }

        public boolean requiresPickupCode() {
            return this == ONLINE_PREORDER;
        }
    }

    public enum PaymentMethod {

        ONLINE_GATEWAY,
        CASH;

        public boolean isAllowedFor(OrderSource source) {
            return source == OrderSource.POS || this == ONLINE_GATEWAY;
        }
    }

    public enum PaymentStatus {

        UNPAID,

        PENDING,

        PAID,

        FAILED,

        REFUNDED;

        public boolean isRetryable() {
            return this == FAILED;
        }

        public boolean isSettled() {
            return this == PAID;
        }
    }

    public enum RoleName {

        CUSTOMER,
        CASHIER,
        KITCHEN,
        ADMIN;

        public static RoleName from(String value) {
            RoleName role = parse(value);
            if (role == null) {
                throw new IllegalArgumentException("Unknown role: " + value);
            }
            return role;
        }

        public static RoleName parse(String value) {
            for (RoleName r : values()) {
                if (r.name().equalsIgnoreCase(value)) {
                    return r;
                }
            }
            return null;
        }
    }

    public enum IssueType {

        OUT_OF_STOCK,

        QUALITY,

        REMAKE,

        COUNTER_REJECT,

        OTHER;

        public static IssueType from(String value) {
            for (IssueType t : values()) {
                if (t.name().equalsIgnoreCase(value)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown issue type: " + value);
        }
    }

    public enum NotificationEvent {

        ORDER_CONFIRMED,
        ORDER_READY,
        ORDER_CANCELLED,
        ORDER_EXPIRED
    }

    public static final class AuditAction {

        public static final String PAYMENT_INITIATED = "PAYMENT_INITIATED";
        public static final String PAYMENT_PAID      = "PAYMENT_PAID";
        public static final String PAYMENT_FAILED    = "PAYMENT_FAILED";
        public static final String PAYMENT_REFUNDED  = "PAYMENT_REFUNDED";
        public static final String CALLBACK_IGNORED  = "CALLBACK_IGNORED";

        public static final String ORDER_CREATED     = "ORDER_CREATED";
        public static final String AUTO_CONFIRM      = "AUTO_CONFIRM";
        public static final String POS_CONFIRM       = "POS_CONFIRM";
        public static final String ORDER_EXPIRED     = "ORDER_EXPIRED";
        public static final String ORDER_CANCELLED   = "ORDER_CANCELLED";
        public static final String ORDER_COMPLETED   = "ORDER_COMPLETED";

        public static final String KDS_RELEASE       = "KDS_RELEASE";
        public static final String ITEM_START        = "ITEM_START";
        public static final String ITEM_READY        = "ITEM_READY";
        public static final String ITEM_HANDED_OVER  = "ITEM_HANDED_OVER";
        public static final String ITEM_RECEIVED     = "ITEM_RECEIVED";
        public static final String ORDER_READY       = "ORDER_READY";
        public static final String ISSUE_OPENED      = "ISSUE_OPENED";
        public static final String ISSUE_RESOLVED    = "ISSUE_RESOLVED";
        public static final String ISSUE_UPDATED     = "ISSUE_UPDATED";
        public static final String ISSUE_CANCELLED   = "ISSUE_CANCELLED";

        public static final String PREP_PLANNED      = "PREP_PLANNED";
        public static final String PREP_UPDATED      = "PREP_UPDATED";
        public static final String PREP_DONE         = "PREP_DONE";
        public static final String PREP_CANCELLED    = "PREP_CANCELLED";

        public static final String PICKUP_VERIFY_OK     = "PICKUP_VERIFY_OK";
        public static final String PICKUP_VERIFY_FAILED = "PICKUP_VERIFY_FAILED";
        public static final String HANDOFF              = "HANDOFF";

        public static final String LOGIN_SUCCESS     = "LOGIN_SUCCESS";
        public static final String LOGIN_FAILED      = "LOGIN_FAILED";
        public static final String LOGIN_BLOCKED     = "LOGIN_BLOCKED";
        public static final String LOGOUT            = "LOGOUT";
        public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
        public static final String PASSWORD_RESET_DONE      = "PASSWORD_RESET_DONE";

        public static final String EMAIL_VERIFY_SENT = "EMAIL_VERIFY_SENT";
        public static final String EMAIL_VERIFIED    = "EMAIL_VERIFIED";

        public static final String PRODUCT_CHANGED   = "PRODUCT_CHANGED";
        public static final String CATEGORY_CHANGED  = "CATEGORY_CHANGED";
        public static final String USER_CHANGED      = "USER_CHANGED";

        public static final String PRODUCT_RETIRED   = "PRODUCT_RETIRED";
        public static final String PRODUCT_RESTORED  = "PRODUCT_RESTORED";
        public static final String CATEGORY_RETIRED  = "CATEGORY_RETIRED";
        public static final String CATEGORY_RESTORED = "CATEGORY_RESTORED";

        public static final String TARGET_CREATED    = "TARGET_CREATED";
        public static final String TARGET_UPDATED    = "TARGET_UPDATED";
        public static final String TARGET_DELETED    = "TARGET_DELETED";

        private AuditAction() {
        }
    }

    public static final class BusinessRule {

        public static final int PICKUP_MIN_LEAD_MINUTES = 30;

        public static final int KITCHEN_PREP_LEAD_MINUTES = 20;

        public static final int PAYMENT_EXPIRY_MINUTES = 15;

        public static final int PICKUP_OVERDUE_MINUTES = 30;

        public static final int PICKUP_CODE_LENGTH = 6;

        public static final int MAX_QUANTITY_PER_LINE = 50;

        public static final int RELEASE_ACCURACY_SECONDS = 60;

        public static final int STORE_OPEN_HOUR = 7;
        public static final int STORE_CLOSE_HOUR = 21;

        private BusinessRule() {
        }
    }
}
