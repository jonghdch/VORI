package com.vori.backend.pet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 상점에서 파는 알 등급 (마스터 데이터 — 테이블 대신 코드로 관리).
 *
 * 확률은 정수 퍼센트이며 합이 정확히 100 이다. 소수 비율 대신 정수를 쓰는 이유:
 * 누적합 비교로 추첨하므로 부동소수점 오차가 끼어들 여지가 없다.
 *
 * 구매 시점의 분포는 eggs.probability_distribution 에 JSON 으로 박제되므로,
 * 여기 값을 바꿔도 이미 팔린 알의 확률은 변하지 않는다.
 */
public enum EggGrade {

    BASIC("기본 알", 2_500, 1, 5, 24, 70),
    PREMIUM("고급 알", 6_000, 5, 20, 45, 30),
    LEGENDARY("최고급 알", 15_000, 20, 50, 30, 0);

    private final String displayName;
    private final int price;
    private final Map<PetTier, Integer> distribution;

    EggGrade(String displayName, int price, int s, int a, int b, int c) {
        this.displayName = displayName;
        this.price = price;
        // LinkedHashMap + unmodifiableMap — S→A→B→C 순서를 유지해 JSON 박제 결과가 등급 순으로 읽힌다.
        // (Map.copyOf 는 순서를 보장하지 않으므로 쓰지 않는다)
        Map<PetTier, Integer> d = new LinkedHashMap<>();
        d.put(PetTier.S, s);
        d.put(PetTier.A, a);
        d.put(PetTier.B, b);
        d.put(PetTier.C, c);
        this.distribution = Collections.unmodifiableMap(d);
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }

    /** 등급별 등장 확률(정수 %). 합 100. */
    public Map<PetTier, Integer> distribution() {
        return distribution;
    }
}
