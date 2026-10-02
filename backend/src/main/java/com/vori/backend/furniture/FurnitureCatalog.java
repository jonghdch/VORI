package com.vori.backend.furniture;

import com.vori.backend.common.StatType;

import java.math.BigDecimal;

/**
 * 상점에서 파는 가구 (마스터 데이터 — 테이블 대신 코드로 관리).
 *
 * user_furniture 가 이름·가격·보너스를 직접 들고 있는 반정규화 구조라 마스터 테이블이 없다.
 * 구매 시 여기 값을 그대로 복사해 넣으므로, 나중에 이 표를 고쳐도 이미 팔린 가구는 변하지 않는다.
 * (알 EggGrade 와 같은 원칙)
 *
 * releaseBonusPct 는 펫 분양가에 가산되는 비율(%). 마이룸에 **배치한** 가구만 계산에 들어간다
 * — PetService.calculateReleaseValue 참조.
 *
 * themeName 은 theme_master.name 을 가리킨다(id 가 아니라 이름 — 시드가 AUTO_INCREMENT 라
 * id 를 코드에 박으면 환경마다 어긋난다). null 이면 테마 없는 가구다.
 * 벽지·바닥은 일부러 테마가 없다 — 세트는 배치된 가구만 세는데 좌표 처리가 보류라
 * 배치될 수 없어, 테마에 넣으면 그 세트가 영원히 발동하지 않는다. V9__theme_seed.sql 참조.
 *
 * 테마는 두 종류다. 우드·코지·스터디는 세트 보너스가 있는 테마(V9), 오션·캠핑·모던·프린세스·
 * 동굴·우주는 상점에서 골라 보기 위한 분류용 테마로 세트 보너스가 0% 다(V39__furniture_themes.sql).
 * 여기서 테마를 옮기면 이미 팔린 가구의 theme_id 는 그대로이므로, 마이그레이션으로 함께 옮긴다.
 */
public enum FurnitureCatalog {

    // 우드 — 세트 +8% (3개)
    BOOKSHELF("책장", FurnitureCategory.SHELF, StatType.IQ, "2.00", 5_000, "우드"),
    DRAWER_CHEST("서랍장", FurnitureCategory.DRAWER, StatType.ENDURANCE, "2.00", 5_000, "우드"),
    WALL_PICTURE("액자", FurnitureCategory.PICTURE, StatType.ENDURANCE, "1.50", 3_000, "우드"),

    // 코지 — 세트 +12% (3개), 칭호 「절약 새싹」 해금
    COZY_BED("포근한 침대", FurnitureCategory.BED, StatType.ENERGY, "3.00", 8_000, "코지"),
    SMALL_MIRROR("거울", FurnitureCategory.MIRROR, StatType.CHARM, "1.50", 3_000, "코지"),
    VANITY_TABLE("화장대", FurnitureCategory.VANITY, StatType.CHARM, "3.00", 8_000, "코지"),
    ROCKING_CHAIR("흔들의자", FurnitureCategory.ROCKING_CHAIR, StatType.IQ, "2.00", 5_000, "코지"),
    FIREPLACE("벽난로", FurnitureCategory.FIREPLACE, StatType.ENDURANCE, "3.00", 8_000, "코지"),

    // 스터디 — 세트 +15% (2개), 칭호 「기록의 시작」 해금
    CORK_BOARD("코르크 보드", FurnitureCategory.BOARD, StatType.IQ, "1.50", 3_000, "스터디"),
    DESK("책상", FurnitureCategory.DESK, StatType.IQ, "2.00", 5_000, "스터디"),
    EMPTY_DESK("빈 책상", FurnitureCategory.EMPTY_DESK, StatType.IQ, "1.50", 3_000, "스터디"),

    // 오션 — 분류용 테마 (세트 보너스 0%)
    PARASOL("파라솔", FurnitureCategory.PARASOL, StatType.CHARM, "1.50", 3_000, "오션"),
    BEACH_BALL("비치볼", FurnitureCategory.BEACH_BALL, StatType.CHARM, "1.50", 3_000, "오션"),
    HAMMOCK("해먹", FurnitureCategory.HAMMOCK, StatType.ENERGY, "2.00", 5_000, "오션"),
    SWIM_TUBE("튜브", FurnitureCategory.SWIM_TUBE, StatType.ENDURANCE, "2.00", 5_000, "오션"),

