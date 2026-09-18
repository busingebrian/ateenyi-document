package com.training.document.notification;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "document.notification", name = "send-type", havingValue = "none")
public class NoOpNotificationSender implements NotificationSender {

    @Override
    public void send(NotificationMessage message) {
        log.debug("Notification sending disabled, skipping: message={}", message.message());
    }
}
