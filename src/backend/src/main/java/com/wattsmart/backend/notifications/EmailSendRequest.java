package com.wattsmart.backend.notifications;

import java.util.UUID;

public record EmailSendRequest(
        UUID notificationId,
        String recipientEmail,
        String subject,
        String body
) {
}
