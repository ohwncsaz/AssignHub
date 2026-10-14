package com.hjh.assignhub.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseForm {

    @NotBlank(message = "강좌명을 입력하세요.")
    @Size(max = 100, message = "강좌명은 100자 이하로 입력하세요.")
    private String name;
}
