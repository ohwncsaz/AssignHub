package com.hjh.assignhub.common;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.enrollment.MyCourseCardService;
import com.hjh.assignhub.user.Role;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final MyCourseCardService myCourseCardService;

    // 역할별 대시보드 — 카드 구성은 템플릿에서 sec:authorize로 분기
    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        // 학생: 수강 중인 강좌 카드 (내 강좌 화면과 같은 카드)
        if (loginUser != null && loginUser.getRole() == Role.STUDENT) {
            model.addAttribute("courseCards", myCourseCardService.findMyCourseCards(loginUser.getId()));
        }
        return "index";
    }
}
