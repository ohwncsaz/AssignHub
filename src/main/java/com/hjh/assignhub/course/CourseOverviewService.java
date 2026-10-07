package com.hjh.assignhub.course;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentRepository;
import com.hjh.assignhub.assignment.AssignmentStatus;
import com.hjh.assignhub.common.IdCount;
import com.hjh.assignhub.enrollment.EnrollmentRepository;
import com.hjh.assignhub.submission.SubmissionRepository;

import lombok.RequiredArgsConstructor;

// 강사용 강좌 요약 — 대시보드 강좌 카드, 강좌 상세의 과제별 제출 현황
// 본인 강좌인지(B5)는 호출하는 쪽에서 먼저 확인한다 (CourseService.getMyCourse)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseOverviewService {

    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;

    // 대시보드 강좌 카드 — submissionRate는 시작된 과제가 없거나 수강생이 없으면 null(표시 안 함)
    public record CourseOverview(Course course, long students, long assignments, long open, Integer submissionRate) {
    }

    // 과제별 제출 현황 — 제출/미제출 인원과 비율(%)
    public record AssignmentProgress(Assignment assignment, long students, long submitted) {

        public long getNotSubmitted() {
            return Math.max(students - submitted, 0);
        }

        public int getSubmittedRate() {
            return students == 0 ? 0 : (int) Math.round(submitted * 100.0 / students);
        }

        public int getNotSubmittedRate() {
            return students == 0 ? 0 : 100 - getSubmittedRate();
        }

        // 시작 전 과제는 아직 제출할 수 없으므로 "미제출"로 세지 않고 "시작 전"으로 표시
        public boolean isStarted() {
            return assignment.getStatus() != AssignmentStatus.UPCOMING;
        }
    }

    public List<CourseOverview> findMyCourseOverviews(Long instructorId) {
        Map<Long, Long> students = toMap(enrollmentRepository.countByCourseOfInstructor(instructorId));
        Map<Long, Long> submissions = toMap(submissionRepository.countByCourseOfInstructor(instructorId));
        Map<Long, List<Assignment>> assignmentsByCourse = assignmentRepository.findByCourseInstructorId(instructorId)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getCourse().getId()));

        return courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId).stream()
                .map(course -> {
                    long studentCount = students.getOrDefault(course.getId(), 0L);
                    List<Assignment> assignments = assignmentsByCourse.getOrDefault(course.getId(), List.of());
                    long started = assignments.stream().filter(a -> a.getStatus() != AssignmentStatus.UPCOMING).count();
                    long open = assignments.stream().filter(a -> a.getStatus() == AssignmentStatus.OPEN).count();
                    // 평균 제출률 = 제출물 수 / (수강생 수 × 시작된 과제 수)
                    long expected = studentCount * started;
                    Integer rate = expected == 0 ? null
                            : (int) Math.round(submissions.getOrDefault(course.getId(), 0L) * 100.0 / expected);
                    return new CourseOverview(course, studentCount, assignments.size(), open, rate);
                })
                .toList();
    }

    public List<AssignmentProgress> findAssignmentProgress(Long courseId, long studentCount) {
        Map<Long, Long> submitted = toMap(submissionRepository.countByAssignmentInCourse(courseId));
        return assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(a -> new AssignmentProgress(a, studentCount, submitted.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    // 강좌 전체 평균 제출률 (시작된 과제 기준) — 없으면 null
    public Integer averageSubmissionRate(List<AssignmentProgress> progresses) {
        List<AssignmentProgress> started = progresses.stream().filter(AssignmentProgress::isStarted).toList();
        long expected = started.stream().mapToLong(AssignmentProgress::students).sum();
        if (expected == 0) {
            return null;
        }
        long submitted = started.stream().mapToLong(AssignmentProgress::submitted).sum();
        return (int) Math.round(submitted * 100.0 / expected);
    }

    private Map<Long, Long> toMap(List<IdCount> counts) {
        return counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getTotal));
    }
}
