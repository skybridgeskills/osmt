package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.collection.CollectionDao
import edu.wgu.osmt.collection.CollectionRepository
import edu.wgu.osmt.db.PublishStatus
import edu.wgu.osmt.richskill.RichSkillDescriptorDao
import edu.wgu.osmt.richskill.RichSkillRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional

private const val SYNC_TYPE = "credential-engine"
private const val SYNC_KEY_DEFAULT = "default"
private const val MDC_CORRELATION_ID = "syncCorrelationId"

private val log = LoggerFactory.getLogger(SyncService::class.java)

@Service
@Transactional
class SyncService(
    private val syncTargetOpt: Optional<SyncTarget>,
    private val ctidGeneratorOpt: Optional<CtidGenerator>,
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
        collectionDao.skills.map {
            ctidGeneratorOpt
                .map { g -> g.generate(it.uuid) }
                .orElse("ce-${it.uuid}")
        }

    /**
     * Marks both skill and collection integrations as in progress immediately.
     * Called before forking the sync job so GET /state returns inProgress=true
     * without waiting for the background job to start.
     */
    fun markSyncInProgress(syncKey: String) {
        val sessionCorrelationId = generateCorrelationId()
        for (recordType in listOf(SyncRecordType.SKILL, SyncRecordType.COLLECTION)) {
            syncStateRepository.getOrCreateRow(SYNC_TYPE, syncKey, recordType)
            syncStateRepository.updateStatusJson(
                SYNC_TYPE,
                syncKey,
                recordType,
                SyncStatusJson(
                    inProgress = true,
                    sessionCorrelationId = sessionCorrelationId,
                    lastUpdatedAt = nowIso(),
                ).toJsonString(),
            )
        }
    }

    fun syncSinceWatermark(
        syncKey: String,
        recordType: String,
    ): Result<Unit> {
        val sessionCorrelationId = generateCorrelationId()
        MDC.put(MDC_CORRELATION_ID, sessionCorrelationId)
        return try {
            syncTargetOpt
                .map { target ->
                    doSyncSinceWatermark(target, syncKey, recordType, sessionCorrelationId)
                }.orElse(Result.failure(IllegalStateException("Sync not configured")))
        } finally {
            MDC.remove(MDC_CORRELATION_ID)
        }
    }

    private fun doSyncSinceWatermark(
        target: SyncTarget,
        syncKey: String,
        recordType: String,
        sessionCorrelationId: String,
    ): Result<Unit> {
        log.info(
            "[{}] Sync started recordType={} syncKey={}",
            sessionCorrelationId,
            recordType,
            syncKey,
        )
        syncStateRepository.getOrCreateRow(SYNC_TYPE, syncKey, recordType)
        syncStateRepository.updateStatusJson(
            SYNC_TYPE,
            syncKey,
            recordType,
            SyncStatusJson(
                inProgress = true,
                sessionCorrelationId = sessionCorrelationId,
                lastUpdatedAt = nowIso(),
            ).toJsonString(),
        )
        var watermarkDate = syncStateRepository.getWatermark(SYNC_TYPE, syncKey, recordType)
        var watermarkId = syncStateRepository.getLastRecordId(SYNC_TYPE, syncKey, recordType)
        var batchIndex = 0
        var consecutiveWatermarkRepeats = 0
        var lastMaxId: Long? = null

        while (true) {
            log.debug(
                "[{}] Batch {} fetch watermarkDate={} watermarkId={}",
                sessionCorrelationId,
                batchIndex,
                watermarkDate,
                watermarkId,
            )
            val batch =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        findSkillsUpdatedSince(watermarkDate, watermarkId, batchSize)
                    }

                    SyncRecordType.COLLECTION -> {
                        findCollectionsUpdatedSince(watermarkDate, watermarkId, batchSize)
                    }

                    else -> {
                        return Result.failure(IllegalArgumentException("Unknown: $recordType"))
                    }
                }
            if (batch.isEmpty()) break

            when (recordType) {
                SyncRecordType.SKILL -> {
                    val skills = batch as List<RichSkillDescriptorDao>
                    log.debug(
                        "[{}] Batch {} raw from DB: size={} ids={} uuids={}",
                        sessionCorrelationId,
                        batchIndex,
                        skills.size,
                        skills.map { it.id.value },
                        skills.map { it.uuid },
                    )
                }

                SyncRecordType.COLLECTION -> {
                    val colls = batch as List<CollectionDao>
                    log.debug(
                        "[{}] Batch {} raw from DB: size={} ids={} uuids={}",
                        sessionCorrelationId,
                        batchIndex,
                        colls.size,
                        colls.map { it.id.value },
                        colls.map { it.uuid },
                    )
                }

                else -> {}
            }

            val cursorFilteredBatch =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        val skills = batch as List<RichSkillDescriptorDao>
                        if (watermarkDate != null && watermarkId != null) {
                            skills.filter {
                                it.updateDate > watermarkDate ||
                                    (
                                        it.updateDate == watermarkDate &&
                                            it.id.value > watermarkId
                                    )
                            }
                        } else {
                            skills
                        }
                    }

                    SyncRecordType.COLLECTION -> {
                        val colls = batch as List<CollectionDao>
                        if (watermarkDate != null && watermarkId != null) {
                            colls.filter {
                                it.updateDate > watermarkDate ||
                                    (
                                        it.updateDate == watermarkDate &&
                                            it.id.value > watermarkId
                                    )
                            }
                        } else {
                            colls
                        }
                    }

                    else -> {
                        batch
                    }
                }
            if (cursorFilteredBatch.size < (batch as List<*>).size) {
                log.warn(
                    "[{}] Batch {} excluded {} records that were <= watermark (Exposed query bug workaround)",
                    sessionCorrelationId,
                    batchIndex,
                    (batch as List<*>).size - cursorFilteredBatch.size,
                )
            }
            if (cursorFilteredBatch.isEmpty()) {
                val (maxD, maxI) =
                    when (recordType) {
                        SyncRecordType.SKILL -> {
                            val b = batch as List<RichSkillDescriptorDao>
                            val m =
                                b.maxWithOrNull(
                                    compareBy<RichSkillDescriptorDao> { it.updateDate }
                                        .thenBy { it.id.value },
                                )!!
                            m.updateDate to m.id.value
                        }

                        SyncRecordType.COLLECTION -> {
                            val b = batch as List<CollectionDao>
                            val m =
                                b.maxWithOrNull(
                                    compareBy<CollectionDao> { it.updateDate }
                                        .thenBy { it.id.value },
                                )!!
                            m.updateDate to m.id.value
                        }

                        else -> {
                            return Result.failure(
                                IllegalArgumentException("Unknown: $recordType"),
                            )
                        }
                    }
                if (lastMaxId == maxI) {
                    consecutiveWatermarkRepeats++
                    if (consecutiveWatermarkRepeats >= 3) {
                        log.error(
                            "[{}] Circuit breaker: watermark stuck at id={} for 3+ batches. " +
                                "Exposed id comparison may be broken. Sync aborted.",
                            sessionCorrelationId,
                            maxI,
                        )
                        return Result.failure(
                            IllegalStateException(
                                "Sync cursor stuck at id=$maxI - Exposed query returns " +
                                    "same record repeatedly. See diagnostic doc.",
                            ),
                        )
                    }
                } else {
                    consecutiveWatermarkRepeats = 0
                }
                lastMaxId = maxI
                syncStateRepository.updateWatermark(SYNC_TYPE, syncKey, recordType, maxD, maxI)
                watermarkDate = maxD
                watermarkId = maxI
                batchIndex++
                continue
            }
            consecutiveWatermarkRepeats = 0

            val dedupedBatch =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        val skills = cursorFilteredBatch as List<RichSkillDescriptorDao>
                        val seen = mutableSetOf<String>()
                        val deduped = skills.filter { seen.add(it.uuid) }
                        if (deduped.size < skills.size) {
                            log.warn(
                                "[{}] Batch {} dropped {} duplicate skills",
                                sessionCorrelationId,
                                batchIndex,
                                skills.size - deduped.size,
                            )
                        }
                        deduped
                    }

                    SyncRecordType.COLLECTION -> {
                        val colls = cursorFilteredBatch as List<CollectionDao>
                        val seen = mutableSetOf<String>()
                        val deduped = colls.filter { seen.add(it.uuid) }
                        if (deduped.size < colls.size) {
                            log.warn(
                                "[{}] Batch {} dropped {} duplicate collections",
                                sessionCorrelationId,
                                batchIndex,
                                colls.size - deduped.size,
                            )
                        }
                        deduped
                    }

                    else -> {
                        cursorFilteredBatch
                    }
                }
            val result =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        processSkillBatch(
                            target,
                            dedupedBatch as List<RichSkillDescriptorDao>,
                            syncKey,
                            recordType,
                            batchIndex,
                            sessionCorrelationId,
                        )
                    }

                    SyncRecordType.COLLECTION -> {
                        processCollectionBatch(
                            target,
                            dedupedBatch as List<CollectionDao>,
                            syncKey,
                            recordType,
                            batchIndex,
                            sessionCorrelationId,
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

            val (maxDate, maxId) =
                when (recordType) {
                    SyncRecordType.SKILL -> {
                        val skillBatch = dedupedBatch as List<RichSkillDescriptorDao>
                        val maxByDate =
                            skillBatch.maxWithOrNull(
                                compareBy<RichSkillDescriptorDao> { it.updateDate }.thenBy { it.id.value },
                            )!!
                        maxByDate.updateDate to maxByDate.id.value
                    }

                    SyncRecordType.COLLECTION -> {
                        val colBatch = dedupedBatch as List<CollectionDao>
                        val maxByDate =
                            colBatch.maxWithOrNull(
                                compareBy<CollectionDao> { it.updateDate }.thenBy { it.id.value },
                            )!!
                        maxByDate.updateDate to maxByDate.id.value
                    }

                    else -> {
                        return Result.failure(IllegalArgumentException("Unknown: $recordType"))
                    }
                }
            syncStateRepository.updateWatermark(
                SYNC_TYPE,
                syncKey,
                recordType,
                maxDate,
                maxId,
            )
            log.debug(
                "[{}] Batch {} done, watermark advanced to date={} id={}",
                sessionCorrelationId,
                batchIndex,
                maxDate,
                maxId,
            )
            watermarkDate = maxDate
            watermarkId = maxId
            batchIndex++
        }
        syncStateRepository.updateStatusJson(
            SYNC_TYPE,
            syncKey,
            recordType,
            SyncStatusJson(
                inProgress = false,
                batchesCompleted = batchIndex,
                sessionCorrelationId = sessionCorrelationId,
                lastUpdatedAt = nowIso(),
            ).toJsonString(),
        )
        log.info("[{}] Sync completed recordType={}", sessionCorrelationId, recordType)
        return Result.success(Unit)
    }

    private fun processSkillBatch(
        target: SyncTarget,
        batch: List<RichSkillDescriptorDao>,
        syncKey: String,
        recordType: String,
        batchIndex: Int,
        sessionCorrelationId: String,
    ): Result<Unit> {
        for (dao in batch) {
            log.debug(
                "[{}] Processing skill id={} uuid={} batch={}",
                sessionCorrelationId,
                dao.id.value,
                dao.uuid,
                batchIndex,
            )
            val result = syncOneSkillWithRetry(target, dao)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                log.error(
                    "[{}] Sync failed skill={} batch={} error={}",
                    sessionCorrelationId,
                    dao.uuid,
                    batchIndex,
                    err?.message,
                )
                val status =
                    SyncStatusJson(
                        lastRecordUuid = dao.uuid,
                        lastRecordName = dao.name,
                        batchIndex = batchIndex,
                        batchesCompleted = batchIndex,
                        lastUpdatedAt = nowIso(),
                        sessionCorrelationId = sessionCorrelationId,
                        inProgress = false,
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
                    sessionCorrelationId = sessionCorrelationId,
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
        sessionCorrelationId: String,
    ): Result<Unit> {
        for (dao in batch) {
            log.debug(
                "[{}] Processing collection id={} uuid={} batch={}",
                sessionCorrelationId,
                dao.id.value,
                dao.uuid,
                batchIndex,
            )
            val result = syncOneCollectionWithRetry(target, dao)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                log.error(
                    "[{}] Sync failed collection={} batch={} error={}",
                    sessionCorrelationId,
                    dao.uuid,
                    batchIndex,
                    err?.message,
                )
                val status =
                    SyncStatusJson(
                        lastRecordUuid = dao.uuid,
                        lastRecordName = dao.name,
                        batchIndex = batchIndex,
                        batchesCompleted = batchIndex,
                        lastUpdatedAt = nowIso(),
                        sessionCorrelationId = sessionCorrelationId,
                        inProgress = false,
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
                    sessionCorrelationId = sessionCorrelationId,
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

    fun syncAllSinceWatermark(syncKey: String = SYNC_KEY_DEFAULT): Result<Unit> {
        val sessionCorrelationId = generateCorrelationId()
        MDC.put(MDC_CORRELATION_ID, sessionCorrelationId)
        return try {
            syncTargetOpt
                .map { target ->
                    log.info("[{}] Sync all started syncKey={}", sessionCorrelationId, syncKey)
                    doSyncSinceWatermark(
                        target,
                        syncKey,
                        SyncRecordType.SKILL,
                        sessionCorrelationId,
                    ).fold(
                        onSuccess = {
                            doSyncSinceWatermark(
                                target,
                                syncKey,
                                SyncRecordType.COLLECTION,
                                sessionCorrelationId,
                            )
                        },
                        onFailure = { e ->
                            log.error(
                                "[{}] Sync all failed at skills error={}",
                                sessionCorrelationId,
                                e.message,
                            )
                            Result.failure(e)
                        },
                    ).also { result ->
                        result
                            .onSuccess {
                                log.info(
                                    "[{}] Sync all completed syncKey={}",
                                    sessionCorrelationId,
                                    syncKey,
                                )
                            }.onFailure { e ->
                                log.error(
                                    "[{}] Sync all failed syncKey={} error={}",
                                    sessionCorrelationId,
                                    syncKey,
                                    e.message,
                                )
                            }
                    }
                }.orElse(Result.failure(IllegalStateException("Sync not configured")))
        } finally {
            MDC.remove(MDC_CORRELATION_ID)
        }
    }

    fun getSyncState(syncKey: String = SYNC_KEY_DEFAULT): List<SyncState> =
        syncStateRepository.findAllBySyncKey(SYNC_TYPE, syncKey)

    fun getPendingCount(
        syncKey: String,
        recordType: String,
    ): Int {
        val watermark =
            syncStateRepository.getWatermark(SYNC_TYPE, syncKey, recordType)
        val lastRecordId =
            syncStateRepository.getLastRecordId(SYNC_TYPE, syncKey, recordType)
        return when (recordType) {
            SyncRecordType.SKILL -> {
                countSkillsUpdatedSince(watermark, lastRecordId)
            }

            SyncRecordType.COLLECTION -> {
                countCollectionsUpdatedSince(watermark, lastRecordId)
            }

            else -> {
                0
            }
        }
    }

    fun isConfigured(): Boolean = syncTargetOpt.isPresent

    fun resyncAll(syncKey: String = SYNC_KEY_DEFAULT): Result<Unit> {
        syncStateRepository.resetWatermark(SYNC_TYPE, syncKey, SyncRecordType.SKILL)
        syncStateRepository.resetWatermark(SYNC_TYPE, syncKey, SyncRecordType.COLLECTION)
        return syncAllSinceWatermark(syncKey)
    }
}
