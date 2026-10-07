package com.hjh.assignhub.assignment;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.FileDownload;
import com.hjh.assignhub.common.FileStorage;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/student/assignments")
@RequiredArgsConstructor
public class StudentAssignmentController {

    private final StudentAssignmentService studentAssignmentService;
    private final FileStorage fileStorage;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("assignments", studentAssignmentService.findMyAssignments(loginUser.getId()));
        return "student/assignments/list";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
        model.addAttribute("assignment", studentAssignmentService.getMyAssignment(id, loginUser.getId())); // B2
        return "student/assignments/detail";
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        Assignment assignment = studentAssignmentService.getMyAssignment(id, loginUser.getId()); // B2
        return FileDownload.attachment(fileStorage.load(assignment.getFilePath()), assignment.getFileName());
    }
}
