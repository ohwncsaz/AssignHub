package com.hjh.assignhub.enrollment;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseRepository;
import com.hjh.assignhub.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    // S1 참여코드로 수강 등록
    @Transactional
    public Course join(Long studentId, String joinCode) {
        String code = joinCode.trim().toUpperCase(Locale.ROOT);
        Course course = courseRepository.findByJoinCode(code)
                .orElseThrow(() -> new FormFieldException("joinCode", "존재하지 않는 참여코드입니다."));
        if (enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), studentId)) {
            throw new FormFieldException("joinCode", "이미 수강 중인 강좌입니다.");
        }
        enrollmentRepository.save(new Enrollment(course, userRepository.getReferenceById(studentId)));
        return course;
    }

    public List<Enrollment> findMyEnrollments(Long studentId) {
        return enrollmentRepository.findMyEnrollments(studentId);
    }

    // I2 수강생 목록 — 본인 강좌 확인(B5)은 호출하는 쪽에서 CourseService.getMyCourse로 먼저 한다
    public List<Enrollment> findStudentsOfCourse(Long courseId) {
        return enrollmentRepository.findStudentsOfCourse(courseId);
    }

    // 학생의 강좌 상세 — B2 수강 등록한 강좌만 볼 수 있다 (없는 강좌는 404, 남의 강좌는 403)
    public Enrollment getMyEnrollment(Long courseId, Long studentId) {
        return enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> courseRepository.existsById(courseId)
                        ? new AccessDeniedException("수강 등록한 강좌만 볼 수 있습니다.")
                        : new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // B2 해당 강좌에 수강 등록된 학생인지
    public boolean isEnrolled(Long courseId, Long studentId) {
        return enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId);
    }
}
