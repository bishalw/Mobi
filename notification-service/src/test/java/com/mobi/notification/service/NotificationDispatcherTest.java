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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    private UserContactRepository contacts;
    private NotificationRecordRepository records;
    private EmailSender email;
    private SmsSender sms;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        contacts = mock(UserContactRepository.class);
        records = mock(NotificationRecordRepository.class);
        email = mock(EmailSender.class);
        sms = mock(SmsSender.class);
        dispatcher = new NotificationDispatcher(contacts, records, email, sms, new SimpleMeterRegistry());
    }

    @Test
    void sendsEmailAndRecordsIt() {
        when(contacts.findById(7L)).thenReturn(Optional.of(contact("ada@example.com", "+15551234567", true)));

        dispatcher.email("evt-1", 7L, "order-confirmed", "Confirmed", Map.of("orderId", 42L));

        verify(email).send(eq("ada@example.com"), eq("Confirmed"), eq("order-confirmed"), anyMap());
        assertEquals(DeliveryStatus.SENT, savedRecord().getStatus());
    }

    @Test
    void roleEmailGoesToEveryEnabledAccountWithThatRoleEachDeduplicatedAlone() {
        UserContact first = contact("a1@mobi.local", null, true);
        first.setUserId(1L);
        UserContact second = contact("a2@mobi.local", null, true);
        second.setUserId(2L);
        when(contacts.findByRoleAndEnabledTrueOrderByUserIdAsc("ADMIN")).thenReturn(java.util.List.of(first, second));
        when(contacts.findById(1L)).thenReturn(Optional.of(first));
        when(contacts.findById(2L)).thenReturn(Optional.of(second));
        when(records.existsByEventIdAndChannel("evt-9:1", Channel.EMAIL)).thenReturn(true); // already sent before a redelivery

        dispatcher.emailRole("evt-9", "ADMIN", "low-stock", "Low stock", Map.of());

        verify(email, never()).send(eq("a1@mobi.local"), any(), any(), anyMap());
        verify(email).send(eq("a2@mobi.local"), eq("Low stock"), eq("low-stock"), anyMap());
    }

    @Test
    void roleEmailBeforeAnyUsersAreKnownIsRetried() {
        when(contacts.findByRoleAndEnabledTrueOrderByUserIdAsc("WAREHOUSE")).thenReturn(java.util.List.of());
        when(contacts.count()).thenReturn(0L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> dispatcher.emailRole("evt-11", "WAREHOUSE", "shipment-notice", "Inbound", Map.of()));
    }

    @Test
    void roleWithNobodyInItIsRecordedNotRetried() {
        when(contacts.findByRoleAndEnabledTrueOrderByUserIdAsc("WAREHOUSE")).thenReturn(java.util.List.of());
        when(contacts.count()).thenReturn(5L);
        dispatcher.emailRole("evt-14", "WAREHOUSE", "shipment-notice", "Inbound", Map.of());
        assertEquals("no enabled WAREHOUSE accounts", savedRecord().getDetail());
    }

    @Test
    void oneFailingRecipientDoesNotStopTheRest() {
        UserContact first = contact("bad@mobi.local", null, true);
        first.setUserId(1L);
        UserContact second = contact("ok@mobi.local", null, true);
        second.setUserId(2L);
        when(contacts.findByRoleAndEnabledTrueOrderByUserIdAsc("ADMIN")).thenReturn(java.util.List.of(first, second));
        when(contacts.findById(1L)).thenReturn(Optional.of(first));
        when(contacts.findById(2L)).thenReturn(Optional.of(second));
        org.mockito.Mockito.doThrow(new DeliveryException("timeout")).when(email)
                .send(eq("bad@mobi.local"), any(), any(), anyMap());

        org.junit.jupiter.api.Assertions.assertThrows(DeliveryException.class,
                () -> dispatcher.emailRole("evt-12", "ADMIN", "po-accepted", "Accepted", Map.of()));
        verify(email).send(eq("ok@mobi.local"), eq("Accepted"), eq("po-accepted"), anyMap());
    }

    @Test
    void permanentlyRejectedAddressIsRecordedAndNotRetried() {
        when(contacts.findById(7L)).thenReturn(Optional.of(contact("gone@example.com", null, true)));
        org.mockito.Mockito.doThrow(new DeliveryException("550 no such user", null, true)).when(email)
                .send(eq("gone@example.com"), any(), any(), anyMap());

        dispatcher.email("evt-13", 7L, "welcome", "Welcome", Map.of());

        assertEquals(DeliveryStatus.SKIPPED, savedRecord().getStatus());
        assertEquals("address rejected", savedRecord().getDetail());
    }

    @Test
    void addressEmailReachesSomeoneWithoutAnAccountOnce() {
        dispatcher.emailAddress("evt-10", "orders@supplier.test", "po-sent", "New PO", Map.of("poNumber", "PO-1"));
        verify(email).send(eq("orders@supplier.test"), eq("New PO"), eq("po-sent"), anyMap());
        assertEquals("orders@supplier.test", savedRecord().getRecipient());

        when(records.existsByEventIdAndChannel("evt-10", Channel.EMAIL)).thenReturn(true);
        dispatcher.emailAddress("evt-10", "orders@supplier.test", "po-sent", "New PO", Map.of());
        verify(email, org.mockito.Mockito.times(1)).send(any(), any(), any(), anyMap());
    }

    @Test
    void redeliveredEventIsNotSentTwice() {
        when(records.existsByEventIdAndChannel("evt-1", Channel.EMAIL)).thenReturn(true);

        dispatcher.email("evt-1", 7L, "order-confirmed", "Confirmed", Map.of());

        verify(email, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void userWithoutPhoneIsSkippedForSms() {
        when(contacts.findById(7L)).thenReturn(Optional.of(contact("ada@example.com", null, true)));

        dispatcher.sms("evt-1", 7L, "order-confirmed", "hi");

        verify(sms, never()).send(anyString(), anyString());
        NotificationRecord record = savedRecord();
        assertEquals(DeliveryStatus.SKIPPED, record.getStatus());
        assertEquals("no sms address", record.getDetail());
    }

    @Test
    void disabledAccountGetsNothing() {
        when(contacts.findById(7L)).thenReturn(Optional.of(contact("ada@example.com", null, false)));

        dispatcher.email("evt-1", 7L, "order-confirmed", "Confirmed", Map.of());

        verify(email, never()).send(anyString(), anyString(), anyString(), anyMap());
        assertEquals("account disabled", savedRecord().getDetail());
    }

    @Test
    void deliveryFailurePropagatesForKafkaRetryAndIsNotRecorded() {
        when(contacts.findById(7L)).thenReturn(Optional.of(contact("ada@example.com", null, true)));
        doThrow(new DeliveryException("smtp down")).when(email).send(anyString(), anyString(), anyString(), anyMap());

        assertThrows(DeliveryException.class,
                () -> dispatcher.email("evt-1", 7L, "order-confirmed", "Confirmed", Map.of()));

        // No record, so the retried delivery is not mistaken for a duplicate.
        verify(records, never()).save(any());
    }

    private NotificationRecord savedRecord() {
        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(records).save(captor.capture());
        return captor.getValue();
    }

    private static UserContact contact(String email, String phone, boolean enabled) {
        UserContact contact = new UserContact();
        contact.setUserId(7L);
        contact.setUsername("ada");
        contact.setEmail(email);
        contact.setPhone(phone);
        contact.setEnabled(enabled);
        return contact;
    }
}
