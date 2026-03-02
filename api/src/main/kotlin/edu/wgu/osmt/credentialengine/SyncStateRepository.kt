package edu.wgu.osmt.credentialengine

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
@Transactional
class SyncStateRepository {
    fun getWatermark(
        syncType: String,
        syncKey: String,
        recordType: String,
    ): LocalDateTime? =
        SyncStateTable
            .select {
                (SyncStateTable.syncType eq syncType) and
                    (SyncStateTable.syncKey eq syncKey) and
                    (SyncStateTable.recordType eq recordType)
            }.firstOrNull()
            ?.get(SyncStateTable.syncWatermark)

    fun updateWatermark(
        syncType: String,
        syncKey: String,
        recordType: String,
        watermark: LocalDateTime,
    ) {
        SyncStateTable.update({
            (SyncStateTable.syncType eq syncType) and
                (SyncStateTable.syncKey eq syncKey) and
                (SyncStateTable.recordType eq recordType)
        }) {
            it[syncWatermark] = watermark
        }
    }

    fun getOrCreateRow(
        syncType: String,
        syncKey: String,
        recordType: String,
    ): SyncState {
        val existing =
            SyncStateTable
                .select {
                    (SyncStateTable.syncType eq syncType) and
                        (SyncStateTable.syncKey eq syncKey) and
                        (SyncStateTable.recordType eq recordType)
                }.firstOrNull()

        return if (existing != null) {
            SyncState(
                syncType = existing[SyncStateTable.syncType],
                syncKey = existing[SyncStateTable.syncKey],
                recordType = existing[SyncStateTable.recordType],
                syncWatermark = existing[SyncStateTable.syncWatermark],
            )
        } else {
            SyncStateTable.insert {
                it[SyncStateTable.syncType] = syncType
                it[SyncStateTable.syncKey] = syncKey
                it[SyncStateTable.recordType] = recordType
                it[SyncStateTable.syncWatermark] = null
            }
            SyncState(syncType, syncKey, recordType, null)
        }
    }

    fun findAllBySyncKey(
        syncType: String,
        syncKey: String,
    ): List<SyncState> =
        SyncStateTable
            .select {
                (SyncStateTable.syncType eq syncType) and
                    (SyncStateTable.syncKey eq syncKey)
            }.map {
                SyncState(
                    syncType = it[SyncStateTable.syncType],
                    syncKey = it[SyncStateTable.syncKey],
                    recordType = it[SyncStateTable.recordType],
                    syncWatermark = it[SyncStateTable.syncWatermark],
                )
            }
}
