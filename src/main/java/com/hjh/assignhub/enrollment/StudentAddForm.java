package com.hjh.assignhub.enrollment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 강사가 수강생을 추가할 때 입력하는 값 — 화면 1명 추가와 엑셀 대량 추가가 같은 검증 규칙을 쓴다
@Getter
@Setter
@NoArgsConstructor
public class StudentAddForm {

    @NotBlank(message = "학번을 입력하세요.")
    @Pattern(regexp = "\\d{4,20}", message = "학번은 숫자 4~20자리로 입력하세요.")
    private String studentNo;

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
    private String name;

    @NotBlank(message = "이메일을 입력하세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
    private String email;

    public StudentAddForm(String studentNo, String name, String email) {
        this.studentNo = studentNo;
        this.name = name;
        this.email = email;
    }
}
