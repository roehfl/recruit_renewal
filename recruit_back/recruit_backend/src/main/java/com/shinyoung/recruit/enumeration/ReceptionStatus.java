package com.shinyoung.recruit.enumeration;

import java.time.LocalDateTime;

public enum ReceptionStatus {
    UPCOMING,
    ACCEPTING,
    CLOSED;

    public static ReceptionStatus from(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (now.isBefore(start)) {
            return UPCOMING;
        }
        if (now.isAfter(end)) {
            return CLOSED;
        }
        return ACCEPTING;
    }
}
