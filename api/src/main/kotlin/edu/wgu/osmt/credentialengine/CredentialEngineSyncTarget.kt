package edu.wgu.osmt.credentialengine

import com.fasterxml.jackson.databind.ObjectMapper
import edu.wgu.osmt.collection.Collection
import edu.wgu.osmt.config.AppConfig
import edu.wgu.osmt.richskill.RichSkillDescriptor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

/**
 * SyncTarget implementation that publishes to the Credential Engine Registry
 * via the Registry Assistant API.
 */
class CredentialEngineSyncTarget(
    private val registryUrl: String,
    private val apiKey: String,
    private val orgCtid: String,
    private val appConfig: AppConfig,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
) : SyncTarget {
    companion object {
        private const val CTID_PREFIX = "ce-"
    }

    private val logger = LoggerFactory.getLogger(CredentialEngineSyncTarget::class.java)
    private val baseUrl = registryUrl.trimEnd('/') + "/assistant"

    override fun publishSkill(rsd: RichSkillDescriptor): Result<Unit> {
        val ctid = "$CTID_PREFIX${rsd.uuid}"
        val body =
            mapOf(
                "Competency" to
                    mapOf(
                        "CTID" to ctid,
                        "CompetencyText" to rsd.statement,
                        "CompetencyLabel" to rsd.name,
                        "Creator" to listOf(orgCtid),
                        "Author" to (rsd.authors.firstOrNull()?.value ?: ""),
                        "CompetencyCategory" to
                            rsd.categories.mapNotNull { it.value }.take(10),
                        "ConceptKeyword" to
                            rsd.searchingKeywords.mapNotNull { it.value }.take(20),
                        "PublicationStatusType" to "Published",
                        "ExactAlignment" to listOf(rsd.canonicalUrl(appConfig.baseUrl)),
                    ),
                "PublishForOrganizationIdentifier" to orgCtid,
                "DefaultLanguage" to "en-US",
            )
        return post("$baseUrl/competency/publish", body)
    }

    override fun deprecateSkill(rsd: RichSkillDescriptor): Result<Unit> {
        val ctid = "$CTID_PREFIX${rsd.uuid}"
        val body =
            mapOf(
                "Competency" to
                    mapOf(
                        "CTID" to ctid,
                        "CompetencyText" to rsd.statement,
                        "CompetencyLabel" to rsd.name,
                        "Creator" to listOf(orgCtid),
                        "Author" to (rsd.authors.firstOrNull()?.value ?: ""),
                        "CompetencyCategory" to
                            rsd.categories.mapNotNull { it.value }.take(10),
                        "ConceptKeyword" to
                            rsd.searchingKeywords.mapNotNull { it.value }.take(20),
                        "PublicationStatusType" to "Deprecated",
                    ),
                "PublishForOrganizationIdentifier" to orgCtid,
                "DefaultLanguage" to "en-US",
            )
        return post("$baseUrl/competency/publish", body)
    }

    override fun publishCollection(
        collection: Collection,
        skillCtids: List<String>,
    ): Result<Unit> {
        val ctid = "$CTID_PREFIX${collection.uuid}"
        val body =
            mapOf(
                "Collection" to
                    mapOf(
                        "CTID" to ctid,
                        "Name" to collection.name,
                        "Description" to (collection.description ?: ""),
                        "HasMember" to skillCtids,
                        "OwnedBy" to listOf(mapOf("CTID" to orgCtid)),
                        "LifeCycleStatusType" to "Active",
                    ),
                "PublishForOrganizationIdentifier" to orgCtid,
                "DefaultLanguage" to "en-US",
            )
        return post("$baseUrl/Collection/publish", body)
    }

    override fun deprecateCollection(collection: Collection): Result<Unit> {
        val ctid = "$CTID_PREFIX${collection.uuid}"
        val body =
            mapOf(
                "Collection" to
                    mapOf(
                        "CTID" to ctid,
                        "Name" to collection.name,
                        "Description" to (collection.description ?: ""),
                        "HasMember" to emptyList<String>(),
                        "OwnedBy" to listOf(mapOf("CTID" to orgCtid)),
                        "LifeCycleStatusType" to "Ceased",
                    ),
                "PublishForOrganizationIdentifier" to orgCtid,
                "DefaultLanguage" to "en-US",
            )
        return post("$baseUrl/Collection/publish", body)
    }

    private fun post(
        url: String,
        body: Map<String, Any?>,
    ): Result<Unit> {
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("Authorization", "ApiToken $apiKey")
            }
        val json = objectMapper.writeValueAsString(body)
        val entity = HttpEntity(json, headers)
        return try {
            restTemplate.postForEntity(url, entity, String::class.java)
            Result.success(Unit)
        } catch (e: HttpStatusCodeException) {
            logger.warn(
                "CE publish failed: {} {}",
                e.statusCode,
                e.responseBodyAsString,
            )
            Result.failure(
                Exception(
                    "CE publish failed: ${e.statusCode} - " +
                        e.responseBodyAsString.take(200),
                ),
            )
        } catch (e: Exception) {
            logger.warn("CE publish error", e)
            Result.failure(e)
        }
    }
}
