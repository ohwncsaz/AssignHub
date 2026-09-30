package com.hjh.assignhub.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

// Security의 accessDeniedPage는 원래 요청 메서드(GET/POST) 그대로 forward 하므로 @RequestMapping으로 모든 메서드를 받는다
@Controller
public class ErrorPageController {

    @RequestMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }

    @RequestMapping("/error/404")
    public String notFound() {
        return "error/404";
    }
}
