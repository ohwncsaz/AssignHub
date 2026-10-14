package com.hjh.assignhub.stats;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentService;
import com.hjh.assignhub.assignment.AssignmentStatus;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.course.CourseService;
import com.hjh.assignhub.enrollment.EnrollmentService;
import com.hjh.assignhub.stats.StatsRepository.SubmittedPair;
import com.hjh.assignhub.user.User;

import lombok.RequiredArgsConstructor;

// I6 통계 대시보드 — 과제별 제출률 · 평균 점수, 학생별 미제출 과제
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorStatsService {

    private final CourseService courseService;
    private final AssignmentService assignmentService;
    private final EnrollmentService enrollmentService;
    private final StatsRepository statsRepository;

    public record AssignmentStat(Assignment assignment, long students, long submitted, long graded, Double averageScore) {

        // 시작 전 과제는 제출률 · 미제출 계산에서 뺀다
        public boolean isStarted() {
            return assignment.getStatus() != AssignmentStatus.UPCOMING;
        }

        public int getSubmittedRate() {
            return students == 0 ? 0 : (int) Math.round(submitted * 100.0 / students);
        }

        // 평균 점수를 배점 대비 %로 (과제마다 배점이 달라도 차트에서 비교 가능) — 채점이 없으면 null
        public Integer getAverageScoreRate() {
            return averageScore == null ? null : (int) Math.round(averageScore * 100.0 / assignment.getMaxScore());
        }

        public String getAverageScoreText() {
            return averageScore == null ? "-" : String.format("%.1f", averageScore);
        }
    }

    // 미제출자 — 시작된 과제 중 안 낸 과제 목록
    public record MissingStudent(User student, List<Assignment> missing) {
    }

    public record CourseStats(Course course, long students, List<AssignmentStat> assignments,
                              Integer averageSubmissionRate, Integer averageScoreRate,
                              List<MissingStudent> missingStudents) {
    }

    public List<Course> findMyCourses(Long instructorId) {
        return courseService.findMyCourses(instructorId);
    }

    public CourseStats getStats(Long courseId, Long instructorId) {
        Course course = courseService.getMyCourse(courseId, instructorId); // B5 본인 강좌만
        List<User> students = enrollmentService.findStudentsOfCourse(courseId).stream()
                .map(e -> e.getStudent()).toList();
        // 오래된 과제부터 (차트 x축이 시간 순서가 되도록)
        List<Assignment> assignments = assignmentService.findByCourse(courseId).stream()
                .sorted(Comparator.comparing(Assignment::getStartAt))
                .toList();

        // group by 집계: 과제별 평균 점수 · 채점 건수
        Map<Long, ScoreStat> scores = statsRepository.scoreStatsByAssignment(courseId).stream()
                .collect(Collectors.toMap(ScoreStat::getId, Function.identity()));
        // 과제별 제출한 학생 id
        Map<Long, Set<Long>> submittedBy = statsRepository.submittedPairs(courseId).stream()
                .collect(Collectors.groupingBy(SubmittedPair::getAssignmentId,
                        Collectors.mapping(SubmittedPair::getStudentId, Collectors.toSet())));

        List<AssignmentStat> stats = assignments.stream()
                .map(a -> {
                    ScoreStat score = scores.get(a.getId());
                    return new AssignmentStat(a, students.size(),
                            submittedBy.getOrDefault(a.getId(), Set.of()).size(),
                            score == null ? 0 : score.getGraded(),
                            score == null ? null : score.getAverage());
                })
                .toList();

        return new CourseStats(course, students.size(), stats,
                averageSubmissionRate(stats), averageScoreRate(stats),
                missingStudents(students, stats, submittedBy));
    }

    // 시작된 과제 기준 평균 제출률 = 제출 합 ÷ (수강생 × 시작된 과제 수)
    private Integer averageSubmissionRate(List<AssignmentStat> stats) {
        List<AssignmentStat> started = stats.stream().filter(AssignmentStat::isStarted).toList();
        long expected = started.stream().mapToLong(AssignmentStat::students).sum();
        if (expected == 0) {
            return null;
        }
        return (int) Math.round(started.stream().mapToLong(AssignmentStat::submitted).sum() * 100.0 / expected);
    }

    // 채점된 과제들의 배점 대비 평균 점수(%)의 평균
    private Integer averageScoreRate(List<AssignmentStat> stats) {
        List<Integer> rates = stats.stream().map(AssignmentStat::getAverageScoreRate).filter(r -> r != null).toList();
        if (rates.isEmpty()) {
            return null;
        }
        return (int) Math.round(rates.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    // 미제출 과제가 많은 학생부터
    private List<MissingStudent> missingStudents(List<User> students, List<AssignmentStat> stats,
                                                 Map<Long, Set<Long>> submittedBy) {
        List<Assignment> started = stats.stream().filter(AssignmentStat::isStarted).map(AssignmentStat::assignment).toList();
        return students.stream()
                .map(student -> new MissingStudent(student, started.stream()
                        .filter(a -> !submittedBy.getOrDefault(a.getId(), Set.of()).contains(student.getId()))
                        .toList()))
                .filter(m -> !m.missing().isEmpty())
                .sorted(Comparator.comparing((MissingStudent m) -> m.missing().size()).reversed()
                        .thenComparing(m -> m.student().getStudentNo()))
                .toList();
    }
}
