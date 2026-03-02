package edu.wgu.osmt.credentialengine

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.Optional

@Configuration
class SyncTargetConfig {
    @Bean
    fun syncTarget(
        @Value("\${credential-engine.api-key:}") apiKey: String,
        @Value("\${credential-engine.org-ctid:}") orgCtid: String,
        environment: Environment,
    ): Optional<SyncTarget> =
        when {
            apiKey.isNotBlank() && orgCtid.isNotBlank() -> {
                Optional.empty()
            }

            // CredentialEngineSyncTarget in Phase 5
            environment.activeProfiles.contains("dev") -> {
                Optional.of(MockSyncTarget())
            }

            else -> {
                Optional.empty()
            }
        }
}
