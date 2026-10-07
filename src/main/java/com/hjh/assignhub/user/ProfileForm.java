package com.hjh.assignhub.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 역할(role)은 일부러 두지 않음 — 프로필 수정으로 역할을 바꿀 수 없다
@Getter
@Setter
public class ProfileForm {

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
    private String name;

    @NotBlank(message = "이메일을 입력하세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
    private String email;

    // 학생만 사용 (강사 화면에는 입력칸이 없어 null로 들어온다)
    @Pattern(regexp = "\\d{4,20}", message = "학번은 숫자 4~20자리로 입력하세요.")
    private String studentNo;

    public static ProfileForm from(User user) {
        ProfileForm form = new ProfileForm();
        form.setName(user.getName());
        form.setEmail(user.getEmail());
        form.setStudentNo(user.getStudentNo());
        return form;
    }
}
