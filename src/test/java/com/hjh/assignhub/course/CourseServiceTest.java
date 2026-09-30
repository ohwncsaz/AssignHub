package com.hjh.assignhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

@SpringBootTest
@Transactional
class CourseServiceTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    private User instructor;
    private User otherInstructor;

    @BeforeEach
    void setUp() {
        instructor = userRepository.save(User.createInstructor("t1@test.com", "pw", "강사1"));
        otherInstructor = userRepository.save(User.createInstructor("t2@test.com", "pw", "강사2"));
    }

    @Test
    @DisplayName("I1 강좌를 개설하면 8자리 참여코드가 자동 발급된다")
    void create_issuesJoinCode() {
        Long id = courseService.create(instructor.getId(), form("  자바 스프링  "));

        Course course = courseRepository.findById(id).orElseThrow();
        assertThat(course.getName()).isEqualTo("자바 스프링");
        assertThat(course.getJoinCode()).matches("[A-Z2-9]{8}");
        assertThat(course.isOwnedBy(instructor.getId())).isTrue();
    }

    @Test
    @DisplayName("I1 참여코드는 강좌마다 서로 다르다")
    void create_joinCodesAreUnique() {
        List<String> codes = IntStream.range(0, 30)
                .mapToObj(i -> courseService.create(instructor.getId(), form("강좌" + i)))
                .map(id -> courseRepository.findById(id).orElseThrow().getJoinCode())
                .toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("I1 내 강좌 목록에는 본인이 개설한 강좌만 나온다")
    void findMyCourses_onlyMine() {
        courseService.create(instructor.getId(), form("내 강좌"));
        courseService.create(otherInstructor.getId(), form("남의 강좌"));

        assertThat(courseService.findMyCourses(instructor.getId()))
                .extracting(Course::getName)
                .containsExactly("내 강좌");
    }

    @Test
    @DisplayName("I1 다른 강사의 강좌 상세는 볼 수 없다 (403)")
    void getMyCourse_otherInstructorDenied() {
        Long id = courseService.create(otherInstructor.getId(), form("남의 강좌"));

        assertThatThrownBy(() -> courseService.getMyCourse(id, instructor.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("I1 없는 강좌는 404")
    void getMyCourse_notFound() {
        assertThatThrownBy(() -> courseService.getMyCourse(999_999L, instructor.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private CourseForm form(String name) {
        CourseForm form = new CourseForm();
        form.setName(name);
        return form;
    }
}
