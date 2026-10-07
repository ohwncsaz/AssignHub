package com.hjh.assignhub.course;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    // 8자리 코드의 경우의 수가 매우 커서 충돌은 드물지만, 무한 루프는 막아둔다
    private static final int MAX_JOIN_CODE_ATTEMPTS = 10;

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final JoinCodeGenerator joinCodeGenerator;

    // I1 강좌 개설 — 참여코드 자동 발급 (중복 없음)
    @Transactional
    public Long create(Long instructorId, CourseForm form) {
        User instructor = userRepository.getReferenceById(instructorId);
        Course course = new Course(instructor, form.getName().trim(), newJoinCode());
        return courseRepository.save(course).getId();
    }

    public List<Course> findMyCourses(Long instructorId) {
        return courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
    }

    // 참여코드 재발급 — 코드가 유출됐을 때 사용
    @Transactional
    public String regenerateJoinCode(Long courseId, Long instructorId) {
        Course course = getMyCourse(courseId, instructorId);
        course.changeJoinCode(newJoinCode());
        return course.getJoinCode();
    }

    // 본인 강좌만 조회 가능 — 다른 강사의 강좌 id를 주소창에 넣어도 403
    public Course getMyCourse(Long courseId, Long instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!course.isOwnedBy(instructorId)) {
            throw new AccessDeniedException("본인 강좌만 조회할 수 있습니다.");
        }
        return course;
    }

    private String newJoinCode() {
        for (int i = 0; i < MAX_JOIN_CODE_ATTEMPTS; i++) {
            String code = joinCodeGenerator.generate();
            if (!courseRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("참여코드 생성에 실패했습니다. 다시 시도해 주세요.");
    }
}
