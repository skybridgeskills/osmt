package edu.wgu.osmt.security

import edu.wgu.osmt.RoutePaths
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.security.config.annotation.web.builders.HttpSecurity

/**
 * Helper utility for shared security configuration between OAuth2 and single-auth profiles.
 *
 * This class provides a single source of truth for all authorization rules, eliminating duplication
 * between [SecurityConfig] and [SingleAuthSecurityConfig].
 *
 * All authorization rules are centralized here to ensure consistency between OAuth2 and single-auth
 * security profiles.
 */
object SecurityConfigHelper {
    /**
     * Builds API path for all versions (v2, v3, unversioned).
     *
     * @param endpoint The endpoint path (e.g., RoutePaths.SKILL_AUDIT_LOG)
     * @return Array of three paths: [v3 path, v2 path, unversioned path]
     */
    fun buildAllVersions(endpoint: String): Array<String> =
        arrayOf(
            RoutePaths.API + RoutePaths.API_V3 + endpoint,
            RoutePaths.API + RoutePaths.API_V2 + endpoint,
            RoutePaths.API + RoutePaths.UNVERSIONED + endpoint,
        )

    /**
     * Configures public endpoints that require no authentication.
     *
     * These endpoints are accessible without authentication:
     * - Search endpoints (POST /api/{version}/search/skills, /api/{version}/search/collections)
     * - Canonical URL endpoints (GET /api/{version}/skills/{uuid},
     * /api/{version}/collections/{uuid})
     * - Collection skills endpoint (POST /api/{version}/collections/{uuid}/skills)
     * - Collection export endpoints (CSV, XLSX)
     * - Task detail endpoints (text, media)
     *
     * @param http HttpSecurity builder to configure
     * @return HttpSecurity builder for method chaining
     */
    fun configurePublicEndpoints(http: HttpSecurity): HttpSecurity =
        http.authorizeHttpRequests { auth ->
            auth
                // UI routes - must be public to allow login page and static resources
                .requestMatchers("/", "/login", "/login/**", "/login/success")
                .permitAll()
                .requestMatchers(
                    "/assets/**",
                    "/config/**",
                    "/*.js",
                    "/*.css",
                    "/*.html",
                    "/*.ico",
                    "/*.png",
                    "/*.svg",
                ).permitAll()
                // Whitelabel config endpoint (needed for frontend initialization)
                .requestMatchers(GET, "/whitelabel/**")
                .permitAll()
                // Login endpoint for single-auth profile
                .requestMatchers(POST, "/api/auth/login")
                .permitAll()
                // Public search endpoints
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SEARCH_SKILLS))
                .permitAll()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SEARCH_COLLECTIONS))
                .permitAll()
                // Public skills filter endpoint (for the skills list page)
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILLS_FILTER))
                .permitAll()
                // Public canonical URL endpoints
                .requestMatchers(GET, *buildAllVersions(RoutePaths.SKILL_DETAIL))
                .permitAll()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTION_DETAIL))
                .permitAll()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_SKILLS))
                .permitAll()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTION_CSV))
                .permitAll()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTION_XLSX))
                .permitAll()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.TASK_DETAIL_TEXT))
                .permitAll()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.TASK_DETAIL_MEDIA))
                .permitAll()
        }

    /**
     * Configures endpoints that require authentication (but no specific roles).
     *
     * These endpoints require any authenticated user:
     * - Audit log endpoints
     * - Task detail endpoints (skills, batch)
     * - Search metadata endpoints (jobcodes, keywords)
     *
     * @param http HttpSecurity builder to configure
     * @return HttpSecurity builder for method chaining
     */
    fun configureAuthenticatedEndpoints(http: HttpSecurity): HttpSecurity =
        http.authorizeHttpRequests { auth ->
            auth
                .requestMatchers(GET, *buildAllVersions(RoutePaths.SKILL_AUDIT_LOG))
                .authenticated()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTION_AUDIT_LOG))
                .authenticated()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.TASK_DETAIL_SKILLS))
                .authenticated()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.TASK_DETAIL_BATCH))
                .authenticated()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.SEARCH_JOBCODES_PATH))
                .authenticated()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.SEARCH_KEYWORDS_PATH))
                .authenticated()
        }

    /**
     * Configures list endpoints with role-based or public access.
     *
     * @param http HttpSecurity builder to configure
     * @param allowPublic If true, lists are public; if false, require ADMIN, CURATOR, VIEW, or READ
     * roles
     * @param admin Admin role name
     * @param curator Curator role name
     * @param view View role name
     * @param read Read scope name
     * @return HttpSecurity builder for method chaining
     */
    fun configureListEndpoints(
        http: HttpSecurity,
        allowPublic: Boolean,
        admin: String,
        curator: String,
        view: String,
        read: String,
    ): HttpSecurity =
        http.authorizeHttpRequests { auth ->
            if (allowPublic) {
                auth
                    .requestMatchers(GET, *buildAllVersions(RoutePaths.SKILLS_LIST))
                    .permitAll()
                    .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTIONS_LIST))
                    .permitAll()
            } else {
                auth
                    .requestMatchers(GET, *buildAllVersions(RoutePaths.SKILLS_LIST))
                    .hasAnyAuthority(admin, curator, view, read)
                    .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTIONS_LIST))
                    .hasAnyAuthority(admin, curator, view, read)
            }
        }

    /**
     * Configures role-based endpoints for write operations.
     *
     * Role requirements:
     * - Skill update/create: ADMIN or CURATOR
     * - Skill publish: ADMIN only
     * - Collection create/update: ADMIN or CURATOR
     * - Collection publish: ADMIN only
     * - Collection delete: ADMIN only
     * - Workspace: ADMIN or CURATOR
     * - All other API endpoints: ADMIN, CURATOR, VIEW, or READ
     *
     * @param http HttpSecurity builder to configure
     * @param admin Admin role name
     * @param curator Curator role name
     * @param view View role name
     * @param read Read scope name
     * @return HttpSecurity builder for method chaining
     */
    fun configureRoleBasedEndpoints(
        http: HttpSecurity,
        admin: String,
        curator: String,
        view: String,
        read: String,
    ): HttpSecurity =
        http.authorizeHttpRequests { auth ->
            auth
                // Skill operations
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILL_UPDATE))
                .hasAnyAuthority(admin, curator)
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILLS_CREATE))
                .hasAnyAuthority(admin, curator)
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILL_PUBLISH))
                .hasAnyAuthority(admin)
                // Collection operations
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_CREATE))
                .hasAnyAuthority(admin, curator)
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_PUBLISH))
                .hasAnyAuthority(admin)
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_UPDATE))
                .hasAnyAuthority(admin, curator)
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_SKILLS_UPDATE))
                .hasAnyAuthority(admin, curator)
                .requestMatchers(DELETE, *buildAllVersions(RoutePaths.COLLECTION_REMOVE))
                .hasAnyAuthority(admin)
                // Workspace
                .requestMatchers(GET, *buildAllVersions(RoutePaths.WORKSPACE_PATH))
                .hasAnyAuthority(admin, curator)
                // Catch-all for other API endpoints
                .requestMatchers("/api/**")
                .hasAnyAuthority(admin, curator, view, read)
                .requestMatchers("/**")
                .permitAll()
        }

    /**
     * Configures endpoints for no-role mode (simple authentication only).
     *
     * In this mode:
     * - List endpoints: Public
     * - Write operations: Require authentication (any authenticated user)
     * - Collection delete: Denied (denyAll)
     * - All other endpoints: Public
     *
     * @param http HttpSecurity builder to configure
     * @return HttpSecurity builder for method chaining
     */
    fun configureNoRoleEndpoints(http: HttpSecurity): HttpSecurity =
        http.authorizeHttpRequests { auth ->
            auth
                // Lists are public
                .requestMatchers(GET, *buildAllVersions(RoutePaths.SKILLS_LIST))
                .permitAll()
                .requestMatchers(GET, *buildAllVersions(RoutePaths.COLLECTIONS_LIST))
                .permitAll()
                // Write operations require authentication
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILL_UPDATE))
                .authenticated()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILLS_CREATE))
                .authenticated()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.SKILL_PUBLISH))
                .authenticated()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_CREATE))
                .authenticated()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_PUBLISH))
                .authenticated()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_UPDATE))
                .authenticated()
                .requestMatchers(POST, *buildAllVersions(RoutePaths.COLLECTION_SKILLS_UPDATE))
                .authenticated()
                // Collection delete is denied
                .requestMatchers(DELETE, *buildAllVersions(RoutePaths.COLLECTION_REMOVE))
                .denyAll()
                // Fall-through: all other endpoints are public
                .requestMatchers("/**")
                .permitAll()
        }
}
