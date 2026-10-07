package com.hjh.assignhub.common;

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

// 학생 대시보드 — 수강 중인 강좌 카드
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentDashboardTest {

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

    private User instructorUser;
    private LoginUser student;
    private LoginUser newStudent;
    private Course myCourse;

    @BeforeEach
    void setUp() {
        instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "김교수"));
        User studentUser = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        student = new LoginUser(studentUser);
        newStudent = new LoginUser(userRepository.save(User.createStudent("s2@test.com", "pw", "학생2", "20260002")));
        myCourse = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
        courseRepository.save(new Course(instructorUser, "데이터베이스", "ZX9M4PQ7"));
        enrollmentRepository.save(new Enrollment(myCourse, studentUser));
        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.save(new Assignment(myCourse, "곧 마감", "내용", now.minusDays(1), now.plusHours(2), 100, null));
    }

    @Test
    @DisplayName("학생 대시보드에 수강 중인 강좌만 카드로 보이고, 누르면 강좌 상세로 간다")
    void showsEnrolledCourses() throws Exception {
        mockMvc.perform(get("/").with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(">수강 중인 강좌</h2>")))
                .andExpect(content().string(Matchers.containsString("자바 스프링")))
                .andExpect(content().string(Matchers.containsString("김교수")))
                .andExpect(content().string(Matchers.containsString("href=\"/student/courses/" + myCourse.getId() + "\"")))
                .andExpect(content().string(Matchers.matchesPattern("(?s).*마감 임박 <span>1</span>.*")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("데이터베이스"))));
    }

    @Test
    @DisplayName("수강 중인 강좌가 없으면 참여코드 수강 등록 안내를 보여준다")
    void noCourses_showsJoinGuide() throws Exception {
        mockMvc.perform(get("/").with(user(newStudent)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("아직 수강 중인 강좌가 없습니다.")));
    }

    @Test
    @DisplayName("강사 대시보드에는 학생용 수강 강좌 영역이 나오지 않는다")
    void instructor_noStudentSection() throws Exception {
        mockMvc.perform(get("/").with(user(new LoginUser(instructorUser))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString(">수강 중인 강좌</h2>"))));
    }
}
