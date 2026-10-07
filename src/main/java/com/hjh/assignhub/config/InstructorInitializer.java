package com.hjh.assignhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hjh.assignhub.user.Role;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;
import com.hjh.assignhub.user.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// U4 강사 계정은 회원가입으로 만들 수 없고, 앱 시작 시 초기 데이터로만 생성
@Slf4j
@Component
@Order(1) // 데모 데이터(DemoDataInitializer)보다 먼저 강사 계정을 만든다
@RequiredArgsConstructor
public class InstructorInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.instructor.email}")
    private String email;

    @Value("${app.instructor.password}")
    private String password;

    @Value("${app.instructor.name}")
    private String name;

    // 강사가 프로필에서 이메일을 바꿔도 다시 만들지 않도록 "강사 계정이 하나라도 있는지"로 판단
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.INSTRUCTOR)) {
            return;
        }
        String normalizedEmail = UserService.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("초기 강사 이메일({})을 다른 계정이 사용 중이라 강사 계정을 만들지 않았습니다.", normalizedEmail);
            return;
        }
        userRepository.save(User.createInstructor(normalizedEmail, passwordEncoder.encode(password), name));
        log.info("초기 강사 계정 생성: {}", normalizedEmail);
    }
}
