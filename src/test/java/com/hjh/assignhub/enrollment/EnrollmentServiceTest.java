package com.hjh.assignhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@Transactional
class EnrollmentServiceTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    private User student;
    private Course course;

    @BeforeEach
    void setUp() {
        User instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        student = userRepository.save(User.createStudent("s1@test.com", "pw", "학생1", "20260001"));
        course = courseRepository.save(new Course(instructor, "자바 스프링", "AB3K7XQ2"));
    }

    @Test
    @DisplayName("S1 참여코드로 수강 등록한다 (앞뒤 공백·소문자 입력도 허용)")
    void join_success() {
        Course joined = enrollmentService.join(student.getId(), "  ab3k7xq2 ");

        assertThat(joined.getId()).isEqualTo(course.getId());
        assertThat(enrollmentService.isEnrolled(course.getId(), student.getId())).isTrue();
        assertThat(enrollmentService.findMyEnrollments(student.getId())).hasSize(1);
    }

    @Test
    @DisplayName("S1 존재하지 않는 참여코드는 등록할 수 없다")
    void join_unknownCode() {
        assertThatThrownBy(() -> enrollmentService.join(student.getId(), "ZZZZZZZZ"))
                .isInstanceOf(FormFieldException.class)
                .hasMessage("존재하지 않는 참여코드입니다.");
    }

    @Test
    @DisplayName("S1 같은 강좌에 중복 등록할 수 없다")
    void join_duplicate() {
        enrollmentService.join(student.getId(), "AB3K7XQ2");

        assertThatThrownBy(() -> enrollmentService.join(student.getId(), "AB3K7XQ2"))
                .isInstanceOf(FormFieldException.class)
                .hasMessage("이미 수강 중인 강좌입니다.");
    }
}
