package edu.wgu.osmt.credentialengine

import com.fasterxml.jackson.databind.ObjectMapper
import edu.wgu.osmt.config.AppConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.client.RestTemplate
import java.util.Optional

@Configuration
class SyncTargetConfig {
    @Bean
    fun credentialEngineRestTemplate(): RestTemplate = RestTemplate()

    @Bean
    fun syncTarget(
        @Value("\${credential-engine.api-key:}") apiKey: String,
        @Value("\${credential-engine.org-ctid:}") orgCtid: String,
        @Value("\${credential-engine.registry-url:https://sandbox.credentialengine.org}")
        registryUrl: String,
        appConfig: AppConfig,
        credentialEngineRestTemplate: RestTemplate,
        objectMapper: ObjectMapper,
        environment: Environment,
    ): Optional<SyncTarget> =
        when {
            apiKey.isNotBlank() && orgCtid.isNotBlank() -> {
                Optional.of(
                    CredentialEngineSyncTarget(
                        registryUrl = registryUrl,
                        apiKey = apiKey,
                        orgCtid = orgCtid,
                        appConfig = appConfig,
                        restTemplate = credentialEngineRestTemplate,
                        objectMapper = objectMapper,
                    ),
                )
            }

            environment.activeProfiles.contains("dev") -> {
                Optional.of(MockSyncTarget())
            }

            else -> {
                Optional.empty()
            }
        }
}
