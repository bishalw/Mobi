package com.mobi.notification.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Renders templates/email/&lt;template&gt;.html with Thymeleaf and sends it over SMTP.
 *
 * <p>SMTP keeps this provider-neutral: Mailpit locally (every message lands in its web
 * inbox, nothing leaves the machine), and Amazon SES, SendGrid or Postmark in the
 * cloud through their SMTP endpoints, configured with spring.mail.* only.
 */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String from;

    public EmailSender(JavaMailSender mailSender, TemplateEngine templateEngine,
                       @Value("${notifications.mail.from}") String from) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.from = from;
    }

    public void send(String to, String subject, String template, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        context.setVariable("subject", subject);
        String html = templateEngine.process("email/" + template, context);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException exp) {
            // Rethrown so the Kafka error handler retries with backoff and, if the mail
            // server stays down, parks the event on the DLT rather than dropping it.
            throw new DeliveryException("Email to " + to + " failed: " + exp.getMessage(), exp, rejectedAddress(exp));
        }
    }

    /**
     * The server refused the recipient address itself: an SMTP 5xx on RCPT that is about the
     * mailbox (5.1.x), not about us. 5.7.x (relay denied, auth required, sender not verified)
     * means our own mail setup is wrong, and stays retryable so it is noticed.
     */
    static boolean rejectedAddress(Throwable exp) {
        for (Throwable t = exp; t != null; t = t.getCause()) {
            if (t instanceof org.springframework.mail.MailSendException send) {
                for (Exception failed : send.getFailedMessages().values()) {
                    if (rejectedAddress(failed)) {
                        return true;
                    }
                }
            }
            if (t instanceof jakarta.mail.SendFailedException sendFailed
                    && sendFailed.getInvalidAddresses() != null && sendFailed.getInvalidAddresses().length > 0) {
                String reply = deepestMessage(sendFailed);
                boolean mailboxProblem = reply.matches("(?s).*\\b5\\.1\\.\\d+\\b.*")
                        || (reply.matches("(?s).*\\b55[03]\\b.*") && !reply.matches("(?s).*\\b5\\.7\\.\\d+\\b.*"));
                if (mailboxProblem) {
                    return true;
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String deepestMessage(Throwable exp) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = exp; t != null; t = t.getCause() == t ? null : t.getCause()) {
            all.append(' ').append(t.getMessage());
            if (t instanceof jakarta.mail.MessagingException messaging && messaging.getNextException() != null
                    && messaging.getNextException() != t.getCause()) {
                all.append(' ').append(messaging.getNextException().getMessage());
            }
        }
        return all.toString();
    }
}
