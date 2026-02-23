package com.minishop.common.security;

import java.util.List;

public class SecurityConstants {

    public static final String INTERNAL_USER_ID_HEADER = "X-User-Id";
    public static final String INTERNAL_USER_ROLE_HEADER = "X-User-Role";

    public static final long ACCESS_TOKEN_DURATION_MS = 3_600_000;   // 1 hour
    public static final long REFRESH_TOKEN_DURATION_MS = 604_800_000; // 7 days

    public static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    );
}
