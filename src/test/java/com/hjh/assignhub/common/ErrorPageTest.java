package com.hjh.assignhub.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

import jakarta.servlet.RequestDispatcher;

// MockMvc는 에러 dispatch를 하지 않으므로, 서블릿 컨테이너가 /error로 넘길 때 붙이는 속성을 직접 넣어 확인
// (브라우저처럼 Accept: text/html을 보내야 JSON이 아닌 HTML 에러 화면이 나온다)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private LoginUser student;

    @BeforeEach
    void setUp() {
        student = new LoginUser(userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001")));
    }

    @Test
    @DisplayName("500 오류는 Tomcat 기본 화면 대신 공통 레이아웃의 안내 화면을 보여준다")
    void serverError_customPage() throws Exception {
        mockMvc.perform(get("/error").with(user(student)).accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret-internal-detail")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(Matchers.containsString("일시적인 오류가 발생했습니다")))
                .andExpect(content().string(Matchers.containsString("accordionSidebar")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret-internal-detail"))));
    }

    @Test
    @DisplayName("400·405 같은 4xx 오류는 공통 안내 화면을 보여준다")
    void clientError_customPage() throws Exception {
        mockMvc.perform(get("/error").with(user(student)).accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 405))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string(Matchers.containsString("요청을 처리할 수 없습니다")))
                .andExpect(content().string(Matchers.containsString("405")));
    }
}
