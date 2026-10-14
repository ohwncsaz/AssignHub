package com.hjh.assignhub.enrollment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 수강생 추가 결과 — 엑셀 대량 추가 결과 표의 배지로도 사용
@Getter
@RequiredArgsConstructor
public enum StudentAddOutcome {

    CREATED("계정 생성 후 등록", "success"),
    ENROLLED("기존 계정 등록", "primary"),
    ALREADY_ENROLLED("이미 수강 중", "secondary"),
    FAILED("실패", "danger");

    private final String label;
    private final String badge;
}
