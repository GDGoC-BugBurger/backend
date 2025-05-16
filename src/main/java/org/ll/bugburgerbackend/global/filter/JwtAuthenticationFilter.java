package org.ll.bugburgerbackend.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.service.MemberService;
import org.ll.bugburgerbackend.global.Ut.Ut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${custom.jwt.secretKey}")
    private String jwtSecretKey;

    private final MemberService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.info("[JwtAuthFilter] START for URI: {}", request.getRequestURI());
        String token = extractToken(request);
        log.debug("[JwtAuthFilter] Request URI: {}", request.getRequestURI());
        log.debug("[JwtAuthFilter] Extracted token: {}", token);

        boolean authenticationProblem = false;
        
        if (token != null) {
            log.debug("[JwtAuthFilter] Token found, attempting validation and authentication.");
            try {
                if (!Ut.jwt.isValid(jwtSecretKey, token)) {
                    log.error("[JwtAuthFilter] Invalid JWT token for URI: {}", request.getRequestURI());
                    authenticationProblem = true;
                } else {
                    log.debug("[JwtAuthFilter] JWT token is valid.");
                    Map<String, Object> payload = Ut.jwt.payload(jwtSecretKey, token);
                    
                    if (payload != null) {
                        log.debug("[JwtAuthFilter] JWT payload successfully parsed: {}", payload);
                        Long id = ((Number) payload.get("id")).longValue();
                        String username = (String) payload.get("username");
                        log.debug("[JwtAuthFilter] User ID from token: {}, Username from token: {}", id, username);
                        
                        Member member = memberService.findById(id)
                                .orElseThrow(() -> {
                                    log.error("[JwtAuthFilter] Member not found for id: {} from token.", id);
                                    return new RuntimeException("Member not found");
                                });
                        log.debug("[JwtAuthFilter] Member found from DB: {}", member.getUsername());

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                member,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.info("[JwtAuthFilter] Successfully set Authentication in SecurityContext for user: {}", member.getUsername());
                    } else {
                        log.error("[JwtAuthFilter] Failed to parse JWT payload for token: {}", token);
                        authenticationProblem = true;
                    }
                }
            } catch (Exception e) {
                log.error("[JwtAuthFilter] JWT token validation failed for URI: {}", request.getRequestURI(), e);
                authenticationProblem = true;
            }
        } else {
            log.debug("[JwtAuthFilter] No JWT token found in request to URI: {}", request.getRequestURI());
        }

        if (authenticationProblem) {
            log.warn("[JwtAuthFilter] Authentication problem detected. Setting response status to 401 UNAUTHORIZED for URI: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // 중요: 인증 문제가 있을 경우, 필터 체인을 여기서 중단하고 싶다면 return; 을 사용할 수 있으나,
            // 현재 코드는 CORS 헤더 등을 적용하기 위해 항상 filterChain.doFilter를 호출합니다.
            // 403 Forbidden은 인증은 되었으나 권한이 없는 경우이므로, 이 로직 이후에 문제가 발생할 가능성이 높습니다.
        }
        
        log.debug("[JwtAuthFilter] Proceeding with filter chain. Current SecurityContext Authentication: {}", SecurityContextHolder.getContext().getAuthentication());
        filterChain.doFilter(request, response);
        log.info("[JwtAuthFilter] END for URI: {}", request.getRequestURI());
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        log.debug("[JwtAuthFilter] Authorization header: {}", bearerToken);
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            log.debug("[JwtAuthFilter] Extracted token from Bearer: {}", token);
            return token;
        }
        log.debug("[JwtAuthFilter] No Bearer token found in Authorization header.");
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.trace("[JwtAuthFilter] Skipping filter for OPTIONS request to {}", path);
            return true;
        }

        boolean shouldNotFilter = path.startsWith("/api/v1/members/sign") || path.equals("/error");
        if (shouldNotFilter) {
            log.trace("[JwtAuthFilter] Skipping filter for path: {}", path);
        }
        return shouldNotFilter;
    }

}