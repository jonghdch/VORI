package com.vori.backend.title;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.goal.GoalRepository;
import com.vori.backend.goal.GoalStatus;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.pet.GachaPullRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetTier;
import com.vori.backend.receipt.OcrStatus;
import com.vori.backend.receipt.ReceiptOcrJobRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 칭호 획득·장착.
 *
 * 평가는 멱등하다 — 이미 가진 칭호는 건너뛰고, UNIQUE(user_id, name) 가 최종 방어선이다.
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
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final GoalRepository goalRepository;
    private final PetRepository petRepository;
    private final GachaPullRepository gachaPullRepository;
    private final AiInquiryRepository aiInquiryRepository;
    private final ReceiptOcrJobRepository receiptOcrJobRepository;

    /** 전체 칭호 목록. 조회 시점에 평가를 겸해 놓친 획득을 메운다. 획득 → 미획득 순. */
    @Transactional
    public List<TitleResponse> list(Long userId) {
        TitleProgress progress = collect(userId);
        grantNewlyAchieved(userId, progress);

        Map<String, UserTitle> owned = userTitleRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(UserTitle::getName, t -> t, (a, b) -> a));
        Long activeId = userRepository.findById(userId)
                .map(User::getActiveTitleId).orElse(null);

        List<TitleResponse> acquired = new ArrayList<>();
        List<TitleResponse> locked = new ArrayList<>();
        for (TitleCatalog c : TitleCatalog.values()) {
            UserTitle t = owned.get(c.displayName());
            if (t != null) {
                acquired.add(TitleResponse.acquired(c, t, progress, Objects.equals(t.getId(), activeId)));
            } else {
                locked.add(TitleResponse.locked(c, progress));
            }
        }
        // 미획득은 달성이 가까운 순으로 — 화면이 "다음 목표" 를 위에 보여줄 수 있다
        locked.sort((a, b) -> Integer.compare(b.progressPct(), a.progressPct()));

        acquired.addAll(locked);
        return acquired;
    }

    /** 칭호 장착. titleId 가 null 이면 해제. */
    @Transactional
    public void setActive(Long userId, Long titleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        if (titleId == null) {
            user.setActiveTitle(null);
            return;
        }

        UserTitle title = userTitleRepository.findById(titleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "칭호를 찾을 수 없습니다"));
        if (!title.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "획득한 칭호만 장착할 수 있습니다");
        }
        user.setActiveTitle(titleId);
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

    /** 조건을 만족했는데 아직 없는 칭호를 지급한다. 이미 가진 것은 건너뛴다(멱등). */
    private void grantNewlyAchieved(Long userId, TitleProgress progress) {
        for (TitleCatalog c : TitleCatalog.values()) {
            if (!c.isAchieved(progress)) continue;
            if (userTitleRepository.findByUserIdAndName(userId, c.displayName()).isPresent()) continue;

            userTitleRepository.save(UserTitle.builder()
                    .userId(userId)
                    .name(c.displayName())
                    // 획득 시점의 근거를 남긴다 — 나중에 "왜 이때 땄지" 를 설명할 수 있어야 한다
                    .unlockCondition(String.format(
                            "{\"code\":\"%s\",\"threshold\":%d,\"value\":%d}",
                            c.name(), c.threshold(), c.currentOf(progress)))
                    .acquiredAt(LocalDateTime.now())
                    .build());

            log.info("칭호 획득 — userId={}, title={}", userId, c.displayName());
        }
    }

    /** 조건 판정에 쓰는 지표를 한 번에 모은다. 칭호를 추가할 때 여기와 TitleProgress 만 손대면 된다. */
    private TitleProgress collect(Long userId) {
        long totalSaved = userRepository.findById(userId)
                .map(u -> u.getTotalSaved() == null ? 0 : u.getTotalSaved())
                .orElse(0);

        return new TitleProgress(
                totalSaved,
                expenseRepository.countByUserId(userId),
                goalRepository.countByUserIdAndStatus(userId, GoalStatus.DONE),
                petRepository.countByUserIdAndReleasedAtIsNotNull(userId),
                gachaPullRepository.countByUserIdAndTier(userId, PetTier.S),
                aiInquiryRepository.countByUserIdAndAnsweredAtIsNotNull(userId),
                receiptOcrJobRepository.countByUserIdAndStatus(userId, OcrStatus.SUCCESS));
    }

    /** 카탈로그 전체 — 어드민·문서용. */
    public List<TitleCatalog> catalog() {
        return Arrays.asList(TitleCatalog.values());
    }
}
