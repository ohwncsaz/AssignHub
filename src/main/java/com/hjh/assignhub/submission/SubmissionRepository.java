package com.hjh.assignhub.submission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    boolean existsByAssignmentId(Long assignmentId);

    // 해당 강좌의 과제 중 이 학생이 제출한 것이 있는지 (수강생 내보내기 검사)
    boolean existsByStudentIdAndAssignmentCourseId(Long studentId, Long courseId);
}
