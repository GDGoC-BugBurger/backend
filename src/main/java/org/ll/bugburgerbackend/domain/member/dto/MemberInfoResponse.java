package org.ll.bugburgerbackend.domain.member.dto;

import org.ll.bugburgerbackend.domain.member.entity.Member;

public record MemberInfoResponse(
        String username
) {

    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(
                member.getUsername()
        );
    }
}

