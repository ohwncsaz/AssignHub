package com.hjh.assignhub.course;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.assignment.AssignmentService;
import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.enrollment.EnrollmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/instructor/courses")
@RequiredArgsConstructor
public class InstructorCourseController {

    private final CourseService courseService;
    private final AssignmentService assignmentService;
    private final EnrollmentService enrollmentService;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("courseForm", new CourseForm());
        model.addAttribute("courses", courseService.findMyCourses(loginUser.getId()));
        return "instructor/courses/list";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser loginUser,
                         @Valid @ModelAttribute CourseForm courseForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("courses", courseService.findMyCourses(loginUser.getId()));
            return "instructor/courses/list";
        }
        Long courseId = courseService.create(loginUser.getId(), courseForm);
        redirectAttributes.addFlashAttribute("successMessage", "강좌가 개설되었습니다. 참여코드를 학생들에게 공유하세요.");
        return "redirect:/instructor/courses/" + courseId;
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
        Course course = courseService.getMyCourse(id, loginUser.getId());
        model.addAttribute("course", course);
        model.addAttribute("assignments", assignmentService.findByCourse(course.getId()));
        model.addAttribute("enrollments", enrollmentService.findStudentsOfCourse(course.getId())); // I2
        return "instructor/courses/detail";
    }
}
