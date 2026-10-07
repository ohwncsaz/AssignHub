package com.hjh.assignhub.assignment;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.enrollment.EnrollmentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAssignmentService {

    private final StudentAssignmentRepository studentAssignmentRepository;
    private final EnrollmentService enrollmentService;

    // S2 내 과제 목록 — 진행중(마감 빠른 순) → 예정(시작 빠른 순) → 마감(최근 마감 순)
    public List<Assignment> findMyAssignments(Long studentId) {
        LocalDateTime now = LocalDateTime.now();
        return studentAssignmentRepository.findForStudent(studentId).stream()
                .sorted(displayOrder(now))
                .toList();
    }

    // 내 강좌 상세 — 그 강좌의 과제만 (정렬은 내 과제 목록과 같음)
    public List<Assignment> findMyAssignmentsInCourse(Long studentId, Long courseId) {
        return findMyAssignments(studentId).stream()
                .filter(a -> a.getCourse().getId().equals(courseId))
                .toList();
    }

    // B2 해당 강좌에 수강 등록된 학생만 과제 열람 — 아니면 403
    public Assignment getMyAssignment(Long assignmentId, Long studentId) {
        Assignment assignment = studentAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!enrollmentService.isEnrolled(assignment.getCourse().getId(), studentId)) {
            throw new AccessDeniedException("수강 등록된 강좌의 과제만 볼 수 있습니다.");
        }
        return assignment;
    }

    private Comparator<Assignment> displayOrder(LocalDateTime now) {
        Comparator<Assignment> byStatus = Comparator.comparingInt(a -> switch (a.statusAt(now)) {
            case OPEN -> 0;
            case UPCOMING -> 1;
            case CLOSED -> 2;
        });
        Comparator<Assignment> withinStatus = (a, b) -> switch (a.statusAt(now)) {
            case OPEN -> a.getEndAt().compareTo(b.getEndAt());
            case UPCOMING -> a.getStartAt().compareTo(b.getStartAt());
            case CLOSED -> b.getEndAt().compareTo(a.getEndAt());
        };
        return byStatus.thenComparing(withinStatus);
    }
}
