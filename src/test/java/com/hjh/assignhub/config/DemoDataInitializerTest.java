package com.hjh.assignhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.assignment.AssignmentStatus;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.Role;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

// demo 프로필 전용 컨텍스트 — 다른 테스트와 DB가 섞이지 않도록 별도 H2 DB(demo)를 사용
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class DemoDataInitializerTest {

    @Autowired
    private DemoDataInitializer demoDataInitializer;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("demo 프로필로 실행하면 강좌·학생·상태별 과제·제출물이 만들어지고, 다시 실행해도 중복되지 않는다")
    void createsDemoDataOnce() {
        Course course = demoCourse();
        List<Assignment> assignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(course.getId());

        assertThat(enrollmentRepository.findStudentsOfCourse(course.getId())).hasSize(3);
        assertThat(assignments).extracting(Assignment::getStatus)
                .contains(AssignmentStatus.UPCOMING, AssignmentStatus.OPEN, AssignmentStatus.CLOSED);
        assertThat(submissionRepository.count()).isEqualTo(3);

        User demoStudent = userRepository.findByStudentNo("20260101").orElseThrow();
        assertThat(passwordEncoder.matches(DemoDataInitializer.DEMO_PASSWORD, demoStudent.getPassword())).isTrue();
        assertThat(demoStudent.isPasswordChangeRequired()).isFalse();

        // 한 번 더 실행 — 강좌·학생·과제가 늘어나지 않아야 한다
        long users = userRepository.count();
        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(courseRepository.findAll()).filteredOn(c -> c.getName().equals(DemoDataInitializer.DEMO_COURSE_NAME)).hasSize(1);
        assertThat(assignmentRepository.findByCourseIdOrderByCreatedAtDesc(course.getId())).hasSize(assignments.size());
    }

    private Course demoCourse() {
        User instructor = userRepository.findFirstByRoleOrderByIdAsc(Role.INSTRUCTOR).orElseThrow();
        return courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId()).stream()
                .filter(c -> c.getName().equals(DemoDataInitializer.DEMO_COURSE_NAME))
                .findFirst().orElseThrow();
    }
}
