package com.hjh.assignhub.enrollment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinForm {

    @NotBlank(message = "참여코드를 입력하세요.")
    @Size(max = 20, message = "참여코드가 올바르지 않습니다.")
    private String joinCode;
}
