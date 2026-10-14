package com.hjh.assignhub.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.hjh.assignhub.user.UserRepository;
import com.hjh.assignhub.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // 로그인 폼의 username 칸에는 이메일이 들어온다 (SecurityConfig의 usernameParameter)
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(UserService.normalizeEmail(email))
                .map(LoginUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다."));
    }
}
