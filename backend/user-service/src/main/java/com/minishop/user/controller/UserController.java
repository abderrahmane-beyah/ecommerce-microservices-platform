package com.minishop.user.controller;

import com.minishop.common.dto.ApiResponse;
import com.minishop.common.util.HeaderUtil;
import com.minishop.user.dto.UserResponse;
import com.minishop.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(HttpServletRequest request) {
        UUID userId = HeaderUtil.getUserId(request);
        return ApiResponse.success(userService.getById(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(userService.getById(id));
    }
}
