package com.hjh.assignhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.user.Role;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.instructor.email}")
    private String instructorEmail;

    @Value("${app.instructor.password}")
    private String instructorPassword;

    @Test
    @DisplayName("U2 로그인하지 않은 사용자는 로그인 페이지로 이동한다")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("U2 로그인 화면이 렌더링된다")
    void loginPage_renders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("AssignHub에 오신 것을 환영합니다!")));
    }

    @Test
    @DisplayName("U3 403 화면이 공통 레이아웃과 함께 렌더링된다")
    void forbiddenPage_renders() throws Exception {
        User instructor = userRepository.findByEmail(instructorEmail).orElseThrow();

        mockMvc.perform(get("/error/403").with(user(new LoginUser(instructor))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("접근 권한이 없습니다")))
                .andExpect(content().string(Matchers.containsString("accordionSidebar")));
    }

    @Test
    @DisplayName("U4 초기 강사 계정으로 로그인하면 INSTRUCTOR 권한을 가진다")
    void instructor_loginWithInitialAccount() throws Exception {
        mockMvc.perform(formLogin("/login").userParameter("email").user(instructorEmail).password(instructorPassword))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("INSTRUCTOR"));
    }

    @Test
    @DisplayName("U2 비밀번호가 틀리면 로그인에 실패한다")
    void login_wrongPassword() throws Exception {
        mockMvc.perform(formLogin("/login").userParameter("email").user(instructorEmail).password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("U2 로그아웃하면 로그인 페이지로 이동한다")
    void logout_redirectsToLogin() throws Exception {
        mockMvc.perform(logout("/logout"))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("U1 회원가입 요청에 role=INSTRUCTOR를 끼워 넣어도 STUDENT로 가입된다")
    void signup_ignoresInjectedRole() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("studentNo", "20260001")
                        .param("name", "홍길동")
                        .param("email", "hong@test.com")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("role", "INSTRUCTOR"))
                .andExpect(redirectedUrl("/login"));

        User saved = userRepository.findByEmail("hong@test.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("U1 입력값이 잘못되면 가입 화면에 에러를 표시한다")
    void signup_invalidInput() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("studentNo", "abc")
                        .param("name", "")
                        .param("email", "not-an-email")
                        .param("password", "short")
                        .param("passwordConfirm", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupForm", "studentNo", "name", "email", "password"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("U3 학생이 강사 URL에 접근하면 403 페이지를 본다")
    void student_forbiddenFromInstructorUrl() throws Exception {
        mockMvc.perform(get("/instructor/courses"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));
    }

    @Test
    @DisplayName("U3 강사로 로그인하면 사이드바에 강사 메뉴만 보인다")
    void instructor_seesInstructorMenuOnly() throws Exception {
        User instructor = userRepository.findByEmail(instructorEmail).orElseThrow();

        mockMvc.perform(get("/").with(user(new LoginUser(instructor))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("강좌 관리")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("수강 등록"))));
    }
}
