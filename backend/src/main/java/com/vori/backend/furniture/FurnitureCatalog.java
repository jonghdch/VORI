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
 */
public enum FurnitureCatalog {

    // 침대 — 에너지
    BASIC_BED("기본 침대", FurnitureCategory.BED, StatType.ENERGY, "1.50", 3_000, null),
    COZY_BED("포근한 침대", FurnitureCategory.BED, StatType.ENERGY, "3.00", 8_000, "코지"),

    // 거울·화장대 — 매력
    SMALL_MIRROR("작은 거울", FurnitureCategory.MIRROR, StatType.CHARM, "1.50", 3_000, "코지"),
    VANITY_TABLE("화장대", FurnitureCategory.VANITY, StatType.CHARM, "3.00", 8_000, "코지"),

    // 책장·컴퓨터 — 지능
    BOOKSHELF("책장", FurnitureCategory.SHELF, StatType.IQ, "2.00", 5_000, "우드"),
    DESKTOP_PC("컴퓨터", FurnitureCategory.COMPUTER, StatType.IQ, "4.00", 12_000, "스터디"),

    // 서랍·액자 — 지구력
    DRAWER_CHEST("서랍장", FurnitureCategory.DRAWER, StatType.ENDURANCE, "2.00", 5_000, "우드"),
    WALL_PICTURE("액자", FurnitureCategory.PICTURE, StatType.ENDURANCE, "1.50", 3_000, "우드"),

    // 벽지·바닥 — 방 전체 (테마 없음)
    PLAIN_WALLPAPER("기본 벽지", FurnitureCategory.WALLPAPER, StatType.CHARM, "1.00", 2_000, null),
    WOOD_FLOOR("원목 바닥", FurnitureCategory.FLOOR, StatType.ENDURANCE, "1.00", 2_000, null),
    CORK_BOARD("코르크 보드", FurnitureCategory.BOARD, StatType.IQ, "1.50", 3_000, "스터디");

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
