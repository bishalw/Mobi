package com.mobi.auth.security;

import com.mobi.auth.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenIssuerTest {

    private static final String ISSUER = "http://auth-service:8082";

    private final SigningKeys keys = new SigningKeys(pkcs8Pem(), "");
    private final TokenIssuer issuer = new TokenIssuer(keys, ISSUER, 900_000, 300);
    private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(keys, issuer);

    @Test
    void userTokenRoundTripsWithClaimsServicesRelyOn() {
        Jwt jwt = decoder.decode(issuer.issueUserToken(42L, "alice", "ROLE_ADMIN"));

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaims().get("userId")).isEqualTo(42L);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getAudience()).containsExactly(TokenIssuer.AUDIENCE);
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", keys.signingKey().getKeyID());
        assertThat(CurrentUser.from(jwt)).isEqualTo(new CurrentUser(42L, "alice", "ADMIN"));
    }

    @Test
    void supplierTokenCarriesItsSupplierAndOthersDoNot() {
        Jwt supplier = decoder.decode(issuer.issueUserToken(5L, "acme", "SUPPLIER", 1L));
        Jwt customer = decoder.decode(issuer.issueUserToken(6L, "carol", "USER", null));

        assertThat(supplier.getClaims().get(TokenIssuer.SUPPLIER_ID_CLAIM)).isEqualTo(1L);
        assertThat(CurrentUser.from(supplier).supplierId()).isEqualTo(1L);
        assertThat(customer.getClaims()).doesNotContainKey(TokenIssuer.SUPPLIER_ID_CLAIM);
        assertThat(CurrentUser.from(customer).supplierId()).isNull();
    }

    @Test
    void serviceTokenActsAsTheServiceNotAUser() {
        Jwt jwt = decoder.decode(issuer.issueServiceToken("order-service"));

        assertThat(jwt.getSubject()).isEqualTo("order-service");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("SERVICE");
        assertThat(jwt.getClaims()).doesNotContainKey("userId");
        assertThat(jwt.getExpiresAt()).isBefore(jwt.getIssuedAt().plusSeconds(301));
    }

    @Test
    void roleClaimBecomesAnUnprefixedAuthority() {
        Jwt jwt = decoder.decode(issuer.issueUserToken(1L, "bob", "USER"));

        var authorities = SecurityConfig.jwtAuthenticationConverter().convert(jwt).getAuthorities();

        // FACTOR_BEARER is Spring Security 7 recording how the caller authenticated.
        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("USER", "FACTOR_BEARER");
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        TokenIssuer other = new TokenIssuer(keys, "http://evil.example", 900_000, 300);

        assertThatThrownBy(() -> decoder.decode(other.issueUserToken(1L, "bob", "ADMIN")))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        TokenIssuer forger = new TokenIssuer(new SigningKeys(pkcs8Pem(), ""), ISSUER, 900_000, 300);

        assertThatThrownBy(() -> decoder.decode(forger.issueUserToken(1L, "mallory", "ADMIN")))
                .hasMessageContaining("signature");
    }

    @Test
    void jwksPublishesOnlyPublicKeyMaterial() {
        @SuppressWarnings("unchecked")
        Map<String, Object> key = ((java.util.List<Map<String, Object>>) keys.publicKeys().toJSONObject().get("keys")).get(0);

        assertThat(key).containsKeys("kty", "n", "e", "kid").doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }

    @Test
    void sameKeyGivesSameKidAcrossInstances() {
        String pem = pkcs8Pem();

        assertThat(new SigningKeys(pem, "").signingKey().getKeyID())
                .isEqualTo(new SigningKeys(pem.replace("\n", ""), "").signingKey().getKeyID());
    }

    @Test
    void previousPublicKeyIsPublishedForRotation() throws Exception {
        SigningKeys old = new SigningKeys(pkcs8Pem(), "");
        String oldPublic = Base64.getEncoder().encodeToString(old.currentPublicKey().getEncoded());

        SigningKeys rotated = new SigningKeys(pkcs8Pem(), oldPublic);

        assertThat(rotated.publicKeys().getKeys()).hasSize(2);
        assertThat(rotated.publicKeys().getKeyByKeyId(old.signingKey().getKeyID())).isNotNull();
    }

    static String pkcs8Pem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
