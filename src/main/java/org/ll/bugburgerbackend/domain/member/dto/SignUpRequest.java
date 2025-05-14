package org.ll.bugburgerbackend.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import org.ll.bugburgerbackend.global.type.DementiaStage;
import org.ll.bugburgerbackend.global.type.GenderType;

public record SignUpRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String nickname,
        @NotBlank String birth,
        @NotBlank String gender,
        @NotBlank String dementiaStage,
        String interests,
        String background,
        String family,
        String caregiverName,
        String caregiverPhone,
        String patientPhone,
        String caregiverEmail
) {
}
