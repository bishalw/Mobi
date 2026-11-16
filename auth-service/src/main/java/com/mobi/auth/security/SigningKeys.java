package com.mobi.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The RSA key pair access tokens are signed with.
 *
 * <p>Only this service holds the private key. Every other service verifies tokens
 * with the public half, fetched from {@code /.well-known/jwks.json}, so a service
 * that leaks its configuration leaks nothing that can mint a token. With the old
 * shared HMAC secret, any one of six services could have forged an admin token.
 *
 * <p>The key id ({@code kid}) is the RFC 7638 thumbprint of the public key, so it is
 * stable across restarts and replicas without being configured separately.
 *
 * <p><b>Rotation:</b> generate a new key, move the current public key to
 * {@code JWT_PREVIOUS_PUBLIC_KEY}, and deploy. Tokens signed with the old key keep
 * verifying until they expire (15 minutes), then the previous key can be dropped.
 */
@Component
public class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

    private final RSAKey signingKey;
    private final JWKSet publicKeys;

    public SigningKeys(@Value("${auth.jwt.private-key:}") String privateKey,
                       @Value("${auth.jwt.previous-public-key:}") String previousPublicKey) {
        try {
            this.signingKey = privateKey.isBlank() ? generated() : fromPrivateKey(privateKey);
            List<JWK> published = new ArrayList<>();
            published.add(signingKey.toPublicJWK());
            if (!previousPublicKey.isBlank()) {
                published.add(rsaKey(publicKey(previousPublicKey), null));
            }
            this.publicKeys = new JWKSet(published);
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException e) {
            throw new IllegalStateException("auth.jwt.private-key / previous-public-key is not a valid RSA key", e);
        }
    }

    /** Private + public, for signing. Never leaves this service. */
    public RSAKey signingKey() {
        return signingKey;
    }

    /** Public keys only, as served on the JWKS endpoint. */
    public JWKSet publicKeys() {
        return publicKeys;
    }

    public RSAPublicKey currentPublicKey() {
        try {
            return signingKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generated() throws GeneralSecurityException, JOSEException {
        // Fine for running one instance from an IDE. Two replicas would each sign with
        // their own key and reject each other's tokens, and a restart signs everyone
        // out, which is why the Helm chart always supplies JWT_PRIVATE_KEY.
        log.warn("JWT_PRIVATE_KEY is not set; generated a temporary signing key. "
                + "Tokens will not survive a restart. Set JWT_PRIVATE_KEY outside local development.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        return rsaKey((RSAPublicKey) pair.getPublic(), (java.security.interfaces.RSAPrivateKey) pair.getPrivate());
    }

    private static RSAKey fromPrivateKey(String encoded) throws GeneralSecurityException, JOSEException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        var key = factory.generatePrivate(new PKCS8EncodedKeySpec(der(encoded)));
        if (!(key instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException("expected an RSA private key in PKCS#8 form");
        }
        // The public key is derivable from the private one, so only one value has to
        // be stored and the two can never be mismatched.
        var publicKey = (RSAPublicKey) factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        return rsaKey(publicKey, crt);
    }

    private static RSAPublicKey publicKey(String encoded) throws GeneralSecurityException {
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(encoded)));
    }

    private static RSAKey rsaKey(RSAPublicKey publicKey, java.security.interfaces.RSAPrivateKey privateKey) throws JOSEException {
        RSAKey.Builder builder = new RSAKey.Builder(publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256);
        if (privateKey != null) {
            builder.privateKey(privateKey);
        }
        return builder.keyIDFromThumbprint().build();
    }

    /**
     * Accepts a PEM block or bare base64 DER. Kubernetes secrets carry PEM fine, but a
     * one-line base64 value is easier to keep in a .env file.
     */
    static byte[] der(String encoded) {
        String body = encoded.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
