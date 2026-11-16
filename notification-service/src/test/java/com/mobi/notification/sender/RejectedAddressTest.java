package com.mobi.notification.sender;

import jakarta.mail.Address;
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
}
