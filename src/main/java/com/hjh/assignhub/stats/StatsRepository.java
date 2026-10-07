package com.hjh.assignhub.stats;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.hjh.assignhub.submission.Submission;

// I6 · S5 통계 전용 조회 — 집계는 DB에서 group by로 한 번에 계산
public interface StatsRepository extends Repository<Submission, Long> {

    // 과제별 평균 점수 · 채점 건수 (채점 완료된 제출만)
    @Query("select s.assignment.id as id, avg(s.score) as average, count(s) as graded from Submission s "
            + "where s.assignment.course.id = :courseId "
            + "and s.status = com.hjh.assignhub.submission.SubmissionStatus.GRADED "
            + "group by s.assignment.id")
    List<ScoreStat> scoreStatsByAssignment(@Param("courseId") Long courseId);

    // 강좌의 (과제, 제출 학생) 쌍 — 미제출자 계산용
    @Query("select s.assignment.id as assignmentId, s.student.id as studentId from Submission s "
            + "where s.assignment.course.id = :courseId")
    List<SubmittedPair> submittedPairs(@Param("courseId") Long courseId);

    // S5 내 평균 점수 — 배점이 과제마다 달라 "배점 대비 %"로 평균 (채점된 것이 없으면 null)
    @Query("select avg(s.score * 100.0 / s.assignment.maxScore) from Submission s "
            + "where s.student.id = :studentId "
            + "and s.status = com.hjh.assignhub.submission.SubmissionStatus.GRADED")
    Double myAverageScoreRate(@Param("studentId") Long studentId);

    interface SubmittedPair {

        Long getAssignmentId();

        Long getStudentId();
    }
}
