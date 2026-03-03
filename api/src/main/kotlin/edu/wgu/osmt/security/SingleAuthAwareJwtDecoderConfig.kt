package edu.wgu.osmt.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant

/**
 * JwtDecoder that accepts single-auth tokens (admin-jwt-*) alongside session tokens.
 *
 * When both single-auth and OAuth2 are enabled (staging), validates admin-jwt-* from
 * form login and backend-issued session tokens from OAuth2 login.
 *
 * @see AdminAuthController for the login endpoint that issues single-auth tokens
 * @see SessionTokenService for OAuth2 session token creation
 */
@Configuration
@Profile("oauth2 & single-auth")
class SingleAuthAwareJwtDecoderConfig(
    private val sessionTokenJwtDecoder: JwtDecoder,
) {
    @Bean
    fun jwtDecoder(): JwtDecoder =
        JwtDecoder { token ->
            if (token.startsWith(ADMIN_JWT_PREFIX)) {
                createAdminJwt(token)
            } else {
                sessionTokenJwtDecoder.decode(token)
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
