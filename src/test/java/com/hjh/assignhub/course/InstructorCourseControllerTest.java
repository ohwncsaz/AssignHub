package com.hjh.assignhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstructorCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private LoginUser instructor;
    private LoginUser otherInstructor;

    @BeforeEach
    void setUp() {
        instructor = new LoginUser(userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1")));
        otherInstructor = new LoginUser(userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2")));
    }

    @Test
    @DisplayName("I1 강좌를 개설하면 상세 화면으로 이동하고 참여코드가 보인다")
    void create_redirectsToDetail() throws Exception {
        mockMvc.perform(post("/instructor/courses").with(user(instructor)).with(csrf())
                        .param("name", "자바 스프링"))
                .andExpect(redirectedUrlPattern("/instructor/courses/*"));

        Course course = courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId()).get(0);
        mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(course.getJoinCode())));
    }

    @Test
    @DisplayName("I1 강좌명이 비어 있으면 폼 에러를 표시한다")
    void create_blankName() throws Exception {
        mockMvc.perform(post("/instructor/courses").with(user(instructor)).with(csrf())
                        .param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("courseForm", "name"));

        assertThat(courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId())).isEmpty();
    }

    @Test
    @DisplayName("I1 강좌 목록 화면에 내 강좌만 보인다")
    void list_showsOnlyMine() throws Exception {
        courseService.create(instructor.getId(), form("내 강좌"));
        courseService.create(otherInstructor.getId(), form("남의 강좌"));

        mockMvc.perform(get("/instructor/courses").with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("내 강좌")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("남의 강좌"))));
    }

    @Test
    @DisplayName("I1 다른 강사의 강좌 주소를 직접 입력하면 403")
    void detail_otherInstructorForbidden() throws Exception {
        Long otherCourseId = courseService.create(otherInstructor.getId(), form("남의 강좌"));

        mockMvc.perform(get("/instructor/courses/{id}", otherCourseId).with(user(instructor)))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error/403"));
    }

    @Test
    @DisplayName("I2 강좌 상세에 등록한 학생만 학번·이름·이메일과 함께 보인다")
    void detail_showsEnrolledStudents() throws Exception {
        Long courseId = courseService.create(instructor.getId(), form("내 강좌"));
        Long otherCourseId = courseService.create(instructor.getId(), form("다른 강좌"));
        Course course = courseRepository.findById(courseId).orElseThrow();
        Course otherCourse = courseRepository.findById(otherCourseId).orElseThrow();
        User enrolled = userRepository.save(User.createStudent("kim@test.com", "pw", "김학생", "20260011"));
        User notEnrolled = userRepository.save(User.createStudent("lee@test.com", "pw", "이학생", "20260022"));
        enrollmentRepository.save(new Enrollment(course, enrolled));
        enrollmentRepository.save(new Enrollment(otherCourse, notEnrolled));

        mockMvc.perform(get("/instructor/courses/{id}", courseId).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("20260011")))
                .andExpect(content().string(Matchers.containsString("김학생")))
                .andExpect(content().string(Matchers.containsString("kim@test.com")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("이학생"))));
    }

    @Test
    @DisplayName("I2 등록한 학생이 없으면 참여코드 공유 안내를 보여준다")
    void detail_noStudents() throws Exception {
        Long courseId = courseService.create(instructor.getId(), form("빈 강좌"));

        mockMvc.perform(get("/instructor/courses/{id}", courseId).with(user(instructor)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("아직 등록한 학생이 없습니다.")));
    }

    @Test
    @DisplayName("강좌 삭제: 제출물이 없으면 과제 · 수강 등록과 함께 삭제되고 학생 계정은 남는다")
    void delete_withoutSubmissions() throws Exception {
        Long courseId = courseService.create(instructor.getId(), form("지울 강좌"));
        Course course = courseRepository.findById(courseId).orElseThrow();
        User student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        enrollmentRepository.save(new Enrollment(course, student));
        assignmentRepository.save(new Assignment(course, "과제", "내용",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, null));

        mockMvc.perform(post("/instructor/courses/{id}/delete", courseId).with(user(instructor)).with(csrf()))
                .andExpect(redirectedUrl("/instructor/courses"))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(courseRepository.findById(courseId)).isEmpty();
        assertThat(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId)).isEmpty();
        assertThat(enrollmentRepository.findStudentsOfCourse(courseId)).isEmpty();
        assertThat(userRepository.findById(student.getId())).isPresent();
    }

    @Test
    @DisplayName("강좌 삭제: 제출물이 1건이라도 있으면 삭제되지 않고 알림을 띄운다 (B8과 같은 기준)")
    void delete_blockedWhenSubmitted() throws Exception {
        Long courseId = courseService.create(instructor.getId(), form("제출 있는 강좌"));
        Course course = courseRepository.findById(courseId).orElseThrow();
        User student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        enrollmentRepository.save(new Enrollment(course, student));
        Assignment assignment = assignmentRepository.save(new Assignment(course, "과제", "내용",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 100, null));
        submissionRepository.save(new Submission(assignment, student, "답안", null));

        mockMvc.perform(post("/instructor/courses/{id}/delete", courseId).with(user(instructor)).with(csrf()))
                .andExpect(redirectedUrl("/instructor/courses/" + courseId))
                .andExpect(flash().attribute("errorMessage", "제출물이 있는 강좌는 삭제할 수 없습니다."));

        assertThat(courseRepository.findById(courseId)).isPresent();
    }

    @Test
    @DisplayName("강좌 삭제: 다른 강사의 강좌는 403 (B5)")
    void delete_otherInstructorForbidden() throws Exception {
        Long courseId = courseService.create(instructor.getId(), form("남의 강좌"));

        mockMvc.perform(post("/instructor/courses/{id}/delete", courseId).with(user(otherInstructor)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(courseRepository.findById(courseId)).isPresent();
    }

    @Test
    @DisplayName("강좌 상세에 강좌 삭제 버튼이 있고, 강좌명은 확인창 스크립트에 직접 들어가지 않는다")
    void detail_hasDeleteButton() throws Exception {
        Long courseId = courseService.create(instructor.getId(), form("');alert(1);//"));

        mockMvc.perform(get("/instructor/courses/{id}", courseId).with(user(instructor)))
                .andExpect(content().string(Matchers.containsString("/instructor/courses/" + courseId + "/delete")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("');alert(1);//"))));
    }

    private CourseForm form(String name) {
        CourseForm form = new CourseForm();
        form.setName(name);
        return form;
    }
}
