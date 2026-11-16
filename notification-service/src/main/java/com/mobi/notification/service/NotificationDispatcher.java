package com.mobi.notification.service;

import com.mobi.notification.contact.UserContact;
import com.mobi.notification.contact.UserContactRepository;
import com.mobi.notification.log.Channel;
import com.mobi.notification.log.DeliveryStatus;
import com.mobi.notification.log.NotificationRecord;
import com.mobi.notification.log.NotificationRecordRepository;
import com.mobi.notification.sender.DeliveryException;
import com.mobi.notification.sender.EmailSender;
import com.mobi.notification.sender.SmsSender;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sends one message per (event, channel), at most once.
 *
 * <p>Delivery happens before the log row is written. If the process dies in between,
 * the redelivered event sends again: a rare duplicate is the price of never silently
 * losing a receipt, which is the right trade for transactional mail.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final UserContactRepository contacts;
    private final NotificationRecordRepository records;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final MeterRegistry meterRegistry;

    public NotificationDispatcher(UserContactRepository contacts, NotificationRecordRepository records,
                                  EmailSender emailSender, SmsSender smsSender, MeterRegistry meterRegistry) {
        this.contacts = contacts;
        this.records = records;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.meterRegistry = meterRegistry;
    }

    public void email(String eventId, Long userId, String template, String subject, Map<String, Object> model) {
        dispatch(eventId, Channel.EMAIL, userId, template, UserContact::getEmail, (contact, to) -> {
            Map<String, Object> variables = new HashMap<>(model);
            variables.put("username", contact.getUsername());
            emailSender.send(to, subject, template, variables);
        });
    }

    /**
     * One email to every enabled account with {@code role}. Each recipient is deduplicated
     * on its own key ({@code eventId:userId}), so a redelivery skips whoever already got it.
     */
    public void emailRole(String eventId, String role, String template, String subject, Map<String, Object> model) {
        if (eventId == null) {
            log.warn("Event without an id for template {}; cannot deduplicate, skipping", template);
            return;
        }
        List<UserContact> recipients = contacts.findByRoleAndEnabledTrueOrderByUserIdAsc(role);
        if (recipients.isEmpty()) {
            if (contacts.count() == 0) {
                // The user projection is still empty (first start, user-events not read yet):
                // retry rather than lose the email.
                throw new IllegalStateException("User projection is empty; retrying " + template + " (event " + eventId + ")");
            }
            // Users are known but nobody has this role: nothing to send, recorded so it shows.
            skip(eventId, template, "no enabled " + role + " accounts");
            return;
        }
        // One failing recipient must not stop the others. A failure is rethrown at the end so
        // the event is retried; recipients already sent to are skipped by their own key.
        DeliveryException firstFailure = null;
        for (UserContact recipient : recipients) {
            try {
                email(eventId + ":" + recipient.getUserId(), recipient.getUserId(), template, subject, model);
            } catch (DeliveryException exp) {
                log.warn("{} to user {} failed, continuing with the rest: {}", template, recipient.getUserId(), exp.getMessage());
                if (firstFailure == null) {
                    firstFailure = exp;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /** Records that an event was deliberately not emailed (e.g. too old), so it shows in the log table. */
    public void skip(String eventId, String template, String reason) {
        if (eventId == null || records.existsByEventIdAndChannel(eventId, Channel.EMAIL)) {
            return;
        }
        record(new NotificationRecord(eventId, Channel.EMAIL, template, null, null, DeliveryStatus.SKIPPED, reason));
        count(Channel.EMAIL, template, "skipped");
    }

    /** An email to an address that isn't a user account, such as a supplier's order desk. */
    public void emailAddress(String eventId, String to, String template, String subject, Map<String, Object> model) {
        if (eventId == null) {
            log.warn("Event without an id for template {}; cannot deduplicate, skipping", template);
            return;
        }
        if (records.existsByEventIdAndChannel(eventId, Channel.EMAIL)) {
            log.debug("EMAIL for event {} already handled", eventId);
            return;
        }
        if (to == null || to.isBlank()) {
            record(new NotificationRecord(eventId, Channel.EMAIL, template, null, null, DeliveryStatus.SKIPPED, "no email address"));
            count(Channel.EMAIL, template, "skipped");
            return;
        }
        try {
            emailSender.send(to, subject, template, new HashMap<>(model));
        } catch (DeliveryException exp) {
            if (!exp.isPermanent()) {
                throw exp;
            }
            record(new NotificationRecord(eventId, Channel.EMAIL, template, null, to, DeliveryStatus.SKIPPED, "address rejected"));
            count(Channel.EMAIL, template, "rejected");
            return;
        }
        record(new NotificationRecord(eventId, Channel.EMAIL, template, null, to, DeliveryStatus.SENT, null));
        count(Channel.EMAIL, template, "sent");
        log.info("Sent EMAIL {} for event {} to {}", template, eventId, to);
    }

    public void sms(String eventId, Long userId, String template, String body) {
        dispatch(eventId, Channel.SMS, userId, template, UserContact::getPhone, (contact, to) -> smsSender.send(to, body));
    }

    private void dispatch(String eventId, Channel channel, Long userId, String template,
                          Function<UserContact, String> address, java.util.function.BiConsumer<UserContact, String> send) {
        if (eventId == null) {
            log.warn("Event without an id for template {}; cannot deduplicate, skipping", template);
            return;
        }
        if (records.existsByEventIdAndChannel(eventId, channel)) {
            log.debug("{} for event {} already handled", channel, eventId);
            return;
        }
        UserContact contact = userId == null ? null : contacts.findById(userId).orElse(null);
        String to = contact == null ? null : address.apply(contact);
        if (contact == null || !contact.isEnabled() || to == null || to.isBlank()) {
            String reason = contact == null ? "no contact on file" : !contact.isEnabled() ? "account disabled" : "no " + channel.name().toLowerCase() + " address";
            record(new NotificationRecord(eventId, channel, template, userId, null, DeliveryStatus.SKIPPED, reason));
            count(channel, template, "skipped");
            return;
        }

        try {
            send.accept(contact, to);
        } catch (DeliveryException exp) {
            if (!exp.isPermanent()) {
                throw exp;
            }
            // The server refused this address; retrying would fail forever and block the event.
            record(new NotificationRecord(eventId, channel, template, userId, to, DeliveryStatus.SKIPPED, "address rejected"));
            count(channel, template, "rejected");
            return;
        }
        record(new NotificationRecord(eventId, channel, template, userId, to, DeliveryStatus.SENT, null));
        count(channel, template, "sent");
        log.info("Sent {} {} for event {} to user {}", channel, template, eventId, userId);
    }

    private void record(NotificationRecord record) {
        try {
            records.save(record);
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent redelivery got there first; the message is already accounted for.
            log.debug("Duplicate notification record for event {}", record.getEventId());
        }
    }

    private void count(Channel channel, String template, String outcome) {
        meterRegistry.counter("notifications.dispatched", "channel", channel.name().toLowerCase(),
                "template", template, "outcome", outcome).increment();
    }
}
