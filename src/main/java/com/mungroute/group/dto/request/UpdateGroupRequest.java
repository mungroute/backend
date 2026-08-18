package com.mungroute.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UpdateGroupRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 200) String description,
        @Pattern(regexp = "PUBLIC|PRIVATE") String visibility,
        @Pattern(regexp = "OPEN|INVITE_ONLY") String joinPolicy
) {
    public UpdateGroupRequest(String name, String description) {
        this(name, description, null, null);
    }
}
