package org.ll.bugburgerbackend.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        String username,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
