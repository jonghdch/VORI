package com.vori.backend.admin;

import com.vori.backend.admin.dto.AdminCoinResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 어드민 전용 코인 지급 — 시연·운영에서 필요한 잔액을 채운다.
 * 일일 보상이나 상점 가격을 바꾸지 않고 관리자 경로로만 지급해 기존 게임 규칙을 보존한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCoinService {

    private static final int MAX_GRANT = 100_000;

    private final UserRepository userRepository;

    /** 보상 규칙을 왜곡하지 않고 시연·운영에 필요한 경우에만 관리자가 지급한다. */
    @Transactional
    public AdminCoinResponse grant(Long adminId, Long userId, int amount) {
        if (amount < 1 || amount > MAX_GRANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지급 코인은 1 이상 100,000 이하여야 합니다");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.addGameMoney(amount);

        log.info("[ADMIN] 코인 지급 — adminId={}, userId={}, amount={}", adminId, userId, amount);
        return new AdminCoinResponse(userId, user.getGameMoney(), amount);
    }
}
