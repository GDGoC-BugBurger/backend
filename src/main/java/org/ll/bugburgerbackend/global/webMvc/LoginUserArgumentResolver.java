package org.ll.bugburgerbackend.global.webMvc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.service.MemberService;
import org.ll.bugburgerbackend.global.Ut.Ut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final MemberService memberService;

    @Value("${custom.jwt.secretKey}")
    private String jwtSecretKey;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean supports = parameter.hasParameterAnnotation(LoginUser.class) &&
                parameter.getParameterType().equals(Member.class);
        log.trace("[LoginUserArgResolver] supportsParameter for {}.{}: {}",
                parameter.getContainingClass().getSimpleName(),
                parameter.getMethod().getName(),
                supports);
        return supports;
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory
    ) {
        log.debug("[LoginUserArgResolver] Attempting to resolve @LoginUser for {}.{}",
                parameter.getContainingClass().getSimpleName(),
                parameter.getMethod().getName());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            log.warn("[LoginUserArgResolver] No authentication found in SecurityContext.");
            return null;
        }
        
        log.debug("[LoginUserArgResolver] Authentication object found: {}", authentication);
        if (!authentication.isAuthenticated()) {
            log.warn("[LoginUserArgResolver] Authentication object is not authenticated: {}", authentication.getName());
            return null;
        }

        Object principal = authentication.getPrincipal();
        log.debug("[LoginUserArgResolver] Principal object: {}, Type: {}", principal, principal != null ? principal.getClass().getName() : "null");

        if (principal instanceof Member) {
            Member member = (Member) principal;
            log.info("[LoginUserArgResolver] Principal is an instance of Member. Resolved Member: {}", member.getUsername());
            return member;
        }

        log.warn("[LoginUserArgResolver] Principal is not an instance of Member. Principal: {}", principal);
        return null;
    }
}