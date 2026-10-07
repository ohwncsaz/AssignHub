package com.hjh.assignhub.stats;

// 과제별 채점 통계 (group by 결과) — JPQL 별칭 id · average · graded와 이름을 맞춘 인터페이스 프로젝션
public interface ScoreStat {

    Long getId();

    Double getAverage();

    long getGraded();
}
