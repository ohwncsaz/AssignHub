package com.hjh.assignhub.common;

// 규칙 위반을 화면 상단 알림(errorMessage)으로 보여줄 때 사용 (예: B1, B4, B8)
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
