package com.mungroute.user.controller;

import com.mungroute.auth.dto.AvailabilityResponse;
import com.mungroute.auth.security.MungrouteUserPrincipal;
import com.mungroute.user.dto.request.NotificationSettingRequest;
import com.mungroute.user.dto.request.UpdateUserProfileRequest;
import com.mungroute.user.dto.response.NotificationSettingResponse;
import com.mungroute.user.dto.response.UserResponse;
import com.mungroute.user.service.NotificationSettingService;
import com.mungroute.user.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/users/me")
public class UserSettingsController {
    private final UserProfileService profileService;
    private final NotificationSettingService notificationService;

    public UserSettingsController(UserProfileService profileService, NotificationSettingService notificationService) {
        this.profileService = profileService;
        this.notificationService = notificationService;
    }

    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal MungrouteUserPrincipal user,
                               @Valid @RequestBody UpdateUserProfileRequest request) {
        return profileService.update(user.userId(), request);
    }

    @GetMapping("/check-nickname")
    public AvailabilityResponse checkNickname(
            @AuthenticationPrincipal MungrouteUserPrincipal user,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String nickname
    ) {
        return new AvailabilityResponse(profileService.isNicknameAvailable(user.userId(), nickname));
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal MungrouteUserPrincipal user) {
        profileService.deactivate(user.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications")
    public NotificationSettingResponse notifications(@AuthenticationPrincipal MungrouteUserPrincipal user) {
        return notificationService.get(user.userId());
    }

    @PatchMapping("/notifications")
    public NotificationSettingResponse updateNotifications(@AuthenticationPrincipal MungrouteUserPrincipal user,
                                                            @Valid @RequestBody NotificationSettingRequest request) {
        return notificationService.update(user.userId(), request);
    }
}
