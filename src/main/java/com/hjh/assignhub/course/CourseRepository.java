package com.hjh.assignhub.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);

    boolean existsByJoinCode(String joinCode);

    Optional<Course> findByJoinCode(String joinCode);
}
