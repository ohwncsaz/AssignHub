package com.hjh.assignhub.assignment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// S2 예정 · 진행중 · 마감 배지 — DB에 저장하지 않고 시작/종료일시와 현재 시각으로 계산
@Getter
@RequiredArgsConstructor
public enum AssignmentStatus {

    UPCOMING("예정", "secondary"),
    OPEN("진행중", "success"),
    CLOSED("마감", "dark");

    private final String label;
    private final String badge;
}
