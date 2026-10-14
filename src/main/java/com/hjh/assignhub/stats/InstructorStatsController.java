package com.hjh.assignhub.stats;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.course.Course;
import com.hjh.assignhub.stats.InstructorStatsService.AssignmentStat;
import com.hjh.assignhub.stats.InstructorStatsService.CourseStats;

import lombok.RequiredArgsConstructor;

// I6 통계 대시보드 — 강좌를 골라 과제별 제출률 · 평균 점수 차트와 미제출자 확인
@Controller
@RequiredArgsConstructor
public class InstructorStatsController {

    private final InstructorStatsService instructorStatsService;

    @GetMapping("/instructor/stats")
    public String stats(@AuthenticationPrincipal LoginUser loginUser,
                        @RequestParam(required = false) Long courseId,
                        Model model) {
        List<Course> courses = instructorStatsService.findMyCourses(loginUser.getId());
        model.addAttribute("courses", courses);
        // 강좌 id가 주소에 있으면 내 강좌가 하나도 없어도 반드시 권한부터 확인 — 남의 강좌 id는 getStats에서 403(B5)
        if (courseId == null && courses.isEmpty()) {
            return "instructor/stats";
        }
        // 강좌를 고르지 않았으면 가장 최근 강좌
        Long selectedId = courseId != null ? courseId : courses.get(0).getId();
        CourseStats stats = instructorStatsService.getStats(selectedId, loginUser.getId());
        model.addAttribute("stats", stats);

        // 차트 데이터 — 시작된 과제만 (Chart.js에 그대로 넘길 수 있게 단순한 리스트로)
        List<AssignmentStat> started = stats.assignments().stream().filter(AssignmentStat::isStarted).toList();
        model.addAttribute("chartLabels", started.stream().map(s -> s.assignment().getTitle()).toList());
        model.addAttribute("chartSubmissionRates", started.stream().map(AssignmentStat::getSubmittedRate).toList());
        model.addAttribute("chartScoreRates", started.stream().map(AssignmentStat::getAverageScoreRate).toList());
        model.addAttribute("chartScoreTexts", started.stream()
                .map(s -> s.getAverageScoreText() + " / " + s.assignment().getMaxScore() + "점").toList());
        return "instructor/stats";
    }
}
