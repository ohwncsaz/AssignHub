package com.hjh.assignhub.submission;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentStatus;
import com.hjh.assignhub.assignment.StudentAssignmentService;
import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.user.UserRepository;

import lombok.RequiredArgsConstructor;

// S3 과제 제출 · 재제출 — 모든 규칙(B1~B4)을 서버에서 검사
// (화면의 제출 버튼 비활성화는 편의일 뿐, 개발자도구로 강제 요청해도 여기서 막힌다)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionService {

    public static final String NOT_IN_PERIOD = "제출 기간이 아닙니다.";
    public static final String ALREADY_GRADED = "채점 완료된 과제는 다시 제출할 수 없습니다.";
    private static final String FILE_CATEGORY = "submissions";

    private final SubmissionRepository submissionRepository;
    private final StudentAssignmentService studentAssignmentService;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    @Transactional
    public Submission submit(Long assignmentId, Long studentId, SubmissionForm form) {
        Assignment assignment = studentAssignmentService.getMyAssignment(assignmentId, studentId); // B2 수강생만

        // B1 시작일시 ≤ 현재 ≤ 종료일시 일 때만
        if (assignment.statusAt(LocalDateTime.now()) != AssignmentStatus.OPEN) {
            throw new BusinessException(NOT_IN_PERIOD);
        }

        Optional<Submission> existing = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        // B4 채점 완료(GRADED)된 제출은 재제출 불가
        if (existing.isPresent() && existing.get().isGraded()) {
            throw new BusinessException(ALREADY_GRADED);
        }

        String newFilePath = form.hasFile() ? fileStorage.store(form.getFile(), FILE_CATEGORY) : null;

        // B3 과제당 학생 1건 — 기간 내 재제출은 기존 제출을 덮어쓴다
        if (existing.isPresent()) {
            Submission submission = existing.get();
            String oldFilePath = submission.getFilePath();
            String filePath = newFilePath != null ? newFilePath : (form.isRemoveFile() ? null : oldFilePath);
            submission.resubmit(form.getContent(), filePath);
            if (oldFilePath != null && !Objects.equals(oldFilePath, filePath)) {
                fileStorage.delete(oldFilePath);
            }
            return submission;
        }
        return submissionRepository.save(new Submission(
                assignment, userRepository.getReferenceById(studentId), form.getContent(), newFilePath));
    }

    public Optional<Submission> findMySubmission(Long assignmentId, Long studentId) {
        return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
    }

    // 내 과제 목록 · 강좌 상세의 제출 여부 표시 — 과제 id → 제출 상태
    public Map<Long, SubmissionStatus> findMyStatusByAssignment(Long studentId) {
        return submissionRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), Submission::getStatus));
    }

    // 내 제출 첨부파일 — B2 수강생만, 본인 제출만
    public Submission getMySubmissionWithFile(Long assignmentId, Long studentId) {
        studentAssignmentService.getMyAssignment(assignmentId, studentId); // B2
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (submission.getFilePath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return submission;
    }
}
