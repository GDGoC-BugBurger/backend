package org.ll.bugburgerbackend.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:3000"
    ));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
    }

  @Bean
  public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorizeRequests ->
            authorizeRequests
                .anyRequest().permitAll()
        )
        .headers(
            headers ->
                headers.frameOptions(
                    HeadersConfigurer.FrameOptionsConfig::sameOrigin
                )
        )
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(
            AbstractHttpConfigurer::disable
        )
        .sessionManagement((sessionManagement) -> sessionManagement
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

    return http.build();
  }
}