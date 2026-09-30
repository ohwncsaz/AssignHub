package com.hjh.assignhub.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // 역할별 대시보드 — 카드 구성은 템플릿에서 sec:authorize로 분기
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
