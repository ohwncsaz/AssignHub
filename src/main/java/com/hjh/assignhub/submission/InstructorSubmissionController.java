package com.hjh.assignhub.submission;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FileDownload;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.common.FormFieldException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// I4 제출 현황 · I5 채점 · 피드백
@Controller
@RequiredArgsConstructor
public class InstructorSubmissionController {

    private final GradingService gradingService;
    private final FileStorage fileStorage;

    @GetMapping("/instructor/assignments/{assignmentId}/submissions")
    public String board(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long assignmentId, Model model) {
        model.addAttribute("board", gradingService.getBoard(assignmentId, loginUser.getId())); // B5
        return "instructor/submissions/list";
    }

    @GetMapping("/instructor/submissions/{id}/grade")
    public String gradeForm(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
        Submission submission = gradingService.getForGrading(id, loginUser.getId()); // B5
        model.addAttribute("submission", submission);
        model.addAttribute("gradeForm", GradeForm.from(submission));
        return "instructor/submissions/grade";
    }

    @PostMapping("/instructor/submissions/{id}/grade")
    public String grade(@AuthenticationPrincipal LoginUser loginUser,
                        @PathVariable Long id,
                        @Valid @ModelAttribute GradeForm gradeForm,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        Submission submission = gradingService.getForGrading(id, loginUser.getId()); // B5
        if (!bindingResult.hasErrors()) {
            try {
                gradingService.grade(id, loginUser.getId(), gradeForm);
                redirectAttributes.addFlashAttribute("successMessage",
                        submission.getStudent().getName() + " 학생의 제출물을 채점했습니다.");
                return "redirect:/instructor/assignments/" + submission.getAssignment().getId() + "/submissions";
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "grade", e.getMessage()); // B6 폼 에러
            }
        }
        model.addAttribute("submission", submission);
        return "instructor/submissions/grade";
    }

    @GetMapping("/instructor/submissions/{id}/file")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        Submission submission = gradingService.getForGrading(id, loginUser.getId()); // B5
        return FileDownload.attachment(fileStorage.load(submission.getFilePath()), submission.getFileName());
    }
}
