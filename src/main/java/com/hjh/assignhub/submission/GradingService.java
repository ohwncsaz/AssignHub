package com.hjh.assignhub.submission;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentService;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.enrollment.EnrollmentService;
import com.hjh.assignhub.user.User;

import lombok.RequiredArgsConstructor;

// I4 제출 현황 · I5 채점 — 강사는 본인 강좌의 과제만(B5), 점수는 0 이상 배점 이하(B6)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradingService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentService assignmentService;
    private final EnrollmentService enrollmentService;

    // 제출 현황 표의 한 줄 — 미제출이면 submission이 null
    public record StudentSubmission(User student, Submission submission) {

        public boolean isSubmitted() {
            return submission != null;
        }
    }

    public record SubmissionBoard(Assignment assignment, List<StudentSubmission> rows) {

        public long getStudents() {
            return rows.size();
        }

        public long getSubmitted() {
            return rows.stream().filter(StudentSubmission::isSubmitted).count();
        }

        public long getNotSubmitted() {
            return getStudents() - getSubmitted();
        }

        public long getGraded() {
            return rows.stream().filter(r -> r.isSubmitted() && r.submission().isGraded()).count();
        }

        public int getSubmittedRate() {
            return getStudents() == 0 ? 0 : (int) Math.round(getSubmitted() * 100.0 / getStudents());
        }
    }

    // I4 과제별 제출 현황 — 수강생 전원 기준 (제출자 + 미제출자)
    public SubmissionBoard getBoard(Long assignmentId, Long instructorId) {
        Assignment assignment = assignmentService.getMyAssignment(assignmentId, instructorId); // B5
        Map<Long, Submission> byStudent = submissionRepository.findByAssignmentIdWithStudent(assignmentId).stream()
                .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));

        List<StudentSubmission> rows = enrollmentService.findStudentsOfCourse(assignment.getCourse().getId()).stream()
                .map(e -> new StudentSubmission(e.getStudent(), byStudent.get(e.getStudent().getId())))
                .toList();
        return new SubmissionBoard(assignment, rows);
    }

    // I5 채점 화면 — B5 본인 강좌의 제출물만 (다른 강사의 제출물 id를 넣으면 403)
    public Submission getForGrading(Long submissionId, Long instructorId) {
        Submission submission = submissionRepository.findDetailById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!submission.getAssignment().getCourse().isOwnedBy(instructorId)) {
            throw new AccessDeniedException("본인 강좌의 제출물만 채점할 수 있습니다.");
        }
        return submission;
    }

    // I5 채점 · 피드백 — 다시 채점하면 점수만 바뀐다. 채점하면 학생은 재제출할 수 없다(B4)
    @Transactional
    public Submission grade(Long submissionId, Long instructorId, GradeForm form) {
        Submission submission = getForGrading(submissionId, instructorId); // B5
        int maxScore = submission.getAssignment().getMaxScore();
        // B6 점수는 0 이상 배점(max_score) 이하
        if (form.getScore() < 0 || form.getScore() > maxScore) {
            throw new FormFieldException("score", "점수는 0점 이상 배점(" + maxScore + "점) 이하로 입력하세요.");
        }
        String feedback = form.getFeedback() == null || form.getFeedback().isBlank() ? null : form.getFeedback().trim();
        submission.grade(form.getScore(), feedback);
        return submission;
    }
}
