package com.vori.backend.pettitle.dto;

/** 칭호 장착. awardId 가 null 이면 장착 해제. */
public record EquipTitleRequest(Long awardId) {}
