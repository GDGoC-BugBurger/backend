package org.ll.bugburgerbackend.domain.member.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ll.bugburgerbackend.domain.member.dto.MemberInfoResponse;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.service.MemberService;
import org.ll.bugburgerbackend.global.webMvc.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/my")
    public ResponseEntity<MemberInfoResponse> getMyInfo(@LoginUser Member loginMember) {
        MemberInfoResponse memberInfoResponse = memberService.getMyInfo(loginMember);

        return ResponseEntity.ok(memberInfoResponse);
    }
}
