package com.hjh.assignhub.enrollment;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseService;
import com.hjh.assignhub.submission.SubmissionRepository;
import com.hjh.assignhub.user.User;
import com.hjh.assignhub.user.UserRepository;
import com.hjh.assignhub.user.UserService;

import lombok.RequiredArgsConstructor;

// 강사의 수강생 관리 — 학생 추가(있으면 등록, 없으면 계정 생성) · 내보내기
@Service
@RequiredArgsConstructor
@Transactional
public class CourseStudentService {

    private final CourseService courseService;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final PasswordEncoder passwordEncoder;

    public record AddResult(StudentAddOutcome outcome, String studentName, String studentNo) {
    }

    // 학번으로 기존 계정을 찾는다 — 있으면 수강 등록만, 없으면 학생 계정(초기 비밀번호 = 학번)을 만들고 등록
    public AddResult addStudent(Long courseId, Long instructorId, StudentAddForm form) {
        Course course = courseService.getMyCourse(courseId, instructorId); // B5 본인 강좌만
        String studentNo = form.getStudentNo().trim();

        Optional<User> existing = userRepository.findByStudentNo(studentNo);
        User student;
        StudentAddOutcome outcome;
        if (existing.isPresent()) {
            student = existing.get();
            if (enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), student.getId())) {
                return new AddResult(StudentAddOutcome.ALREADY_ENROLLED, student.getName(), studentNo);
            }
            outcome = StudentAddOutcome.ENROLLED;
        } else {
            String email = UserService.normalizeEmail(form.getEmail());
            if (userRepository.existsByEmail(email)) {
                throw new FormFieldException("email", "이 이메일은 다른 학번의 계정이 이미 사용 중입니다.");
            }
            student = userRepository.save(User.createStudentByInstructor(
                    email, passwordEncoder.encode(studentNo), form.getName().trim(), studentNo));
            outcome = StudentAddOutcome.CREATED;
        }

        enrollmentRepository.save(new Enrollment(course, student));
        return new AddResult(outcome, student.getName(), studentNo);
    }

    // 수강생 내보내기 — 학생 계정은 그대로 두고 수강 등록만 삭제
    // 이미 제출물이 있으면 채점·통계 기록이 깨지므로 내보낼 수 없다 (B8과 같은 기준)
    public String removeStudent(Long courseId, Long instructorId, Long studentId) {
        courseService.getMyCourse(courseId, instructorId); // B5
        Enrollment enrollment = enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (submissionRepository.existsByStudentIdAndAssignmentCourseId(studentId, courseId)) {
            throw new BusinessException("제출물이 있는 학생은 내보낼 수 없습니다.");
        }
        enrollmentRepository.delete(enrollment);
        return enrollment.getStudent().getName();
    }
}
