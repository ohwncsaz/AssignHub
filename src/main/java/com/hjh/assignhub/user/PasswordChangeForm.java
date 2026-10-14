package com.hjh.assignhub.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank(message = "현재 비밀번호를 입력하세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력하세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력하세요.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력하세요.")
    private String newPasswordConfirm;
}
