package edu.wgu.osmt.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import edu.wgu.osmt.config.AppConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * JWT encoder and decoder for OAuth2 session tokens.
 *
 * Backend issues its own signed JWT after OAuth2 login instead of passing
 * the IdP token. Uses HS256 with configurable secret.
 */
@Configuration
@Profile("oauth2")
class SessionTokenJwtConfig(
    private val appConfig: AppConfig,
) {
    @Bean
    fun sessionTokenJwtEncoder(): JwtEncoder {
        val key = resolveSecretKey()
        val immutableSecret = ImmutableSecret<SecurityContext>(key)
        return NimbusJwtEncoder(immutableSecret)
    }

    @Bean
    fun sessionTokenJwtDecoder(): JwtDecoder {
        val key = resolveSecretKey()
        val decoder =
            NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
        val issuer = appConfig.sessionTokenIssuer.ifBlank { appConfig.baseUrl }
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer))
        return decoder
    }

    @Bean
    @Profile("oauth2 & !single-auth")
    fun jwtDecoder(): JwtDecoder = sessionTokenJwtDecoder()

    private fun resolveSecretKey(): javax.crypto.SecretKey {
        val secretBytes = resolveSecretBytes()
        return SecretKeySpec(secretBytes, "HmacSHA256")
    }

    private fun resolveSecretBytes(): ByteArray {
        val secret = appConfig.sessionTokenSecret
        return if (secret.isNotBlank()) {
            Base64.getDecoder().decode(secret)
        } else {
            deriveFallbackSecret()
        }
    }

    private fun deriveFallbackSecret(): ByteArray {
        val seed = appConfig.baseUrl.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(seed)
    }
}
