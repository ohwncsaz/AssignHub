package com.hjh.assignhub.assignment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 학생 화면(S2)용 조회 전용 Repository — 강사용 AssignmentRepository와 분리
public interface StudentAssignmentRepository extends Repository<Assignment, Long> {

    // 내가 수강 중인 강좌의 과제만
    @Query("select a from Assignment a join fetch a.course c "
            + "where c.id in (select e.course.id from Enrollment e where e.student.id = :studentId)")
    List<Assignment> findForStudent(@Param("studentId") Long studentId);

    @Query("select a from Assignment a join fetch a.course c join fetch c.instructor where a.id = :id")
    Optional<Assignment> findDetailById(@Param("id") Long id);
}
