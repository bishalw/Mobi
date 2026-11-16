package com.mobi.notification.sender;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Twilio's Messages API over plain HTTPS (no SDK). Enabled with
 * notifications.sms.provider=twilio plus TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and
 * TWILIO_FROM_NUMBER. Twilio test credentials accept the magic number +15005550006
 * as a sender, which exercises the integration without sending a real text.
 */
@Component
@ConditionalOnProperty(name = "notifications.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private final RestClient restClient;
    private final String accountSid;
    private final String from;

    public TwilioSmsSender(@Value("${notifications.sms.twilio.account-sid}") String accountSid,
                           @Value("${notifications.sms.twilio.auth-token}") String authToken,
                           @Value("${notifications.sms.twilio.from-number}") String from,
                           ObservationRegistry observationRegistry) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank()) {
            throw new IllegalStateException("Twilio SMS needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM_NUMBER");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.twilio.com")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Basic " + basic)
                .observationRegistry(observationRegistry)
                .build();
        this.accountSid = accountSid;
        this.from = from;
    }

    @Override
    public void send(String to, String body) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", from);
        form.add("Body", body);
        try {
            restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exp) {
            throw new DeliveryException("SMS to Twilio failed: " + exp.getMessage(), exp);
        }
    }
}
