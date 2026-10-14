package com.hjh.assignhub.common;

import lombok.Getter;

// Service 검증 실패를 폼의 특정 입력칸(field) 에러로 보여줄 때 사용 (예: B6, B7)
@Getter
public class FormFieldException extends RuntimeException {

    private final String field;

    public FormFieldException(String field, String message) {
        super(message);
        this.field = field;
    }
}
