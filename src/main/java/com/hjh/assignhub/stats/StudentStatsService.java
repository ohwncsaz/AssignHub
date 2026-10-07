package com.hjh.assignhub.stats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.assignment.Assignment;
import com.hjh.assignhub.assignment.AssignmentStatus;
import com.hjh.assignhub.assignment.StudentAssignmentService;
import com.hjh.assignhub.submission.SubmissionService;
import com.hjh.assignhub.submission.SubmissionStatus;

import lombok.RequiredArgsConstructor;

// S5 내 통계 — 제출률 · 평균 점수 · 마감 임박(미제출) 과제
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentStatsService {

    static final int DUE_SOON_LIMIT = 5;

    private final StudentAssignmentService studentAssignmentService;
    private final SubmissionService submissionService;
    private final StatsRepository statsRepository;

    // submissionRate · averageScoreRate는 계산할 대상이 없으면 null (화면에 "-")
    public record StudentStats(long started, long submitted, Integer submissionRate,
                               long graded, Integer averageScoreRate,
                               long todo, List<Assignment> dueSoon) {
    }

    public StudentStats getStats(Long studentId) {
        LocalDateTime now = LocalDateTime.now();
        // 수강 중인 강좌의 과제 — 진행중(마감 빠른 순) → 예정 → 마감 순으로 정렬돼 있다
        List<Assignment> assignments = studentAssignmentService.findMyAssignments(studentId);
        Map<Long, SubmissionStatus> mine = submissionService.findMyStatusByAssignment(studentId);

        // 제출률 = 시작된 과제 중 제출한 비율 (예정 과제는 아직 낼 수 없으므로 제외)
        List<Assignment> started = assignments.stream()
                .filter(a -> a.statusAt(now) != AssignmentStatus.UPCOMING).toList();
        long submitted = started.stream().filter(a -> mine.containsKey(a.getId())).count();
        Integer submissionRate = started.isEmpty() ? null : (int) Math.round(submitted * 100.0 / started.size());

        long graded = mine.values().stream().filter(s -> s == SubmissionStatus.GRADED).count();
        Double average = statsRepository.myAverageScoreRate(studentId);

        // 해야 할 과제 = 진행중인데 아직 안 낸 과제 (마감 빠른 순)
        List<Assignment> todo = assignments.stream()
                .filter(a -> a.statusAt(now) == AssignmentStatus.OPEN && !mine.containsKey(a.getId()))
                .toList();

        return new StudentStats(started.size(), submitted, submissionRate, graded,
                average == null ? null : (int) Math.round(average),
                todo.size(), todo.stream().limit(DUE_SOON_LIMIT).toList());
    }
}
