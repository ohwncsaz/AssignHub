package com.hjh.assignhub.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 역할(role) 필드를 일부러 두지 않음 — 요청에 role을 끼워 넣어도 무시되고 항상 STUDENT로 가입 (U4)
@Getter
@Setter
public class SignupForm {

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

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력하세요.")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력하세요.")
    private String passwordConfirm;
}
