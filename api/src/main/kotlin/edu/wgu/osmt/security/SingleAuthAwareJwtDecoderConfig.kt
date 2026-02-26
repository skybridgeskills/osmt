package edu.wgu.osmt.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import java.time.Instant

/**
 * JwtDecoder that accepts single-auth tokens (admin-jwt-*) alongside OAuth2 JWTs.
 *
 * When both single-auth and OAuth2 are enabled (staging), the OAuth2 resource server's
 * BearerTokenAuthenticationFilter validates all Bearer tokens via JwtDecoder. Single-auth
 * tokens from the login endpoint are not valid OAuth2 JWTs, so the default decoder fails
 * and returns 401 before authorization is checked.
 *
 * This decoder delegates: for tokens starting with "admin-jwt-", it builds a Jwt
 * in-memory. For all other tokens, it delegates to the OAuth2 issuer-based decoder.
 *
 * @see AdminAuthController for the login endpoint that issues single-auth tokens
 * @see SecurityConfig for wiring when singleAuthEnabled and oauth2 are both active
 */
@Configuration
@Profile("oauth2 & single-auth")
class SingleAuthAwareJwtDecoderConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val issuerUri: String,
) {
    private val oauth2Decoder: JwtDecoder by lazy {
        JwtDecoders.fromIssuerLocation(issuerUri)
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        JwtDecoder { token ->
            if (token.startsWith(ADMIN_JWT_PREFIX)) {
                createAdminJwt(token)
            } else {
                oauth2Decoder.decode(token)
            }
        }

    private fun createAdminJwt(tokenValue: String): Jwt =
        Jwt
            .withTokenValue(tokenValue)
            .header("typ", "JWT")
            .header("alg", "none")
            .claim("email", AdminAuthConstants.ADMIN_EMAIL)
            .claim("name", AdminAuthConstants.ADMIN_NAME)
            .claim("sub", AdminAuthConstants.ADMIN_EMAIL)
            .claim("roles", AdminAuthConstants.ADMIN_ROLE)
            .claim("scope", AdminAuthConstants.ADMIN_ROLE)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

    companion object {
        private const val ADMIN_JWT_PREFIX = "admin-jwt-"
    }
}
