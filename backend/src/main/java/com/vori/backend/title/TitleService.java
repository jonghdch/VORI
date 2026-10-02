package com.vori.backend.title;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.goal.GoalStatus;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.pet.GachaPullRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetTier;
import com.vori.backend.pettitle.PetTitleAwardRepository;
import com.vori.backend.receipt.OcrStatus;
import com.vori.backend.receipt.ReceiptOcrJobRepository;
import com.vori.backend.theme.ThemeMaster;
import com.vori.backend.theme.ThemeMasterRepository;
import com.vori.backend.title.dto.TitleResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 칭호 획득·장착.
 *
 * 평가는 멱등하다 — 이미 가진 칭호는 건너뛰고, UNIQUE(user_id, title_id) 가 최종 방어선이다.
 * 그래서 두 경로에서 안전하게 호출한다:
 *   1) 지표가 바뀌는 시점의 이벤트 — 획득 순간을 잡아 화면에 알릴 수 있다
 *   2) 목록 조회 — 이벤트를 놓쳤더라도 다음 조회에서 자동으로 복구된다
 *
 * 조건 판정에 필요한 지표는 한 번에 모아 읽는다(TitleProgress). 칭호마다 쿼리를 던지면
 * 칭호 수만큼 DB 를 왕복하게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleService {

    private final UserTitleRepository userTitleRepository;
    private final TitleRepository titleRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final GoalRepository goalRepository;
    private final PetRepository petRepository;
    private final GachaPullRepository gachaPullRepository;
    private final AiInquiryRepository aiInquiryRepository;
    private final ReceiptOcrJobRepository receiptOcrJobRepository;
    private final ThemeMasterRepository themeMasterRepository;
    private final com.vori.backend.notification.NotificationService notificationService;
    private final PetTitleAwardRepository petTitleAwardRepository;

    /**
     * 전체 칭호 목록. 조회 시점에 평가를 겸해 놓친 획득을 메운다. 획득 → 미획득 순.
     * 못 딴 히든 업적도 목록에 나오지만 조건은 가린다 — 설명 "???" 와 달성률만 내려간다.
     */
    @Transactional
    public List<TitleResponse> list(Long userId) {
        TitleProgress progress = collect(userId);
        grantNewlyAchieved(userId, progress);

        Map<Long, UserTitle> owned = userTitleRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(t -> t.getTitle().getId(), t -> t, (a, b) -> a));

        List<TitleResponse> acquired = new ArrayList<>();
        List<TitleResponse> locked = new ArrayList<>();
        for (Title title : titleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()) {
            UserTitle t = owned.get(title.getId());
            if (t != null) {
                acquired.add(TitleResponse.acquired(title, t, progress));
            } else {
                locked.add(TitleResponse.locked(title, progress));
            }
        }
        // 미획득은 달성이 가까운 순으로 — 화면이 "다음 목표" 를 위에 보여줄 수 있다
        locked.sort((a, b) -> Integer.compare(b.progressPct(), a.progressPct()));

        acquired.addAll(locked);
        return acquired;
    }

    /** 장착할 수 있는 업적 수(내 정보 상자 3칸). */
    public static final int EQUIP_LIMIT = 3;

    /**
     * 업적을 장착한다. userTitleIds 순서가 칸 순서이고, 빈 목록이면 모두 장착 해제.
     * 본인이 딴 업적만, 최대 3개까지.
     */
    @Transactional
    public List<TitleResponse> equip(Long userId, List<Long> userTitleIds) {
        List<Long> ids = userTitleIds == null ? List.of() : userTitleIds;
        if (ids.size() > EQUIP_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업적은 " + EQUIP_LIMIT + "개까지 장착할 수 있습니다");
        }
        if (ids.stream().distinct().count() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 업적을 두 번 장착할 수 없습니다");
        }
        // 같은 사용자의 장착 요청이 겹치면 순서가 엉키므로(UNIQUE(user_id, equip_order)) 사용자 행을 잠그고 처리한다
        userRepository.findByIdForUpdate(userId);
        List<UserTitle> owned = userTitleRepository.findByUserId(userId);
        Map<Long, UserTitle> byId = owned.stream()
                .collect(java.util.stream.Collectors.toMap(UserTitle::getId, t -> t));
        if (!byId.keySet().containsAll(ids)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "획득한 업적만 장착할 수 있습니다");
        }
        // UNIQUE(user_id, equip_order) 라 먼저 모두 비우고 반영한 뒤 새 순서를 매긴다
        owned.forEach(t -> t.equip(null));
        userTitleRepository.flush();
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).equip(i + 1);
        }
        return list(userId);
    }

    /**
     * 지표가 바뀐 뒤 칭호 조건을 다시 본다.
     *
     * 커밋 이후에 실행한다 — 아직 커밋되지 않은 지출을 세면 조건이 어긋난다.
     * 별도 트랜잭션을 여는 것도 그 때문이다(원본 트랜잭션은 이미 끝났다).
     * 실패해도 원래 동작(지출 등록 등)에 영향이 없어야 하므로 예외를 삼킨다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTitleCheck(TitleCheckEvent event) {
        try {
            grantNewlyAchieved(event.userId(), collect(event.userId()));
        } catch (Exception e) {
            log.error("칭호 평가 실패 — userId={}, reason={}", event.userId(), event.reason(), e);
        }
    }

    // ───── 내부 ─────

    /** 조건을 만족했는데 아직 없는 칭호를 지급하고, 새로 지급한 칭호를 돌려준다. 이미 가진 것은 건너뛴다(멱등). */
    private List<Title> grantNewlyAchieved(Long userId, TitleProgress progress) {
        List<Title> granted = new ArrayList<>();
        // 헤더와 칭호 화면의 동시 조회에서도 중복 지급을 막는다.
        User owner = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        boolean admin = owner.getRole() == com.vori.backend.user.Role.ADMIN;
        for (Title title : titleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()) {
            if (!admin && !title.isAchieved(progress)) continue;
            if (userTitleRepository.findByUserIdAndTitleId(userId, title.getId()).isPresent()) continue;

            Long unlocksThemeId = unlockedThemeIdOf(title.getId());

            userTitleRepository.save(UserTitle.builder()
                    .userId(userId)
                    .title(title)
                    // 획득 시점의 근거를 남긴다 — 나중에 "왜 이때 땄지" 를 설명할 수 있어야 한다
                    .unlockCondition(String.format(
                            "{\"code\":\"%s\",\"threshold\":%d,\"value\":%d}",
                            title.getCode(), title.getThreshold(), title.currentOf(progress)))
                    .unlocksThemeId(unlocksThemeId)
                    .acquiredAt(LocalDateTime.now())
                    .build());

            log.info("칭호 획득 — userId={}, title={}, unlocksThemeId={}",
                    userId, title.getName(), unlocksThemeId);
            granted.add(title);
            // 관리자는 시연용으로 칭호를 한꺼번에 받으므로 알림을 쌓지 않는다
            if (!admin) {
                notificationService.notify(userId, com.vori.backend.notification.NotificationType.TITLE_ACQUIRED,
                        "새 칭호 「" + title.getName() + "」를 얻었어요", title.getDescription(),
                        "/dex?tab=titles", "title:" + title.getId());
            }
        }
        return granted;
    }

    /**
     * 이 칭호가 해제하는 테마 id — 기록용이다.
     *
     * 해금 판정 자체는 ThemeService 가 theme_master.unlock_title_id 로만 한다. 여기 값은
     * 칭호 화면이 "🎁 코지 테마 해금" 을 띄우기 위한 것이라, 없어도 해금은 정상 동작한다.
     * 그래서 조회 실패를 막지 않고 null 로 흘린다. 칭호 id 로 잇는다(V15).
     */
    private Long unlockedThemeIdOf(Long titleId) {
        List<ThemeMaster> themes = themeMasterRepository.findByUnlockTitleId(titleId);
        return themes.isEmpty() ? null : themes.get(0).getId();
    }

    /** 조건 판정에 쓰는 지표를 한 번에 모은다. 칭호를 추가할 때 여기와 TitleProgress 만 손대면 된다. */
    private TitleProgress collect(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        long totalSaved = user == null || user.getTotalSaved() == null ? 0 : user.getTotalSaved();
        long loginCount = user == null || user.getLoginCount() == null ? 0 : user.getLoginCount();

        return new TitleProgress(
                totalSaved,
                expenseRepository.countByUserId(userId),
                goalRepository.countByUserIdAndStatus(userId, GoalStatus.DONE),
                petRepository.countByUserIdAndReleasedAtIsNotNull(userId),
                gachaPullRepository.countByUserIdAndTier(userId, PetTier.S),
                aiInquiryRepository.countByUserIdAndAnsweredAtIsNotNull(userId),
                receiptOcrJobRepository.countByUserIdAndStatus(userId, OcrStatus.SUCCESS),
                loginCount,
                petRepository.maxInteractionCountByUserId(userId),
                petRepository.countByUserId(userId),
                petRepository.countGraduatedSpeciesByUserId(userId),
                petTitleAwardRepository.countByUserId(userId),
                petTitleAwardRepository.countPublicKindsByUserId(userId),
                petTitleAwardRepository.countPerPetByUserId(userId).stream().findFirst().orElse(0L),
                petTitleAwardRepository.countHiddenByUserId(userId));
    }

    /** 칭호 마스터 전체 — 어드민·문서용. */
    public List<Title> catalog() {
        return titleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();
    }
}
