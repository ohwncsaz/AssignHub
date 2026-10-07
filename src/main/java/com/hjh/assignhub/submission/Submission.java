package com.hjh.assignhub.submission;

import java.time.LocalDateTime;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// 제출·채점 기능(S3·I5)은 W3에서 구현 — 지금은 B8(제출물 있는 과제 삭제 불가) 검사를 위해 ERD대로 테이블만 둔다
// B3 과제당 학생 1건은 DB 제약으로도 막는다
@Entity
@Table(name = "submission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "student_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    private Integer score;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    public Submission(Assignment assignment, User student, String content, String filePath) {
        this.assignment = assignment;
        this.student = student;
        this.content = content;
        this.filePath = filePath;
        this.submittedAt = LocalDateTime.now();
        this.status = SubmissionStatus.SUBMITTED;
    }

    // B3 기간 내 재제출 — 새 행을 만들지 않고 기존 제출을 덮어쓴다 (제출일시도 갱신)
    public void resubmit(String content, String filePath) {
        this.content = content;
        this.filePath = filePath;
        this.submittedAt = LocalDateTime.now();
    }

    // I5 채점 — 채점하면 GRADED가 되어 재제출할 수 없다(B4)
    public void grade(int score, String feedback) {
        this.score = score;
        this.feedback = feedback;
        this.status = SubmissionStatus.GRADED;
        this.gradedAt = LocalDateTime.now();
    }

    public boolean isGraded() {
        return status == SubmissionStatus.GRADED;
    }

    public String getFileName() {
        if (filePath == null) {
            return null;
        }
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }
}
