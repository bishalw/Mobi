package com.mobi.notification.sender;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders every email template with realistic data, so a broken expression fails the build. */
class EmailTemplatesTest {

    private static TemplateEngine engine;

    public record Item(String productName, Integer quantity, BigDecimal lineTotal) {
    }

    @BeforeAll
    static void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine springEngine = new SpringTemplateEngine();
        springEngine.setTemplateResolver(resolver);
        engine = springEngine;
    }

    @ParameterizedTest
    @ValueSource(strings = {"welcome", "order-placed", "order-confirmed", "order-cancelled",
            "payment-receipt", "payment-declined", "payment-refunded",
            "po-sent", "po-accepted", "po-rejected", "shipment-notice", "low-stock"})
    void rendersWithinLayout(String template) {
        Context context = new Context();
        context.setVariables(Map.of(
                "subject", "Subject",
                "username", "ada",
                "orderId", 42L,
                "total", new BigDecimal("2398.00"),
                "items", List.of(new Item("Pixel 10 Pro", 2, new BigDecimal("2398.00"))),
                "amount", new BigDecimal("2398.00"),
                "currency", "USD",
                "cardBrand", "visa",
                "cardLast4", "4242",
                "paymentId", "pay-1"));
        context.setVariable("failureMessage", "Your card was declined.");
        context.setVariables(Map.of(
                "poNumber", "PO-000042",
                "supplierName", "Mobi Distribution",
                "note", "Discontinued",
                "lines", List.of(Map.of("productId", 3L, "name", "Pixel 8", "quantityOrdered", 50, "quantityShipped", 48)),
                "productName", "Pixel 8",
                "productId", 42L,
                "availableQuantity", 4,
                "reorderLevel", 5,
                "warehouseLocation", "Shelf C2"));

        String html = engine.process("email/" + template, context);

        assertTrue(html.contains(">mobi<"), "layout header missing");
        assertFalse(html.matches("(?s).*\\sth:[a-z]+=.*"), "unprocessed Thymeleaf attribute:\n" + html);
        if (template.startsWith("order-") || template.startsWith("payment-")) {
            assertTrue(html.contains("#42"), "order number missing");
        }
        if (template.equals("order-confirmed")) {
            assertTrue(html.contains("Pixel 10 Pro") && html.contains("$2,398.00"), "line items missing");
        }
        if (template.equals("po-sent")) {
            assertTrue(html.contains("PO-000042") && html.contains("Pixel 8") && html.contains("× 50"), "PO lines missing");
        }
        if (template.equals("shipment-notice")) {
            assertTrue(html.contains("× 48"), "shipped quantity missing");
        }
        if (template.equals("po-rejected")) {
            assertTrue(html.contains("Discontinued"), "reason missing");
        }
        if (template.equals("payment-receipt")) {
            assertTrue(html.contains("visa •••• 4242"), "card summary missing");
        }
    }
}
