package com.example.invoices.support

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date

/**
 * Mints real, signed JWTs for the integration tests, and publishes the matching JWKS.
 *
 * The point is that nothing in the security configuration is stubbed out: WireMock serves this key
 * set at the JWKS endpoint, so the tests exercise the production NimbusJwtDecoder, the real
 * signature check and the real issuer/audience validators. A test that replaces the JwtDecoder
 * with a mock proves only that the mock works.
 */
object JwtTestTokens {
    const val ISSUER: String = "https://test-issuer.example.com/"
    const val AUDIENCE: String = "https://api.test.example.com"
    const val JWKS_PATH: String = "/.well-known/jwks.json"

    private const val KEY_ID = "test-key-1"

    private val key: RSAKey =
        RSAKeyGenerator(RSAKeyGenerator.MIN_KEY_SIZE_BITS)
            .keyID(KEY_ID)
            .keyUse(KeyUse.SIGNATURE)
            .generate()

    /** What the JWKS endpoint returns: public parts only. */
    val jwksJson: String get() = JWKSet(key.toPublicJWK()).toString()

    fun token(
        scopes: List<String> = listOf("invoices:read", "invoices:write"),
        audience: String = AUDIENCE,
        issuer: String = ISSUER,
        issuedAt: Instant = Instant.now().minusSeconds(60),
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        subject: String = "auth0|test-user",
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                // Auth0 puts granted scopes in a single space-delimited claim, which Spring maps
                // to SCOPE_* authorities.
                .claim("scope", scopes.joinToString(" "))
                .build()

        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .type(JOSEObjectType.JWT)
                .build()

        return SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
    }

    fun expiredToken(): String =
        token(
            issuedAt = Instant.now().minusSeconds(7200),
            expiresAt = Instant.now().minusSeconds(3600),
        )

    fun readOnlyToken(): String = token(scopes = listOf("invoices:read"))

    fun wrongAudienceToken(): String = token(audience = "https://api.someone-else.example.com")
}
