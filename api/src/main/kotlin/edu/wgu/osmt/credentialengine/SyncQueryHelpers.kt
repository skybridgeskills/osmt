package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.collection.CollectionDao
import edu.wgu.osmt.collection.CollectionTable
import edu.wgu.osmt.richskill.RichSkillDescriptorDao
import edu.wgu.osmt.richskill.RichSkillDescriptorTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.time.LocalDateTime

object SyncRecordType {
    const val SKILL = "skill"
    const val COLLECTION = "collection"
}

fun findSkillsUpdatedSince(
    watermark: LocalDateTime?,
    limit: Int,
): List<RichSkillDescriptorDao> =
    RichSkillDescriptorDao
        .wrapRows(
            when (watermark) {
                null -> {
                    RichSkillDescriptorTable.select {
                        RichSkillDescriptorTable.publishDate.isNotNull()
                    }
                }

                else -> {
                    RichSkillDescriptorTable.select {
                        (RichSkillDescriptorTable.updateDate greater watermark) and
                            RichSkillDescriptorTable.publishDate.isNotNull()
                    }
                }
            }.orderBy(RichSkillDescriptorTable.updateDate),
        ).limit(limit, 0)
        .toList()

fun findCollectionsUpdatedSince(
    watermark: LocalDateTime?,
    limit: Int,
): List<CollectionDao> =
    CollectionDao
        .wrapRows(
            when (watermark) {
                null -> {
                    CollectionTable.select {
                        CollectionTable.status inList
                            listOf(
                                edu.wgu.osmt.db.PublishStatus.Published,
                                edu.wgu.osmt.db.PublishStatus.Archived,
                            )
                    }
                }

                else -> {
                    CollectionTable.select {
                        (CollectionTable.updateDate greater watermark) and
                            (
                                CollectionTable.status inList
                                    listOf(
                                        edu.wgu.osmt.db.PublishStatus.Published,
                                        edu.wgu.osmt.db.PublishStatus.Archived,
                                    )
                            )
                    }
                }
            }.orderBy(CollectionTable.updateDate),
        ).limit(limit, 0)
        .toList()
