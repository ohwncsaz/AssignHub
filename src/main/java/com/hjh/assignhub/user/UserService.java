package com.hjh.assignhub.user;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hjh.assignhub.common.FormFieldException;

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

    public User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    // 프로필 수정 — 이메일·학번은 다른 사용자와 중복 불가
    @Transactional
    public User updateProfile(Long userId, ProfileForm form) {
        User user = getUser(userId);
        String email = normalizeEmail(form.getEmail());
        String studentNo = null;

        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new FormFieldException("email", "이미 사용 중인 이메일입니다.");
        }
        if (user.isStudent()) {
            if (form.getStudentNo() == null || form.getStudentNo().isBlank()) {
                throw new FormFieldException("studentNo", "학번을 입력하세요.");
            }
            studentNo = form.getStudentNo().trim();
            if (!studentNo.equals(user.getStudentNo()) && userRepository.existsByStudentNo(studentNo)) {
                throw new FormFieldException("studentNo", "이미 사용 중인 학번입니다.");
            }
        }

        user.updateProfile(form.getName().trim(), email, studentNo);
        return user;
    }

    // 비밀번호 변경 — 현재 비밀번호를 확인한 뒤에만 바꾼다
    @Transactional
    public User changePassword(Long userId, PasswordChangeForm form) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            throw new FormFieldException("currentPassword", "현재 비밀번호가 올바르지 않습니다.");
        }
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            throw new FormFieldException("newPasswordConfirm", "새 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(form.getNewPassword(), user.getPassword())) {
            throw new FormFieldException("newPassword", "현재 비밀번호와 다른 비밀번호를 입력하세요.");
        }
        user.changePassword(passwordEncoder.encode(form.getNewPassword()));
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
