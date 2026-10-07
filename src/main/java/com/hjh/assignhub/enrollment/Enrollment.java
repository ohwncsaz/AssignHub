package com.hjh.assignhub.enrollment;

import java.time.LocalDateTime;

import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// course N : M users(학생) 중간 테이블 — 같은 강좌 중복 수강은 DB 제약으로도 막는다
@Entity
@Table(name = "enrollment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "student_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    public Enrollment(Course course, User student) {
        this.course = course;
        this.student = student;
        this.joinedAt = LocalDateTime.now();
    }
}
