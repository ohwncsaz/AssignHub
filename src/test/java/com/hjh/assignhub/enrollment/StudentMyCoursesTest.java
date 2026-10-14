package com.hjh.assignhub.enrollment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// 학생 "내 강좌" — 수강 중인 강좌 카드와 강좌 상세(그 강좌의 과제)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentMyCoursesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    private LoginUser student;
    private Course myCourse;
    private Course otherCourse;

    @BeforeEach
    void setUp() {
        User instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "김교수"));
        User studentUser = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        student = new LoginUser(studentUser);
        myCourse = courseRepository.save(new Course(instructor, "자바 스프링", "AB3K7XQ2"));
        otherCourse = courseRepository.save(new Course(instructor, "데이터베이스", "ZX9M4PQ7"));
        enrollmentRepository.save(new Enrollment(myCourse, studentUser));

        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.save(new Assignment(myCourse, "곧 마감 과제", "내용", now.minusDays(1), now.plusHours(2), 100, null));
        assignmentRepository.save(new Assignment(myCourse, "여유 있는 과제", "내용", now.minusDays(1), now.plusDays(5), 100, null));
        assignmentRepository.save(new Assignment(otherCourse, "다른 강좌 과제", "내용", now.minusDays(1), now.plusDays(5), 100, null));
    }

    @Test
    @DisplayName("내 강좌: 수강 중인 강좌만 카드로 보이고, 강사·과제 수·진행중·마감 임박 개수가 표시된다")
    void myCourses_cards() throws Exception {
        mockMvc.perform(get("/student/courses").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("자바 스프링")))
                .andExpect(content().string(Matchers.containsString("김교수")))
                .andExpect(content().string(Matchers.containsString("href=\"/student/courses/" + myCourse.getId() + "\"")))
                .andExpect(content().string(Matchers.matchesPattern("(?s).*마감 임박 <span>1</span>.*")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("데이터베이스"))));
    }

    @Test
    @DisplayName("강좌 상세: 그 강좌의 과제만 보이고 과제 상세로 이동할 수 있다")
    void detail_showsCourseAssignments() throws Exception {
        mockMvc.perform(get("/student/courses/{id}", myCourse.getId()).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("곧 마감 과제")))
                .andExpect(content().string(Matchers.containsString("여유 있는 과제")))
                .andExpect(content().string(Matchers.containsString("href=\"/student/assignments/")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("다른 강좌 과제"))));
    }

    @Test
    @DisplayName("B2 수강 등록하지 않은 강좌 상세는 403, 없는 강좌는 404")
    void detail_accessControl() throws Exception {
        mockMvc.perform(get("/student/courses/{id}", otherCourse.getId()).with(user(student)))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));
        mockMvc.perform(get("/student/courses/{id}", 999_999L).with(user(student)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("수강 등록 화면의 강좌명을 누르면 강좌 상세로 이동한다")
    void joinPage_linksToCourse() throws Exception {
        mockMvc.perform(get("/student/courses/join").with(user(student)))
                .andExpect(content().string(Matchers.containsString("href=\"/student/courses/" + myCourse.getId() + "\"")));
    }

    @Test
    @DisplayName("사이드바: 내 강좌와 수강 등록이 각각 자기 화면에서만 강조된다")
    void sidebar_activeMenus() throws Exception {
        mockMvc.perform(get("/student/courses/{id}", myCourse.getId()).with(user(student)))
                .andExpect(content().string(Matchers.matchesPattern(
                        "(?s).*<li class=\"nav-item active\">\\s*<a class=\"nav-link\" href=\"/student/courses\">.*")));
        mockMvc.perform(get("/student/courses/join").with(user(student)))
                .andExpect(content().string(Matchers.matchesPattern(
                        "(?s).*<li class=\"nav-item active\">\\s*<a class=\"nav-link\" href=\"/student/courses/join\">.*")))
                .andExpect(content().string(Matchers.not(Matchers.matchesPattern(
                        "(?s).*<li class=\"nav-item active\">\\s*<a class=\"nav-link\" href=\"/student/courses\">.*"))));
    }
}
