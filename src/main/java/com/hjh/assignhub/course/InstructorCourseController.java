package com.hjh.assignhub.course;

import java.util.List;

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

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.course.CourseOverviewService.AssignmentProgress;
import com.hjh.assignhub.enrollment.Enrollment;
import com.hjh.assignhub.enrollment.EnrollmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/instructor/courses")
@RequiredArgsConstructor
public class InstructorCourseController {

    private final CourseService courseService;
    private final CourseOverviewService courseOverviewService;
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
        Course course = courseService.getMyCourse(id, loginUser.getId()); // B5 본인 강좌만
        List<Enrollment> enrollments = enrollmentService.findStudentsOfCourse(course.getId()); // I2
        List<AssignmentProgress> progresses =
                courseOverviewService.findAssignmentProgress(course.getId(), enrollments.size());
        model.addAttribute("course", course);
        model.addAttribute("enrollments", enrollments);
        // 과제별 제출/미제출 인원·비율과 강좌 평균 제출률
        model.addAttribute("progresses", progresses);
        model.addAttribute("averageRate", courseOverviewService.averageSubmissionRate(progresses));
        return "instructor/courses/detail";
    }

    @PostMapping("/{id}/join-code")
    public String regenerateJoinCode(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id,
                                     RedirectAttributes redirectAttributes) {
        String joinCode = courseService.regenerateJoinCode(id, loginUser.getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "참여코드를 " + joinCode + "(으)로 재발급했습니다. 이전 코드로는 더 이상 수강 등록할 수 없습니다.");
        return "redirect:/instructor/courses/" + id;
    }
}
