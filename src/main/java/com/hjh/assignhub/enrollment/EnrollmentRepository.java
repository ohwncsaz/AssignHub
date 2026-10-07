package com.hjh.assignhub.enrollment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    @Query("select e from Enrollment e join fetch e.course c join fetch c.instructor "
            + "where e.student.id = :studentId order by e.joinedAt desc")
    List<Enrollment> findMyEnrollments(@Param("studentId") Long studentId);
}
