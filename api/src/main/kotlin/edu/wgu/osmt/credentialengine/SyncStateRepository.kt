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

    fun getStatusJson(
        syncType: String,
        syncKey: String,
        recordType: String,
    ): String? =
        SyncStateTable
            .select {
                (SyncStateTable.syncType eq syncType) and
                    (SyncStateTable.syncKey eq syncKey) and
                    (SyncStateTable.recordType eq recordType)
            }.firstOrNull()
            ?.get(SyncStateTable.statusJson)

    fun updateStatusJson(
        syncType: String,
        syncKey: String,
        recordType: String,
        statusJson: String,
    ) {
        SyncStateTable.update({
            (SyncStateTable.syncType eq syncType) and
                (SyncStateTable.syncKey eq syncKey) and
                (SyncStateTable.recordType eq recordType)
        }) {
            it[SyncStateTable.statusJson] = statusJson
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
                statusJson = existing[SyncStateTable.statusJson],
            )
        } else {
            SyncStateTable.insert {
                it[SyncStateTable.syncType] = syncType
                it[SyncStateTable.syncKey] = syncKey
                it[SyncStateTable.recordType] = recordType
                it[SyncStateTable.syncWatermark] = null
                it[SyncStateTable.statusJson] = null
            }
            SyncState(syncType, syncKey, recordType, null, null)
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
                    statusJson = it[SyncStateTable.statusJson],
                )
            }
}
