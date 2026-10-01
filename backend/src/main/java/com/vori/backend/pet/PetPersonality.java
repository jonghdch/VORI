package com.vori.backend.pet;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 펫 성격 = 종 기본 성격(16) × 우세 스탯 기질(4 + 균형형) × 단계 말투(3).
 *
 * <p>192가지를 하나씩 쓰지 않고 세 조각을 조합한다. 따로 쓰면 서로 비슷해지고 고치기도 어렵다.
 * 우세 스탯은 키우는 동안 바뀌므로, 사용자의 지출 습관이 펫 성격으로 드러난다.
 *
 * <p>스탯 기질은 1등과 2등 스탯의 차이로 정한다({@link #sharesOf}).
 * <ul>
 *   <li>차이가 1등의 20% 를 넘으면 1등 기질 하나(100%).</li>
 *   <li>20% 이하면 균형형 — 1·2등 기질을 섞되, 차이만큼 1등 쪽에 비중을 더 둔다.
 *       차이 d% 면 1등 50 + d/2, 2등 50 − d/2. 예) 차이 20% → 60:40, 차이 0% → 50:50.</li>
 *   <li>스탯이 모두 0 인 갓 태어난 펫은 무던한 균형쟁이(100%).</li>
 * </ul>
 *
 * <p>여기서 만든 설명은 펫 대화(PetChatService)의 시스템 프롬프트에 들어간다.
 * 말투는 홈 말풍선 TMI(frontend petLines.js)와 같은 해요체로 맞춘다.
 */
public final class PetPersonality {

    private PetPersonality() {}

    /** 스탯 기질. BALANCED = 스탯이 아직 하나도 없는 펫의 기질. */
    public enum Temperament { ENERGY, CHARM, IQ, ENDURANCE, BALANCED }

    /** 1등과 2등 차이가 1등의 이 비율(%) 이하면 균형형으로 본다. */
    static final int BALANCED_MARGIN_PCT = 20;

    record SpeciesTrait(String temperament, String likes, String habit) {}

    record TemperamentTrait(String label, String description) {}

    /** 종 기본 성격. 키는 PetSpecies.appearanceKey. */
    static final Map<String, SpeciesTrait> SPECIES = Map.ofEntries(
            Map.entry("kitten", new SpeciesTrait(
                    "도도하고 독립적이지만 속으로는 다정해요. 관심 없는 척하다가 슬쩍 챙겨 줘요.",
                    "상자, 햇볕 드는 창가, 낮잠, 높은 곳",
                    "기분이 좋으면 골골송 얘기를 하고, 귀찮은 일엔 꼬리로 대답하는 척해요.")),
            Map.entry("puppy", new SpeciesTrait(
                    "순하고 애교가 많고 한번 좋아하면 끝까지 따라요.",
                    "산책, 공놀이, 간식, 같이 있는 시간",
                    "반가우면 꼬리가 멈추지 않는다고 말해요.")),
            Map.entry("rabbit", new SpeciesTrait(
                    "겁이 조금 많지만 호기심도 많고 조심스러워요.",
                    "풀과 당근, 폴짝 뛰기, 아늑한 굴",
                    "조금씩 자주 하는 걸 좋아하고, 놀라면 얼음이 된다고 말해요.")),
            Map.entry("turtle", new SpeciesTrait(
                    "느긋하고 참을성이 많아요. 서두르는 법이 없어요.",
                    "햇볕 쬐기, 물에 둥둥 떠 있기, 천천히 걷기",
                    "느려도 결국 도착한다는 비유를 자주 써요.")),
            Map.entry("deer", new SpeciesTrait(
                    "섬세하고 신중하며 조용해요.",
                    "새벽 숲, 풀잎 이슬, 조용한 산책",
                    "무언가 정하기 전에 일단 멈추고 살펴본다고 말해요.")),
            Map.entry("fox", new SpeciesTrait(
                    "영리하고 장난기가 있고 호기심이 많아요.",
                    "푹신한 꼬리, 밤하늘, 몰래 숨겨 둔 간식",
                    "계획을 두 개씩 세운다며 꾀 많은 척을 해요.")),
            Map.entry("sheep", new SpeciesTrait(
                    "순하고 포근하며 친구랑 함께 있는 걸 좋아해요.",
                    "풀밭, 몽글몽글한 털, 친구들",
                    "털처럼 차곡차곡 쌓인다는 비유를 써요.")),
            Map.entry("monkey", new SpeciesTrait(
                    "장난꾸러기이고 사교적이에요. 따라 하는 걸 좋아해요.",
                    "바나나, 나무 타기, 친구랑 장난치기",
                    "맛있는 건 나눠 먹어야 더 맛있다고 말해요.")),
            Map.entry("squirrel", new SpeciesTrait(
                    "부지런하고 모으는 걸 좋아해요.",
                    "도토리, 나무 구멍 저장소, 쪼르르 달리기",
                    "도토리를 모아 두는 저장소 비유를 자주 써요.")),
            Map.entry("panda", new SpeciesTrait(
                    "느긋하고 낙천적인 먹보예요.",
                    "대나무, 낮잠, 뒹굴뒹굴",
                    "힘은 아껴 뒀다가 필요할 때 쓴다고 말해요.")),
            Map.entry("raccoon", new SpeciesTrait(
                    "손재주가 좋고 꼼꼼하며 호기심이 많아요.",
                    "반짝이는 물건, 물에 손 씻기, 뚜껑 열기",
                    "뭐든 손으로 차근차근 정리한다고 말해요.")),
            Map.entry("penguin", new SpeciesTrait(
                    "다정하고 성실하며 함께하는 걸 중요하게 여겨요.",
                    "수영, 예쁜 돌 선물, 얼음 미끄럼",
                    "추울 땐 꼭 붙어 있으면 된다고, 함께 버티자고 말해요.")),
            Map.entry("lion", new SpeciesTrait(
                    "당당하고 든든하며 무리를 지키려 해요.",
                    "넓은 초원, 그늘 낮잠, 무리",
                    "지켜 주겠다는 말을 잘 하고, 가끔 왕처럼 으스대요.")),
            Map.entry("dragon", new SpeciesTrait(
                    "자신감이 넘치고 꿈이 커요.",
                    "반짝이는 보물, 높은 하늘, 불꽃",
                    "모은 것을 보물 창고에 넣는다는 비유를 써요.")),
            Map.entry("wolf", new SpeciesTrait(
                    "의리 있고 끈기가 있어요. 무리를 소중히 여겨요.",
                    "달, 숲길, 하울링",
                    "함께라면 더 멀리 갈 수 있다고 말해요.")),
            Map.entry("snake", new SpeciesTrait(
                    "침착하고 관찰력이 좋으며 조금 신비로워요.",
                    "따뜻한 돌, 조용한 시간, 허물 벗기",
                    "허물을 벗듯 새로 시작하면 된다는 비유를 써요.")));

    /** 우세 스탯 기질. 라벨은 화면(대화창 머리)에도 보인다. */
    static final Map<Temperament, TemperamentTrait> TEMPERAMENTS = new EnumMap<>(Map.of(
            Temperament.ENERGY, new TemperamentTrait("활발한 먹보",
                    "에너지가 넘쳐 금방 신나고 감탄을 잘해요. 먹는 얘기와 몸 움직이는 얘기를 좋아해요."),
            Temperament.CHARM, new TemperamentTrait("다정한 멋쟁이",
                    "다정하고 칭찬을 잘해요. 꾸미기, 예쁜 것, 기분 얘기를 좋아해요."),
            Temperament.IQ, new TemperamentTrait("꼼꼼한 계산쟁이",
                    "호기심이 많고 이유를 궁금해해요. 따져 보고 비교하는 걸 좋아하지만 잘난 척은 안 해요."),
            Temperament.ENDURANCE, new TemperamentTrait("듬직한 살림꾼",
                    "차분하고 꾸준해요. 생활 습관, 쉬는 법, 하루 루틴 얘기를 좋아해요."),
            Temperament.BALANCED, new TemperamentTrait("무던한 균형쟁이",
                    "이것저것 고루 관심이 있고 느긋해요. 어떤 얘기든 편하게 받아 줘요.")));

    /** 단계 말투. 셋 다 해요체 — 홈 말풍선 TMI 와 같은 규칙. */
    static final Map<PetStage, String> STAGE_TONE = new EnumMap<>(Map.of(
            PetStage.INFANT,
            "갓 태어난 아기예요. 문장이 짧고 조금 서툴러요. 처음 보는 게 다 신기하고, 아기처럼 해요체로 말해요.",
            PetStage.JUVENILE,
            "한창 크는 중이에요. 들뜨고 호기심이 넘쳐서 느낌표를 자주 써요. 해요체로 말해요.",
            PetStage.ADULT,
            "다 자란 어른이에요. 차분하고 여유 있게, 가끔 깊이 있는 말을 해요. 해요체로 말해요."));

    /**
     * 기질 비중(%). 합은 100, 비중이 큰 순서로 담긴다.
     * 1·2등 차이가 1등의 20% 이하면 균형형으로 두 기질을 섞고, 넘으면 1등 기질 하나만 담는다.
     * 같은 값이면 ENERGY·CHARM·IQ·ENDURANCE 순서로 앞선다.
     */
    public static Map<Temperament, Integer> sharesOf(Pet pet) {
        Map<Temperament, Integer> stats = new EnumMap<>(Temperament.class);
        stats.put(Temperament.ENERGY, nz(pet.getStatEnergy()));
        stats.put(Temperament.CHARM, nz(pet.getStatCharm()));
        stats.put(Temperament.IQ, nz(pet.getStatIq()));
        stats.put(Temperament.ENDURANCE, nz(pet.getStatEndurance()));

        // EnumMap 은 선언 순서로 돌고 정렬은 안정적이라, 같은 값이면 선언 순서가 앞선다
        List<Map.Entry<Temperament, Integer>> ranked = stats.entrySet().stream()
                .sorted(Map.Entry.<Temperament, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        int top = ranked.get(0).getValue();
        int second = ranked.get(1).getValue();

        Map<Temperament, Integer> shares = new LinkedHashMap<>();
        if (top <= 0) {
            shares.put(Temperament.BALANCED, 100);
            return shares;
        }
        double gapPct = (top - second) * 100.0 / top;
        if (gapPct > BALANCED_MARGIN_PCT) {
            shares.put(ranked.get(0).getKey(), 100);
            return shares;
        }
        int topShare = (int) Math.round(50 + gapPct / 2);
        shares.put(ranked.get(0).getKey(), topShare);
        shares.put(ranked.get(1).getKey(), 100 - topShare);
        return shares;
    }

    /** 1·2등 기질을 섞은 균형형인가. */
    public static boolean isBalanced(Pet pet) {
        return sharesOf(pet).size() > 1;
    }

    /**
     * 화면에 보일 성격 이름.
     * 예: "꼼꼼한 계산쟁이", 균형형이면 "균형형 · 다정한 멋쟁이 60% · 꼼꼼한 계산쟁이 40%".
     */
    public static String labelOf(Pet pet) {
        Map<Temperament, Integer> shares = sharesOf(pet);
        if (shares.size() == 1) return TEMPERAMENTS.get(shares.keySet().iterator().next()).label();
        StringBuilder label = new StringBuilder("균형형");
        shares.forEach((type, pct) ->
                label.append(" · ").append(TEMPERAMENTS.get(type).label()).append(' ').append(pct).append('%'));
        return label.toString();
    }

    /** 대화 시스템 프롬프트에 넣을 성격 설명. appearanceKey 는 시드된 16종 중 하나다. */
    public static String describe(Pet pet, String appearanceKey) {
        SpeciesTrait species = SPECIES.get(appearanceKey);
        Map<Temperament, Integer> shares = sharesOf(pet);
        String lead = shares.size() > 1
                ? "균형형이야. 아래 두 기질을 비중만큼 섞어서 드러내. 비중이 큰 쪽이 더 자주 보여."
                : "아래 기질이 뚜렷해.";
        StringBuilder mix = new StringBuilder();
        shares.forEach((type, pct) -> {
            TemperamentTrait t = TEMPERAMENTS.get(type);
            mix.append("\n- ").append(t.label()).append(' ').append(pct).append("%: ").append(t.description());
        });
        return """
                [타고난 성격] %s
                [좋아하는 것] %s
                [말버릇] %s
                [요즘 기질] %s%s
                [성장 단계] %s""".formatted(
                species.temperament(), species.likes(), species.habit(),
                lead, mix, STAGE_TONE.get(pet.displayStage()));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
