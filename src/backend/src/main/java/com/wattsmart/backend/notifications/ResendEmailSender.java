package com.wattsmart.backend.notifications;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Primary
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
@Slf4j
public class ResendEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String apiKey;
    private final String from;

    public ResendEmailSender(
            @Value("${app.email.resend.base-url}") String baseUrl,
            @Value("${app.email.resend.api-key}") String apiKey,
            @Value("${app.email.from}") String from,
            @Value("${app.email.timeout}") Duration timeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public EmailSendResult send(EmailSendRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured.");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("EMAIL_FROM is not configured.");
        }

        ResendSendEmailResponse response;
        try {
            response = restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Idempotency-Key", request.notificationId().toString())
                    .body(new ResendSendEmailRequest(
                            from,
                            List.of(request.recipientEmail()),
                            request.subject(),
                            request.body()))
                    .retrieve()
                    .body(ResendSendEmailResponse.class);
        } catch (RuntimeException exception) {
            log.warn("Resend email request failed. notificationId={}, to={}, message={}",
                    request.notificationId(),
                    request.recipientEmail(),
                    exception.getMessage(),
                    exception);
            throw exception;
        }

        String providerMessageId = response != null ? response.id() : null;
        return new EmailSendResult(providerMessageId, "{\"provider\":\"resend\",\"id\":\"%s\"}".formatted(providerMessageId));
    }

    private record ResendSendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String text
    ) {
    }

    private record ResendSendEmailResponse(String id) {
    }
}
