package com.hjh.assignhub.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

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
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentAssignmentControllerTest {

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

    @Autowired
    private StudentAssignmentService studentAssignmentService;

    private LoginUser student;
    private LoginUser outsider;
    private Course myCourse;
    private Course otherCourse;

    @BeforeEach
    void setUp() {
        User instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        User studentUser = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        student = new LoginUser(studentUser);
        outsider = new LoginUser(userRepository.save(User.createStudent("s2@test.com", "pw", "학생2", "20260002")));
        myCourse = courseRepository.save(new Course(instructor, "내 강좌", "AB3K7XQ2"));
        otherCourse = courseRepository.save(new Course(instructor, "남의 강좌", "ZX9M4PQ7"));
        enrollmentRepository.save(new Enrollment(myCourse, studentUser));
    }

    @Test
    @DisplayName("S2 수강 중인 강좌의 과제만 상태 배지와 함께 보인다")
    void list_onlyEnrolledCoursesWithBadges() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        save(myCourse, "진행중 과제", now.minusDays(1), now.plusDays(1));
        save(myCourse, "예정 과제", now.plusDays(1), now.plusDays(3));
        save(myCourse, "마감 과제", now.minusDays(3), now.minusDays(1));
        save(otherCourse, "남의 강좌 과제", now.minusDays(1), now.plusDays(1));

        mockMvc.perform(get("/student/assignments").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("진행중 과제")))
                .andExpect(content().string(Matchers.containsString("예정 과제")))
                .andExpect(content().string(Matchers.containsString("마감 과제")))
                .andExpect(content().string(Matchers.containsString("badge-success")))
                .andExpect(content().string(Matchers.containsString("badge-secondary")))
                .andExpect(content().string(Matchers.containsString("badge-dark")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("남의 강좌 과제"))));
    }

    @Test
    @DisplayName("S2 목록은 진행중(마감 빠른 순) → 예정 → 마감 순서로 정렬된다")
    void list_sortedByStatus() {
        LocalDateTime now = LocalDateTime.now();
        save(myCourse, "마감", now.minusDays(3), now.minusDays(1));
        save(myCourse, "예정", now.plusDays(1), now.plusDays(3));
        save(myCourse, "진행중-늦게마감", now.minusDays(1), now.plusDays(5));
        save(myCourse, "진행중-곧마감", now.minusDays(1), now.plusHours(2));

        List<String> titles = studentAssignmentService.findMyAssignments(student.getId()).stream()
                .map(Assignment::getTitle).toList();

        assertThat(titles).containsExactly("진행중-곧마감", "진행중-늦게마감", "예정", "마감");
    }

    @Test
    @DisplayName("S2 수강 중인 강좌의 과제 상세를 볼 수 있다")
    void detail_enrolled() throws Exception {
        Assignment assignment = save(myCourse, "1주차 과제", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/student/assignments/{id}", assignment.getId()).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("1주차 과제")));
    }

    @Test
    @DisplayName("B2 수강 등록하지 않은 학생은 과제 상세·첨부에 접근할 수 없다 (403)")
    void detail_notEnrolledForbidden() throws Exception {
        Assignment assignment = save(myCourse, "1주차 과제", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/student/assignments/{id}", assignment.getId()).with(user(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));
        mockMvc.perform(get("/student/assignments/{id}/file", assignment.getId()).with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("S2 수강 중인 강좌가 없으면 수강 등록 안내를 보여준다")
    void list_empty() throws Exception {
        mockMvc.perform(get("/student/assignments").with(user(outsider)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("아직 볼 수 있는 과제가 없습니다.")));
    }

    private Assignment save(Course course, String title, LocalDateTime startAt, LocalDateTime endAt) {
        return assignmentRepository.save(new Assignment(course, title, "내용", startAt, endAt, 100, null));
    }
}
