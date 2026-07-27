package com.wattsmart.backend.notifications;

public record EmailSendResult(
        String providerMessageId,
        String responsePayload
) {
}
