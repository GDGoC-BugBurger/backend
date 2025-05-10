package org.ll.bugburgerbackend.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record SignUpRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String nickname,
        @NotBlank String birth
) {
}
