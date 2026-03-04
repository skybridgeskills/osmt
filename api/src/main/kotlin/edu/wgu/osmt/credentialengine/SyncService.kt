package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.collection.CollectionDao
import edu.wgu.osmt.collection.CollectionRepository
import edu.wgu.osmt.db.PublishStatus
import edu.wgu.osmt.richskill.RichSkillDescriptorDao
import edu.wgu.osmt.richskill.RichSkillRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional

private const val SYNC_TYPE = "credential-engine"
private const val SYNC_KEY_DEFAULT = "default"
private const val CTID_PREFIX = "ce-"

@Service
@Transactional
class SyncService(
    private val syncTargetOpt: Optional<SyncTarget>,
    private val syncStateRepository: SyncStateRepository,
    private val richSkillRepository: RichSkillRepository,
    private val collectionRepository: CollectionRepository,
    private val syncRetryHelper: SyncRetryHelper,
    @Value("\${credential-engine.sync.batch-size:20}") private val batchSize: Int,
    @Value("\${credential-engine.sync.retry-attempts:5}") private val retryAttempts: Int,
    @Value("\${credential-engine.sync.retry-initial-delay-ms:5000}")
    private val retryInitialDelayMs: Long,
    @Value("\${credential-engine.sync.retry-delay-multiplier:1.5}")
    private val retryDelayMultiplier: Double,
) {
    fun syncRecord(
        recordType: String,
        uuid: String,
    ): Result<Unit> =
        syncTargetOpt
            .map { target -> doSyncRecord(target, recordType, uuid) }
            .orElse(Result.failure(IllegalStateException("Sync not configured")))

    private fun doSyncRecord(
        target: SyncTarget,
        recordType: String,
        uuid: String,
    ): Result<Unit> =
        when (recordType) {
            SyncRecordType.SKILL -> syncSkill(target, uuid)
            SyncRecordType.COLLECTION -> syncCollection(target, uuid)
            else -> Result.failure(IllegalArgumentException("Unknown recordType: $recordType"))
        }

    private fun syncSkill(
        target: SyncTarget,
        uuid: String,
    ): Result<Unit> {
        val dao =
            richSkillRepository.findByUUID(uuid)
                ?: return Result.failure(NoSuchElementException("Skill not found: $uuid"))
        val rsd = dao.toModel()
        return when (rsd.publishStatus()) {
            PublishStatus.Published -> target.publishSkill(rsd)
            PublishStatus.Archived -> target.deprecateSkill(rsd)
            else -> Result.success(Unit)
        }
    }

    private fun syncCollection(
        target: SyncTarget,
        uuid: String,
    ): Result<Unit> {
        val dao =
            collectionRepository.findByUUID(uuid)
                ?: return Result.failure(NoSuchElementException("Collection not found: $uuid"))
        val collection = dao.toModel()
        return when (collection.status) {
            PublishStatus.Published -> {
                target.publishCollection(collection, skillCtids(dao))
            }

            PublishStatus.Archived -> {
                target.deprecateCollection(collection)
            }

            else -> {
                Result.success(Unit)
            }
        }
    }

    private fun skillCtids(collectionDao: CollectionDao): List<String> =
        collectionDao.skills.map { "$CTID_PREFIX${it.uuid}" }

    fun syncSinceWatermark(
        syncKey: String,
        recordType: String,
    ): Result<Unit> =
        syncTargetOpt
            .map { target -> doSyncSinceWatermark(target, syncKey, recordType) }
            .orElse(Result.failure(IllegalStateException("Sync not configured")))

    private fun doSyncSinceWatermark(
        target: SyncTarget,
        syncKey: String,
        recordType: String,
    ): Result<Unit> {
        syncStateRepository.getOrCreateRow(SYNC_TYPE, syncKey, recordType)
        var watermark = syncStateRepository.getWatermark(SYNC_TYPE, syncKey, recordType)
        var batchIndex = 0

        while (true) {
            val batch =
                when (recordType) {
                    SyncRecordType.SKILL -> findSkillsUpdatedSince(watermark, batchSize)
                    SyncRecordType.COLLECTION -> findCollectionsUpdatedSince(watermark, batchSize)
                    else -> return Result.failure(IllegalArgumentException("Unknown: $recordType"))
                }
            if (batch.isEmpty()) break

            val result =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        processSkillBatch(
                            target,
                            batch as List<RichSkillDescriptorDao>,
                            syncKey,
                            recordType,
                            batchIndex,
                        )
                    }

                    SyncRecordType.COLLECTION -> {
                        processCollectionBatch(
                            target,
                            batch as List<CollectionDao>,
                            syncKey,
                            recordType,
                            batchIndex,
                        )
                    }

                    else -> {
                        return Result.failure(IllegalArgumentException("Unknown: $recordType"))
                    }
                }
            result.fold(
                onSuccess = { },
                onFailure = { return Result.failure(it) },
            )

            val maxDate =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        (batch as List<RichSkillDescriptorDao>).maxOf { it.updateDate }
                    }

                    SyncRecordType.COLLECTION -> {
                        (batch as List<CollectionDao>).maxOf { it.updateDate }
                    }

                    else -> {
                        return Result.failure(IllegalArgumentException("Unknown: $recordType"))
                    }
                }
            syncStateRepository.updateWatermark(SYNC_TYPE, syncKey, recordType, maxDate)
            watermark = maxDate
            batchIndex++
        }
        return Result.success(Unit)
    }

    private fun processSkillBatch(
        target: SyncTarget,
        batch: List<RichSkillDescriptorDao>,
        syncKey: String,
        recordType: String,
        batchIndex: Int,
    ): Result<Unit> {
        for (dao in batch) {
            val result = syncOneSkillWithRetry(target, dao)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                val status =
                    SyncStatusJson(
                        lastRecordUuid = dao.uuid,
                        lastRecordName = dao.name,
                        batchIndex = batchIndex,
                        batchesCompleted = batchIndex,
                        lastUpdatedAt = nowIso(),
                        error =
                            SyncStatusError(
                                message = err?.message ?: "Unknown error",
                                correlationId = generateCorrelationId(),
                                recordUuid = dao.uuid,
                                recordName = dao.name,
                                occurredAt = nowIso(),
                            ),
                    )
                syncStateRepository.updateStatusJson(
                    SYNC_TYPE,
                    syncKey,
                    recordType,
                    status.toJsonString(),
                )
                return Result.failure(err ?: IllegalStateException("Sync failed"))
            }
            val progress =
                SyncStatusJson(
                    lastRecordUuid = dao.uuid,
                    lastRecordName = dao.name,
                    batchIndex = batchIndex,
                    batchesCompleted = batchIndex,
                    lastUpdatedAt = nowIso(),
                )
            syncStateRepository.updateStatusJson(
                SYNC_TYPE,
                syncKey,
                recordType,
                progress.toJsonString(),
            )
        }
        return Result.success(Unit)
    }

    private fun processCollectionBatch(
        target: SyncTarget,
        batch: List<CollectionDao>,
        syncKey: String,
        recordType: String,
        batchIndex: Int,
    ): Result<Unit> {
        for (dao in batch) {
            val result = syncOneCollectionWithRetry(target, dao)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                val status =
                    SyncStatusJson(
                        lastRecordUuid = dao.uuid,
                        lastRecordName = dao.name,
                        batchIndex = batchIndex,
                        batchesCompleted = batchIndex,
                        lastUpdatedAt = nowIso(),
                        error =
                            SyncStatusError(
                                message = err?.message ?: "Unknown error",
                                correlationId = generateCorrelationId(),
                                recordUuid = dao.uuid,
                                recordName = dao.name,
                                occurredAt = nowIso(),
                            ),
                    )
                syncStateRepository.updateStatusJson(
                    SYNC_TYPE,
                    syncKey,
                    recordType,
                    status.toJsonString(),
                )
                return Result.failure(err ?: IllegalStateException("Sync failed"))
            }
            val progress =
                SyncStatusJson(
                    lastRecordUuid = dao.uuid,
                    lastRecordName = dao.name,
                    batchIndex = batchIndex,
                    batchesCompleted = batchIndex,
                    lastUpdatedAt = nowIso(),
                )
            syncStateRepository.updateStatusJson(
                SYNC_TYPE,
                syncKey,
                recordType,
                progress.toJsonString(),
            )
        }
        return Result.success(Unit)
    }

    private fun syncOneSkillWithRetry(
        target: SyncTarget,
        dao: RichSkillDescriptorDao,
    ): Result<Unit> {
        val rsd = dao.toModel()
        return syncRetryHelper.withRetry(
            retryAttempts,
            retryInitialDelayMs,
            retryDelayMultiplier,
        ) {
            when (rsd.publishStatus()) {
                PublishStatus.Published -> target.publishSkill(rsd)
                PublishStatus.Archived -> target.deprecateSkill(rsd)
                else -> Result.success(Unit)
            }
        }
    }

    private fun syncOneCollectionWithRetry(
        target: SyncTarget,
        dao: CollectionDao,
    ): Result<Unit> {
        val collection = dao.toModel()
        return syncRetryHelper.withRetry(
            retryAttempts,
            retryInitialDelayMs,
            retryDelayMultiplier,
        ) {
            when (collection.status) {
                PublishStatus.Published -> {
                    target.publishCollection(collection, skillCtids(dao))
                }

                PublishStatus.Archived -> {
                    target.deprecateCollection(collection)
                }

                else -> {
                    Result.success(Unit)
                }
            }
        }
    }

    private fun nowIso(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun syncAllSinceWatermark(syncKey: String = SYNC_KEY_DEFAULT): Result<Unit> =
        syncTargetOpt
            .map { target ->
                doSyncSinceWatermark(target, syncKey, SyncRecordType.SKILL)
                    .fold(
                        onSuccess = {
                            doSyncSinceWatermark(target, syncKey, SyncRecordType.COLLECTION)
                        },
                        onFailure = { Result.failure(it) },
                    )
            }.orElse(Result.failure(IllegalStateException("Sync not configured")))

    fun getSyncState(syncKey: String = SYNC_KEY_DEFAULT): List<SyncState> =
        syncStateRepository.findAllBySyncKey(SYNC_TYPE, syncKey)

    fun isConfigured(): Boolean = syncTargetOpt.isPresent
}
