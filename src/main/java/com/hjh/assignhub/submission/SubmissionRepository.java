package com.hjh.assignhub.submission;

import java.util.List;
import java.util.Optional;

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

    // B6 유지 — 이미 준 최고 점수보다 배점을 낮출 수 없게 (채점된 것이 없으면 null)
    @Query("select max(s.score) from Submission s where s.assignment.id = :assignmentId")
    Integer findMaxScoreByAssignmentId(@Param("assignmentId") Long assignmentId);

    // I4 과제별 제출물 (제출한 학생 정보 함께)
    @Query("select s from Submission s join fetch s.student where s.assignment.id = :assignmentId")
    List<Submission> findByAssignmentIdWithStudent(@Param("assignmentId") Long assignmentId);

    // I5 채점 화면 — 과제 · 강좌(본인 강좌 확인용) · 학생 정보 함께
    @Query("select s from Submission s join fetch s.assignment a join fetch a.course join fetch s.student "
            + "where s.id = :id")
    Optional<Submission> findDetailById(@Param("id") Long id);

    // B3 과제당 학생 1건 — 있으면 재제출(덮어쓰기)
    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    // 내 과제 목록의 제출 여부 표시용
    List<Submission> findByStudentId(Long studentId);

    // S4 내 제출 · 결과 — 과제 · 강좌 정보 함께, 최근 제출 순
    @Query("select s from Submission s join fetch s.assignment a join fetch a.course "
            + "where s.student.id = :studentId order by s.submittedAt desc")
    List<Submission> findMySubmissions(@Param("studentId") Long studentId);

    // 해당 강좌의 과제 중 이 학생이 제출한 것이 있는지 (수강생 내보내기 검사)
    boolean existsByStudentIdAndAssignmentCourseId(Long studentId, Long courseId);
}
