package com.hjh.assignhub.course;

import java.time.LocalDateTime;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 강사 1 : N 강좌
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "join_code", nullable = false, unique = true, length = 20)
    private String joinCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Course(User instructor, String name, String joinCode) {
        this.instructor = instructor;
        this.name = name;
        this.joinCode = joinCode;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long instructorId) {
        return instructor.getId().equals(instructorId);
    }

    // 참여코드 재발급 — 기존 코드는 더 이상 수강 등록에 쓸 수 없다 (이미 등록한 학생은 그대로)
    public void changeJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }
}
