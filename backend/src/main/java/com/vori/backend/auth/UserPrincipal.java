package com.vori.backend.auth;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 세션에 저장되는 로그인 신원. id·email·role 만 들고 User 엔티티는 들지 않는다.
 *
 * 예전에는 User 를 통째로 들고 있었는데, 로그인 시점의 스냅샷이라 잔액·닉네임이 바뀌어도
 * 로그아웃 전까지 옛 값이 나갔다(상점 잔액, 프로필 닉네임). 신원 외의 값은 꺼낼 수 없게 해서
 * 호출부가 항상 DB 에서 다시 읽도록 강제한다.
 */
@Getter
public class UserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final Role role;
    /** 인증(비밀번호 대조)에만 쓴다. 인증이 끝나면 eraseCredentials() 로 지워져 세션에 남지 않는다. */
    private String passwordHash;
    /** 활성 제재(BAN / 미만료 SUSPENSION) 여부. true 면 인증 시 LockedException. */
    private final boolean locked;

    public UserPrincipal(User user, boolean locked) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
        this.locked = locked;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
