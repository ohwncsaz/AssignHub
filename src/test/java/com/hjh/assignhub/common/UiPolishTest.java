package com.hjh.assignhub.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// 화면 다듬기: 마감 임박 표시 · 사이드바 현재 메뉴 · 미구현 메뉴 · 세션 만료 처리 · 공통 CSS
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UiPolishTest {

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

    private LoginUser instructor;
    private LoginUser student;
    private Course course;

    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        User studentUser = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        instructor = new LoginUser(instructorUser);
        student = new LoginUser(studentUser);
        course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
        enrollmentRepository.save(new Enrollment(course, studentUser));
    }

    // ---------------------------------------------------------------- 마감 임박

    @Test
    @DisplayName("남은 시간: 24시간 미만은 시간·분, 그 이상은 D-N, 진행중이 아니면 표시하지 않는다")
    void remainingText() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 12, 0);
        Assignment threeHours = assignment(now.minusDays(1), now.plusHours(3));
        Assignment thirtyMinutes = assignment(now.minusDays(1), now.plusMinutes(30));
        Assignment threeDays = assignment(now.minusDays(1), LocalDateTime.of(2026, 10, 10, 9, 0));
        Assignment upcoming = assignment(now.plusDays(1), now.plusDays(3));
        Assignment closed = assignment(now.minusDays(3), now.minusDays(1));

        assertThat(threeHours.remainingTextAt(now)).isEqualTo("3시간 남음");
        assertThat(threeHours.isClosingSoonAt(now)).isTrue();
        assertThat(thirtyMinutes.remainingTextAt(now)).isEqualTo("30분 남음");
        assertThat(threeDays.remainingTextAt(now)).isEqualTo("D-3");
        assertThat(threeDays.isClosingSoonAt(now)).isFalse();
        assertThat(upcoming.remainingTextAt(now)).isNull();
        assertThat(closed.remainingTextAt(now)).isNull();
    }

    @Test
    @DisplayName("내 과제 목록에서 24시간 이내 마감 과제는 행이 강조되고 남은 시간이 보인다")
    void studentList_highlightsClosingSoon() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.save(assignment(now.minusDays(1), now.plusHours(3)));

        mockMvc.perform(get("/student/assignments").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("table-warning")))
                .andExpect(content().string(Matchers.containsString("시간 남음")));
    }

    // ---------------------------------------------------------------- 사이드바

    @Test
    @DisplayName("사이드바: 지금 보고 있는 메뉴가 강조되고, 하위 화면(과제 등록)에서도 상위 메뉴가 강조된다")
    void sidebar_activeMenu() throws Exception {
        mockMvc.perform(get("/instructor/courses").with(user(instructor)))
                .andExpect(content().string(Matchers.matchesPattern(
                        "(?s).*<li class=\"nav-item active\">\\s*<a class=\"nav-link\" href=\"/instructor/courses\">.*")));

        mockMvc.perform(get("/instructor/assignments/new").param("courseId", course.getId().toString()).with(user(instructor)))
                .andExpect(content().string(Matchers.matchesPattern(
                        "(?s).*<li class=\"nav-item active\">\\s*<a class=\"nav-link\" href=\"/instructor/courses\">.*")));
    }

    @Test
    @DisplayName("사이드바: 아직 없는 메뉴(통계 I6)는 '준비 중'으로 비활성화, 구현된 내 제출(S4)은 링크로 열린다")
    void sidebar_unbuiltMenusDisabled() throws Exception {
        mockMvc.perform(get("/").with(user(instructor)))
                .andExpect(content().string(Matchers.containsString("준비 중")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/instructor/stats\""))));
        mockMvc.perform(get("/").with(user(student)))
                .andExpect(content().string(Matchers.containsString("href=\"/student/submissions\"")));
    }

    // ---------------------------------------------------------------- 세션 만료

    @Test
    @DisplayName("로그인 폼의 CSRF 토큰이 만료되면 '권한 없음' 대신 로그인 화면으로 보내 다시 시도하게 한다")
    void expiredCsrfOnLogin_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/login").param("email", "s1@test.com").param("password", "pw"))
                .andExpect(redirectedUrl("/login?expired"));

        mockMvc.perform(get("/login").param("expired", ""))
                .andExpect(content().string(Matchers.containsString("세션이 만료되었습니다")));
    }

    @Test
    @DisplayName("역할이 맞지 않는 접근은 기존처럼 403 페이지")
    void roleMismatch_stillForbidden() throws Exception {
        mockMvc.perform(get("/instructor/courses").with(user(student)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 공통 CSS

    @Test
    @DisplayName("공통 CSS(한글 어절 단위 줄바꿈)가 모든 화면에 포함되고 로그인 없이도 내려받을 수 있다")
    void appCss_includedAndServed() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(Matchers.containsString("/css/app.css")));
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("word-break: keep-all")));
    }

    private Assignment assignment(LocalDateTime startAt, LocalDateTime endAt) {
        return new Assignment(course, "과제", "내용", startAt, endAt, 100, null);
    }
}
