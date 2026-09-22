package com.vori.backend.admin;

import com.vori.backend.admin.dto.AdminTitleResponse;
import com.vori.backend.admin.dto.TitleUpsertRequest;
import com.vori.backend.theme.ThemeMaster;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.title.Title;
import com.vori.backend.title.TitleRepository;
import com.vori.backend.title.UserTitleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 어드민 칭호 관리 — 마스터(titles) CRUD.
 *
 * 지급 판정(TitleService)은 enabled=true 인 칭호만 보므로, 운영 중 칭호를 내리려면
 * 삭제가 아니라 비활성화가 기본이다. 삭제는 보유자가 없고 어떤 테마의 해금 조건도
 * 아닐 때만 허용한다 — user_titles 가 FK RESTRICT 라 DB 도 막지만, 500 대신 이유를
 * 사용자에게 돌려주기 위해 서비스에서 먼저 검사한다.
 *
 * code 는 생성 후 바꿀 수 없다. user_titles.unlock_condition 과 획득 로그가 code 로
 * 남아 있어, 바꾸면 "왜 그때 땄지" 를 추적할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTitleService {

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;
    private final ThemeMasterRepository themeMasterRepository;

    @Transactional(readOnly = true)
    public List<AdminTitleResponse> list() {
        return titleRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminTitleResponse create(TitleUpsertRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코드를 입력해주세요.");
        }
        if (titleRepository.existsByCode(req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 있는 코드입니다: " + req.code());
        }
        Title saved = titleRepository.save(Title.create(
                req.code(), req.name().trim(), req.description().trim(), req.metricType(),
                req.threshold(), req.enabled() == null || req.enabled(),
                req.sortOrder() == null ? 0 : req.sortOrder()));
        log.info("어드민 칭호 생성 — id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public AdminTitleResponse update(Long id, TitleUpsertRequest req) {
        Title title = find(id);
        title.update(req.name().trim(), req.description().trim(), req.metricType(),
                req.threshold(), req.enabled() == null || req.enabled(),
                req.sortOrder() == null ? 0 : req.sortOrder());
        log.info("어드민 칭호 수정 — id={}, code={}, enabled={}", id, title.getCode(), title.getEnabled());
        return toResponse(title);
    }

    /** 활성/비활성 토글 — 운영 중 칭호를 내리는 기본 경로. 보유자의 획득 기록은 그대로 남는다. */
    @Transactional
    public AdminTitleResponse setEnabled(Long id, boolean enabled) {
        Title title = find(id);
        title.update(title.getName(), title.getDescription(), title.getMetricType(),
                title.getThreshold(), enabled, title.getSortOrder() == null ? 0 : title.getSortOrder());
        return toResponse(title);
    }

    @Transactional
    public void delete(Long id) {
        Title title = find(id);
        long holders = userTitleRepository.countByTitleId(id);
        if (holders > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "보유자가 " + holders + "명 있어 삭제할 수 없습니다. 대신 비활성화하세요.");
        }
        List<ThemeMaster> themes = themeMasterRepository.findByUnlockTitleId(id);
        if (!themes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + themes.get(0).getName() + "' 테마의 해금 조건이라 삭제할 수 없습니다. 테마 조건을 먼저 바꾸세요.");
        }
        titleRepository.delete(title);
        log.info("어드민 칭호 삭제 — id={}, code={}", id, title.getCode());
    }

    private Title find(Long id) {
        return titleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "칭호를 찾을 수 없습니다."));
    }

    private AdminTitleResponse toResponse(Title t) {
        List<ThemeMaster> themes = themeMasterRepository.findByUnlockTitleId(t.getId());
        return AdminTitleResponse.of(t,
                userTitleRepository.countByTitleId(t.getId()),
                themes.isEmpty() ? null : themes.get(0).getName());
    }
}
