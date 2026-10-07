package com.hjh.assignhub.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// user는 PostgreSQL 예약어라 테이블명을 users로 지정
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    // 강사는 학번이 없으므로 nullable
    @Column(name = "student_no", length = 20)
    private String studentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private User(String email, String password, String name, String studentNo, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.studentNo = studentNo;
        this.role = role;
    }

    public static User createStudent(String email, String encodedPassword, String name, String studentNo) {
        return new User(email, encodedPassword, name, studentNo, Role.STUDENT);
    }

    public static User createInstructor(String email, String encodedPassword, String name) {
        return new User(email, encodedPassword, name, null, Role.INSTRUCTOR);
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    // 프로필 수정 — 강사는 학번이 없으므로 학생일 때만 학번을 바꾼다
    public void updateProfile(String name, String email, String studentNo) {
        this.name = name;
        this.email = email;
        if (isStudent()) {
            this.studentNo = studentNo;
        }
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
