package edu.wgu.osmt.ui

import com.fasterxml.jackson.databind.ObjectMapper
import edu.wgu.osmt.config.AppConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class UiController {
    @Autowired
    lateinit var appConfig: AppConfig

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @RequestMapping()
    fun index(): String = javaClass.getResource("/ui/index.html")?.readText(Charsets.UTF_8) ?: "UI not configured"

    @GetMapping(
        "/whitelabel/whitelabel.json",
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @ResponseBody
    fun whitelabelConfig(): Map<String, Any> {
        // Start with static whitelabel config if it exists
        val staticConfig =
            try {
                val staticJson = javaClass.getResource("/docker/whitelabel/whitelabel.json")?.readText(Charsets.UTF_8)
                if (staticJson != null) {
                    objectMapper.readValue(staticJson, Map::class.java) as Map<String, Any>
                } else {
                    mutableMapOf<String, Any>()
                }
            } catch (e: Exception) {
                mutableMapOf<String, Any>()
            }

        // Create dynamic config with loginUrl and authMode
        val dynamicConfig = mutableMapOf<String, Any>()
        if (appConfig.loginUrl.isNotBlank()) {
            dynamicConfig["loginUrl"] = appConfig.loginUrl
        }
        // Set authMode from configuration
        dynamicConfig["authMode"] = appConfig.authMode

        // Merge static and dynamic config (dynamic takes precedence)
        return staticConfig + dynamicConfig
    }
}
