package com.mungroute.user.controller;

import com.mungroute.auth.service.AuthService;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal MungrouteUserPrincipal principal) {
        return authService.getUser(principal.userId());
    }
}
