package com.hjh.assignhub.enrollment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hjh.assignhub.common.FormFieldException;
import com.hjh.assignhub.course.CourseService;
import com.hjh.assignhub.enrollment.CourseStudentService.AddResult;
import com.hjh.assignhub.enrollment.StudentExcel.StudentRow;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

// 학생 대량 추가 — 일부러 @Transactional을 두지 않는다
// 줄마다 CourseStudentService.addStudent(각자 트랜잭션)를 호출해서, 한 줄이 실패해도 나머지 줄은 저장된다
@Service
@RequiredArgsConstructor
public class StudentImportService {

    private final CourseService courseService;
    private final CourseStudentService courseStudentService;
    private final StudentExcel studentExcel;
    private final Validator validator;

    public record RowResult(int rowNumber, String studentNo, String name, String email,
                            StudentAddOutcome outcome, String message) {
    }

    public List<RowResult> importStudents(Long courseId, Long instructorId, MultipartFile file) {
        courseService.getMyCourse(courseId, instructorId); // B5 — 파일을 읽기 전에 본인 강좌인지 먼저 확인
        List<StudentRow> rows = studentExcel.read(file);

        List<RowResult> results = new ArrayList<>();
        for (StudentRow row : rows) {
            results.add(importRow(courseId, instructorId, row));
        }
        return results;
    }

    private RowResult importRow(Long courseId, Long instructorId, StudentRow row) {
        StudentAddForm form = row.toForm();
        // 1명 추가 화면과 같은 검증 규칙(@Pattern, @Email 등)을 엑셀 행에도 적용
        Set<ConstraintViolation<StudentAddForm>> violations = validator.validate(form);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(" "));
            return failed(row, message);
        }
        try {
            AddResult result = courseStudentService.addStudent(courseId, instructorId, form);
            return new RowResult(row.rowNumber(), row.studentNo(), result.studentName(), row.email(),
                    result.outcome(), null);
        } catch (FormFieldException e) {
            return failed(row, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            return failed(row, "이미 사용 중인 학번 또는 이메일입니다.");
        }
    }

    private RowResult failed(StudentRow row, String message) {
        return new RowResult(row.rowNumber(), row.studentNo(), row.name(), row.email(),
                StudentAddOutcome.FAILED, message);
    }
}
