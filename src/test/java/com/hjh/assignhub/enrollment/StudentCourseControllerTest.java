package com.hjh.assignhub.enrollment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    private LoginUser student;
    private LoginUser instructor;

    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        instructor = new LoginUser(instructorUser);
        student = new LoginUser(userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001")));
        courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
    }

    @Test
    @DisplayName("S1 참여코드로 등록하면 성공 알림과 함께 수강 중인 강좌에 표시된다")
    void join_success() throws Exception {
        mockMvc.perform(post("/student/courses/join").with(user(student)).with(csrf())
                        .param("joinCode", "AB3K7XQ2"))
                .andExpect(redirectedUrl("/student/courses/join"))
                .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(get("/student/courses/join").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("자바 스프링")));
    }

    @Test
    @DisplayName("S1 잘못된 참여코드는 입력칸에 에러를 표시한다")
    void join_unknownCode() throws Exception {
        mockMvc.perform(post("/student/courses/join").with(user(student)).with(csrf())
                        .param("joinCode", "ZZZZZZZZ"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("joinForm", "joinCode"))
                .andExpect(content().string(Matchers.containsString("존재하지 않는 참여코드입니다.")));
    }

    @Test
    @DisplayName("S1 강사는 수강 등록 화면에 접근할 수 없다 (403)")
    void instructor_forbidden() throws Exception {
        mockMvc.perform(get("/student/courses/join").with(user(instructor)))
                .andExpect(status().isForbidden());
    }
}
