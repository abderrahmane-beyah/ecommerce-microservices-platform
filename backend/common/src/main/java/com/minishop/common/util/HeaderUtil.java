package com.minishop.common.util;

import com.minishop.common.security.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public class HeaderUtil {

    public static UUID getUserId(HttpServletRequest request) {
        String value = request.getHeader(SecurityConstants.INTERNAL_USER_ID_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public static String getUserRole(HttpServletRequest request) {
        return request.getHeader(SecurityConstants.INTERNAL_USER_ROLE_HEADER);
    }
}
