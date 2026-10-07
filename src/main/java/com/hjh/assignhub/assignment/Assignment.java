package com.hjh.assignhub.assignment;

import java.time.LocalDateTime;

import com.hjh.assignhub.course.Course;

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
@Table(name = "assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 강좌 1 : N 과제
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    // @Lob은 PostgreSQL에서 oid 타입이 되므로 text로 직접 지정
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    // 업로드 폴더 기준 상대 경로 (assignments/{uuid}/{원본파일명})
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Assignment(Course course, String title, String content,
                      LocalDateTime startAt, LocalDateTime endAt, int maxScore, String filePath) {
        this.course = course;
        this.title = title;
        this.content = content;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxScore = maxScore;
        this.filePath = filePath;
        this.createdAt = LocalDateTime.now();
    }

    public void update(String title, String content, LocalDateTime startAt, LocalDateTime endAt, int maxScore) {
        this.title = title;
        this.content = content;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxScore = maxScore;
    }

    public void changeFile(String filePath) {
        this.filePath = filePath;
    }

    // 시작일시 ≤ 현재 ≤ 종료일시 이면 진행중 (B1과 같은 기준)
    public AssignmentStatus statusAt(LocalDateTime now) {
        if (now.isBefore(startAt)) {
            return AssignmentStatus.UPCOMING;
        }
        if (now.isAfter(endAt)) {
            return AssignmentStatus.CLOSED;
        }
        return AssignmentStatus.OPEN;
    }

    public AssignmentStatus getStatus() {
        return statusAt(LocalDateTime.now());
    }

    public String getFileName() {
        if (filePath == null) {
            return null;
        }
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }
}
