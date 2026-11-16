package com.mobi.notification.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Product names for staff email. Procurement and inventory events carry only product ids,
 * so names come from product-service's public read API, cached in memory. If it can't be
 * reached the email still goes out, naming the product by id: a missing name must never
 * hold up or fail a notification.
 */
@Component
public class ProductNames {

    private static final Logger log = LoggerFactory.getLogger(ProductNames.class);

    private final Function<Long, String> lookup;
    private final Map<Long, String> cache = new ConcurrentHashMap<>();
    // After a failed lookup, product-service isn't asked again for this long, so an outage
    // costs one timeout per minute instead of one per product per email.
    private static final long BACKOFF_MS = 60_000;
    private final java.util.concurrent.atomic.AtomicLong unavailableUntil = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.function.LongSupplier clock;

    @Autowired
    public ProductNames(@Value("${services.product.url:http://localhost:8083}") String productServiceUrl,
                        @Value("${services.product.timeout-ms:2000}") long timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient client = RestClient.builder().baseUrl(productServiceUrl).requestFactory(requestFactory).build();
        this.lookup = id -> {
            Product product = client.get().uri("/productapi/products/{id}", id).retrieve().body(Product.class);
            return product == null ? null : product.name();
        };
        this.clock = System::currentTimeMillis;
    }

    /** For tests. */
    public ProductNames(Function<Long, String> lookup) {
        this(lookup, System::currentTimeMillis);
    }

    public ProductNames(Function<Long, String> lookup, java.util.function.LongSupplier clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    public String nameOf(Long productId) {
        if (productId == null) {
            return "Unknown product";
        }
        String cached = cache.get(productId);
        if (cached != null) {
            return cached;
        }
        if (clock.getAsLong() < unavailableUntil.get()) {
            return "Product #" + productId;
        }
        try {
            String name = lookup.apply(productId);
            if (name != null && !name.isBlank()) {
                cache.put(productId, name);
                return name;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound gone) {
            // That one product is gone; product-service itself is fine.
            cache.put(productId, "Product #" + productId);
            return "Product #" + productId;
        } catch (RuntimeException exp) {
            unavailableUntil.set(clock.getAsLong() + BACKOFF_MS);
            log.warn("Could not look up product {} (not asking again for {} s): {}", productId, BACKOFF_MS / 1000, exp.getMessage());
        }
        return "Product #" + productId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(Long id, String name) {
    }
}
