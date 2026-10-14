package com.hjh.assignhub.assignment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    // 강사 대시보드 — 내 모든 강좌의 과제
    List<Assignment> findByCourseInstructorId(Long instructorId);

    @Query("select a from Assignment a join fetch a.course where a.id = :id")
    Optional<Assignment> findWithCourseById(@Param("id") Long id);
}
