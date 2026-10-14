package com.hjh.assignhub.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.submission.GradingService.SubmissionBoard;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// I4 제출 현황 · I5 채점 · 피드백 — B5 본인 강좌만, B6 점수 범위, 채점 후 B4
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GradingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GradingService gradingService;

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

    private LoginUser instructor;
    private LoginUser otherInstructor;
    private User submitter;
    private User notSubmitter;
    private Assignment assignment;
    private Submission submission;

    // 수강생 2명 중 1명만 제출 · 배점 50점
    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        instructor = new LoginUser(instructorUser);
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
        submitter = userRepository.save(User.createStudent("s1@test.com", "pw", "제출학생", "20260001"));
        notSubmitter = userRepository.save(User.createStudent("s2@test.com", "pw", "미제출학생", "20260002"));
        Course course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
        enrollmentRepository.save(new Enrollment(course, submitter));
        enrollmentRepository.save(new Enrollment(course, notSubmitter));

        LocalDateTime now = LocalDateTime.now();
        assignment = assignmentRepository.save(new Assignment(course, "1주차 과제", "내용", now.minusDays(1), now.plusDays(1), 50, null));
        submission = submissionRepository.save(new Submission(assignment, submitter, "제 답안입니다.", null));
    }

    // ---------------------------------------------------------------- I4 제출 현황

    @Test
    @DisplayName("I4 제출 현황: 수강생 전원이 제출자 · 미제출자로 나뉘고 인원 · 비율이 계산된다")
    void board() {
        SubmissionBoard board = gradingService.getBoard(assignment.getId(), instructor.getId());

        assertThat(board.getStudents()).isEqualTo(2);
        assertThat(board.getSubmitted()).isEqualTo(1);
        assertThat(board.getNotSubmitted()).isEqualTo(1);
        assertThat(board.getSubmittedRate()).isEqualTo(50);
        assertThat(board.getGraded()).isZero();
    }

    @Test
    @DisplayName("I4 화면: 제출자는 '채점' 버튼, 미제출자는 '미제출'로 표시된다")
    void boardPage() throws Exception {
        mockMvc.perform(get("/instructor/assignments/{id}/submissions", assignment.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("제출학생")))
                .andExpect(content().string(Matchers.containsString("미제출학생")))
                .andExpect(content().string(Matchers.containsString("href=\"/instructor/submissions/" + submission.getId() + "/grade\"")))
                .andExpect(content().string(Matchers.containsString("data-search=\"미제출\"")))
                .andExpect(content().string(Matchers.containsString("data-search=\"채점 대기\"")));
    }

    @Test
    @DisplayName("B5 다른 강사는 제출 현황 · 채점 화면에 접근할 수 없다 (403)")
    void otherInstructorForbidden() throws Exception {
        mockMvc.perform(get("/instructor/assignments/{id}/submissions", assignment.getId()).with(user(otherInstructor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/instructor/submissions/{id}/grade", submission.getId()).with(user(otherInstructor)))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> gradingService.grade(submission.getId(), otherInstructor.getId(), grade(40, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------- I5 채점

    @Test
    @DisplayName("I5 채점: 점수 · 피드백이 저장되고 GRADED가 된다 (경계값 0점, 배점 50점 모두 허용)")
    void grade_success() {
        gradingService.grade(submission.getId(), instructor.getId(), grade(0, "  다시 해 보세요  "));
        Submission graded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(graded.getScore()).isZero();
        assertThat(graded.getFeedback()).isEqualTo("다시 해 보세요");
        assertThat(graded.getGradedAt()).isNotNull();

        gradingService.grade(submission.getId(), instructor.getId(), grade(50, ""));
        Submission regraded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(regraded.getScore()).isEqualTo(50);
        assertThat(regraded.getFeedback()).isNull();
    }

    @Test
    @DisplayName("B6 점수가 배점을 넘거나 음수면 점수 칸 에러로 거부된다")
    void grade_outOfRange() {
        assertThatThrownBy(() -> gradingService.grade(submission.getId(), instructor.getId(), grade(51, null)))
                .isInstanceOf(FormFieldException.class)
                .hasMessage("점수는 0점 이상 배점(50점) 이하로 입력하세요.")
                .extracting("field").isEqualTo("score");
        assertThatThrownBy(() -> gradingService.grade(submission.getId(), instructor.getId(), grade(-1, null)))
                .isInstanceOf(FormFieldException.class);
        assertThat(submissionRepository.findById(submission.getId()).orElseThrow().isGraded()).isFalse();
    }

    @Test
    @DisplayName("I5 화면: 채점하면 제출 현황으로 돌아가고, 배점 초과는 폼 에러를 보여준다")
    void gradeViaController() throws Exception {
        mockMvc.perform(post("/instructor/submissions/{id}/grade", submission.getId()).with(user(instructor)).with(csrf())
                        .param("score", "51"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("gradeForm", "score"))
                .andExpect(content().string(Matchers.containsString("배점(50점) 이하")));

        mockMvc.perform(post("/instructor/submissions/{id}/grade", submission.getId()).with(user(instructor)).with(csrf())
                        .param("score", "45")
                        .param("feedback", "잘했어요"))
                .andExpect(redirectedUrl("/instructor/assignments/" + assignment.getId() + "/submissions"))
                .andExpect(flash().attribute("successMessage", "제출학생 학생의 제출물을 채점했습니다."));

        mockMvc.perform(get("/instructor/assignments/{id}/submissions", assignment.getId()).with(user(instructor)))
                .andExpect(content().string(Matchers.matchesPattern("(?s).*<strong>45</strong> / <span>50</span>.*")));
    }

    @Test
    @DisplayName("B4 채점이 끝나면 학생은 기간 안이라도 다시 제출할 수 없다")
    void gradedBlocksResubmission() throws Exception {
        gradingService.grade(submission.getId(), instructor.getId(), grade(40, null));

        mockMvc.perform(multipart("/student/assignments/{id}/submission", assignment.getId())
                        .with(user(new LoginUser(submitter))).with(csrf())
                        .param("content", "수정본"))
                .andExpect(flash().attribute("errorMessage", SubmissionService.ALREADY_GRADED));
        assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getContent()).isEqualTo("제 답안입니다.");
    }

    @Test
    @DisplayName("B6 유지: 이미 45점으로 채점했다면 배점을 45점 미만으로 낮출 수 없다 (45점까지는 가능)")
    void cannotLowerMaxScoreBelowGivenScore() throws Exception {
        gradingService.grade(submission.getId(), instructor.getId(), grade(45, null));

        mockMvc.perform(multipart("/instructor/assignments/{id}/edit", assignment.getId()).with(user(instructor)).with(csrf())
                        .param("courseId", assignment.getCourse().getId().toString())
                        .param("title", "1주차 과제")
                        .param("content", "내용")
                        .param("startAt", "2026-10-01T09:00")
                        .param("endAt", "2026-12-31T23:59")
                        .param("maxScore", "40"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("assignmentForm", "maxScore"));
        assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getMaxScore()).isEqualTo(50);

        mockMvc.perform(multipart("/instructor/assignments/{id}/edit", assignment.getId()).with(user(instructor)).with(csrf())
                        .param("courseId", assignment.getCourse().getId().toString())
                        .param("title", "1주차 과제")
                        .param("content", "내용")
                        .param("startAt", "2026-10-01T09:00")
                        .param("endAt", "2026-12-31T23:59")
                        .param("maxScore", "45"))
                .andExpect(redirectedUrl("/instructor/courses/" + assignment.getCourse().getId()));
    }

    private GradeForm grade(int score, String feedback) {
        GradeForm form = new GradeForm();
        form.setScore(score);
        form.setFeedback(feedback);
        return form;
    }
}
