package com.training.document.notification;

import com.training.document.entity.DocStatus;

public record NotificationMessage(String message, String email) {

    public static NotificationMessage forStatus(String drn, DocStatus status, String email) {
        return new NotificationMessage("Document with %s has got status %s".formatted(drn, status), email);
    }
}
