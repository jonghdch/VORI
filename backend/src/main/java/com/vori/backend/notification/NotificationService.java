package com.vori.backend.notification;

import com.vori.backend.notification.dto.NotificationResponse;
import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 저장·조회. 다른 서비스가 notify(...) 를 부른다.
 * 같은 일은 dedupeKey 로 한 번만 저장한다 — 배치가 두 번 돌거나 같은 칭호 판정이 겹쳐도 알림은 하나.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void notify(Long userId, NotificationType type, String title, String body, String link, String dedupeKey) {
        if (dedupeKey != null && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) return;
        notificationRepository.save(Notification.builder()
                .userId(userId).type(type).title(title).body(body).link(link)
                .dedupeKey(dedupeKey).createdAt(LocalDateTime.now())
                .build());
        log.info("알림 — userId={}, type={}, key={}", userId, type, dedupeKey);
    }

    /**
     * 펫 경험치가 오른 뒤 부른다. levelBefore → 지금 레벨 사이에서 진화(5·15)·졸업(30) 지점을 넘었으면 알린다.
     */
    public void petGrew(Long userId, Pet pet, int levelBefore) {
        int after = pet.level();
        if (after <= levelBefore) return;
        String who = pet.getName() == null || pet.getName().isBlank() ? "펫" : pet.getName();
        if (levelBefore < PetLevel.JUVENILE_LEVEL && after >= PetLevel.JUVENILE_LEVEL) {
            notify(userId, NotificationType.PET_EVOLVED, who + "이(가) 2차로 진화했어요!",
                    "Lv. " + PetLevel.JUVENILE_LEVEL + " 달성", "/myroom",
                    "pet:" + pet.getId() + ":lv" + PetLevel.JUVENILE_LEVEL);
        }
        if (levelBefore < PetLevel.ADULT_LEVEL && after >= PetLevel.ADULT_LEVEL) {
            notify(userId, NotificationType.PET_EVOLVED, who + "이(가) 3차로 진화했어요!",
                    "Lv. " + PetLevel.ADULT_LEVEL + " 달성", "/myroom",
                    "pet:" + pet.getId() + ":lv" + PetLevel.ADULT_LEVEL);
        }
        if (levelBefore < PetLevel.MAX_LEVEL && after >= PetLevel.MAX_LEVEL) {
            notify(userId, NotificationType.PET_GRADUATE_READY, who + "이(가) 30레벨이 됐어요. 졸업시킬 수 있어요!",
                    "마이룸에서 분양하면 코인을 받아요", "/myroom",
                    "pet:" + pet.getId() + ":lv" + PetLevel.MAX_LEVEL);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId) {
        return notificationRepository.findTop30ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** 본인 알림만 읽음 처리한다. 남의 알림·없는 알림은 조용히 무시. */
    @Transactional
    public void markRead(Long userId, Long id) {
        notificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> n.markRead(LocalDateTime.now()));
    }

    /**
     * 본인 알림을 모두 지운다. 같은 일(dedupeKey)로 다시 알림이 오지는 않는다 —
     * 기록이 없어지므로 배치가 같은 키로 다시 돌면 새로 생길 수 있지만, 정산·진화·칭호는 한 번만 일어난다.
     */
    @Transactional
    public void deleteAll(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        notificationRepository.findByUserIdAndReadAtIsNull(userId).forEach(n -> n.markRead(now));
    }
}
