package org.ll.bugburgerbackend.domain.member.dto;

import org.ll.bugburgerbackend.domain.member.entity.Member;

public record MemberInfoResponse(
    String username,
    String nickname,
    String birth,
    String gender,
    String dementiaStage,
    String interests,
    String background,
    String family,
    String caregiverName,
    String caregiverPhone,
    String patientPhone,
    String caregiverEmail
) {
    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(
            member.getUsername(),
            member.getNickname(),
            member.getBirth(),
            member.getGender().toString(),
            member.getDementiaStage().toString(),
            member.getInterests(),
            member.getBackground(),
            member.getFamily(),
            member.getCaregiverName(),
            member.getCaregiverPhone(),
            member.getPatientPhone(),
            member.getCaregiverEmail()
        );
    }
}

