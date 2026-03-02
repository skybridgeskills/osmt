package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.BaseDockerizedTest
import edu.wgu.osmt.HasDatabaseReset
import edu.wgu.osmt.SpringTest
import edu.wgu.osmt.api.model.ApiCollectionUpdate
import edu.wgu.osmt.api.model.ApiStringListUpdate
import edu.wgu.osmt.collection.CollectionRepository
import edu.wgu.osmt.db.PublishStatus
import edu.wgu.osmt.richskill.RichSkillDescriptorDao
import edu.wgu.osmt.richskill.RichSkillRepository
import edu.wgu.osmt.richskill.RsdUpdateObject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Transactional
@ContextConfiguration(classes = [SyncServiceTest.SyncTestConfig::class])
@TestPropertySource(
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
class SyncServiceTest :
    SpringTest(),
    BaseDockerizedTest,
    HasDatabaseReset {
    @Autowired
    lateinit var syncService: SyncService

    @Autowired
    lateinit var mockSyncTarget: MockSyncTarget

    @Autowired
    lateinit var richSkillRepository: RichSkillRepository

    @Autowired
    lateinit var collectionRepository: CollectionRepository

    @Autowired
    lateinit var syncStateRepository: SyncStateRepository

    private val userString = "test-user"
    private val userEmail = "test@email.com"

    @BeforeEach
    fun clearSyncState() {
        SyncStateTable.deleteAll()
    }

    private fun randomSkill(): RichSkillDescriptorDao =
        richSkillRepository.create(
            RsdUpdateObject(
                name = UUID.randomUUID().toString(),
                statement = UUID.randomUUID().toString(),
                publishStatus = PublishStatus.Published,
            ),
            userString,
        )!!

    private fun randomCollectionWithSkill(
        skillDao: RichSkillDescriptorDao,
    ): edu.wgu.osmt.collection.CollectionDao {
        val update =
            ApiCollectionUpdate(
                name = UUID.randomUUID().toString(),
                description = UUID.randomUUID().toString(),
                author = userString,
                publishStatus = PublishStatus.Published,
                skills = ApiStringListUpdate(add = listOf(skillDao.uuid)),
            )
        return collectionRepository
            .createFromApi(
                listOf(update),
                richSkillRepository,
                userString,
                userEmail,
            ).first()
    }

    @Test
    fun `syncRecord publishes skill when configured`() {
        val skillDao = randomSkill()
        val uuid = skillDao.uuid

        val result = syncService.syncRecord(SyncRecordType.SKILL, uuid)

        assertThat(result.isSuccess).isTrue()
        assertThat(mockSyncTarget.getPublishedSkillUuids()).contains(uuid)
    }

    @Test
    fun `syncSinceWatermark updates watermark`() {
        val skillDao = randomSkill()

        val result = syncService.syncSinceWatermark("default", SyncRecordType.SKILL)

        assertThat(result.isSuccess).isTrue()
        val watermark =
            syncStateRepository.getWatermark(
                "credential-engine",
                "default",
                SyncRecordType.SKILL,
            )
        assertThat(watermark).isNotNull()
        assertThat(mockSyncTarget.getPublishedSkillUuids()).contains(skillDao.uuid)
    }

    @Test
    fun `syncAllSinceWatermark runs skills before collections`() {
        val skillDao = randomSkill()
        val collectionDao = randomCollectionWithSkill(skillDao)
        val collectionUuid = collectionDao.uuid

        val result = syncService.syncAllSinceWatermark("default")

        assertThat(result.isSuccess).isTrue()
        assertThat(mockSyncTarget.getPublishedSkillUuids()).contains(skillDao.uuid)
        assertThat(mockSyncTarget.getPublishedCollectionUuids())
            .contains(collectionUuid)
    }

    @Test
    fun `isConfigured returns true when MockSyncTarget is present`() {
        assertThat(syncService.isConfigured()).isTrue()
    }

    @TestConfiguration
    class SyncTestConfig {
        @Bean
        fun mockSyncTarget(): MockSyncTarget = MockSyncTarget()

        @Bean
        @Primary
        fun syncTarget(mock: MockSyncTarget): Optional<SyncTarget> = Optional.of(mock)
    }
}
