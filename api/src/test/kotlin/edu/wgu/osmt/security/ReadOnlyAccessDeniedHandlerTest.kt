package edu.wgu.osmt.security

import com.fasterxml.jackson.databind.ObjectMapper
import edu.wgu.osmt.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException

internal class ReadOnlyAccessDeniedHandlerTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `writes json message for read-only denial`() {
        val appConfig: AppConfig = mockk(relaxed = true)
        every { appConfig.writableInstanceUrl } returns ""
        val handler = ReadOnlyAccessDeniedHandler(appConfig, objectMapper)
        val response = MockHttpServletResponse()
        handler.handle(null, response, AccessDeniedException("denied"))
        assertThat(response.status).isEqualTo(HttpServletResponse.SC_FORBIDDEN)
        assertThat(response.contentAsString).contains("read-only")
    }

    @Test
    fun `includes authoring url when configured`() {
        val appConfig: AppConfig = mockk(relaxed = true)
        every { appConfig.writableInstanceUrl } returns "https://author.example.com"
        val handler = ReadOnlyAccessDeniedHandler(appConfig, objectMapper)
        val response = MockHttpServletResponse()
        handler.handle(null, response, AccessDeniedException("denied"))
        assertThat(response.contentAsString).contains("author.example.com")
    }
}
