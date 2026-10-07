package com.hjh.assignhub.submission;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionForm {

    @NotBlank(message = "제출 내용을 입력하세요.")
    @Size(max = 10000, message = "제출 내용은 10,000자 이하로 입력하세요.")
    private String content;

    private MultipartFile file;

    // 재제출 시 기존 첨부 삭제 체크박스 (새 파일을 올리면 자동으로 교체)
    private boolean removeFile;

    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }

    public static SubmissionForm from(Submission submission) {
        SubmissionForm form = new SubmissionForm();
        form.setContent(submission.getContent());
        return form;
    }
}
