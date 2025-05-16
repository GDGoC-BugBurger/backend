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
        String token = extractToken(request);
        log.debug("Request URI: {}", request.getRequestURI());
        log.debug("Extracted token: {}", token);

        boolean authenticationProblem = false;
        
        if (token != null) {
            try {
                if (!Ut.jwt.isValid(jwtSecretKey, token)) {
                    log.error("Invalid JWT token");
                    authenticationProblem = true;
                } else {
                    Map<String, Object> payload = Ut.jwt.payload(jwtSecretKey, token);
                    
                    if (payload != null) {
                        Long id = ((Number) payload.get("id")).longValue();
                        String username = (String) payload.get("username");
                        
                        Member member = memberService.findById(id)
                                .orElseThrow(() -> {
                                    log.error("Member not found for id: {}", id);
                                    return new RuntimeException("Member not found");
                                });

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                member,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.error("Failed to parse JWT payload");
                        authenticationProblem = true;
                    }
                }
            } catch (Exception e) {
                log.error("JWT token validation failed", e);
                authenticationProblem = true;
            }
        }

        if (authenticationProblem) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        
        // Always continue the filter chain to ensure CORS headers are applied
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        log.debug("Authorization header: {}", bearerToken);
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            log.debug("Extracted token from Bearer: {}", token);
            return token;
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // OPTIONS 요청은 항상 건너뛰기
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }

        String path = request.getRequestURI();
        
        // API 도메인 엔드포인트도 허용
        if (path.startsWith("/api/members/sign-in") || 
            path.startsWith("/api/members/sign-up") || 
            path.startsWith("/api/members/sign-out") || 
            path.startsWith("/api/members/token/refresh")) {
            log.debug("Auth endpoint detected: {}, skipping JWT filter", path);
            return true;
        }
        
        // Handle health check endpoints
        if (path.equals("/health") || path.equals("/actuator/health") || path.startsWith("/actuator/")) {
            log.debug("Health check path detected: {}, skipping JWT filter", path);
            return true;
        }
        
        // Handle all open endpoints with or without /api prefix
        boolean shouldNotFilter = 
               path.startsWith("/api/members/sign-in") || path.startsWith("/members/sign-in") ||
               path.startsWith("/api/members/sign-up") || path.startsWith("/members/sign-up") ||
               path.startsWith("/api/members/login") || path.startsWith("/members/login") ||
               path.startsWith("/api/members/register") || path.startsWith("/members/register") ||
               path.equals("/") ||
               path.equals("/api/members/") || path.equals("/members/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.equals("/error");

        log.debug("Path: {}, shouldNotFilter: {}", path, shouldNotFilter);
        return shouldNotFilter;
    }
}