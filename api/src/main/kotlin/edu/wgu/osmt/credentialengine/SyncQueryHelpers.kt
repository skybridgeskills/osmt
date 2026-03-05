package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.collection.CollectionDao
import edu.wgu.osmt.collection.CollectionTable
import edu.wgu.osmt.richskill.RichSkillDescriptorDao
import edu.wgu.osmt.richskill.RichSkillDescriptorTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import java.time.LocalDateTime

object SyncRecordType {
    const val SKILL = "skill"
    const val COLLECTION = "collection"
}

/** Composite cursor: (watermarkDate, watermarkId) for deterministic pagination when many records share the same updateDate. */
fun findSkillsUpdatedSince(
    watermarkDate: LocalDateTime?,
    watermarkId: Long?,
    limit: Int,
): List<RichSkillDescriptorDao> =
    RichSkillDescriptorDao
        .wrapRows(
            when {
                watermarkDate == null -> {
                    RichSkillDescriptorTable.select {
                        RichSkillDescriptorTable.publishDate.isNotNull()
                    }
                }

                watermarkId != null -> {
                    val watermarkEntityId = EntityID(watermarkId, RichSkillDescriptorTable)
                    RichSkillDescriptorTable.select {
                        (
                            (RichSkillDescriptorTable.updateDate greater watermarkDate) or
                                (
                                    (RichSkillDescriptorTable.updateDate eq watermarkDate) and
                                        (RichSkillDescriptorTable.id greater watermarkEntityId)
                                )
                        ) and RichSkillDescriptorTable.publishDate.isNotNull()
                    }
                }

                else -> {
                    RichSkillDescriptorTable.select {
                        (RichSkillDescriptorTable.updateDate greater watermarkDate) and
                            RichSkillDescriptorTable.publishDate.isNotNull()
                    }
                }
            }.orderBy(
                RichSkillDescriptorTable.updateDate to SortOrder.ASC,
                RichSkillDescriptorTable.id to SortOrder.ASC,
            ),
        ).limit(limit, 0)
        .toList()

fun findCollectionsUpdatedSince(
    watermarkDate: LocalDateTime?,
    watermarkId: Long?,
    limit: Int,
): List<CollectionDao> =
    CollectionDao
        .wrapRows(
            when {
                watermarkDate == null -> {
                    CollectionTable.select {
                        CollectionTable.status inList
                            listOf(
                                edu.wgu.osmt.db.PublishStatus.Published,
                                edu.wgu.osmt.db.PublishStatus.Archived,
                            )
                    }
                }

                watermarkId != null -> {
                    val watermarkEntityId = EntityID(watermarkId, CollectionTable)
                    CollectionTable.select {
                        (
                            (CollectionTable.updateDate greater watermarkDate) or
                                (
                                    (CollectionTable.updateDate eq watermarkDate) and
                                        (CollectionTable.id greater watermarkEntityId)
                                )
                        ) and
                            (
                                CollectionTable.status inList
                                    listOf(
                                        edu.wgu.osmt.db.PublishStatus.Published,
                                        edu.wgu.osmt.db.PublishStatus.Archived,
                                    )
                            )
                    }
                }

                else -> {
                    CollectionTable.select {
                        (CollectionTable.updateDate greater watermarkDate) and
                            (
                                CollectionTable.status inList
                                    listOf(
                                        edu.wgu.osmt.db.PublishStatus.Published,
                                        edu.wgu.osmt.db.PublishStatus.Archived,
                                    )
                            )
                    }
                }
            }.orderBy(
                CollectionTable.updateDate to SortOrder.ASC,
                CollectionTable.id to SortOrder.ASC,
            ),
        ).limit(limit, 0)
        .toList()
