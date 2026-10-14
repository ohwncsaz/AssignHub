package com.hjh.assignhub.enrollment;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hjh.assignhub.auth.LoginUser;
import com.hjh.assignhub.common.BusinessException;
import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.CourseService;
import com.hjh.assignhub.enrollment.CourseStudentService.AddResult;
import com.hjh.assignhub.enrollment.StudentImportService.RowResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 강사의 수강생 관리 — 1명 추가 · 엑셀 대량 추가 · 내보내기
@Controller
@RequestMapping("/instructor/courses/{courseId}/students")
@RequiredArgsConstructor
public class InstructorCourseStudentController {

    private final CourseService courseService;
    private final CourseStudentService courseStudentService;
    private final StudentImportService studentImportService;
    private final StudentExcel studentExcel;

    @GetMapping("/new")
    public String addForm(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long courseId, Model model) {
        model.addAttribute("course", courseService.getMyCourse(courseId, loginUser.getId())); // B5
        model.addAttribute("studentAddForm", new StudentAddForm());
        return "instructor/students/add";
    }

    @PostMapping
    public String add(@AuthenticationPrincipal LoginUser loginUser,
                      @PathVariable Long courseId,
                      @Valid @ModelAttribute StudentAddForm studentAddForm,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                AddResult result = courseStudentService.addStudent(courseId, loginUser.getId(), studentAddForm);
                if (result.outcome() == StudentAddOutcome.ALREADY_ENROLLED) {
                    bindingResult.rejectValue("studentNo", "student", "이미 이 강좌를 수강 중인 학생입니다.");
                } else {
                    redirectAttributes.addFlashAttribute("successMessage", addMessage(result));
                    return "redirect:/instructor/courses/" + courseId;
                }
            } catch (FormFieldException e) {
                bindingResult.rejectValue(e.getField(), "student", e.getMessage());
            }
        }
        model.addAttribute("course", courseService.getMyCourse(courseId, loginUser.getId()));
        return "instructor/students/add";
    }

    @GetMapping("/import")
    public String importForm(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long courseId, Model model) {
        model.addAttribute("course", courseService.getMyCourse(courseId, loginUser.getId())); // B5
        return "instructor/students/import";
    }

    // 결과 표를 바로 보여주기 위해 redirect 하지 않고 같은 화면에 결과를 렌더링
    @PostMapping("/import")
    public String importStudents(@AuthenticationPrincipal LoginUser loginUser,
                                 @PathVariable Long courseId,
                                 @RequestParam(value = "file", required = false) MultipartFile file,
                                 Model model) {
        model.addAttribute("course", courseService.getMyCourse(courseId, loginUser.getId())); // B5
        try {
            List<RowResult> results = studentImportService.importStudents(courseId, loginUser.getId(), file);
            Map<StudentAddOutcome, Long> counts = results.stream()
                    .collect(Collectors.groupingBy(RowResult::outcome, Collectors.counting()));
            model.addAttribute("results", results);
            model.addAttribute("counts", counts);
            model.addAttribute("outcomes", StudentAddOutcome.values());
        } catch (FormFieldException e) {
            model.addAttribute("fileError", e.getMessage());
        }
        return "instructor/students/import";
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long courseId) {
        courseService.getMyCourse(courseId, loginUser.getId()); // B5
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("수강생_추가_양식.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(studentExcel.template());
    }

    @PostMapping("/{studentId}/remove")
    public String remove(@AuthenticationPrincipal LoginUser loginUser,
                         @PathVariable Long courseId,
                         @PathVariable Long studentId,
                         RedirectAttributes redirectAttributes) {
        try {
            String name = courseStudentService.removeStudent(courseId, loginUser.getId(), studentId);
            redirectAttributes.addFlashAttribute("successMessage", name + " 학생을 강좌에서 내보냈습니다.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/instructor/courses/" + courseId;
    }

    private String addMessage(AddResult result) {
        String who = result.studentName() + "(" + result.studentNo() + ")";
        if (result.outcome() == StudentAddOutcome.CREATED) {
            return who + " 학생 계정을 만들고 강좌에 등록했습니다. 초기 비밀번호는 학번이며, 첫 로그인 때 변경해야 합니다.";
        }
        return "가입된 " + who + " 학생을 강좌에 등록했습니다.";
    }
}
