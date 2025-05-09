package org.ll.bugburgerbackend.global.webMvc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.service.MemberService;
import org.springframework.core.MethodParameter;
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

        // 모든 헤더 출력
        webRequest.getHeaderNames().forEachRemaining(headerName -> {
            log.debug("Header: {} = {}", headerName, webRequest.getHeader(headerName));
        });

        String userIdStr = webRequest.getHeader("X-User-Id");

        if (userIdStr == null) {
            log.debug("X-User-Id header not found");
            return null;
        }

        try {
            Long userId = Long.parseLong(userIdStr);
            log.debug("Found userId in header: {}", userId);

            Member loginUser = memberService.findById(userId).get();

            return loginUser;
        } catch (NumberFormatException e) {
            log.error("Invalid userId format: {}", userIdStr, e);
            return null;
        }
    }
}