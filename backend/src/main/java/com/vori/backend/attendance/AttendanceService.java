package com.vori.backend.attendance;

import com.vori.backend.common.StatType;
import com.vori.backend.pet.*;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AttendanceService {
    private static final int ITEM_CHANCE_PERCENT = 60;
    private final AttendanceCheckinRepository checkins;
    private final UserStatItemRepository items;
    private final UserRepository users;
    private final PetRepository pets;
    private final PetGrowthLogRepository growthLogs;

    @Transactional(readOnly = true)
    public AttendanceResponse today(Long userId) {
        return checkins.findByUserIdAndCheckedInDate(userId, LocalDate.now())
                .map(c -> new AttendanceResponse(c.getCheckedInDate(), true, c.isItemAwarded(), null, c.getStreakCount(), c.isStreakBonus()))
                .orElseGet(() -> new AttendanceResponse(LocalDate.now(), false, false, null, currentStreak(userId, LocalDate.now().minusDays(1)), false));
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryResponse> month(Long userId, YearMonth month) {
        return checkins.findByUserIdAndCheckedInDateBetweenOrderByCheckedInDate(userId, month.atDay(1), month.plusMonths(1).atDay(1)).stream().map(AttendanceHistoryResponse::from).toList();
    }

    @Transactional
    public AttendanceResponse checkIn(Long userId) {
        LocalDate date = LocalDate.now();
        User user = users.findByIdForUpdate(userId).orElseThrow();
        if (checkins.findByUserIdAndCheckedInDate(userId, date).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "오늘은 이미 출석했어요.");
        }
        int streak = currentStreak(userId, date.minusDays(1)) + 1;
        boolean streakBonus = streak % 3 == 0;
        boolean awarded = streakBonus || java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < ITEM_CHANCE_PERCENT;
        StatItemResponse itemResponse = null;
        if (awarded) {
            StatType stat = StatType.values()[java.util.concurrent.ThreadLocalRandom.current().nextInt(StatType.values().length)];
            String name = itemName(stat);
            int delta = streakBonus ? 10 : 5;
            if (streakBonus) name = "연속 출석 " + name;
            UserStatItem item = items.save(UserStatItem.builder().userId(user.getId()).name(name).statType(stat).statDelta(delta).acquiredAt(LocalDateTime.now()).build());
            itemResponse = StatItemResponse.from(item);
        }
        checkins.save(AttendanceCheckin.builder().userId(userId).checkedInDate(date).itemAwarded(awarded).rewardName(itemResponse == null ? null : itemResponse.name()).rewardStatType(itemResponse == null ? null : itemResponse.statType()).rewardStatDelta(itemResponse == null ? null : itemResponse.statDelta()).streakCount(streak).streakBonus(streakBonus).createdAt(LocalDateTime.now()).build());
        return new AttendanceResponse(date, true, awarded, itemResponse, streak, streakBonus);
    }

    @Transactional(readOnly = true)
    public List<StatItemResponse> listItems(Long userId) {
        List<StatItemResponse> owned = new ArrayList<>(items.findByUserIdOrderByAcquiredAtDesc(userId).stream().map(StatItemResponse::from).toList());
        User user = users.findById(userId).orElseThrow();
        if (user.getRole() == com.vori.backend.user.Role.ADMIN) {
            for (StatType stat : StatType.values()) {
                owned.add(new StatItemResponse(-1L - stat.ordinal(), "관리자 " + itemName(stat), stat, 5, LocalDateTime.now()));
                owned.add(new StatItemResponse(-101L - stat.ordinal(), "관리자 3일 연속 " + itemName(stat), stat, 10, LocalDateTime.now()));
            }
        }
        return owned;
    }

    @Transactional
    public void useItem(Long userId, Long itemId) {
        if (itemId < 0) {
            User user = users.findById(userId).orElseThrow();
            if (user.getRole() != com.vori.backend.user.Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 전용 아이템입니다.");
            boolean streakItem = itemId <= -101;
            int ordinal = streakItem ? (int) (-itemId - 101) : (int) (-itemId - 1);
            applyStatItem(userId, StatType.values()[ordinal], streakItem ? 10 : 5);
            return;
        }
        UserStatItem item = items.findById(itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "아이템을 찾을 수 없어요."));
        if (!item.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 아이템만 사용할 수 있어요.");
        applyStatItem(userId, item.getStatType(), item.getStatDelta());
        items.delete(item);
    }

    private void applyStatItem(Long userId, StatType statType, int delta) {
        Pet pet = pets.findByUserIdAndReleasedAtIsNull(userId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "키우는 펫이 있어야 사용할 수 있어요."));
        pet.addStat(statType, delta);
        pet.evaluateStage();
        growthLogs.save(PetGrowthLog.builder().petId(pet.getId()).userId(userId).statType(statType).delta(delta).savedAmount(0).reason(GrowthReason.ATTENDANCE_ITEM).createdAt(LocalDateTime.now()).build());
    }

    private int currentStreak(Long userId, LocalDate end) {
        int streak = 0;
        LocalDate cursor = end;
        while (checkins.findByUserIdAndCheckedInDate(userId, cursor).isPresent()) { streak++; cursor = cursor.minusDays(1); }
        return streak;
    }

    private String itemName(StatType stat) {
        return switch (stat) { case ENERGY -> "활력 비타민"; case CHARM -> "매력 향수"; case IQ -> "집중 캔디"; case ENDURANCE -> "튼튼 드링크"; };
    }
}
