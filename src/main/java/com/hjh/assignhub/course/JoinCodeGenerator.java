package com.hjh.assignhub.course;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

// I1 참여코드 — 학생이 손으로 입력하므로 헷갈리는 문자(0/O, 1/I/L)는 제외
@Component
public class JoinCodeGenerator {

    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
