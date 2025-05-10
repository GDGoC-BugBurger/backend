package org.ll.bugburgerbackend.domain.member.dto;

import jakarta.validation.constraints.NotNull;

public record MemberUpdateResponse(
        @NotNull Long id
) {
}
