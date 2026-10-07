package com.hjh.assignhub.config;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.course.JoinCodeGenerator;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.Role;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 시연용 데이터 — demo 프로필로 실행할 때만 동작 (평소 실행·테스트에는 영향 없음)
// 실행: ./gradlew bootRun --args='--spring.profiles.active=demo'
// 같은 이름의 데모 강좌가 이미 있으면 아무것도 하지 않으므로 여러 번 실행해도 중복되지 않는다
@Slf4j
@Component
@Profile("demo")
@Order(2)
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    static final String DEMO_COURSE_NAME = "자바 스프링 웹 개발 (데모)";
    static final String DEMO_PASSWORD = "password123";

    // 학번, 이름, 이메일 — 마지막 학생은 강좌에 등록하지 않음 (시연 중 수강 등록·학생 추가를 보여주기 위해)
    private static final String[][] STUDENTS = {
            {"20260101", "김민준", "demo1@test.com"},
            {"20260102", "이서연", "demo2@test.com"},
            {"20260103", "박지호", "demo3@test.com"},
            {"20260104", "최수아", "demo4@test.com"},
    };

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final JoinCodeGenerator joinCodeGenerator;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User instructor = userRepository.findFirstByRoleOrderByIdAsc(Role.INSTRUCTOR).orElse(null);
        if (instructor == null) {
            log.warn("강사 계정이 없어 데모 데이터를 만들지 않았습니다.");
            return;
        }
        boolean exists = courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId()).stream()
                .anyMatch(course -> course.getName().equals(DEMO_COURSE_NAME));
        if (exists) {
            return;
        }

        List<User> students = new ArrayList<>();
        for (String[] s : STUDENTS) {
            students.add(userRepository.findByStudentNo(s[0]).orElseGet(() ->
                    userRepository.save(User.createStudent(s[2], passwordEncoder.encode(DEMO_PASSWORD), s[1], s[0]))));
        }

        Course course = courseRepository.save(new Course(instructor, DEMO_COURSE_NAME, newJoinCode()));
        for (User student : students.subList(0, 3)) {
            enrollmentRepository.save(new Enrollment(course, student));
        }

        // 예정 · 진행중(여유 있음) · 진행중(마감 임박) · 마감 — S2 배지를 한 화면에서 모두 보여주기
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Assignment closed = assignment(course, "1주차 — 회원가입 구현",
                "학번 · 이름 · 이메일 · 비밀번호로 가입하는 기능을 구현하세요.\n중복 이메일은 가입할 수 없어야 합니다.",
                now.minusDays(14), now.minusDays(7), 100);
        Assignment open = assignment(course, "2주차 — 로그인 · 권한 처리",
                "Spring Security 폼 로그인과 역할별 접근 제어를 구현하세요.",
                now.minusDays(2), now.plusDays(5).withHour(23).withMinute(59), 100);
        assignment(course, "2주차 퀴즈 — Security 개념",
                "인증과 인가의 차이를 3줄 이내로 설명하세요.",
                now.minusDays(1), now.plusHours(3), 20);
        assignment(course, "3주차 — 과제 제출 기능",
                "제출 기간 검증(B1)과 재제출(B3)을 구현하세요.",
                now.plusDays(3), now.plusDays(10).withHour(23).withMinute(59), 100);

        submissionRepository.save(new Submission(closed, students.get(0), "회원가입 구현했습니다. 깃허브 링크 첨부합니다.", null));
        submissionRepository.save(new Submission(closed, students.get(1), "중복 이메일 검사까지 완료했습니다.", null));
        submissionRepository.save(new Submission(open, students.get(0), "로그인 구현 중간 제출합니다.", null));

        log.info("데모 데이터 생성: 강좌 [{}] 참여코드 {}, 학생 {}명(데모 비밀번호는 README 참고)",
                DEMO_COURSE_NAME, course.getJoinCode(), STUDENTS.length);
    }

    private Assignment assignment(Course course, String title, String content,
                                  LocalDateTime startAt, LocalDateTime endAt, int maxScore) {
        return assignmentRepository.save(new Assignment(course, title, content, startAt, endAt, maxScore, null));
    }

    private String newJoinCode() {
        String code;
        do {
            code = joinCodeGenerator.generate();
        } while (courseRepository.existsByJoinCode(code));
        return code;
    }
}
