package com.vori.backend.title.dto;

import java.util.List;

/** 장착할 업적의 user_titles.id 목록(칸 순서대로, 최대 3개). 빈 목록이면 모두 장착 해제. */
public record EquipAchievementsRequest(List<Long> ids) {}
