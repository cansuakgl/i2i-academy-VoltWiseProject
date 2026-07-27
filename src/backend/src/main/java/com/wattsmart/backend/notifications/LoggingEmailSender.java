package com.wattsmart.backend.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public EmailSendResult send(EmailSendRequest request) {
        log.info("""
                        Email notification logged instead of sent.
                        notificationId={}
                        to={}
                        subject={}
                        body={}
                        """,
                request.notificationId(),
                request.recipientEmail(),
                request.subject(),
                request.body());
        return new EmailSendResult("logging-" + request.notificationId(), "{\"provider\":\"logging\"}");
    }
}
