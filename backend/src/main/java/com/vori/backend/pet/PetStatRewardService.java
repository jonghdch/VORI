package com.vori.backend.pet;

import com.vori.backend.attendance.UserStatItem;
import com.vori.backend.attendance.UserStatItemRepository;
import com.vori.backend.common.StatType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Comparator;

/** 모든 펫 성장 보상의 100 상한과 초과분 전환 아이템을 한곳에서 처리한다. */
@Service @RequiredArgsConstructor
public class PetStatRewardService {
    private final UserStatItemRepository items;
    public int grant(Pet pet, Long userId, StatType source, int delta) {
        int applied = pet.addStatUpToMax(source, delta);
        int overflow = delta - applied;
        if (overflow > 0) {
            StatType target = java.util.Arrays.stream(StatType.values()).filter(s -> s != source)
                    .min(Comparator.comparingInt(pet::statValue)).orElse(StatType.ENERGY);
            items.save(UserStatItem.builder().userId(userId).name("전환 성장 아이템").statType(target)
                    .statDelta(overflow).acquiredAt(LocalDateTime.now()).build());
        }
        return applied;
    }
}
