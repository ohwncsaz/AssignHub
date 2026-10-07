package com.hjh.assignhub.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// S3 과제 제출 · 재제출 — B1 기간 · B2 수강생 · B3 덮어쓰기 · B4 채점 완료
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionService submissionService;

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

    @Autowired
    private FileStorage fileStorage;

    private User student;
    private User outsider;
    private Assignment open;
    private Assignment upcoming;
    private Assignment closed;

    @BeforeEach
    void setUp() {
        User instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        outsider = userRepository.save(User.createStudent("s2@test.com", "pw", "학생2", "20260002"));
        Course course = courseRepository.save(new Course(instructor, "자바 스프링", "AB3K7XQ2"));
        enrollmentRepository.save(new Enrollment(course, student));

        LocalDateTime now = LocalDateTime.now();
        open = assignmentRepository.save(new Assignment(course, "진행중", "내용", now.minusDays(1), now.plusDays(1), 100, null));
        upcoming = assignmentRepository.save(new Assignment(course, "예정", "내용", now.plusDays(1), now.plusDays(3), 100, null));
        closed = assignmentRepository.save(new Assignment(course, "마감", "내용", now.minusDays(3), now.minusDays(1), 100, null));
    }

    // ---------------------------------------------------------------- Service

    @Test
    @DisplayName("S3 진행중 과제는 제출할 수 있다 (첨부 포함)")
    void submit_open() {
        Submission submission = submissionService.submit(open.getId(), student.getId(), form("1차 제출", file("report.pdf")));

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getFileName()).isEqualTo("report.pdf");
        assertThat(fileStorage.load(submission.getFilePath()).exists()).isTrue();
    }

    @Test
    @DisplayName("B1 시작 전 · 마감 후에는 제출할 수 없다")
    void submit_outOfPeriod() {
        assertThatThrownBy(() -> submissionService.submit(upcoming.getId(), student.getId(), form("제출", null)))
                .isInstanceOf(BusinessException.class).hasMessage(SubmissionService.NOT_IN_PERIOD);
        assertThatThrownBy(() -> submissionService.submit(closed.getId(), student.getId(), form("제출", null)))
                .isInstanceOf(BusinessException.class).hasMessage(SubmissionService.NOT_IN_PERIOD);
        assertThat(submissionRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    @DisplayName("B2 수강 등록하지 않은 학생은 제출할 수 없다 (403)")
    void submit_notEnrolled() {
        assertThatThrownBy(() -> submissionService.submit(open.getId(), outsider.getId(), form("제출", null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("B3 재제출은 새로 만들지 않고 덮어쓴다 — 새 파일을 올리면 첨부도 교체")
    void resubmit_overwrites() {
        Submission first = submissionService.submit(open.getId(), student.getId(), form("1차", file("old.pdf")));
        String oldPath = first.getFilePath();

        Submission second = submissionService.submit(open.getId(), student.getId(), form("2차", file("new.pdf")));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getContent()).isEqualTo("2차");
        assertThat(second.getFileName()).isEqualTo("new.pdf");
        assertThat(submissionRepository.findByStudentId(student.getId())).hasSize(1);
        // 교체된 예전 첨부파일은 지워져서 더 이상 불러올 수 없다
        assertThatThrownBy(() -> fileStorage.load(oldPath)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("B3 재제출 때 파일을 고르지 않으면 기존 첨부 유지, '기존 첨부 삭제'를 체크하면 제거")
    void resubmit_keepOrRemoveFile() {
        submissionService.submit(open.getId(), student.getId(), form("1차", file("keep.pdf")));

        Submission kept = submissionService.submit(open.getId(), student.getId(), form("2차", null));
        assertThat(kept.getFileName()).isEqualTo("keep.pdf");

        SubmissionForm remove = form("3차", null);
        remove.setRemoveFile(true);
        Submission removed = submissionService.submit(open.getId(), student.getId(), remove);
        assertThat(removed.getFilePath()).isNull();
    }

    @Test
    @DisplayName("B4 채점 완료(GRADED)된 제출은 재제출할 수 없다")
    void resubmit_afterGraded() {
        Submission submission = submissionService.submit(open.getId(), student.getId(), form("1차", null));
        submission.grade(90, "좋아요");

        assertThatThrownBy(() -> submissionService.submit(open.getId(), student.getId(), form("2차", null)))
                .isInstanceOf(BusinessException.class).hasMessage(SubmissionService.ALREADY_GRADED);
        assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getContent()).isEqualTo("1차");
    }

    // ---------------------------------------------------------------- 화면 · 강제 요청

    @Test
    @DisplayName("B1 시연: 마감된 과제는 제출 폼이 비활성화되고, 개발자도구로 강제로 요청해도 서버에서 막힌다")
    void forcedRequestOnClosedAssignment() throws Exception {
        mockMvc.perform(get("/student/assignments/{id}", closed.getId()).with(user(new LoginUser(student))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<fieldset disabled=\"disabled\">")))
                .andExpect(content().string(Matchers.containsString("지금은 제출 기간이 아닙니다.")));

        // 비활성화를 풀고 보낸 것과 같은 요청
        mockMvc.perform(multipart("/student/assignments/{id}/submission", closed.getId())
                        .with(user(new LoginUser(student))).with(csrf())
                        .param("content", "강제 제출"))
                .andExpect(redirectedUrl("/student/assignments/" + closed.getId()))
                .andExpect(flash().attribute("errorMessage", SubmissionService.NOT_IN_PERIOD));

        assertThat(submissionRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    @DisplayName("S3 화면: 제출하면 성공 알림, 다시 제출하면 덮어쓰기 알림")
    void submitViaController() throws Exception {
        mockMvc.perform(multipart("/student/assignments/{id}/submission", open.getId())
                        .with(user(new LoginUser(student))).with(csrf())
                        .param("content", "1차"))
                .andExpect(redirectedUrl("/student/assignments/" + open.getId()))
                .andExpect(flash().attribute("successMessage", "제출했습니다."));

        mockMvc.perform(multipart("/student/assignments/{id}/submission", open.getId())
                        .with(user(new LoginUser(student))).with(csrf())
                        .param("content", "2차"))
                .andExpect(flash().attribute("successMessage", "다시 제출했습니다. 기존 제출 내용을 덮어썼습니다."));

        mockMvc.perform(get("/student/assignments/{id}", open.getId()).with(user(new LoginUser(student))))
                .andExpect(content().string(Matchers.containsString("제출 완료")))
                .andExpect(content().string(Matchers.containsString("다시 제출 (기존 제출 덮어쓰기)")));
    }

    @Test
    @DisplayName("S3 화면: 내용이 비어 있으면 입력칸 에러, 허용되지 않은 첨부는 첨부칸 에러")
    void submitValidation() throws Exception {
        mockMvc.perform(multipart("/student/assignments/{id}/submission", open.getId())
                        .with(user(new LoginUser(student))).with(csrf())
                        .param("content", " "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("submissionForm", "content"));

        mockMvc.perform(multipart("/student/assignments/{id}/submission", open.getId())
                        .file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", "MZ".getBytes()))
                        .with(user(new LoginUser(student))).with(csrf())
                        .param("content", "내용"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("submissionForm", "file"));

        assertThat(submissionRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    @DisplayName("내 과제 목록에 제출함 / 미제출이 표시된다")
    void listShowsSubmissionStatus() throws Exception {
        submissionService.submit(open.getId(), student.getId(), form("제출", null));

        mockMvc.perform(get("/student/assignments").with(user(new LoginUser(student))))
                .andExpect(content().string(Matchers.containsString("제출함")))
                .andExpect(content().string(Matchers.containsString("미제출")));
    }

    @Test
    @DisplayName("내 제출 첨부는 본인만 내려받을 수 있다")
    void downloadOwnSubmissionOnly() throws Exception {
        submissionService.submit(open.getId(), student.getId(), form("제출", file("mine.txt")));

        mockMvc.perform(get("/student/assignments/{id}/submission/file", open.getId()).with(user(new LoginUser(student))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/student/assignments/{id}/submission/file", open.getId()).with(user(new LoginUser(outsider))))
                .andExpect(status().isForbidden());
    }

    private SubmissionForm form(String content, MockMultipartFile file) {
        SubmissionForm form = new SubmissionForm();
        form.setContent(content);
        form.setFile(file);
        return form;
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", "data".getBytes(StandardCharsets.UTF_8));
    }
}
