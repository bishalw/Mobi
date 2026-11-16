package com.mobi.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints the two kinds of access token this service hands out.
 *
 * <ul>
 *   <li><b>User tokens</b> after login or refresh: {@code sub} is the username, plus
 *       {@code userId} and {@code role}, and {@code supplier_id} for a supplier account.</li>
 *   <li><b>Service tokens</b> from the client-credentials grant: {@code sub} is the
 *       calling service (e.g. {@code order-service}) and {@code role} is
 *       {@code SERVICE}. There is no {@code userId}: the caller acts as itself.</li>
 * </ul>
 *
 * <p>Both carry {@code iss} and {@code aud}, which every service checks, so a token
 * from some other issuer, or one meant for some other API, is rejected even if the
 * signature were somehow valid.
 */
@Component
public class TokenIssuer {

    public static final String AUDIENCE = "mobi-api";
    public static final String SERVICE_ROLE = "SERVICE";
    public static final String SUPPLIER_ID_CLAIM = "supplier_id";

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration userTokenTtl;
    private final Duration serviceTokenTtl;

    public TokenIssuer(SigningKeys keys,
                       @Value("${auth.jwt.issuer}") String issuer,
                       @Value("${jwt.expiration-ms}") long userTokenTtlMs,
                       @Value("${auth.service-token.ttl-seconds:300}") long serviceTokenTtlSeconds) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
        this.issuer = issuer;
        this.userTokenTtl = Duration.ofMillis(userTokenTtlMs);
        this.serviceTokenTtl = Duration.ofSeconds(serviceTokenTtlSeconds);
    }

    public String issueUserToken(Long userId, String username, String role) {
        return issueUserToken(userId, username, role, null);
    }

    public String issueUserToken(Long userId, String username, String role, Long supplierId) {
        // Roles are stored bare ("ADMIN"); strip a legacy prefix so services can map
        // the claim straight to an authority.
        String bareRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        JwtClaimsSet.Builder claims = base(username, userTokenTtl)
                .claim("userId", userId)
                .claim("role", bareRole);
        if (supplierId != null) {
            claims.claim(SUPPLIER_ID_CLAIM, supplierId);
        }
        return encode(claims.build());
    }

    public String issueServiceToken(String clientId) {
        return encode(base(clientId, serviceTokenTtl)
                .claim("role", SERVICE_ROLE)
                .claim("client_id", clientId)
                .build());
    }

    public long getExpirationMs() {
        return userTokenTtl.toMillis();
    }

    public long serviceTokenTtlSeconds() {
        return serviceTokenTtl.toSeconds();
    }

    public String issuer() {
        return issuer;
    }

    private JwtClaimsSet.Builder base(String subject, Duration ttl) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(AUDIENCE))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString());
    }

    private String encode(JwtClaimsSet claims) {
        // The encoder picks the only key in the set and stamps its kid in the header,
        // which is how verifiers choose the right public key during a rotation.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
