package org.ll.bugburgerbackend.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ll.bugburgerbackend.domain.member.dto.MemberUpdateRequest;
import org.ll.bugburgerbackend.domain.member.dto.MemberUpdateResponse;
import org.ll.bugburgerbackend.domain.member.dto.SignInRequest;
import org.ll.bugburgerbackend.domain.member.dto.SignInResponse;
import org.ll.bugburgerbackend.domain.member.dto.SignUpRequest;
import org.ll.bugburgerbackend.domain.member.dto.MemberInfoResponse;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.service.MemberService;
import org.ll.bugburgerbackend.global.webMvc.LoginUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/")
    public String member() {
        return "member";
    }

    @GetMapping("/my")
    public ResponseEntity<MemberInfoResponse> getMyInfo(@LoginUser Member loginMember) {

        if (loginMember == null) {
            return ResponseEntity.status(401).build();
        }

        MemberInfoResponse memberInfoResponse = memberService.getMyInfo(loginMember);

        return ResponseEntity.ok(memberInfoResponse);
    }

    @PatchMapping("/my")
    public ResponseEntity<MemberUpdateResponse> updateMyInfo(@LoginUser Member loginMember,
                                                             @Valid @RequestBody MemberUpdateRequest memberUpdateRequest) {
        if (loginMember == null) {
            return ResponseEntity.status(401).build();
        }

        MemberUpdateResponse memberUpdateResponse = memberService.updateMyInfo(loginMember, memberUpdateRequest);

        return ResponseEntity.ok(memberUpdateResponse);
    }

    @GetMapping("/token/refresh")
    public ResponseEntity<?> refreshToken(@RequestParam String token) {
        Member member = memberService.findByToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));

        String newAccessToken = memberService.genAccessToken(member);

        // 응답 헤더와 쿠키에 새 토큰 추가
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token + " " + newAccessToken);

        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", newAccessToken)
            .path("/")
            .maxAge(3600)
            .httpOnly(true)
            .secure(true)
            .build();

        return ResponseEntity.ok()
            .headers(headers)
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
            .body(Map.of("accessToken", newAccessToken));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<SignInResponse> signIn(@Valid @RequestBody SignInRequest signInRequest) {
        SignInResponse signInResponse = memberService.signIn(signInRequest);

        return ResponseEntity.ok(signInResponse);

    }

    @PostMapping("/sign-up")
    public ResponseEntity<SignInResponse> signUp(@Valid @RequestBody SignUpRequest signUpRequest) {
        SignInResponse signInResponse = memberService.signUp(signUpRequest);

        return ResponseEntity.ok(signInResponse);
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(@LoginUser Member loginMember) {
        if (loginMember == null) {
            return ResponseEntity.status(401).build();
        }

        memberService.signOut();

        return ResponseEntity.ok().build();
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }
}
