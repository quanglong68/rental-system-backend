package com.rental.user.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.rental.user.repository.DeviceTokenRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class NotificationChannelService {
    private final JavaMailSender mailSender;
    private final DeviceTokenRepository devices;
    private final boolean emailEnabled;
    private final boolean pushEnabled;
    private final String from;

    public NotificationChannelService(JavaMailSender mailSender, DeviceTokenRepository devices,
            @Value("${notifications.email.enabled:false}") boolean emailEnabled,
            @Value("${notifications.push.enabled:false}") boolean pushEnabled,
            @Value("${notifications.email.from:}") String from) {
        this.mailSender = mailSender; this.devices = devices; this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled; this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationDeliveryEvent event) {
        if (emailEnabled && event.email() != null && !event.email().isBlank()) sendEmail(event);
        if (pushEnabled) event.deviceTokens().forEach(token -> sendPush(token, event));
    }

    private void sendEmail(NotificationDeliveryEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) message.setFrom(from);
            message.setTo(event.email()); message.setSubject(event.title()); message.setText(event.content());
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Khong gui duoc email notification", ex);
        }
    }

    private void sendPush(String token, NotificationDeliveryEvent event) {
        try {
            Message message = Message.builder().setToken(token)
                    .setNotification(Notification.builder().setTitle(event.title()).setBody(event.content()).build())
                    .putData("type", "notification").build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception ex) {
            log.warn("Khong gui duoc push notification", ex);
        }
    }
}
