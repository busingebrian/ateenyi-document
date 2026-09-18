package com.training.document.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "document.notification")
public class NotificationProperties {

    private SendType sendType = SendType.sync;
    private String url;
    private String email;

    public enum SendType {
        sync, none
    }
}
