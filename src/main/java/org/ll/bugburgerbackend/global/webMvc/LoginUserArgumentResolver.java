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
        return parameter.hasParameterAnnotation(LoginUser.class) &&
                parameter.getParameterType().equals(Member.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authentication found in SecurityContext");
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Member) {
            log.debug("Found authenticated member: {}", ((Member) principal).getUsername());
            return principal;
        }

        log.debug("Principal is not a Member instance: {}", principal);
        return null;
    }
}