package com.hjh.assignhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.hjh.assignhub.auth.SessionExpiredAwareAccessDeniedHandler;

@Configuration
public class SecurityConfig {

    // remember-me 쿠키 서명 키 — 운영에서는 환경변수로 바꾼다
    @Value("${app.remember-me-key}")
    private String rememberMeKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 기본 정적 경로(/css, /js, /images 등)에 /img가 없어 로그인 화면 그림용으로 따로 허용
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/img/**").permitAll()
                        .requestMatchers("/login", "/signup", "/error", "/error/**").permitAll()
                        .requestMatchers("/instructor/**").hasRole("INSTRUCTOR")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .anyRequest().authenticated())
                // U2 세션 기반 폼 로그인 — 아이디 대신 이메일로 로그인
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                // 로그인 화면의 "로그인 상태 유지" — 체크하면 브라우저를 닫아도 14일간 자동 로그인 (로그아웃하면 쿠키 삭제)
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(14 * 24 * 60 * 60)
                        .userDetailsService(userDetailsService))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout"))
                // 역할이 맞지 않는 URL 접근 → 403 페이지 (세션 만료로 인한 CSRF 오류는 로그인 화면으로)
                .exceptionHandling(ex -> ex.accessDeniedHandler(new SessionExpiredAwareAccessDeniedHandler("/error/403")));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
