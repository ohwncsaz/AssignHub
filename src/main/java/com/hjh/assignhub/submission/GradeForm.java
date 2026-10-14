package com.hjh.assignhub.submission;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// I5 채점 — 점수 상한(배점)은 과제마다 달라서 Service에서 검사 (B6)
@Getter
@Setter
public class GradeForm {

    @NotNull(message = "점수를 입력하세요.")
    @Min(value = 0, message = "점수는 0점 이상이어야 합니다.")
    private Integer score;

    @Size(max = 2000, message = "피드백은 2,000자 이하로 입력하세요.")
    private String feedback;

    public static GradeForm from(Submission submission) {
        GradeForm form = new GradeForm();
        form.setScore(submission.getScore());
        form.setFeedback(submission.getFeedback());
        return form;
    }
}
