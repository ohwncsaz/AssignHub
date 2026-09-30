package com.hjh.assignhub.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.hjh.assignhub.user.Role;
import com.hjh.assignhub.user.User;

import lombok.Getter;

// 세션에 저장되는 로그인 사용자 정보 — 이후 B5(본인 강좌 확인) 등에서 getId()로 사용
@Getter
public class LoginUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String name;
    private final Role role;

    public LoginUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.name = user.getName();
        this.role = user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
