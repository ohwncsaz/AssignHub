package com.hjh.assignhub.submission;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hjh.assignhub.auth.LoginUser;

import lombok.RequiredArgsConstructor;

// S4 결과 확인 — 내 제출물의 상태 · 점수 · 피드백 (로그인한 본인 것만)
@Controller
@RequiredArgsConstructor
public class StudentSubmissionController {

    private final SubmissionService submissionService;

    @GetMapping("/student/submissions")
    public String mySubmissions(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        List<Submission> submissions = submissionService.findMySubmissions(loginUser.getId());
        model.addAttribute("submissions", submissions);
        model.addAttribute("gradedCount", submissions.stream().filter(Submission::isGraded).count());
        return "student/submissions/list";
    }
}
