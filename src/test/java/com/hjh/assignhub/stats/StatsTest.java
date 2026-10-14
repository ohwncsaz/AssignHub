package com.hjh.assignhub.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.stats.InstructorStatsService.AssignmentStat;
import com.hjh.assignhub.stats.InstructorStatsService.CourseStats;
import com.hjh.assignhub.stats.StudentStatsService.StudentStats;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// I6 통계 대시보드 · S5 내 통계
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstructorStatsService instructorStatsService;

    @Autowired
    private StudentStatsService studentStatsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private LoginUser instructor;
    private LoginUser otherInstructor;
    private User kim;
    private User lee;
    private User park;
    private Course course;
    private Assignment closed;   // 배점 50 — kim 40점, lee 30점 채점 / park 미제출
    private Assignment open;     // 배점 100 — kim 제출(채점 전) / lee · park 미제출, 2시간 뒤 마감
    private Assignment upcoming; // 시작 전

    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        instructor = new LoginUser(instructorUser);
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
        course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
        kim = student("kim@test.com", "김학생", "20260001");
        lee = student("lee@test.com", "이학생", "20260002");
        park = student("park@test.com", "박학생", "20260003");

        LocalDateTime now = LocalDateTime.now();
        closed = assignmentRepository.save(new Assignment(course, "1주차", "내용", now.minusDays(7), now.minusDays(1), 50, null));
        open = assignmentRepository.save(new Assignment(course, "2주차", "내용", now.minusDays(1), now.plusHours(2), 100, null));
        upcoming = assignmentRepository.save(new Assignment(course, "3주차", "내용", now.plusDays(1), now.plusDays(5), 100, null));

        submissionRepository.save(new Submission(closed, kim, "답안", null)).grade(40, null);
        submissionRepository.save(new Submission(closed, lee, "답안", null)).grade(30, null);
        submissionRepository.save(new Submission(open, kim, "답안", null));
    }

    // ---------------------------------------------------------------- I6

    @Test
    @DisplayName("I6 과제별 제출률 · 평균 점수(group by) · 강좌 평균, 시작 전 과제는 제출률 계산에서 제외")
    void courseStats() {
        CourseStats stats = instructorStatsService.getStats(course.getId(), instructor.getId());

        AssignmentStat closedStat = find(stats.assignments(), closed);
        assertThat(closedStat.submitted()).isEqualTo(2);
        assertThat(closedStat.getSubmittedRate()).isEqualTo(67);
        assertThat(closedStat.graded()).isEqualTo(2);
        assertThat(closedStat.averageScore()).isEqualTo(35.0);      // (40 + 30) / 2
        assertThat(closedStat.getAverageScoreRate()).isEqualTo(70); // 35 / 50

        AssignmentStat openStat = find(stats.assignments(), open);
        assertThat(openStat.getSubmittedRate()).isEqualTo(33);
        assertThat(openStat.averageScore()).isNull();

        assertThat(find(stats.assignments(), upcoming).isStarted()).isFalse();
        // 평균 제출률 = (2 + 1) / (3명 × 시작된 과제 2개) = 50%
        assertThat(stats.averageSubmissionRate()).isEqualTo(50);
        assertThat(stats.averageScoreRate()).isEqualTo(70);
    }

    @Test
    @DisplayName("I6 미제출자: 시작된 과제 중 안 낸 과제 목록, 많이 안 낸 학생부터")
    void missingStudents() {
        CourseStats stats = instructorStatsService.getStats(course.getId(), instructor.getId());

        assertThat(stats.missingStudents()).extracting(m -> m.student().getName()).containsExactly("박학생", "이학생");
        assertThat(stats.missingStudents().get(0).missing()).extracting(Assignment::getTitle).containsExactly("1주차", "2주차");
        assertThat(stats.missingStudents().get(1).missing()).extracting(Assignment::getTitle).containsExactly("2주차");
    }

    @Test
    @DisplayName("I6 화면: 요약 카드 · 차트 데이터 · 미제출자가 보이고, 다른 강사의 강좌는 403")
    void statsPage() throws Exception {
        mockMvc.perform(get("/instructor/stats").with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"averageSubmissionRate\">50%</span>")))
                .andExpect(content().string(Matchers.containsString("id=\"averageScoreRate\">70%</span>")))
                .andExpect(content().string(Matchers.containsString("submissionChart")))
                .andExpect(content().string(Matchers.containsString("const submissionRates = [67,33];")))
                .andExpect(content().string(Matchers.containsString("박학생")));

        mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString()).with(user(otherInstructor)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("I6 과제 제목의 따옴표 · 스크립트는 차트 데이터(자바스크립트) 안에서 이스케이프된다")
    void chartDataEscaped() throws Exception {
        assignmentRepository.save(new Assignment(course, "\"</script><script>alert(1)</script>", "내용",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, null));

        mockMvc.perform(get("/instructor/stats").with(user(instructor)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<script>alert(1)</script>"))));
    }

    // ---------------------------------------------------------------- S5

    @Test
    @DisplayName("S5 내 통계: 제출률(시작된 과제 기준) · 평균 점수(배점 대비) · 해야 할 과제")
    void studentStats() {
        StudentStats kimStats = studentStatsService.getStats(kim.getId());
        assertThat(kimStats.submissionRate()).isEqualTo(100);  // 시작된 2개 모두 제출
        assertThat(kimStats.averageScoreRate()).isEqualTo(80); // 40 / 50
        assertThat(kimStats.todo()).isZero();

        StudentStats parkStats = studentStatsService.getStats(park.getId());
        assertThat(parkStats.submissionRate()).isZero();
        assertThat(parkStats.averageScoreRate()).isNull();
        assertThat(parkStats.todo()).isEqualTo(1);
        assertThat(parkStats.dueSoon()).extracting(Assignment::getTitle).containsExactly("2주차");
    }

    @Test
    @DisplayName("S5 학생 대시보드에 내 통계와 마감 임박 · 미제출 과제가 보인다")
    void studentDashboard() throws Exception {
        mockMvc.perform(get("/").with(user(new LoginUser(lee))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(">내 통계</h2>")))
                .andExpect(content().string(Matchers.containsString("id=\"mySubmissionRate\">50</span>")))
                .andExpect(content().string(Matchers.containsString("id=\"myAverageScore\">60</span>")))
                .andExpect(content().string(Matchers.containsString("마감 임박 · 미제출 과제")))
                .andExpect(content().string(Matchers.containsString("시간 남음")));
    }

    private User student(String email, String name, String studentNo) {
        User s = userRepository.save(User.createStudent(email, "pw", name, studentNo));
        enrollmentRepository.save(new Enrollment(course, s));
        return s;
    }

    private AssignmentStat find(List<AssignmentStat> stats, Assignment assignment) {
        return stats.stream().filter(s -> s.assignment().getId().equals(assignment.getId())).findFirst().orElseThrow();
    }
}
