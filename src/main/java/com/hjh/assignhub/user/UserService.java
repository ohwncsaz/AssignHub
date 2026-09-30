package com.hjh.assignhub.user;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // U1 학생 회원가입 — 화면 검증과 별개로 서버에서 다시 검사
    @Transactional
    public Long signup(SignupForm form) {
        String email = normalizeEmail(form.getEmail());
        String studentNo = form.getStudentNo().trim();

        if (!form.getPassword().equals(form.getPasswordConfirm())) {
            throw new SignupException("passwordConfirm", "비밀번호가 일치하지 않습니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new SignupException("email", "이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByStudentNo(studentNo)) {
            throw new SignupException("studentNo", "이미 가입된 학번입니다.");
        }

        User student = User.createStudent(
                email,
                passwordEncoder.encode(form.getPassword()),
                form.getName().trim(),
                studentNo);
        return userRepository.save(student).getId();
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
