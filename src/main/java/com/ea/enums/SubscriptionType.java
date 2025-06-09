package com.ea.enums;

public enum SubscriptionType {
    SCOREBOARD("scoreboard"),
    EVENTS("events"),
    IP_UPDATE("ip-update"),
    ACTIVITY_MAP("activity-map");

    private final String value;

    SubscriptionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SubscriptionType fromValue(String value) {
        for (SubscriptionType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown subscription type: " + value);
    }
}
