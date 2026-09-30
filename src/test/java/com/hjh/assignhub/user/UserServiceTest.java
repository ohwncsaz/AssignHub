package com.hjh.assignhub.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("U1 회원가입하면 STUDENT 역할로 저장되고 비밀번호는 BCrypt로 암호화된다")
    void signup_savesStudentWithEncodedPassword() {
        Long id = userService.signup(form("20260001", "Student@Test.com", "password123", "password123"));

        User saved = userRepository.findById(id).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getEmail()).isEqualTo("student@test.com");
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("U1 비밀번호 확인이 다르면 가입할 수 없다")
    void signup_passwordMismatch() {
        assertThatThrownBy(() -> userService.signup(form("20260001", "a@test.com", "password123", "different123")))
                .isInstanceOf(SignupException.class)
                .extracting("field").isEqualTo("passwordConfirm");
    }

    @Test
    @DisplayName("U1 이미 가입된 이메일(대소문자 무시)로는 가입할 수 없다")
    void signup_duplicateEmail() {
        userService.signup(form("20260001", "dup@test.com", "password123", "password123"));

        assertThatThrownBy(() -> userService.signup(form("20260002", "DUP@test.com", "password123", "password123")))
                .isInstanceOf(SignupException.class)
                .extracting("field").isEqualTo("email");
    }

    @Test
    @DisplayName("U1 이미 가입된 학번으로는 가입할 수 없다")
    void signup_duplicateStudentNo() {
        userService.signup(form("20260001", "first@test.com", "password123", "password123"));

        assertThatThrownBy(() -> userService.signup(form("20260001", "second@test.com", "password123", "password123")))
                .isInstanceOf(SignupException.class)
                .extracting("field").isEqualTo("studentNo");
    }

    private SignupForm form(String studentNo, String email, String password, String passwordConfirm) {
        SignupForm form = new SignupForm();
        form.setStudentNo(studentNo);
        form.setName("홍길동");
        form.setEmail(email);
        form.setPassword(password);
        form.setPasswordConfirm(passwordConfirm);
        return form;
    }
}
