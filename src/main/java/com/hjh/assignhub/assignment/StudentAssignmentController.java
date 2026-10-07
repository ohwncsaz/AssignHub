package com.hjh.assignhub.assignment;

import java.time.LocalDateTime;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FileDownload;
import com.hjh.assignhub.common.FileStorage;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.submission.Submission;
import com.hjh.assignhub.submission.SubmissionForm;
import com.hjh.assignhub.submission.SubmissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/student/assignments")
@RequiredArgsConstructor
public class StudentAssignmentController {

    private final StudentAssignmentService studentAssignmentService;
    private final SubmissionService submissionService;
    private final FileStorage fileStorage;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("assignments", studentAssignmentService.findMyAssignments(loginUser.getId()));
        model.addAttribute("submissionStatus", submissionService.findMyStatusByAssignment(loginUser.getId()));
        return "student/assignments/list";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
        Assignment assignment = studentAssignmentService.getMyAssignment(id, loginUser.getId()); // B2
        Submission submission = submissionService.findMySubmission(id, loginUser.getId()).orElse(null);
        model.addAttribute("submissionForm", submission == null ? new SubmissionForm() : SubmissionForm.from(submission));
        addDetailModel(model, assignment, submission);
        return "student/assignments/detail";
    }

    // S3 제출 · 재제출 — 기간(B1) · 수강 여부(B2) · 덮어쓰기(B3) · 채점 완료(B4)는 SubmissionService에서 검사
    @PostMapping("/{id}/submission")
    public String submit(@AuthenticationPrincipal LoginUser loginUser,
                         @PathVariable Long id,
                         @Valid @ModelAttribute SubmissionForm submissionForm,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Assignment assignment = studentAssignmentService.getMyAssignment(id, loginUser.getId()); // B2
        if (!bindingResult.hasErrors()) {
            try {
                boolean resubmit = submissionService.findMySubmission(id, loginUser.getId()).isPresent();
                submissionService.submit(id, loginUser.getId(), submissionForm);
                redirectAttributes.addFlashAttribute("successMessage",
                        resubmit ? "다시 제출했습니다. 기존 제출 내용을 덮어썼습니다." : "제출했습니다.");
                return "redirect:/student/assignments/" + id;
            } catch (BusinessException e) {
                // B1 · B4 위반 — 화면 상단 알림
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/student/assignments/" + id;
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "submission", e.getMessage());
            }
        }
        addDetailModel(model, assignment, submissionService.findMySubmission(id, loginUser.getId()).orElse(null));
        return "student/assignments/detail";
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        Assignment assignment = studentAssignmentService.getMyAssignment(id, loginUser.getId()); // B2
        return FileDownload.attachment(fileStorage.load(assignment.getFilePath()), assignment.getFileName());
    }

    // 내가 제출한 첨부파일 다운로드 (본인 제출만)
    @GetMapping("/{id}/submission/file")
    public ResponseEntity<Resource> downloadMySubmission(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        Submission submission = submissionService.getMySubmissionWithFile(id, loginUser.getId());
        return FileDownload.attachment(fileStorage.load(submission.getFilePath()), submission.getFileName());
    }

    // 제출 가능 여부는 화면 표시용(버튼 비활성화) — 실제 차단은 SubmissionService
    private void addDetailModel(Model model, Assignment assignment, Submission submission) {
        boolean open = assignment.statusAt(LocalDateTime.now()) == AssignmentStatus.OPEN;
        boolean graded = submission != null && submission.isGraded();
        model.addAttribute("assignment", assignment);
        model.addAttribute("submission", submission);
        model.addAttribute("canSubmit", open && !graded);
        model.addAttribute("blockedReason", graded ? "채점이 완료되어 다시 제출할 수 없습니다."
                : !open ? "지금은 제출 기간이 아닙니다." : null);
    }
}
