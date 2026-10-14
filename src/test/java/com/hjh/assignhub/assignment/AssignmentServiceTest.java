package com.hjh.assignhub.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@Transactional
class AssignmentServiceTest {

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorage fileStorage;

    private User instructor;
    private User otherInstructor;
    private User student;
    private Course course;

    @BeforeEach
    void setUp() {
        instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        otherInstructor = userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2"));
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        course = courseRepository.save(new Course(instructor, "자바 스프링", "AB3K7XQ2"));
    }

    @Test
    @DisplayName("I3 과제를 첨부파일과 함께 등록한다")
    void create_withFile() {
        AssignmentForm form = form("1주차 과제", 0, 7);
        form.setFile(file("과제안내.pdf"));

        Long id = assignmentService.create(instructor.getId(), form);

        Assignment saved = assignmentRepository.findById(id).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("1주차 과제");
        assertThat(saved.getMaxScore()).isEqualTo(100);
        assertThat(saved.getFileName()).isEqualTo("과제안내.pdf");
        assertThat(fileStorage.load(saved.getFilePath()).exists()).isTrue();
    }

    @Test
    @DisplayName("B7 종료일시가 시작일시보다 뒤가 아니면 등록할 수 없다")
    void create_endNotAfterStart() {
        AssignmentForm form = form("과제", 0, 0);
        form.setEndAt(form.getStartAt());

        assertThatThrownBy(() -> assignmentService.create(instructor.getId(), form))
                .isInstanceOf(FormFieldException.class)
                .extracting("field").isEqualTo("endAt");
    }

    @Test
    @DisplayName("B5 다른 강사의 강좌에는 과제를 등록할 수 없다")
    void create_otherInstructorDenied() {
        assertThatThrownBy(() -> assignmentService.create(otherInstructor.getId(), form("과제", 0, 7)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("B5 다른 강사의 과제는 수정·삭제할 수 없다")
    void updateAndDelete_otherInstructorDenied() {
        Long id = assignmentService.create(instructor.getId(), form("과제", 0, 7));

        assertThatThrownBy(() -> assignmentService.update(id, otherInstructor.getId(), form("수정", 0, 7)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assignmentService.delete(id, otherInstructor.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("I3 수정 시 새 파일을 올리면 첨부가 교체되고, 첨부 삭제를 체크하면 제거된다")
    void update_replaceAndRemoveFile() {
        AssignmentForm createForm = form("과제", 0, 7);
        createForm.setFile(file("old.txt"));
        Long id = assignmentService.create(instructor.getId(), createForm);
        String oldPath = assignmentRepository.findById(id).orElseThrow().getFilePath();

        AssignmentForm replaceForm = form("수정된 과제", 1, 10);
        replaceForm.setFile(file("new.txt"));
        Assignment replaced = assignmentService.update(id, instructor.getId(), replaceForm);

        assertThat(replaced.getTitle()).isEqualTo("수정된 과제");
        assertThat(replaced.getFileName()).isEqualTo("new.txt");
        assertThatThrownBy(() -> fileStorage.load(oldPath)).isInstanceOf(ResponseStatusException.class);

        AssignmentForm removeForm = form("수정된 과제", 1, 10);
        removeForm.setRemoveFile(true);
        Assignment removed = assignmentService.update(id, instructor.getId(), removeForm);

        assertThat(removed.getFilePath()).isNull();
    }

    @Test
    @DisplayName("I3 제출물이 없는 과제는 삭제된다")
    void delete_success() {
        Long id = assignmentService.create(instructor.getId(), form("과제", 0, 7));

        assignmentService.delete(id, instructor.getId());

        assertThat(assignmentRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("B8 제출이 1건이라도 있는 과제는 삭제할 수 없다")
    void delete_withSubmissionDenied() {
        Long id = assignmentService.create(instructor.getId(), form("과제", 0, 7));
        submissionRepository.save(new Submission(assignmentRepository.findById(id).orElseThrow(), student, "제출", null));

        assertThatThrownBy(() -> assignmentService.delete(id, instructor.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제출물이 있어 삭제할 수 없습니다.");
        assertThat(assignmentRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("S2 상태 배지: 시작 전 예정, 시작~종료(경계 포함) 진행중, 종료 후 마감")
    void statusAt_boundaries() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 7, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 14, 23, 59);
        Assignment assignment = new Assignment(course, "과제", "내용", start, end, 100, null);

        assertThat(assignment.statusAt(start.minusMinutes(1))).isEqualTo(AssignmentStatus.UPCOMING);
        assertThat(assignment.statusAt(start)).isEqualTo(AssignmentStatus.OPEN);
        assertThat(assignment.statusAt(end)).isEqualTo(AssignmentStatus.OPEN);
        assertThat(assignment.statusAt(end.plusMinutes(1))).isEqualTo(AssignmentStatus.CLOSED);
    }

    private AssignmentForm form(String title, int startAfterDays, int endAfterDays) {
        AssignmentForm form = AssignmentForm.forNew(course.getId());
        LocalDateTime base = LocalDateTime.of(2026, 10, 7, 9, 0);
        form.setTitle(title);
        form.setContent("과제 내용");
        form.setStartAt(base.plusDays(startAfterDays));
        form.setEndAt(base.plusDays(endAfterDays).plusHours(1));
        return form;
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                "test".getBytes(StandardCharsets.UTF_8));
    }
}
