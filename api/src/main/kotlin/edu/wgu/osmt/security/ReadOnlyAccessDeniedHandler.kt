package edu.wgu.osmt.security

import com.fasterxml.jackson.databind.ObjectMapper
import edu.wgu.osmt.api.model.ApiError
import edu.wgu.osmt.config.AppConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * JSON 403 responses for operations blocked on a read-only instance.
 */
@Component
@Profile("readonly & !oauth2")
class ReadOnlyAccessDeniedHandler(
    private val appConfig: AppConfig,
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        ignored: AccessDeniedException?,
    ) {
        response?.let {
            it.contentType = "application/json;charset=UTF-8"
            it.status = HttpStatus.FORBIDDEN.value()
            val base = "This instance is read-only. Changes are not allowed here."
            val url = appConfig.writableInstanceUrl.trim()
            val message =
                if (url.isNotEmpty()) {
                    "$base Use the authoring instance at $url."
                } else {
                    base
                }
            objectMapper.writeValue(it.writer, ApiError(message = message))
            it.flushBuffer()
        }
    }
}
