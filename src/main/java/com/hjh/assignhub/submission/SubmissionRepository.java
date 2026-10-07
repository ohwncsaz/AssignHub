package com.hjh.assignhub.submission;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hjh.assignhub.common.IdCount;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    // 강좌 상세 — 과제별 제출 인원 (과제당 학생 1건이므로 제출물 수 = 제출 인원)
    @Query("select s.assignment.id as id, count(s) as total from Submission s "
            + "where s.assignment.course.id = :courseId group by s.assignment.id")
    List<IdCount> countByAssignmentInCourse(@Param("courseId") Long courseId);

    // 강사 대시보드 — 강좌별 전체 제출물 수
    @Query("select s.assignment.course.id as id, count(s) as total from Submission s "
            + "where s.assignment.course.instructor.id = :instructorId group by s.assignment.course.id")
    List<IdCount> countByCourseOfInstructor(@Param("instructorId") Long instructorId);

    boolean existsByAssignmentId(Long assignmentId);

    // 해당 강좌의 과제 중 이 학생이 제출한 것이 있는지 (수강생 내보내기 검사)
    boolean existsByStudentIdAndAssignmentCourseId(Long studentId, Long courseId);
}
