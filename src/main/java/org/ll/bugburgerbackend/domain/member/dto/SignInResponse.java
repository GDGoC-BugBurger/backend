package org.ll.bugburgerbackend.domain.member.dto;

import lombok.NonNull;

public record SignInResponse(
        @NonNull String accessToken
        ) {
}
