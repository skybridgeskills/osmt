package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.SpringTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
class SyncStateRepositoryTest : SpringTest() {
    @Autowired
    lateinit var syncStateRepository: SyncStateRepository

    @Test
    fun `getWatermark returns null when no row exists`() {
        val w =
            syncStateRepository.getWatermark(
                "credential-engine",
                "default",
                "skill",
            )
        assertThat(w).isNull()
    }

    @Test
    fun `updateWatermark and getWatermark`() {
        syncStateRepository.getOrCreateRow("credential-engine", "default", "skill")
        val now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        syncStateRepository.updateWatermark(
            "credential-engine",
            "default",
            "skill",
            now,
        )
        val w =
            syncStateRepository.getWatermark(
                "credential-engine",
                "default",
                "skill",
            )
        assertThat(w).isNotNull
        assertThat(w).isEqualTo(now)
    }

    @Test
    fun `getOrCreateRow creates and returns row`() {
        val s =
            syncStateRepository.getOrCreateRow(
                "credential-engine",
                "default",
                "skill",
            )
        assertThat(s.syncType).isEqualTo("credential-engine")
        assertThat(s.syncKey).isEqualTo("default")
        assertThat(s.recordType).isEqualTo("skill")
        assertThat(s.syncWatermark).isNull()
    }

    @Test
    fun `getOrCreateRow returns existing row`() {
        syncStateRepository.getOrCreateRow("credential-engine", "default", "skill")
        syncStateRepository.updateWatermark(
            "credential-engine",
            "default",
            "skill",
            LocalDateTime.now(),
        )
        val s =
            syncStateRepository.getOrCreateRow(
                "credential-engine",
                "default",
                "skill",
            )
        assertThat(s.syncWatermark).isNotNull()
    }

    @Test
    fun `getStatusJson returns null when not set`() {
        syncStateRepository.getOrCreateRow("credential-engine", "default", "skill")
        val s =
            syncStateRepository.getStatusJson(
                "credential-engine",
                "default",
                "skill",
            )
        assertThat(s).isNull()
    }

    @Test
    fun `updateStatusJson and getStatusJson`() {
        syncStateRepository.getOrCreateRow("credential-engine", "default", "skill")
        syncStateRepository.updateStatusJson(
            "credential-engine",
            "default",
            "skill",
            """{"error":{"message":"test","correlationId":"abc12"}}""",
        )
        val s =
            syncStateRepository.getStatusJson(
                "credential-engine",
                "default",
                "skill",
            )
        assertThat(s).contains("abc12")
        assertThat(s).contains("test")
    }

    @Test
    fun `findAllBySyncKey includes statusJson`() {
        syncStateRepository.getOrCreateRow("credential-engine", "default", "skill")
        syncStateRepository.updateStatusJson(
            "credential-engine",
            "default",
            "skill",
            """{"batchesCompleted":1}""",
        )
        val all =
            syncStateRepository.findAllBySyncKey(
                "credential-engine",
                "default",
            )
        val skillState = all.find { it.recordType == "skill" }
        assertThat(skillState).isNotNull
        assertThat(skillState!!.statusJson).contains("batchesCompleted")
    }

    @Test
    fun `findAllBySyncKey returns multiple record types`() {
        syncStateRepository.getOrCreateRow("credential-engine", "default", "skill")
        syncStateRepository.getOrCreateRow("credential-engine", "default", "collection")
        val all =
            syncStateRepository.findAllBySyncKey(
                "credential-engine",
                "default",
            )
        assertThat(all).hasSize(2)
        assertThat(all.map { it.recordType }).containsExactlyInAnyOrder(
            "skill",
            "collection",
        )
    }
}
