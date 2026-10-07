package com.hjh.assignhub.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FormFieldException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User student;
    private User instructor;

    @BeforeEach
    void setUp() {
        student = userRepository.save(User.createStudent("s1@test.com", passwordEncoder.encode("password123"), "학생1", "20260001"));
        instructor = userRepository.save(User.createInstructor("t9@test.com", passwordEncoder.encode("password123"), "강사9"));
        userRepository.save(User.createStudent("taken@test.com", "pw", "학생2", "20260002"));
    }

    @Test
    @DisplayName("학생은 이름·이메일·학번을 수정할 수 있다 (이메일은 소문자로 저장)")
    void updateProfile_student() {
        userService.updateProfile(student.getId(), profile("새이름", "New@Test.com", "20269999"));

        User updated = userRepository.findById(student.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("새이름");
        assertThat(updated.getEmail()).isEqualTo("new@test.com");
        assertThat(updated.getStudentNo()).isEqualTo("20269999");
    }

    @Test
    @DisplayName("다른 사용자가 쓰는 이메일·학번으로는 바꿀 수 없다")
    void updateProfile_duplicates() {
        assertThatThrownBy(() -> userService.updateProfile(student.getId(), profile("학생1", "TAKEN@test.com", "20260001")))
                .isInstanceOf(FormFieldException.class).extracting("field").isEqualTo("email");
        assertThatThrownBy(() -> userService.updateProfile(student.getId(), profile("학생1", "s1@test.com", "20260002")))
                .isInstanceOf(FormFieldException.class).extracting("field").isEqualTo("studentNo");
    }

    @Test
    @DisplayName("강사는 학번을 보내도 학번이 생기지 않는다")
    void updateProfile_instructorHasNoStudentNo() {
        userService.updateProfile(instructor.getId(), profile("stadmin", "t9@test.com", "12345678"));

        assertThat(userRepository.findById(instructor.getId()).orElseThrow().getStudentNo()).isNull();
    }

    @Test
    @DisplayName("비밀번호 변경: 현재 비밀번호가 틀리거나, 확인이 다르거나, 기존과 같으면 거부된다")
    void changePassword_validation() {
        assertThatThrownBy(() -> userService.changePassword(student.getId(), password("wrong-pw", "newpass123", "newpass123")))
                .isInstanceOf(FormFieldException.class).extracting("field").isEqualTo("currentPassword");
        assertThatThrownBy(() -> userService.changePassword(student.getId(), password("password123", "newpass123", "different1")))
                .isInstanceOf(FormFieldException.class).extracting("field").isEqualTo("newPasswordConfirm");
        assertThatThrownBy(() -> userService.changePassword(student.getId(), password("password123", "password123", "password123")))
                .isInstanceOf(FormFieldException.class).extracting("field").isEqualTo("newPassword");
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호로만 확인된다 (BCrypt 저장)")
    void changePassword_success() {
        userService.changePassword(student.getId(), password("password123", "newpass123", "newpass123"));

        String saved = userRepository.findById(student.getId()).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("newpass123", saved)).isTrue();
        assertThat(passwordEncoder.matches("password123", saved)).isFalse();
    }

    @Test
    @DisplayName("프로필을 바꾸면 다시 로그인하지 않아도 다음 화면 상단바에 새 이름이 보인다")
    void updateProfile_refreshesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile").with(user(new LoginUser(student))).with(csrf()).session(session)
                        .param("name", "바뀐이름")
                        .param("email", "s1@test.com")
                        .param("studentNo", "20260001"))
                .andExpect(redirectedUrl("/profile"));

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("바뀐이름")));
    }

    @Test
    @DisplayName("프로필 수정 요청에 role=INSTRUCTOR를 끼워 넣어도 역할은 바뀌지 않는다")
    void updateProfile_cannotChangeRole() throws Exception {
        mockMvc.perform(post("/profile").with(user(new LoginUser(student))).with(csrf())
                        .param("name", "학생1")
                        .param("email", "s1@test.com")
                        .param("studentNo", "20260001")
                        .param("role", "INSTRUCTOR"))
                .andExpect(redirectedUrl("/profile"));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 비밀번호 변경 화면에 에러를 표시한다")
    void changePassword_wrongCurrentShowsError() throws Exception {
        mockMvc.perform(post("/profile/password").with(user(new LoginUser(student))).with(csrf())
                        .param("currentPassword", "wrong-pw")
                        .param("newPassword", "newpass123")
                        .param("newPasswordConfirm", "newpass123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordChangeForm", "currentPassword"))
                .andExpect(content().string(Matchers.containsString("현재 비밀번호가 올바르지 않습니다.")));
    }

    private ProfileForm profile(String name, String email, String studentNo) {
        ProfileForm form = new ProfileForm();
        form.setName(name);
        form.setEmail(email);
        form.setStudentNo(studentNo);
        return form;
    }

    private PasswordChangeForm password(String current, String next, String confirm) {
        PasswordChangeForm form = new PasswordChangeForm();
        form.setCurrentPassword(current);
        form.setNewPassword(next);
        form.setNewPasswordConfirm(confirm);
        return form;
    }
}
