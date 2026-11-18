package com.mobi.notification.sender;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectedAddressTest {

    @Test
    void refusedRecipientIsPermanentAndAnOutageIsNot() throws Exception {
        SendFailedException refused = new SendFailedException("550 5.1.1 no such user", null,
                new Address[0], new Address[0], new Address[] {new InternetAddress("gone@example.com")});
        assertTrue(EmailSender.rejectedAddress(new MailSendException(Map.of(new Object(), refused))));
        assertTrue(EmailSender.rejectedAddress(refused));

        assertFalse(EmailSender.rejectedAddress(new MailSendException("Connection refused")));

        // Our own setup is wrong (relay/auth/unverified sender): keep retrying so it's noticed.
        SendFailedException relayDenied = new SendFailedException("550 5.7.1 Relaying denied", null,
                new Address[0], new Address[0], new Address[] {new InternetAddress("someone@example.com")});
        assertFalse(EmailSender.rejectedAddress(relayDenied));
    }

    private static final String FROM = "Mobi <no-reply@staging.mobi.example.com>";

    @Test
    void sesSandboxRefusingAnUnverifiedRecipientIsPermanent() {
        MessagingException sandbox = new MessagingException("554 Message rejected: Email address is not verified. "
                + "The following identities failed the check in region US-EAST-1: Admin@Mobi.local");
        assertTrue(EmailSender.unverifiedRecipient(sandbox, "admin@mobi.local", FROM));
        // Wrapped the way JavaMailSender reports it.
        assertTrue(EmailSender.unverifiedRecipient(new MailSendException(Map.of(new Object(), sandbox)),
                "admin@mobi.local", FROM));
        // Someone else was the unverified one: not about this recipient.
        assertFalse(EmailSender.unverifiedRecipient(sandbox, "ada@example.com", FROM));
        assertFalse(EmailSender.unverifiedRecipient(sandbox, "min@mobi.local", FROM));
    }

    @Test
    void unverifiedSenderStaysRetryable() {
        // Our domain isn't verified yet: SES lists the sender (and the recipient). Our setup is wrong.
        MessagingException domainPending = new MessagingException("554 Message rejected: Email address is not verified. "
                + "The following identities failed the check in region US-EAST-1: no-reply@staging.mobi.example.com, admin@mobi.local");
        assertFalse(EmailSender.unverifiedRecipient(domainPending, "admin@mobi.local", FROM));
        assertFalse(EmailSender.unverifiedRecipient(new MailSendException("Connection refused"), "admin@mobi.local", FROM));
    }
}
