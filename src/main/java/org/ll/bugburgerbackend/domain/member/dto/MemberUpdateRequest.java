package org.ll.bugburgerbackend.domain.member.dto;

import jakarta.validation.constraints.NotNull;

public record MemberUpdateRequest(
        @NotNull Long id,
        String nickname,
        String caregiverName,
        String caregiverPhone,
        String patientPhone,
        String caregiverEmail
) {
    public boolean hasNickname() {
        return nickname != null && !nickname.isBlank();
    }
    
    public boolean hasCaregiverName() {
        return caregiverName != null && !caregiverName.isBlank();
    }
    
    public boolean hasCaregiverPhone() {
        return caregiverPhone != null && !caregiverPhone.isBlank();
    }
    
    public boolean hasPatientPhone() {
        return patientPhone != null && !patientPhone.isBlank();
    }
    
    public boolean hasCaregiverEmail() {
        return caregiverEmail != null && !caregiverEmail.isBlank();
    }
}
