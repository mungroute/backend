package com.mungroute.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinGroupRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9가-힣]{6}") String inviteCode
) {
}
