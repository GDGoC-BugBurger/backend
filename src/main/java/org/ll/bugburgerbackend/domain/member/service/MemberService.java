package org.ll.bugburgerbackend.domain.member.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Slf4j 임포트 추가
import org.hibernate.service.spi.ServiceException;
import org.ll.bugburgerbackend.domain.member.dto.MemberInfoResponse;
import org.ll.bugburgerbackend.domain.member.dto.MemberUpdateRequest;
import org.ll.bugburgerbackend.domain.member.dto.MemberUpdateResponse;
import org.ll.bugburgerbackend.domain.member.dto.SignInRequest;
import org.ll.bugburgerbackend.domain.member.dto.SignInResponse;
import org.ll.bugburgerbackend.domain.member.dto.SignUpRequest;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.repository.MemberRepository;
// import org.ll.bugburgerbackend.global.error.ErrorCode; // ErrorCode 사용 시 주석 해제
import org.ll.bugburgerbackend.global.rq.Rq;
import org.ll.bugburgerbackend.global.type.DementiaStage;
import org.ll.bugburgerbackend.global.type.GenderType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets; // StandardCharsets 임포트 추가
import java.util.Arrays; // Arrays 임포트 추가
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j // Slf4j 어노테이션 추가
public class MemberService {

     private final MemberRepository memberRepository;
     private final AuthTokenService authTokenService;
     private final PasswordEncoder passwordEncoder;
    private final Rq rq;

     public MemberInfoResponse getMyInfo(Member loginUser) {
         return MemberInfoResponse.from(loginUser);
     }

     public Optional<Member> findById(Long memberId) {
         return memberRepository.findById(memberId);
     }

    public String genAccessToken(Member member) {
        return authTokenService.genAccessToken(member);
    }

    public Optional<Member> findByToken(String token) {
        return memberRepository.findByToken(token);
    }

    @Transactional
    public SignInResponse signIn(SignInRequest signInRequest) {
        String rawPasswordFromRequest = signInRequest.password();
        String username = signInRequest.username();

        log.info("========== PASSWORD MATCHING DEBUG START for user: {} ==========", username);
        log.info("1. Raw Password from Request: '{}'", rawPasswordFromRequest);
        if (rawPasswordFromRequest != null) {
            log.info("2. Length of Raw Password from Request: {}", rawPasswordFromRequest.length());
            log.info("3. Raw Password Bytes (UTF-8) from Request: {}", Arrays.toString(rawPasswordFromRequest.getBytes(StandardCharsets.UTF_8)));
            StringBuilder rawPasswordChars = new StringBuilder();
            for (char c : rawPasswordFromRequest.toCharArray()) {
                rawPasswordChars.append(String.format("'%c'(%d) ", c, (int) c));
            }
            log.info("4. Raw Password Characters (char, int_value): [{}]", rawPasswordChars.toString().trim());
        } else {
            log.warn("2. Raw Password from Request is NULL for user: {}", username);
        }
        log.info("--------------------------------------------------------------------");

        Member member = memberRepository.findByUsername(signInRequest.username())
                .orElseThrow(() -> {
                    log.warn("[SignIn] User not found: {}", signInRequest.username());
                    return new EntityNotFoundException("해당 유저는 존재하지 않습니다.");
                });

        String hashedPasswordFromDB = member.getPassword();
        log.info("5. Hashed Password from DB for user {}: '{}'", username, hashedPasswordFromDB);
        if (hashedPasswordFromDB != null) {
            log.info("6. Length of Hashed Password from DB: {}", hashedPasswordFromDB.length());
            log.info("7. Hashed Password Bytes (UTF-8) from DB: {}", Arrays.toString(hashedPasswordFromDB.getBytes(StandardCharsets.UTF_8)));
        } else {
            log.warn("6. Hashed Password from DB is NULL for user: {}", username);
        }
        log.info("--------------------------------------------------------------------");

        boolean passwordMatchesResult = false;
        if (rawPasswordFromRequest != null && hashedPasswordFromDB != null) {
            passwordMatchesResult = passwordEncoder.matches(rawPasswordFromRequest, hashedPasswordFromDB);
        } else {
            log.error("Cannot perform password match for user {} because raw password or hashed password from DB is null. Raw: {}, DB: {}",
                    username, rawPasswordFromRequest == null ? "NULL" : "NOT_NULL", hashedPasswordFromDB == null ? "NULL" : "NOT_NULL");
        }
        log.info("8. Result of passwordEncoder.matches(requestPassword, dbHash) for user {}: {}", username, passwordMatchesResult);
        log.info("========== PASSWORD MATCHING DEBUG END for user: {} ==========", username);

        if (!passwordMatchesResult) { // Line 56 (원래 예외 발생 지점)
            log.error("Password mismatch for user: {}. Raw password was '{}' (length {}), DB hash was '{}' (length {})",
                    username,
                    rawPasswordFromRequest,
                    rawPasswordFromRequest != null ? rawPasswordFromRequest.length() : "N/A",
                    hashedPasswordFromDB,
                    hashedPasswordFromDB != null ? hashedPasswordFromDB.length() : "N/A");
            // 더 나은 예외 처리를 위해 ErrorCode.PASSWORD_MISMATCH 를 사용하는 커스텀 예외를 고려하세요.
            // 예: throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
            throw new ServiceException("비밀번호가 일치하지 않습니다.");
        }

        String token = rq.makeAuthCookies(member);

        return new SignInResponse(token);
    }

