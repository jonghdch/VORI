package com.vori.backend.admin;

import com.vori.backend.admin.dto.AdminUserResponse;
import com.vori.backend.admin.dto.PageResponse;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    /**
     * 유저 현황 목록. 최신 가입순. role 이 주어지면 해당 권한만 필터.
     * page/size 는 방어적으로 클램프 (음수·과대 size 차단).
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(int page, int size, Role role) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = (role == null)
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);

        return PageResponse.of(result, AdminUserResponse::from);
    }
}
