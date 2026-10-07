package com.hjh.assignhub.common;

// group by 집계 결과 (id별 개수) — Spring Data 인터페이스 프로젝션
// JPQL에서 "select x.id as id, count(...) as total" 처럼 별칭을 맞춰 사용
public interface IdCount {

    Long getId();

    long getTotal();
}
