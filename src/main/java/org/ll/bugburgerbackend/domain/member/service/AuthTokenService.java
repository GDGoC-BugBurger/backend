package org.ll.bugburgerbackend.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.global.Ut.Ut;
import org.ll.bugburgerbackend.global.rq.Rq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    @Value("${custom.jwt.secretKey}")
    private String jwtSecretKey;

    @Value("${custom.accessToken.expirationSeconds}")
    private long accessTokenExpirationSeconds;

    private final Rq rq;

    String genAccessToken(Member member) {
        long id = member.getId();
        String username = member.getUsername();

        return Ut.jwt.toString(
                jwtSecretKey,
                accessTokenExpirationSeconds,
                Map.of("id", id, "username", username)
        );
    }

    Map<String, Object> payload(String accessToken) {
        Map<String, Object> parsedPayload = Ut.jwt.payload(jwtSecretKey, accessToken);

        if (parsedPayload == null) return null;

        long id = (long) (Integer) parsedPayload.get("id");
        String username = (String) parsedPayload.get("username");

        return Map.of("id", id, "username", username);
    }

    void deleteCookies() {
        rq.deleteCookie("accessToken");
        rq.deleteCookie("token");
    }
}
