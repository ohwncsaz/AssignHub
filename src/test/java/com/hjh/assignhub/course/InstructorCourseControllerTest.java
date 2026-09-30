package com.hjh.assignhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstructorCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseService courseService;

    private LoginUser instructor;
    private LoginUser otherInstructor;

    @BeforeEach
    void setUp() {
        instructor = new LoginUser(userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1")));
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
    }

    @Test
    @DisplayName("I1 강좌를 개설하면 상세 화면으로 이동하고 참여코드가 보인다")
    void create_redirectsToDetail() throws Exception {
        mockMvc.perform(post("/instructor/courses").with(user(instructor)).with(csrf())
                        .param("name", "자바 스프링"))
                .andExpect(redirectedUrlPattern("/instructor/courses/*"));

        Course course = courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId()).get(0);
        mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(course.getJoinCode())));
    }

    @Test
    @DisplayName("I1 강좌명이 비어 있으면 폼 에러를 표시한다")
    void create_blankName() throws Exception {
        mockMvc.perform(post("/instructor/courses").with(user(instructor)).with(csrf())
                        .param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("courseForm", "name"));

        assertThat(courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId())).isEmpty();
    }

    @Test
    @DisplayName("I1 강좌 목록 화면에 내 강좌만 보인다")
    void list_showsOnlyMine() throws Exception {
        courseService.create(instructor.getId(), form("내 강좌"));
        courseService.create(otherInstructor.getId(), form("남의 강좌"));

        mockMvc.perform(get("/instructor/courses").with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("내 강좌")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("남의 강좌"))));
    }

    @Test
    @DisplayName("I1 다른 강사의 강좌 주소를 직접 입력하면 403")
    void detail_otherInstructorForbidden() throws Exception {
        Long otherCourseId = courseService.create(otherInstructor.getId(), form("남의 강좌"));

        mockMvc.perform(get("/instructor/courses/{id}", otherCourseId).with(user(instructor)))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));
    }

    private CourseForm form(String name) {
        CourseForm form = new CourseForm();
        form.setName(name);
        return form;
    }
}