    @Transactional
    public SignInResponse signUp(SignUpRequest signUpRequest) {
        String nickname = signUpRequest.nickname();
        String username = signUpRequest.username();
        String rawPassword = signUpRequest.password(); // 회원가입 시 평문 비밀번호

        log.info("[SignUp] Attempting to sign up user: {}, nickname: {}", username, nickname);
        log.info("[SignUp] Plain password for new user {}: '{}' (length: {})", username, rawPassword, rawPassword != null ? rawPassword.length() : "N/A");
        if (rawPassword != null) {
            StringBuilder rawPasswordChars = new StringBuilder();
            for (char c : rawPassword.toCharArray()) {
                rawPasswordChars.append(String.format("'%c'(%d) ", c, (int) c));
            }
            log.info("[SignUp] Plain password characters for user {} (char, int_value): [{}]", username, rawPasswordChars.toString().trim());
        }


        memberRepository
                .findByNickname(nickname)
                .ifPresent(user -> {
                    log.warn("[SignUp] Nickname {} already in use.", nickname);
                    throw new ServiceException("해당 nickname은 이미 사용중입니다.");
                });
        
        String encodedPassword = passwordEncoder.encode(rawPassword);
        log.info("[SignUp] Encoded password for user {}: '{}' (length: {})", username, encodedPassword, encodedPassword.length());

        Member member = memberRepository.save(Member.builder()
                .username(username)
                .password(encodedPassword) // 암호화된 비밀번호 사용
                .nickname(nickname)
                .birth(signUpRequest.birth())
                .gender(GenderType.valueOf(signUpRequest.gender()))
                .dementiaStage(DementiaStage.valueOf(signUpRequest.dementiaStage()))
                .interests(signUpRequest.interests())
                .background(signUpRequest.background())
                .family(signUpRequest.family())
                .caregiverName(signUpRequest.caregiverName())
                .caregiverPhone(signUpRequest.caregiverPhone())
                .patientPhone(signUpRequest.patientPhone())
                .caregiverEmail(signUpRequest.caregiverEmail())
                .token(UUID.randomUUID().toString())
                .build());
        log.info("[SignUp] User {} signed up successfully with ID: {}", username, member.getId());

        // 회원가입 후 로그인
        // 주의: signUpRequest.password()는 평문이므로 그대로 전달
        return signIn(new SignInRequest(username, rawPassword));
     }

    public void signOut() {
         authTokenService.deleteCookies();
    }

    public MemberUpdateResponse updateMyInfo(Member loginMember, MemberUpdateRequest memberUpdateRequest) {
        Member member = memberRepository.findById(memberUpdateRequest.id()).orElseThrow(()
                -> new EntityNotFoundException("해당 유저는 존재하지 않습니다."));

        if (!loginMember.getUsername().equals(member.getUsername())) {
            throw new ServiceException("해당 유저는 존재하지 않습니다.");
        }

        if (memberUpdateRequest.hasNickname()) {
            member.setNickname(memberUpdateRequest.nickname());
        }
        
        if (memberUpdateRequest.hasCaregiverName()) {
            member.setCaregiverName(memberUpdateRequest.caregiverName());
        }
        
        if (memberUpdateRequest.hasCaregiverPhone()) {
            member.setCaregiverPhone(memberUpdateRequest.caregiverPhone());
        }
        
        if (memberUpdateRequest.hasPatientPhone()) {
            member.setPatientPhone(memberUpdateRequest.patientPhone());
        }
        
        if (memberUpdateRequest.hasCaregiverEmail()) {
            member.setCaregiverEmail(memberUpdateRequest.caregiverEmail());
        }

        memberRepository.save(member);

        return new MemberUpdateResponse(member.getId());
    }
}
