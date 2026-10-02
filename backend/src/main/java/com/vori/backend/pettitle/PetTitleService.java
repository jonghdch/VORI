package com.vori.backend.pettitle;

import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.pet.GrowthReason;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetGrowthLogRepository;
import com.vori.backend.pet.PetRepository;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pet.PetSpeciesRepository;
import com.vori.backend.pettitle.dto.PetTitleBoardResponse;
import com.vori.backend.pettitle.dto.PetTitleItem;
import com.vori.backend.pettitle.dto.PetTitleSummary;
import com.vori.backend.title.TitleCheckEvent;
import com.vori.backend.title.dto.GrantedTitle;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 펫 칭호 — 판정·칭호 탭·장착.
 *
 * 과제는 "이 펫"의 기록으로만 판정한다. 펫 단위 값(레벨·상호작용·매력 보너스)은 그대로 쓰고,
 * 유저 단위 기록(AI 답변)은 펫이 부화한 뒤의 것만 센다. 그래서 새 펫이 오면 따로 초기화하지 않아도
 * 진행도가 0부터 시작한다. 분양한 펫은 판정하지 않는다 — 그 펫의 칭호는 분양 순간에 고정된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetTitleService {

    private final PetTitleRepository petTitleRepository;
    private final PetTitleAwardRepository awardRepository;
    private final PetRepository petRepository;
    private final PetSpeciesRepository petSpeciesRepository;
    private final PetGrowthLogRepository petGrowthLogRepository;
    private final AiInquiryRepository aiInquiryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 펫의 칭호 조건을 보고 새로 채운 칭호를 지급한다. 부르는 쪽 트랜잭션 안에서 돈다.
     * 상호작용·분양처럼 펫을 바꾼 바로 그 자리에서 부르면, 응답에 획득 칭호를 실을 수 있다.
     */
    @Transactional
    public List<GrantedTitle> evaluate(Pet pet) {
        if (pet == null || pet.isReleased()) return List.of();
        List<PetTitle> pending = pendingTitles(pet.getId());
        if (pending.isEmpty()) return List.of();

        PetTitleProgress progress = collect(pet, metricsOf(pending));
        List<PetTitle> reached = pending.stream().filter(t -> t.isAchieved(progress)).toList();
        if (reached.isEmpty()) return List.of();

        // 지급할 게 있을 때만 사용자 행을 잠그고 다시 확인한다 — 상호작용과 커밋 후 판정이 겹쳐도 두 번 지급하지 않게.
        userRepository.findByIdForUpdate(pet.getUserId());
        Set<Long> owned = ownedTitleIds(pet.getId());

        List<GrantedTitle> granted = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (PetTitle title : reached) {
            if (owned.contains(title.getId())) continue;
            awardRepository.save(PetTitleAward.builder()
                    .petId(pet.getId())
                    .title(title)
                    .unlockCondition(String.format("{\"code\":\"%s\",\"threshold\":%d,\"value\":%d}",
                            title.getCode(), title.getThreshold(), title.currentOf(progress)))
                    .acquiredAt(now)
                    .build());
            log.info("펫 칭호 획득 — userId={}, petId={}, title={}", pet.getUserId(), pet.getId(), title.getCode());
            // 같은 칭호라도 펫이 다르면 다른 알림이다 — 키에 펫 id 를 넣는다
            notificationService.notify(pet.getUserId(), NotificationType.PET_TITLE_ACQUIRED,
                    displayName(pet) + "이(가) 칭호 「" + title.getName() + "」을(를) 얻었어요",
                    title.getDescription(), "/dex?tab=titles",
                    "pet-title:" + pet.getId() + ":" + title.getId());
            granted.add(new GrantedTitle(title.getName(), title.isHidden()));
        }
        // 칭호 수가 바뀌었으므로 업적(칭호 획득 수 등)도 다시 본다
        if (!granted.isEmpty()) eventPublisher.publishEvent(new TitleCheckEvent(pet.getUserId(), "PET_TITLE_ACQUIRED"));
        return granted;
    }

    /**
     * 펫 지표가 바뀐 트랜잭션이 커밋된 뒤 키우는 펫을 판정한다.
     * 별도 트랜잭션이고 예외를 삼킨다 — 칭호 판정 실패가 지출 저장 같은 원래 동작을 되돌리면 안 된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPetTitleCheck(PetTitleCheckEvent event) {
        try {
            petRepository.findByUserIdAndReleasedAtIsNull(event.userId()).stream().findFirst()
                    .ifPresent(this::evaluate);
        } catch (Exception e) {
            log.error("펫 칭호 판정 실패 — userId={}, reason={}", event.userId(), event.reason(), e);
        }
    }

    /** 칭호 탭 — 키우는 펫의 과제와 진행도. 조회하면서 놓친 지급도 메운다. */
    @Transactional
    public PetTitleBoardResponse board(Long userId) {
        Pet pet = petRepository.findByUserIdAndReleasedAtIsNull(userId).stream().findFirst().orElse(null);
        List<PetTitle> titles = petTitleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();
        if (pet == null) {
            List<PetTitleItem> items = titles.stream()
                    .map(t -> item(t, PetTitleProgress.ZERO, null, null))
                    .toList();
            return new PetTitleBoardResponse(null, null, null, items);
        }

        evaluate(pet);
        PetTitleProgress progress = collect(pet, EnumSet.allOf(PetTitleMetricType.class));
        Map<Long, PetTitleAward> awardByTitle = awardRepository.findByPetIdIn(List.of(pet.getId())).stream()
                .collect(Collectors.toMap(a -> a.getTitle().getId(), a -> a, (a, b) -> a));
        List<PetTitleItem> items = new ArrayList<>();
        for (PetTitle t : titles) {
            PetTitleAward award = awardByTitle.get(t.getId());
            items.add(item(t, progress, award, pet.getEquippedTitleAwardId()));
        }
        PetSpecies species = petSpeciesRepository.findById(pet.getSpeciesId()).orElse(null);
        return new PetTitleBoardResponse(pet.getId(), pet.getName(),
                species == null ? null : species.getName(), items);
    }

    /**
     * 칭호 장착(awardId) / 장착 해제(null). 키우는 펫만 바꿀 수 있다 — 분양한 펫이 장착한 칭호는 기록이다.
     */
    @Transactional
    public PetTitleBoardResponse equip(Long userId, Long petId, Long awardId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "펫을 찾을 수 없습니다"));
        if (!pet.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 펫만 바꿀 수 있습니다");
        }
        if (pet.isReleased()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "분양한 펫의 장착 칭호는 바꿀 수 없습니다");
        }
        if (awardId != null) {
            PetTitleAward award = awardRepository.findById(awardId)
                    .filter(a -> a.getPetId().equals(petId))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "이 펫이 얻은 칭호만 장착할 수 있습니다"));
            pet.equipTitle(award.getId());
        } else {
            pet.equipTitle(null);
        }
        return board(userId);
    }

    /** 펫별 획득 칭호 — 펫 응답(PetResponse)에 싣는다. 펫마다 따로 조회하지 않고 한 번에 읽는다. */
    @Transactional(readOnly = true)
    public Map<Long, List<PetTitleSummary>> summariesByPet(Collection<Long> petIds) {
        if (petIds.isEmpty()) return Map.of();
        Map<Long, List<PetTitleSummary>> byPet = new HashMap<>();
        for (PetTitleAward award : awardRepository.findByPetIdIn(petIds)) {
            byPet.computeIfAbsent(award.getPetId(), k -> new ArrayList<>()).add(PetTitleSummary.of(award));
        }
        return byPet;
    }

    // ───── 내부 ─────

    private List<PetTitle> pendingTitles(Long petId) {
        Set<Long> owned = ownedTitleIds(petId);
        return petTitleRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .filter(t -> !owned.contains(t.getId()))
                .toList();
    }

    private Set<Long> ownedTitleIds(Long petId) {
        return awardRepository.findByPetIdIn(List.of(petId)).stream()
                .map(a -> a.getTitle().getId())
                .collect(Collectors.toSet());
    }

    private static Set<PetTitleMetricType> metricsOf(List<PetTitle> titles) {
        Set<PetTitleMetricType> metrics = EnumSet.noneOf(PetTitleMetricType.class);
        titles.forEach(t -> metrics.add(t.getMetricType()));
        return metrics;
    }

    /** 필요한 지표만 센다 — 이미 딴 칭호의 지표까지 매번 조회하지 않게. */
    PetTitleProgress collect(Pet pet, Set<PetTitleMetricType> needed) {
        long aiAnswers = needed.contains(PetTitleMetricType.AI_ANSWERS)
                ? aiInquiryRepository.countAnsweredForExpensesSince(pet.getUserId(), lifeStart(pet)) : 0;
        long charmBonuses = needed.contains(PetTitleMetricType.CHARM_BONUS)
                ? petGrowthLogRepository.countByPetIdAndReason(pet.getId(), GrowthReason.PET_INTERACTION) : 0;
        return new PetTitleProgress(pet.level(), nz(pet.getInteractionCount()), aiAnswers, charmBonuses);
    }

    /** 펫 수명 구간의 시작 — 부화 시각, 없으면(시작 펫 등) 생성 시각. */
    private static LocalDateTime lifeStart(Pet pet) {
        return pet.getHatchedAt() != null ? pet.getHatchedAt() : pet.getCreatedAt();
    }

    /** 못 딴 히든 칭호는 조건을 가린다 — 설명은 "???", 현재값·목표치는 0, 달성률만 보낸다. */
    static final String HIDDEN_DESCRIPTION = "???";

    private static PetTitleItem item(PetTitle t, PetTitleProgress progress, PetTitleAward award, Long equippedAwardId) {
        boolean acquired = award != null;
        if (t.isHidden() && !acquired) {
            return new PetTitleItem(t.getCode(), t.getName(), HIDDEN_DESCRIPTION, t.getMetricType().name(),
                    true, 0, 0, t.progressPct(progress), false, null, null, false);
        }
        return new PetTitleItem(t.getCode(), t.getName(), t.getDescription(), t.getMetricType().name(),
                t.isHidden(),
                acquired ? Math.max(t.currentOf(progress), t.getThreshold()) : t.currentOf(progress),
                t.getThreshold(),
                acquired ? 100 : t.progressPct(progress),
                acquired,
                acquired ? award.getId() : null,
                acquired ? award.getAcquiredAt() : null,
                acquired && Objects.equals(award.getId(), equippedAwardId));
    }

    private static String displayName(Pet pet) {
        return pet.getName() == null || pet.getName().isBlank() ? "펫" : pet.getName();
    }

    private static long nz(Integer v) {
        return v == null ? 0 : v;
    }
}
