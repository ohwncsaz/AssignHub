package com.hjh.assignhub.course;

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
import com.hjh.assignhub.course.CourseOverviewService.AssignmentProgress;
import com.hjh.assignhub.course.CourseOverviewService.CourseOverview;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// 강사용 강좌 요약 — 대시보드 강좌 카드, 강좌 상세의 수강생 수·과제별 제출 현황
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseOverviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseOverviewService courseOverviewService;

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
    private Course course;
    private Course emptyCourse;
    private Assignment closed;
    private Assignment open;
    private Assignment upcoming;

    // 강좌(학생 3명): 마감 과제 1/3 제출, 진행중 과제 3/3 제출, 예정 과제 / 빈 강좌(학생 0명)
    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        User other = userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2"));
        instructor = new LoginUser(instructorUser);
        course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
        emptyCourse = courseRepository.save(new Course(instructorUser, "빈 강좌", "EMPTY234"));
        courseRepository.save(new Course(other, "남의 강좌", "OTHER234"));

        User s1 = student("s1@test.com", "20260001");
        User s2 = student("s2@test.com", "20260002");
        User s3 = student("s3@test.com", "20260003");
        for (User s : List.of(s1, s2, s3)) {
            enrollmentRepository.save(new Enrollment(course, s));
        }

        LocalDateTime now = LocalDateTime.now();
        closed = assignmentRepository.save(new Assignment(course, "마감 과제", "내용", now.minusDays(7), now.minusDays(1), 100, null));
        open = assignmentRepository.save(new Assignment(course, "진행중 과제", "내용", now.minusDays(1), now.plusDays(3), 100, null));
        upcoming = assignmentRepository.save(new Assignment(course, "예정 과제", "내용", now.plusDays(1), now.plusDays(5), 100, null));
        submissionRepository.save(new Submission(closed, s1, "제출", null));
        for (User s : List.of(s1, s2, s3)) {
            submissionRepository.save(new Submission(open, s, "제출", null));
        }
    }

    @Test
    @DisplayName("과제별 제출 현황: 제출/미제출 인원과 비율(반올림), 시작 전 과제는 '시작 전'")
    void assignmentProgress() {
        List<AssignmentProgress> progresses = courseOverviewService.findAssignmentProgress(course.getId(), 3);

        AssignmentProgress closedProgress = find(progresses, closed);
        assertThat(closedProgress.submitted()).isEqualTo(1);
        assertThat(closedProgress.getNotSubmitted()).isEqualTo(2);
        assertThat(closedProgress.getSubmittedRate()).isEqualTo(33);
        assertThat(closedProgress.getNotSubmittedRate()).isEqualTo(67);

        AssignmentProgress openProgress = find(progresses, open);
        assertThat(openProgress.getSubmittedRate()).isEqualTo(100);
        assertThat(openProgress.getNotSubmitted()).isZero();

        assertThat(find(progresses, upcoming).isStarted()).isFalse();
        // 평균 제출률 = (1 + 3) / (3명 × 시작된 과제 2개) = 4/6 = 67%
        assertThat(courseOverviewService.averageSubmissionRate(progresses)).isEqualTo(67);
    }

    @Test
    @DisplayName("대시보드 강좌 카드: 내 강좌만, 수강생 수 · 과제 수 · 진행중 · 평균 제출률 (학생 0명이면 제출률 없음)")
    void courseOverviews() {
        List<CourseOverview> overviews = courseOverviewService.findMyCourseOverviews(instructor.getId());

        assertThat(overviews).extracting(o -> o.course().getName()).containsExactlyInAnyOrder("자바 스프링", "빈 강좌");
        CourseOverview main = overviews.stream().filter(o -> o.course().getId().equals(course.getId())).findFirst().orElseThrow();
        assertThat(main.students()).isEqualTo(3);
        assertThat(main.assignments()).isEqualTo(3);
        assertThat(main.open()).isEqualTo(1);
        assertThat(main.submissionRate()).isEqualTo(67);

        CourseOverview empty = overviews.stream().filter(o -> o.course().getId().equals(emptyCourse.getId())).findFirst().orElseThrow();
        assertThat(empty.students()).isZero();
        assertThat(empty.submissionRate()).isNull();
    }

    @Test
    @DisplayName("강좌 상세 화면: 수강생 수가 크게 보이고, 과제마다 제출·미제출 인원과 %가 보인다")
    void courseDetailPage() throws Exception {
        mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"studentCount\">3</span>")))
                .andExpect(content().string(Matchers.matchesPattern(
                        "(?s).*제출 <span>1</span>명 \\(<span>33</span>%\\).*미제출 <span>2</span>명 \\(<span>67</span>%\\).*")))
                .andExpect(content().string(Matchers.containsString("시작 전")))
                .andExpect(content().string(Matchers.containsString(">67</span><small class=\"h6\">%</small>")));
    }

    @Test
    @DisplayName("강사 대시보드: 내 강좌 카드가 보이고 남의 강좌는 보이지 않는다")
    void instructorDashboard() throws Exception {
        mockMvc.perform(get("/").with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(">내 강좌</h2>")))
                .andExpect(content().string(Matchers.containsString("자바 스프링")))
                .andExpect(content().string(Matchers.containsString("href=\"/instructor/courses/" + course.getId() + "\"")))
                .andExpect(content().string(Matchers.containsString("67%")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("남의 강좌"))));
    }

    private User student(String email, String studentNo) {
        return userRepository.save(User.createStudent(email, "pw", "학생" + studentNo, studentNo));
    }

    private AssignmentProgress find(List<AssignmentProgress> progresses, Assignment assignment) {
        return progresses.stream().filter(p -> p.assignment().getId().equals(assignment.getId())).findFirst().orElseThrow();
    }
}
