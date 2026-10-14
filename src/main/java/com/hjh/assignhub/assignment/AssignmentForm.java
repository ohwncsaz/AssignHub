package com.hjh.assignhub.assignment;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentForm {

    // <input type="datetime-local">이 보내는 형식
    private static final String DATETIME_LOCAL = "yyyy-MM-dd'T'HH:mm";

    @NotNull
    private Long courseId;

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
    private String title;

    @NotBlank(message = "내용을 입력하세요.")
    private String content;

    @NotNull(message = "시작일시를 입력하세요.")
    @DateTimeFormat(pattern = DATETIME_LOCAL)
    private LocalDateTime startAt;

    @NotNull(message = "종료일시를 입력하세요.")
    @DateTimeFormat(pattern = DATETIME_LOCAL)
    private LocalDateTime endAt;

    @NotNull(message = "배점을 입력하세요.")
    @Min(value = 1, message = "배점은 1점 이상이어야 합니다.")
    @Max(value = 1000, message = "배점은 1000점 이하로 입력하세요.")
    private Integer maxScore;

    private MultipartFile file;

    // 수정 화면에서 기존 첨부 삭제 체크박스
    private boolean removeFile;

    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }

    // 새 과제 기본값: 지금부터 7일 뒤 23:59까지, 100점
    public static AssignmentForm forNew(Long courseId) {
        AssignmentForm form = new AssignmentForm();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        form.setCourseId(courseId);
        form.setStartAt(now);
        form.setEndAt(now.plusDays(7).withHour(23).withMinute(59));
        form.setMaxScore(100);
        return form;
    }

    public static AssignmentForm from(Assignment assignment) {
        AssignmentForm form = new AssignmentForm();
        form.setCourseId(assignment.getCourse().getId());
        form.setTitle(assignment.getTitle());
        form.setContent(assignment.getContent());
        form.setStartAt(assignment.getStartAt());
        form.setEndAt(assignment.getEndAt());
        form.setMaxScore(assignment.getMaxScore());
        return form;
    }
}
