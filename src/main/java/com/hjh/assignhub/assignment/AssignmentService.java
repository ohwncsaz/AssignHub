package com.hjh.assignhub.assignment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseService;
import com.hjh.assignhub.submission.SubmissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private static final String FILE_CATEGORY = "assignments";

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseService courseService;
    private final FileStorage fileStorage;

    // I3 과제 등록
    @Transactional
    public Long create(Long instructorId, AssignmentForm form) {
        Course course = courseService.getMyCourse(form.getCourseId(), instructorId); // B5 본인 강좌만
        validatePeriod(form);                                                          // B7

        String filePath = form.hasFile() ? fileStorage.store(form.getFile(), FILE_CATEGORY) : null;
        Assignment assignment = new Assignment(course, form.getTitle().trim(), form.getContent(),
                form.getStartAt(), form.getEndAt(), form.getMaxScore(), filePath);
        return assignmentRepository.save(assignment).getId();
    }

    // I3 과제 수정 — 새 파일을 올리면 교체, '첨부 삭제'를 체크하면 제거
    @Transactional
    public Assignment update(Long assignmentId, Long instructorId, AssignmentForm form) {
        Assignment assignment = getMyAssignment(assignmentId, instructorId); // B5
        validatePeriod(form);                                                 // B7

        // B6 유지 — 이미 채점한 최고 점수보다 배점을 낮추면 "점수 ≤ 배점"이 깨지므로 막는다
        Integer maxGivenScore = submissionRepository.findMaxScoreByAssignmentId(assignmentId);
        if (maxGivenScore != null && form.getMaxScore() < maxGivenScore) {
            throw new FormFieldException("maxScore",
                    "이미 " + maxGivenScore + "점으로 채점된 제출물이 있어 배점을 " + maxGivenScore + "점보다 낮출 수 없습니다.");
        }

        assignment.update(form.getTitle().trim(), form.getContent(),
                form.getStartAt(), form.getEndAt(), form.getMaxScore());

        String oldFilePath = assignment.getFilePath();
        if (form.hasFile()) {
            assignment.changeFile(fileStorage.store(form.getFile(), FILE_CATEGORY));
            fileStorage.delete(oldFilePath);
        } else if (form.isRemoveFile()) {
            assignment.changeFile(null);
            fileStorage.delete(oldFilePath);
        }
        return assignment;
    }

    // I3 과제 삭제
    @Transactional
    public void delete(Long assignmentId, Long instructorId) {
        Assignment assignment = getMyAssignment(assignmentId, instructorId); // B5
        if (submissionRepository.existsByAssignmentId(assignmentId)) {       // B8
            throw new BusinessException("제출물이 있어 삭제할 수 없습니다.");
        }
        assignmentRepository.delete(assignment);
        fileStorage.delete(assignment.getFilePath());
    }

    // B5 강사는 본인 강좌의 과제만 수정·삭제 — 다른 강사의 과제 id를 넣으면 403
    public Assignment getMyAssignment(Long assignmentId, Long instructorId) {
        Assignment assignment = assignmentRepository.findWithCourseById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!assignment.getCourse().isOwnedBy(instructorId)) {
            throw new AccessDeniedException("본인 강좌의 과제만 관리할 수 있습니다.");
        }
        return assignment;
    }

    public List<Assignment> findByCourse(Long courseId) {
        return assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
    }

    // B7 종료일시는 시작일시보다 뒤
    private void validatePeriod(AssignmentForm form) {
        if (!form.getEndAt().isAfter(form.getStartAt())) {
            throw new FormFieldException("endAt", "종료일시는 시작일시보다 뒤여야 합니다.");
        }
    }
}
