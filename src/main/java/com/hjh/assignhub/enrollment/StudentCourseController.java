package com.hjh.assignhub.enrollment;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.Course;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/student/courses")
@RequiredArgsConstructor
public class StudentCourseController {

    private final EnrollmentService enrollmentService;

    @GetMapping("/join")
    public String joinForm(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("joinForm", new JoinForm());
        model.addAttribute("enrollments", enrollmentService.findMyEnrollments(loginUser.getId()));
        return "student/courses/join";
    }

    @PostMapping("/join")
    public String join(@AuthenticationPrincipal LoginUser loginUser,
                       @Valid @ModelAttribute JoinForm joinForm,
                       BindingResult bindingResult,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Course course = enrollmentService.join(loginUser.getId(), joinForm.getJoinCode());
                redirectAttributes.addFlashAttribute("successMessage",
                        "[" + course.getName() + "] 강좌에 수강 등록되었습니다.");
                return "redirect:/student/courses/join";
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "join", e.getMessage());
            }
        }
        model.addAttribute("enrollments", enrollmentService.findMyEnrollments(loginUser.getId()));
        return "student/courses/join";
    }
}
