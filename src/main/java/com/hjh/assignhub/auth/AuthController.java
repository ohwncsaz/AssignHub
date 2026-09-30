package com.hjh.assignhub.auth;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.user.SignupException;
import com.hjh.assignhub.user.SignupForm;
import com.hjh.assignhub.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // 로그인 처리(POST /login)는 Spring Security가 담당하고, 여기서는 화면만 보여준다
    @GetMapping("/login")
    public String loginForm(Authentication authentication) {
        if (authentication != null) {
            return "redirect:/";
        }
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupForm(Authentication authentication, Model model) {
        if (authentication != null) {
            return "redirect:/";
        }
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupForm signupForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }
        try {
            userService.signup(signupForm);
        } catch (SignupException e) {
            bindingResult.rejectValue(e.getField(), "signup", e.getMessage());
            return "auth/signup";
        }
        redirectAttributes.addFlashAttribute("successMessage", "회원가입이 완료되었습니다. 로그인해 주세요.");
        return "redirect:/login";
    }
}
