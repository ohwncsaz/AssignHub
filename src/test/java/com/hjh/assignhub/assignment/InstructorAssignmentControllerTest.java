package com.hjh.assignhub.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstructorAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private LoginUser instructor;
    private LoginUser otherInstructor;
    private User student;
    private Course course;

    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        instructor = new LoginUser(instructorUser);
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
    }

    @Test
    @DisplayName("I3 과제를 첨부파일과 함께 등록하면 강좌 상세로 이동하고 목록에 보인다")
    void create_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "안내.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/instructor/assignments").file(file).with(user(instructor)).with(csrf())
                        .param("courseId", course.getId().toString())
                        .param("title", "1주차 과제")
                        .param("content", "회원가입 구현")
                        .param("startAt", "2026-10-07T09:00")
                        .param("endAt", "2026-10-14T23:59")
                        .param("maxScore", "100"))
                .andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
                .andExpect(flash().attribute("successMessage", "과제가 등록되었습니다."));

        mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("1주차 과제")));
    }

    @Test
    @DisplayName("B7 종료일시가 시작일시보다 앞이면 폼 에러를 표시한다")
    void create_invalidPeriod() throws Exception {
        mockMvc.perform(multipart("/instructor/assignments").with(user(instructor)).with(csrf())
                        .param("courseId", course.getId().toString())
                        .param("title", "과제")
                        .param("content", "내용")
                        .param("startAt", "2026-10-14T09:00")
                        .param("endAt", "2026-10-07T09:00")
                        .param("maxScore", "100"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("assignmentForm", "endAt"))
                .andExpect(content().string(Matchers.containsString("종료일시는 시작일시보다 뒤여야 합니다.")));

        assertThat(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(course.getId())).isEmpty();
    }

    @Test
    @DisplayName("I3 허용되지 않은 형식(.exe)을 첨부하면 첨부 입력칸에 에러를 표시하고 저장하지 않는다")
    void create_blockedFileType() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "setup.exe", "application/octet-stream",
                "MZ".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/instructor/assignments").file(exe).with(user(instructor)).with(csrf())
                        .param("courseId", course.getId().toString())
                        .param("title", "과제")
                        .param("content", "내용")
                        .param("startAt", "2026-10-07T09:00")
                        .param("endAt", "2026-10-14T23:59")
                        .param("maxScore", "100"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("assignmentForm", "file"))
                .andExpect(content().string(Matchers.containsString("허용되지 않는 파일 형식입니다.")));

        assertThat(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(course.getId())).isEmpty();
    }

    @Test
    @DisplayName("I3 수정 화면에 기존 값이 채워진다")
    void editForm_prefilled() throws Exception {
        Assignment assignment = saveAssignment();

        mockMvc.perform(get("/instructor/assignments/{id}/edit", assignment.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"기존 과제\"")))
                .andExpect(content().string(Matchers.containsString("2026-10-07T09:00")));
    }

    @Test
    @DisplayName("B5 다른 강사가 수정 화면에 접근하면 403")
    void editForm_otherInstructorForbidden() throws Exception {
        Assignment assignment = saveAssignment();

        mockMvc.perform(get("/instructor/assignments/{id}/edit", assignment.getId()).with(user(otherInstructor)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B8 제출물이 있는 과제를 삭제하면 알림을 띄우고 삭제하지 않는다")
    void delete_withSubmission() throws Exception {
        Assignment assignment = saveAssignment();
        submissionRepository.save(new Submission(assignment, student, "제출", null));

        mockMvc.perform(post("/instructor/assignments/{id}/delete", assignment.getId()).with(user(instructor)).with(csrf()))
                .andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
                .andExpect(flash().attribute("errorMessage", "제출물이 있어 삭제할 수 없습니다."));

        assertThat(assignmentRepository.findById(assignment.getId())).isPresent();
    }

    @Test
    @DisplayName("I3 첨부파일을 원래 이름(한글)으로 다운로드한다")
    void download_attachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "과제안내.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/instructor/assignments").file(file).with(user(instructor)).with(csrf())
                .param("courseId", course.getId().toString())
                .param("title", "첨부 과제")
                .param("content", "내용")
                .param("startAt", "2026-10-07T09:00")
                .param("endAt", "2026-10-14T23:59")
                .param("maxScore", "100"));
        Assignment saved = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(course.getId()).get(0);

        mockMvc.perform(get("/instructor/assignments/{id}/file", saved.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("filename*=UTF-8''")))
                .andExpect(content().string("hello"));
    }

    private Assignment saveAssignment() {
        return assignmentRepository.save(new Assignment(course, "기존 과제", "내용",
                LocalDateTime.of(2026, 10, 7, 9, 0),
                LocalDateTime.of(2026, 10, 14, 23, 59), 100, null));
    }
}
