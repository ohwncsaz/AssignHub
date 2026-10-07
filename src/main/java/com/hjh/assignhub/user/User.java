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

    // ERD 추가 컬럼 — 강사가 만든 학생 계정은 초기 비밀번호(학번)로 시작하므로 첫 로그인 때 변경을 강제한다
    // 기존 행이 있는 DB에 컬럼을 추가해도 실패하지 않도록 DB 기본값 false 지정
    @Column(name = "password_change_required", nullable = false, columnDefinition = "boolean default false")
    private boolean passwordChangeRequired;

    // ERD 추가 컬럼 — 프로필 사진 (업로드 폴더 기준 상대 경로, 없으면 null)
    @Column(name = "profile_image_path", length = 500)
    private String profileImagePath;

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

    // 강사가 수강생 추가로 만든 학생 계정 — 첫 로그인 때 비밀번호 변경 필요
    public static User createStudentByInstructor(String email, String encodedInitialPassword, String name, String studentNo) {
        User student = new User(email, encodedInitialPassword, name, studentNo, Role.STUDENT);
        student.passwordChangeRequired = true;
        return student;
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

    public void changeProfileImage(String profileImagePath) {
        this.profileImagePath = profileImagePath;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangeRequired = false;
    }
}
