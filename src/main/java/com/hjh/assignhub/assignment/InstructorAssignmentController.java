package com.hjh.assignhub.assignment;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FileDownload;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.CourseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/instructor/assignments")
@RequiredArgsConstructor
public class InstructorAssignmentController {

    private final AssignmentService assignmentService;
    private final CourseService courseService;
    private final FileStorage fileStorage;

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal LoginUser loginUser, @RequestParam Long courseId, Model model) {
        model.addAttribute("course", courseService.getMyCourse(courseId, loginUser.getId())); // B5
        model.addAttribute("assignmentForm", AssignmentForm.forNew(courseId));
        return "instructor/assignments/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser loginUser,
                         @Valid @ModelAttribute AssignmentForm assignmentForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                assignmentService.create(loginUser.getId(), assignmentForm);
                redirectAttributes.addFlashAttribute("successMessage", "과제가 등록되었습니다.");
                return "redirect:/instructor/courses/" + assignmentForm.getCourseId();
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "assignment", e.getMessage());
            }
        }
        model.addAttribute("course", courseService.getMyCourse(assignmentForm.getCourseId(), loginUser.getId()));
        return "instructor/assignments/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
        Assignment assignment = assignmentService.getMyAssignment(id, loginUser.getId()); // B5
        model.addAttribute("assignment", assignment);
        model.addAttribute("course", assignment.getCourse());
        model.addAttribute("assignmentForm", AssignmentForm.from(assignment));
        return "instructor/assignments/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@AuthenticationPrincipal LoginUser loginUser,
                         @PathVariable Long id,
                         @Valid @ModelAttribute AssignmentForm assignmentForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Assignment assignment = assignmentService.getMyAssignment(id, loginUser.getId()); // B5
        if (!bindingResult.hasErrors()) {
            try {
                assignmentService.update(id, loginUser.getId(), assignmentForm);
                redirectAttributes.addFlashAttribute("successMessage", "과제가 수정되었습니다.");
                return "redirect:/instructor/courses/" + assignment.getCourse().getId();
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "assignment", e.getMessage());
            }
        }
        model.addAttribute("assignment", assignment);
        model.addAttribute("course", assignment.getCourse());
        return "instructor/assignments/form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        Long courseId = assignmentService.getMyAssignment(id, loginUser.getId()).getCourse().getId(); // B5
        try {
            assignmentService.delete(id, loginUser.getId());
            redirectAttributes.addFlashAttribute("successMessage", "과제가 삭제되었습니다.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage()); // B8 알림
        }
        return "redirect:/instructor/courses/" + courseId;
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        Assignment assignment = assignmentService.getMyAssignment(id, loginUser.getId()); // B5
        return FileDownload.attachment(fileStorage.load(assignment.getFilePath()), assignment.getFileName());
    }
}
