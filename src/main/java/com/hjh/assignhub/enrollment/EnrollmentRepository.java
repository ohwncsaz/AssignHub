package com.hjh.assignhub.enrollment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hjh.assignhub.common.IdCount;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    // 강사 대시보드 — 강좌별 수강생 수
    @Query("select e.course.id as id, count(e) as total from Enrollment e "
            + "where e.course.instructor.id = :instructorId group by e.course.id")
    List<IdCount> countByCourseOfInstructor(@Param("instructorId") Long instructorId);

    Optional<Enrollment> findByCourseIdAndStudentId(Long courseId, Long studentId);

    @Query("select e from Enrollment e join fetch e.course c join fetch c.instructor "
            + "where e.student.id = :studentId order by e.joinedAt desc")
    List<Enrollment> findMyEnrollments(@Param("studentId") Long studentId);

    // I2 강좌별 등록 학생 — 먼저 등록한 순
    @Query("select e from Enrollment e join fetch e.student "
            + "where e.course.id = :courseId order by e.joinedAt asc")
    List<Enrollment> findStudentsOfCourse(@Param("courseId") Long courseId);
}
