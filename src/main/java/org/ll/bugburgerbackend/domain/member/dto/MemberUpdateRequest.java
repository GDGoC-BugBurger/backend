package org.ll.bugburgerbackend.domain.member.dto;

import jakarta.validation.constraints.NotNull;

public record MemberUpdateRequest(
        @NotNull Long id,
        String nickname
) {
    public boolean hasNickname() {
        return nickname != null && !nickname.isBlank();
    }
}
