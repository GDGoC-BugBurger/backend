package org.ll.bugburgerbackend.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.ll.bugburgerbackend.domain.auth.service.AuthTokenService;
import org.ll.bugburgerbackend.domain.member.dto.MemberInfoResponse;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

     private final MemberRepository memberRepository;
     private final AuthTokenService authTokenService;
     private final PasswordEncoder passwordEncoder;

     public MemberInfoResponse getMyInfo(Member loginUser) {
         return MemberInfoResponse.from(loginUser);
     }

     public Optional<Member> findById(Long memberId) {
         return memberRepository.findById(memberId);
     }
}
