package com.wattsmart.backend.notifications;

public interface EmailSender {

    EmailSendResult send(EmailSendRequest request);
}
