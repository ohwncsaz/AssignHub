package com.hjh.assignhub.user;

import lombok.Getter;

// 회원가입 검증 실패 — 어떤 입력칸(field)에 에러를 표시할지 함께 전달
@Getter
public class SignupException extends RuntimeException {

    private final String field;

    public SignupException(String field, String message) {
        super(message);
        this.field = field;
    }
}
