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
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:5173", 
            "https://bugburger.whqtker.site",
            "https://www.bugburger.whqtker.site",
            "https://api.bugburger.whqtker.site"  // API 도메인 추가
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));  // OPTIONS 명시적으로 추가
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));  // 필요한 헤더들 명시적으로 추가
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "refreshToken", "accessToken"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);  // preflight 요청 캐시 시간 설정 (1시간)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorizeRequests ->
                authorizeRequests
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()  // OPTIONS 요청 명시적 허용
                    .requestMatchers("/api/members/sign-in", "/api/members/sign-up", "/api/members/login", "/api/members/register").permitAll()
                    .requestMatchers("/actuator/health", "/health").permitAll()
                    .anyRequest().authenticated()
            )
            .headers(
                headers ->
                    headers.frameOptions(
                        HeadersConfigurer.FrameOptionsConfig::sameOrigin
                    )
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement((sessionManagement) -> sessionManagement
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}