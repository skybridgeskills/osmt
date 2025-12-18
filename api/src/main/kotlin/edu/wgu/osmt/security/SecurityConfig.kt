package edu.wgu.osmt.security

import com.fasterxml.jackson.databind.ObjectMapper
import edu.wgu.osmt.RoutePaths
import edu.wgu.osmt.api.model.ApiError
import edu.wgu.osmt.config.AppConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Security configurations
 * - to enable another OAuth provider, include the profile name in place of `OTHER-OAUTH-PROFILE`
 *   in the @Profile annotation
 */
@Configuration
@EnableWebSecurity
@Profile("oauth2-okta | OTHER-OAUTH-PROFILE")
class SecurityConfig {
    @Autowired
    lateinit var appConfig: AppConfig

    @Autowired
    lateinit var redirectToFrontend: RedirectToFrontend

    @Autowired
    lateinit var returnUnauthorized: ReturnUnauthorized

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors()
            .and()
            .csrf()
            .disable()
            .httpBasic()
            .disable()
            // Configure public and authenticated endpoints using shared helper
            // This ensures consistency with single-auth security configuration
            .also {
                SecurityConfigHelper.configurePublicEndpoints(it)
                SecurityConfigHelper.configureAuthenticatedEndpoints(it)
            }.exceptionHandling()
            .authenticationEntryPoint(returnUnauthorized)
            .and()
            .oauth2Login()
            .successHandler(redirectToFrontend)
            .and()
            .oauth2ResourceServer()
            .jwt()

        if (appConfig.enableRoles) {
            configureForRoles(http)
        } else {
            configureForNoRoles(http)
        }

        return http.build()
    }

    fun configureForRoles(http: HttpSecurity) {
        val ADMIN = appConfig.roleAdmin
        val CURATOR = appConfig.roleCurator
        val VIEW = appConfig.roleView
        val READ = appConfig.scopeRead

        // Use shared helper to ensure consistency with single-auth configuration
        SecurityConfigHelper.configureListEndpoints(
            http,
            appConfig.allowPublicLists,
            ADMIN,
            CURATOR,
            VIEW,
            READ,
        )
        SecurityConfigHelper.configureRoleBasedEndpoints(
            http,
            ADMIN,
            CURATOR,
            VIEW,
            READ,
        )
    }

    fun configureForNoRoles(http: HttpSecurity) {
        // Use shared helper to ensure consistency with single-auth configuration
        SecurityConfigHelper.configureNoRoleEndpoints(http)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource? {
        val configuration: CorsConfiguration = CorsConfiguration()
        configuration.allowedOrigins = appConfig.corsAllowedOrigins.split(",")
        configuration.allowedMethods = listOf("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH")
        // setAllowCredentials(true) is important, otherwise:
        // The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' when the request's credentials mode is 'include'.
        configuration.allowCredentials = true
        // setAllowedHeaders is important! Without it, OPTIONS preflight request
        // will fail with 403 Invalid CORS request
        configuration.allowedHeaders = listOf("Authorization", "Cache-Control", "Content-Type")
        configuration.exposedHeaders = listOf("X-Total-Count")
        val source: UrlBasedCorsConfigurationSource = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

@Component
class RedirectToFrontend : AuthenticationSuccessHandler {
    @Autowired
    lateinit var appConfig: AppConfig

    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        val redirectStrategy: DefaultRedirectStrategy = DefaultRedirectStrategy()
        when (authentication?.principal) {
            is OidcUser -> {
                val oidcUser: OidcUser = authentication.principal as OidcUser
                val tokenValue = oidcUser.idToken.tokenValue
                val url = "${appConfig.loginSuccessRedirectUrl}?token=$tokenValue"
                redirectStrategy.sendRedirect(request, response, url)
            }
        }
    }
}

@Component
class ReturnUnauthorized : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: AuthenticationException?,
    ) {
        response?.let {
            it.contentType = "application/json"
            it.characterEncoding = "UTF-8"
            val apiError = ApiError("Unauthorized")
            val mapper = ObjectMapper()
            mapper.writeValue(it.writer, apiError)
            it.flushBuffer()
        }
    }
}
