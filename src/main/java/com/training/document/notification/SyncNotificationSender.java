package com.training.document.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "document.notification", name = "send-type", havingValue = "sync", matchIfMissing = true)
public class SyncNotificationSender implements NotificationSender {

    private final RestClient.Builder restClientBuilder;
    private final NotificationProperties properties;

    @Override
    public void send(NotificationMessage message) {
        try {
            restClientBuilder.build().post()
                    .uri(properties.getUrl() + "/notification/send")
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notification sent synchronously: message={}", message.message());
        } catch (RestClientException e) {
            log.warn("Failed to send notification synchronously: message={}", message.message(), e);
        }
    }
}
