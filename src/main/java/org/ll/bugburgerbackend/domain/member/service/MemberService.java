package org.ll.bugburgerbackend.domain.member.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.service.spi.ServiceException;
import org.ll.bugburgerbackend.domain.member.dto.MemberInfoResponse;
import org.ll.bugburgerbackend.domain.member.dto.MemberUpdateRequest;
import org.ll.bugburgerbackend.domain.member.dto.MemberUpdateResponse;
import org.ll.bugburgerbackend.domain.member.dto.SignInRequest;
import org.ll.bugburgerbackend.domain.member.dto.SignInResponse;
import org.ll.bugburgerbackend.domain.member.dto.SignUpRequest;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.repository.MemberRepository;
import org.ll.bugburgerbackend.global.error.ErrorCode;
import org.ll.bugburgerbackend.global.rq.Rq;
import org.ll.bugburgerbackend.global.type.DementiaStage;
import org.ll.bugburgerbackend.global.type.GenderType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
        Member member = memberRepository.findByUsername(signInRequest.username())
                .orElseThrow(() -> new EntityNotFoundException("해당 유저는 존재하지 않습니다."));

        if (!passwordEncoder.matches(signInRequest.password(), member.getPassword()))
            throw new ServiceException("비밀번호가 일치하지 않습니다.");

        String token = rq.makeAuthCookies(member);

        return new SignInResponse(token);
    }

    @Transactional
    public SignInResponse signUp(SignUpRequest signUpRequest) {
        String nickname = signUpRequest.nickname();
        String username = signUpRequest.username();

        memberRepository
                .findByNickname(nickname)
                .ifPresent(user -> {
                    throw new ServiceException("해당 nickname은 이미 사용중입니다.");
                });

        Member member = memberRepository.save(Member.builder()
                .username(username)
                .password(passwordEncoder.encode(signUpRequest.password()))
                .nickname(nickname)
                .birth(signUpRequest.birth())
                .gender(GenderType.valueOf(signUpRequest.gender()))
                .dementiaStage(DementiaStage.valueOf(signUpRequest.dementiaStage()))
                .interests(signUpRequest.interests())
                .background(signUpRequest.background())
                .family(signUpRequest.family())
                .token(UUID.randomUUID().toString())
                .build());

        // 회원가입 후 로그인
        return signIn(new SignInRequest(username, signUpRequest.password()));
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

        memberRepository.save(member);

        return new MemberUpdateResponse(member.getId());
    }
}