    // 캠핑 — 분류용 테마 (세트 보너스 0%)
    CAMPING_TENT("텐트", FurnitureCategory.TENT, StatType.ENERGY, "3.00", 8_000, "캠핑"),
    CAMPFIRE("모닥불", FurnitureCategory.CAMPFIRE, StatType.ENDURANCE, "2.00", 5_000, "캠핑"),
    CAMP_CHAIR("캠핑 의자", FurnitureCategory.CAMP_CHAIR, StatType.IQ, "1.50", 3_000, "캠핑"),
    LANTERN("랜턴", FurnitureCategory.LANTERN, StatType.CHARM, "1.50", 3_000, "캠핑"),
    ICEBOX("아이스박스", FurnitureCategory.ICEBOX, StatType.ENDURANCE, "2.00", 5_000, "캠핑"),

    // 모던 — 분류용 테마 (세트 보너스 0%)
    DESKTOP_PC("컴퓨터", FurnitureCategory.COMPUTER, StatType.IQ, "4.00", 12_000, "모던"),
    LEATHER_SOFA("가죽 소파", FurnitureCategory.LEATHER_SOFA, StatType.ENERGY, "3.00", 8_000, "모던"),
    GLASS_TABLE("유리 커피 테이블", FurnitureCategory.GLASS_TABLE, StatType.CHARM, "2.00", 5_000, "모던"),
    SAFE("금고", FurnitureCategory.SAFE, StatType.IQ, "3.00", 8_000, "모던"),

    // 프린세스 — 분류용 테마 (세트 보너스 0%)
    CANOPY_BED("캐노피 침대", FurnitureCategory.CANOPY_BED, StatType.ENERGY, "4.00", 12_000, "프린세스"),
    TEA_TABLE("티 테이블", FurnitureCategory.TEA_TABLE, StatType.CHARM, "2.00", 5_000, "프린세스"),

    // 동굴 — 분류용 테마 (세트 보너스 0%)
    TREASURE_CHEST("보물상자", FurnitureCategory.TREASURE_CHEST, StatType.CHARM, "3.00", 8_000, "동굴"),

    // 우주 — 분류용 테마 (세트 보너스 0%)
    SLEEP_CAPSULE("수면캡슐", FurnitureCategory.SLEEP_CAPSULE, StatType.ENERGY, "4.00", 12_000, "우주"),
    TELESCOPE("망원경", FurnitureCategory.TELESCOPE, StatType.IQ, "3.00", 8_000, "우주"),

    // 테마 없음 — 단품 보너스만
    PICNIC_MAT("피크닉 매트", FurnitureCategory.PICNIC_MAT, StatType.CHARM, "2.00", 5_000, null),
    FRIDGE("냉장고", FurnitureCategory.FRIDGE, StatType.ENDURANCE, "3.00", 8_000, null),

    // 벽지·바닥 — 방 전체 (테마 없음)
    PLAIN_WALLPAPER("기본 벽지", FurnitureCategory.WALLPAPER, StatType.CHARM, "1.00", 2_000, null),
    WOOD_FLOOR("원목 바닥", FurnitureCategory.FLOOR, StatType.ENDURANCE, "1.00", 2_000, null);

    private final String displayName;
    private final FurnitureCategory category;
    private final StatType statTarget;
    private final BigDecimal releaseBonusPct;
    private final int price;
    private final String themeName;

    FurnitureCatalog(String displayName, FurnitureCategory category, StatType statTarget,
                     String releaseBonusPct, int price, String themeName) {
        this.displayName = displayName;
        this.category = category;
        this.statTarget = statTarget;
        // 문자열로 받아 BigDecimal 생성 — double 을 거치면 3.00 이 2.9999… 로 들어간다
        this.releaseBonusPct = new BigDecimal(releaseBonusPct);
        this.price = price;
        this.themeName = themeName;
    }

    public String displayName() { return displayName; }
    public FurnitureCategory category() { return category; }
    public StatType statTarget() { return statTarget; }
    public BigDecimal releaseBonusPct() { return releaseBonusPct; }
    public int price() { return price; }
    /** theme_master.name. 테마 없는 가구는 null. */
    public String themeName() { return themeName; }
}
