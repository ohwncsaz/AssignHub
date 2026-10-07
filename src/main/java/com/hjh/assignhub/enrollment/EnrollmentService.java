package com.hjh.assignhub.enrollment;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // B2 해당 강좌에 수강 등록된 학생인지
    public boolean isEnrolled(Long courseId, Long studentId) {
        return enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId);
    }
}
