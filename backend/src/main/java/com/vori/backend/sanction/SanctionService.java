package com.vori.backend.sanction;

import com.vori.backend.admin.dto.PageResponse;
import com.vori.backend.sanction.dto.SanctionCreateRequest;
import com.vori.backend.sanction.dto.SanctionResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SanctionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;

    @Transactional
    public SanctionResponse create(SanctionCreateRequest req) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = null;
        if (req.type() == SanctionType.SUSPENSION) {
            if (req.durationDays() == null || req.durationDays() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정지는 기간(일)을 1 이상 입력해야 합니다");
            }
            expiresAt = now.plusDays(req.durationDays());
        }

        Sanction saved = sanctionRepository.save(Sanction.builder()
                .userId(user.getId())
                .type(req.type())
                .reason(req.reason().trim())
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());

        return toResponse(saved, user, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<SanctionResponse> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Sanction> result = sanctionRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<Long> userIds = result.getContent().stream().map(Sanction::getUserId).distinct().toList();
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        LocalDateTime now = LocalDateTime.now();
        return PageResponse.of(result, s -> toResponse(s, users.get(s.getUserId()), now));
    }

    /** 조기 해제. 이미 해제됐으면 그대로 둠. */
    @Transactional
    public SanctionResponse lift(Long id) {
        Sanction s = sanctionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제재를 찾을 수 없습니다"));
        if (s.getLiftedAt() == null) {
            s.lift(LocalDateTime.now());
        }
        User user = userRepository.findById(s.getUserId()).orElse(null);
        return toResponse(s, user, LocalDateTime.now());
    }

    private SanctionResponse toResponse(Sanction s, User user, LocalDateTime now) {
        return new SanctionResponse(
                s.getId(),
                s.getUserId(),
                user != null ? user.getNickname() : null,
                user != null ? user.getEmail() : null,
                s.getType(),
                s.getReason(),
                status(s, now),
                s.getCreatedAt(),
                s.getExpiresAt(),
                s.getLiftedAt());
    }

    /** ACTIVE(활성) / EXPIRED(만료) / LIFTED(해제). */
    private String status(Sanction s, LocalDateTime now) {
        if (s.getLiftedAt() != null) return "LIFTED";
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(now)) return "EXPIRED";
        return "ACTIVE";
    }
}
