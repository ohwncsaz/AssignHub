package com.hjh.assignhub.submission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// 제출 첨부 미리보기 — 강사 채점 화면 · 학생 내 제출
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionPreviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private FileStorage fileStorage;

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
    private User student;
    private User otherStudent;
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        User instructorUser = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        instructor = new LoginUser(instructorUser);
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        otherStudent = userRepository.save(User.createStudent("s2@test.com", "pw", "학생2", "20260002"));
        Course course = courseRepository.save(new Course(instructorUser, "자바 스프링", "AB3K7XQ2"));
        enrollmentRepository.save(new Enrollment(course, student));
        enrollmentRepository.save(new Enrollment(course, otherStudent));
        LocalDateTime now = LocalDateTime.now();
        assignment = assignmentRepository.save(new Assignment(course, "과제", "내용", now.minusDays(1), now.plusDays(1), 100, null));
    }

    @Test
    @DisplayName("코드 파일은 채점 화면에 내용이 그대로 보이고, 파일 안의 <script>는 실행되지 않게 이스케이프된다")
    void textPreviewEscaped() throws Exception {
        Submission submission = submit(student, "Main.java",
                "public class Main {\n    // <script>alert(1)</script>\n}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/instructor/submissions/{id}/grade", submission.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("public class Main {")))
                .andExpect(content().string(Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<script>alert(1)</script>"))));
    }

    @Test
    @DisplayName("PDF는 화면 안(iframe)에 inline으로 열리고, 같은 사이트 iframe은 허용(SAMEORIGIN)된다")
    void pdfInline() throws Exception {
        Submission submission = submit(student, "report.pdf", "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/instructor/submissions/{id}/grade", submission.getId()).with(user(instructor)))
                .andExpect(content().string(Matchers.containsString("<iframe")))
                .andExpect(content().string(Matchers.containsString("/instructor/submissions/" + submission.getId() + "/preview")));

        mockMvc.perform(get("/instructor/submissions/{id}/preview", submission.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    @DisplayName("hwp 등 미지원 형식은 다운로드 안내만 보이고, 미리보기 주소는 404")
    void unsupportedType() throws Exception {
        Submission submission = submit(student, "과제.hwp", new byte[] {1, 2, 3});

        mockMvc.perform(get("/instructor/submissions/{id}/grade", submission.getId()).with(user(instructor)))
                .andExpect(content().string(Matchers.containsString("미리보기를 지원하지 않습니다")));
        mockMvc.perform(get("/instructor/submissions/{id}/preview", submission.getId()).with(user(instructor)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("B5 · 본인 제출: 다른 강사 · 다른 학생은 미리보기를 열 수 없다")
    void accessControl() throws Exception {
        Submission submission = submit(student, "photo.png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        mockMvc.perform(get("/instructor/submissions/{id}/preview", submission.getId()).with(user(otherInstructor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/student/assignments/{id}/submission/preview", assignment.getId()).with(user(new LoginUser(student))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        // 다른 학생은 이 과제에 제출물이 없으므로 자기 미리보기도 없음(남의 것은 볼 방법이 없음)
        mockMvc.perform(get("/student/assignments/{id}/submission/preview", assignment.getId()).with(user(new LoginUser(otherStudent))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("학생 내 제출에도 내가 낸 파일 미리보기가 보인다")
    void studentOwnPreview() throws Exception {
        submit(student, "answer.txt", "제 답안입니다".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/student/assignments/{id}", assignment.getId()).with(user(new LoginUser(student))))
                .andExpect(content().string(Matchers.containsString("미리보기")))
                .andExpect(content().string(Matchers.containsString("제 답안입니다")));
    }

    private Submission submit(User who, String fileName, byte[] bytes) {
        String path = fileStorage.store(new MockMultipartFile("file", fileName, "application/octet-stream", bytes), "submissions");
        return submissionRepository.save(new Submission(assignment, who, "내용", path));
    }
}
