package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.RoutePaths
import edu.wgu.osmt.config.AppConfig
import edu.wgu.osmt.security.OAuthHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ForkJoinPool

data class SyncStateResponse(
    val integrations: List<SyncIntegrationDto>,
)

data class SyncIntegrationDto(
    val syncKey: String,
    val recordType: String,
    val syncWatermark: String?,
    val statusJson: String? = null,
)

@Controller
@RequestMapping("${RoutePaths.API}")
class SyncController
    @Autowired
    constructor(
        private val syncService: SyncService,
        private val appConfig: AppConfig,
        private val oAuthHelper: OAuthHelper,
    ) {
        private fun ensureAdmin() {
            if (!oAuthHelper.hasRole(appConfig.roleAdmin)) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
            }
        }

        private fun ensureConfigured() {
            if (!syncService.isConfigured()) {
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Credential Engine sync is not configured",
                )
            }
        }

        @GetMapping(RoutePaths.SYNC_STATE)
        fun getSyncState(): ResponseEntity<SyncStateResponse> {
            ensureAdmin()
            ensureConfigured()
            val states = syncService.getSyncState()
            val integrations =
                states.map {
                    SyncIntegrationDto(
                        syncKey = it.syncKey,
                        recordType = it.recordType,
                        syncWatermark = it.syncWatermark?.toString(),
                        statusJson = it.statusJson,
                    )
                }
            return ResponseEntity.ok(SyncStateResponse(integrations = integrations))
        }

        @PostMapping(RoutePaths.SYNC_SKILL_UUID)
        fun syncSkill(
            @PathVariable uuid: String,
        ): ResponseEntity<Unit> {
            ensureAdmin()
            ensureConfigured()
            return syncService
                .syncRecord(SyncRecordType.SKILL, uuid)
                .fold(
                    onSuccess = { ResponseEntity.ok().build() },
                    onFailure = { e ->
                        when (e) {
                            is NoSuchElementException -> {
                                throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
                            }

                            is IllegalArgumentException -> {
                                throw ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    e.message,
                                )
                            }

                            else -> {
                                throw ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    e.message,
                                )
                            }
                        }
                    },
                )
        }

        @PostMapping(RoutePaths.SYNC_COLLECTION_UUID)
        fun syncCollection(
            @PathVariable uuid: String,
        ): ResponseEntity<Unit> {
            ensureAdmin()
            ensureConfigured()
            return syncService
                .syncRecord(SyncRecordType.COLLECTION, uuid)
                .fold(
                    onSuccess = { ResponseEntity.ok().build() },
                    onFailure = { e ->
                        when (e) {
                            is NoSuchElementException -> {
                                throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
                            }

                            is IllegalArgumentException -> {
                                throw ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    e.message,
                                )
                            }

                            else -> {
                                throw ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    e.message,
                                )
                            }
                        }
                    },
                )
        }

        @PostMapping(RoutePaths.SYNC_ALL)
        fun syncAll(): ResponseEntity<String> {
            ensureAdmin()
            ensureConfigured()
            ForkJoinPool.commonPool().submit {
                syncService.syncAllSinceWatermark()
            }
            return ResponseEntity(
                "Sync started. Check logs for progress.",
                HttpStatus.ACCEPTED,
            )
        }
    }
