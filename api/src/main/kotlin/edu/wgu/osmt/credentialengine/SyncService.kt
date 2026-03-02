package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.collection.CollectionDao
import edu.wgu.osmt.collection.CollectionRepository
import edu.wgu.osmt.db.PublishStatus
import edu.wgu.osmt.richskill.RichSkillDescriptorDao
import edu.wgu.osmt.richskill.RichSkillRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
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
        val watermark = syncStateRepository.getWatermark(SYNC_TYPE, syncKey, recordType)
        val recordsAndDates =
            when (recordType) {
                SyncRecordType.SKILL -> processSkills(target, watermark)
                SyncRecordType.COLLECTION -> processCollections(target, watermark)
                else -> return Result.failure(IllegalArgumentException("Unknown recordType: $recordType"))
            }
        recordsAndDates.maxOrNull()?.let { maxDate ->
            syncStateRepository.updateWatermark(SYNC_TYPE, syncKey, recordType, maxDate)
        }
        return Result.success(Unit)
    }

    private fun processSkills(
        target: SyncTarget,
        watermark: LocalDateTime?,
    ): List<LocalDateTime> {
        val daos = findSkillsUpdatedSince(watermark)
        return daos.mapNotNull { dao ->
            val rsd = dao.toModel()
            val result =
                when (rsd.publishStatus()) {
                    PublishStatus.Published -> target.publishSkill(rsd)
                    PublishStatus.Archived -> target.deprecateSkill(rsd)
                    else -> return@mapNotNull null
                }
            result.fold(
                onSuccess = { dao.updateDate },
                onFailure = { throw it },
            )
        }
    }

    private fun processCollections(
        target: SyncTarget,
        watermark: LocalDateTime?,
    ): List<LocalDateTime> {
        val daos = findCollectionsUpdatedSince(watermark)
        return daos.mapNotNull { dao ->
            val collection = dao.toModel()
            val result =
                when (collection.status) {
                    PublishStatus.Published -> {
                        target.publishCollection(collection, skillCtids(dao))
                    }

                    PublishStatus.Archived -> {
                        target.deprecateCollection(collection)
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }
            result.fold(
                onSuccess = { dao.updateDate },
                onFailure = { throw it },
            )
        }
    }

    fun syncAllSinceWatermark(syncKey: String = SYNC_KEY_DEFAULT): Result<Unit> =
        syncTargetOpt
            .map {
                doSyncSinceWatermark(it, syncKey, SyncRecordType.SKILL)
                doSyncSinceWatermark(it, syncKey, SyncRecordType.COLLECTION)
                Result.success(Unit)
            }.orElse(Result.failure(IllegalStateException("Sync not configured")))

    fun getSyncState(syncKey: String = SYNC_KEY_DEFAULT): List<SyncState> =
        syncStateRepository.findAllBySyncKey(SYNC_TYPE, syncKey)

    fun isConfigured(): Boolean = syncTargetOpt.isPresent
}
