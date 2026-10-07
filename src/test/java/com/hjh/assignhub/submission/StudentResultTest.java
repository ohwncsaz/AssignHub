package com.hjh.assignhub.submission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

// S4 결과 확인 — 내 제출물의 상태 · 점수 · 피드백
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentResultTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    private User student;
    private User otherStudent;
    private Assignment gradedAssignment;

    @BeforeEach
    void setUp() {
        User instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        otherStudent = userRepository.save(User.createStudent("s2@test.com", "pw", "학생2", "20260002"));
        Course course = courseRepository.save(new Course(instructor, "자바 스프링", "AB3K7XQ2"));
        enrollmentRepository.save(new Enrollment(course, student));
        enrollmentRepository.save(new Enrollment(course, otherStudent));

        LocalDateTime now = LocalDateTime.now();
        gradedAssignment = assignmentRepository.save(new Assignment(course, "채점된 과제", "내용", now.minusDays(3), now.minusDays(1), 50, null));
        Assignment waitingAssignment = assignmentRepository.save(new Assignment(course, "채점 대기 과제", "내용", now.minusDays(1), now.plusDays(1), 100, null));

        Submission graded = submissionRepository.save(new Submission(gradedAssignment, student, "답안", null));
        graded.grade(45, "잘했어요\n다음엔 예외 처리도 해 보세요");
        submissionRepository.save(new Submission(waitingAssignment, student, "답안", null));

        Submission others = submissionRepository.save(new Submission(gradedAssignment, otherStudent, "남의 답안", null));
        others.grade(10, "다른 학생 피드백");
    }

    @Test
    @DisplayName("S4 내 제출: 채점된 과제는 점수/배점 · 피드백, 채점 전은 '채점 대기'로 보인다")
    void mySubmissions() throws Exception {
        mockMvc.perform(get("/student/submissions").with(user(new LoginUser(student))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("채점된 과제")))
                .andExpect(content().string(Matchers.containsString("채점 대기 과제")))
                .andExpect(content().string(Matchers.matchesPattern("(?s).*<strong class=\"h6\">45</strong> / <span>50</span>.*")))
                .andExpect(content().string(Matchers.containsString("잘했어요")))
                .andExpect(content().string(Matchers.containsString("채점 대기")));
    }

    @Test
    @DisplayName("S4 다른 학생의 점수 · 피드백은 보이지 않는다")
    void doesNotShowOthers() throws Exception {
        mockMvc.perform(get("/student/submissions").with(user(new LoginUser(student))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("다른 학생 피드백"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("남의 답안"))));
    }

    @Test
    @DisplayName("S4 과제 상세에도 채점 결과(점수 · 피드백)가 보인다")
    void assignmentDetailShowsResult() throws Exception {
        mockMvc.perform(get("/student/assignments/{id}", gradedAssignment.getId()).with(user(new LoginUser(student))))
                .andExpect(content().string(Matchers.containsString("alert alert-primary")))
                .andExpect(content().string(Matchers.containsString("다음엔 예외 처리도 해 보세요")));
    }

    @Test
    @DisplayName("S4 제출한 과제가 없으면 내 과제로 가는 안내를 보여준다")
    void empty() throws Exception {
        User newStudent = userRepository.save(User.createStudent("s3@test.com", "pw", "학생3", "20260003"));

        mockMvc.perform(get("/student/submissions").with(user(new LoginUser(newStudent))))
                .andExpect(content().string(Matchers.containsString("아직 제출한 과제가 없습니다.")));
    }
}
