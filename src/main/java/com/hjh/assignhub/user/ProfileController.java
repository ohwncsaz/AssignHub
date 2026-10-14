package com.hjh.assignhub.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginSessionRefresher;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FormFieldException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 강사·학생 공통 — 항상 로그인한 본인의 정보만 수정 (다른 사용자 id를 받지 않는다)
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final LoginSessionRefresher loginSessionRefresher;

    @GetMapping
    public String profileForm(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        User user = userService.getUser(loginUser.getId());
        model.addAttribute("user", user);
        model.addAttribute("profileForm", ProfileForm.from(user));
        return "profile/edit";
    }

    @PostMapping
    public String updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                @Valid @ModelAttribute ProfileForm profileForm,
                                BindingResult bindingResult,
                                Model model,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                User updated = userService.updateProfile(loginUser.getId(), profileForm);
                loginSessionRefresher.refresh(updated, request, response);
                redirectAttributes.addFlashAttribute("successMessage", "프로필이 수정되었습니다.");
                return "redirect:/profile";
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "profile", e.getMessage());
            }
        }
        model.addAttribute("user", userService.getUser(loginUser.getId()));
        return "profile/edit";
    }

    @GetMapping("/password")
    public String passwordForm(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "profile/password";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                 @Valid @ModelAttribute PasswordChangeForm passwordChangeForm,
                                 BindingResult bindingResult,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                User updated = userService.changePassword(loginUser.getId(), passwordChangeForm);
                loginSessionRefresher.refresh(updated, request, response);
                redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
                return "redirect:/profile";
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "password", e.getMessage());
            }
        }
        return "profile/password";
    }
}
