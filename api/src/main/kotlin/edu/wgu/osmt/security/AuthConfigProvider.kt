package edu.wgu.osmt.security

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Component

/**
 * Provides OAuth provider information for the whitelabel API.
 * Uses ClientRegistrationRepository when available (oauth2 profile).
 * ObjectProvider allows lazy resolution when repository is created by security config.
 */
@Component
class AuthConfigProvider {
    @Autowired
    lateinit var clientRegistrationRepositoryProvider: ObjectProvider<ClientRegistrationRepository>

    @Value("\${app.baseUrl:http://localhost:8080}")
    lateinit var baseUrl: String

    fun getOAuthProviders(): List<AuthProviderInfo> {
        val repo = clientRegistrationRepositoryProvider.getIfAvailable() ?: return emptyList()

        val providers = mutableListOf<AuthProviderInfo>()
        for (registrationId in KNOWN_PROVIDERS.keys) {
            val registration =
                repo.findByRegistrationId(registrationId)
                    ?: continue
            if (registration.clientId != "xxxxxx") {
                providers.add(
                    AuthProviderInfo(
                        id = registration.registrationId,
                        name = getDisplayName(registration.registrationId),
                        authorizationUrl = "$baseUrl/oauth2/authorization/${registration.registrationId}",
                    ),
                )
            }
        }
        return providers
    }

    private fun getDisplayName(registrationId: String): String =
        KNOWN_PROVIDERS[registrationId] ?: registrationId.replaceFirstChar {
            it.uppercase()
        }

    companion object {
        private val KNOWN_PROVIDERS =
            mapOf("google" to "Google", "okta" to "Okta")
    }
}

data class AuthProviderInfo(
    val id: String,
    val name: String,
    val authorizationUrl: String,
)
