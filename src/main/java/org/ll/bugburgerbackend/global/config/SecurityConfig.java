package org.ll.bugburgerbackend.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ll.bugburgerbackend.global.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List; // List 임포트 추가

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. 허용 Origin 패턴 명확히 지정 (와일드카드 패턴 제거)
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "https://bugburger.whqtker.site",
                "https://www.bugburger.whqtker.site",
                "http://localhost:3000",
                "http://localhost:5173"
        ));

        // 2. 허용 헤더 구체화 (* 대신 명시적 설정)
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));

        // 3. 노출 헤더 추가
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "refreshToken",
                "accessToken"
        ));

        // 4. 크레덴셜 허용 (중요!)
        configuration.setAllowCredentials(true);

        // 5. Preflight 캐시 시간 설정
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


    @Bean
    public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // OPTIONS 허용
                        .requestMatchers("/api/v1/members/**").permitAll() // 회원 관련 API 허용
                        .anyRequest().authenticated()
            )
            .headers(
                headers ->
                    headers.frameOptions(
                        HeadersConfigurer.FrameOptionsConfig::sameOrigin
                    )
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}