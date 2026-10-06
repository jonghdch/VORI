package com.vori.backend.category;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.gemini.GeminiClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 사용자가 입력한 내역(name) 의 의도를 추론해 카테고리 leaf 를 결정.
 *
 * 순서(처음 정해지는 것을 쓴다):
 *  1. 내 기록 — 본인이 같은 이름으로 저장한 지출의 카테고리(가장 최근). 사용자가 고친 분류를 기억한다.
 *  2. 상호·낱말 규칙(RULES)
 *  3. 임베딩 — 시작 직후 비동기로 leaf 전체 embedding 을 캐시해 두고, 입력 embedding 과 cosine 비교
 *  4. 모두 실패하면 categorizeOrFallback 이 "기타 생활"
 *
 * 비용: 시작 시 leaf 수 만큼 embedding 호출 (현재 36개), 이후엔 입력당 1회.
 * 모델: Gemini text-embedding-004 — 무료 tier (분당 1500, 일 무제한)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategorizeService {

    private final CategoryRepository categoryRepository;
    private final GeminiClient geminiClient;
    private final ExpenseRepository expenseRepository;

    // leaf id → 임베딩 벡터 (768차원)
    private final Map<Long, double[]> leafEmbeddings = new HashMap<>();
    // leaf id → 부모 + 자식 정보 (응답 생성용)
    private final Map<Long, CachedLeaf> leafMeta = new HashMap<>();
    private volatile boolean ready = false;

    // 점수 미달 시 null. 0.55 정도면 매칭. 튜닝 필요.
    private static final double MATCH_THRESHOLD = 0.55;

    private record CachedLeaf(Long id, String name, Long parentId, String parentName) {}

    /**
     * Leaf 이름 → 풍부한 임베딩 텍스트 매핑.
     * 라벨만 임베딩하면 "헤드셋 ≈ 헤어" 같은 글자/음 유사도 오인 발생. 예시 단어를 같이 넣어 의미 공간 분리.
     * key = leaf name (CategorySeeder 와 일치), value = "예시1, 예시2, ..." 같은 자연어 hint.
     */
    private static final Map<String, String> LEAF_HINTS = Map.ofEntries(
        // 식비
        Map.entry("외식", "식당, 한식, 일식, 중식, 양식, 분식, 회식, 한정식, 비빔밥, 짜장면, 초밥"),
        Map.entry("카페", "커피, 카페, 스타벅스, 라떼, 디저트, 케이크, 빵"),
        Map.entry("배달", "배달의민족, 배민, 쿠팡이츠, 요기요, 야식 배달"),
        Map.entry("마트·식자재", "이마트, 홈플러스, 마트, 식자재, 식료품, 코스트코"),
        Map.entry("편의점", "GS25, CU, 세븐일레븐, 편의점, 삼각김밥, 컵라면"),
        // 쇼핑
        Map.entry("의류", "옷, 셔츠, 바지, 자켓, 무신사, 유니클로, 자라"),
        Map.entry("신발·잡화", "신발 운동화 구두 부츠 슬리퍼 샌들, 양말 스타킹 깔창 등 발과 신발 관련"),
        Map.entry("가방", "가방, 백팩, 크로스백, 토트백, 지갑"),
        Map.entry("액세서리", "목걸이, 반지, 귀걸이, 시계, 팔찌, 헤어핀"),
        Map.entry("생필품·잡화", "PC 컴퓨터 노트북 데스크탑 모니터 키보드 마우스 헤드셋 이어폰 등 전자제품과 디지털 기기. 충전기 케이블 등 액세서리. 텀블러 다이소 등 생활 잡화."),
        // 뷰티
        Map.entry("화장품", "스킨, 로션, 크림, 립스틱, 파운데이션, 마스카라, 아이섀도"),
        Map.entry("향수", "향수, 디퓨저, 향료, 르라보, 조말론"),
        Map.entry("헤어·미용실", "미용실에서 머리 자르기, 염색, 펌, 파마, 헤어 시술"),
        Map.entry("네일·시술", "네일아트, 손톱, 발톱, 왁싱, 피부관리"),
        // 문화
        Map.entry("영화", "영화관 입장권, CGV 메가박스 롯데시네마, 극장 영화 티켓"),
        Map.entry("공연·뮤지컬", "콘서트 티켓, 뮤지컬 관람, 연극 관람, 라이브 공연 입장권"),
        Map.entry("전시·박물관", "미술 전시회 입장권, 박물관 관람료, 갤러리 입장료"),
        Map.entry("도서", "책, 도서, 교보문고, 알라딘, 예스24, 소설"),
        // 여가
        Map.entry("게임·구독", "OTT 스트리밍 구독 서비스: 넷플릭스 디즈니플러스 왓챠 티빙 웨이브. 음악 구독: 멜론 스포티파이 지니. 게임 스팀 플레이스테이션 닌텐도. 유튜브 프리미엄 정기 결제."),
        Map.entry("취미·레저", "보드게임 카드게임 등 취미용품, 등산 캠핑 낚시 등 야외 레저 활동 장비"),
        Map.entry("여행·숙박", "여행, 호텔, 숙박, 펜션, 게스트하우스, 항공권, 비행기"),
        Map.entry("스포츠·헬스", "헬스장, 피트니스, PT, 요가, 필라테스, 골프, 테니스"),
        // 생활
        Map.entry("대중교통", "지하철, 버스, 교통카드, 정기권"),
        Map.entry("택시", "택시, 카카오택시, 카카오T, 우버"),
        Map.entry("주유", "주유소, 휘발유, 경유, 기름값"),
        Map.entry("주차·통행료", "주차장, 톨게이트, 하이패스, 통행료"),
        Map.entry("장거리 교통", "KTX, SRT, 기차, 고속버스, 시외버스"),
        Map.entry("의료·약국", "병원, 약국, 한의원, 치과, 처방약, 진료비"),
        Map.entry("펫용품", "강아지 사료, 고양이 간식, 반려동물 장난감, 펫 용품, 동물병원 진료비"),
        Map.entry("학용품·문구", "학용품 문구, 공책 다이어리 종이, 볼펜 연필 형광펜, 지우개 자 가위 등 필기도구"),
        Map.entry("기타 생활", "기타 일상 비용"),
        // 고정비
        Map.entry("통신비", "통신비, KT, SKT, LG U+, 휴대폰 요금, 인터넷 요금"),
        Map.entry("공과금", "전기세, 가스비, 수도세, 공과금"),
        Map.entry("주거·관리비", "월세, 전세, 관리비, 임대료"),
        Map.entry("보험", "보험료, 실비보험, 자동차보험, 생명보험"),
        Map.entry("구독료", "OTT 외 정기 구독, SaaS, 멤버십"),
        Map.entry("대출 상환", "대출 상환, 원금, 이자, 카드값")
    );

    @PostConstruct
    void initAsync() {
        // 부팅 차단하지 않게 별도 스레드. 첫 분류 요청 전엔 ready=false 라 fallback 처리.
        new Thread(this::loadEmbeddings, "categorize-init").start();
    }

    private void loadEmbeddings() {
        try {
            List<Category> all = categoryRepository.findAll();
            Map<Long, String> parents = new HashMap<>();
            for (Category c : all) {
                if (c.getParentId() == null) parents.put(c.getId(), c.getName());
            }
            int loaded = 0;
            for (Category c : all) {
                if (c.getParentId() == null) continue; // leaf 만
                if (!Boolean.TRUE.equals(c.getIsActive())) continue;
                String parentName = parents.getOrDefault(c.getParentId(), "");
                // 부모 이름 + 라벨 + 예시 단어 hint 까지 함께 임베딩 → 글자 유사도가 아닌 의미 거리로 정렬
                String hint = LEAF_HINTS.getOrDefault(c.getName(), "");
                String text = hint.isEmpty()
                        ? parentName + " " + c.getName()
                        : parentName + " " + c.getName() + " - " + hint;
                double[] vec = geminiClient.embed(text);
                leafEmbeddings.put(c.getId(), vec);
                leafMeta.put(c.getId(), new CachedLeaf(
                        c.getId(), c.getName(), c.getParentId(), parentName
                ));
                loaded++;
            }
            ready = true;
            log.info("CategorizeService 준비 완료 — {} 개 leaf 임베딩 캐시됨", loaded);
        } catch (Exception e) {
            log.error("카테고리 임베딩 초기화 실패. /api/categorize 응답 안 됨", e);
        }
    }

    // 분류 실패(서비스 미준비·threshold 미달) 시 떨어질 기본 leaf 이름. CategorySeeder 와 일치.
    private static final String FALLBACK_LEAF_NAME = "기타 생활";

    /**
     * 내 기록 → 규칙 → 임베딩 순으로 분류하고, 모두 실패하면 폴백 leaf("기타 생활")로 떨어뜨린다.
     * → 자동 분류가 안 돼도(예: Gemini 미연결) 사용자가 입력을 이어갈 수 있게.
     * userId 가 null 이면 내 기록은 보지 않는다.
     */
    public Result categorizeOrFallback(Long userId, String name) {
        if (name == null || name.isBlank()) return null;
        Result mine = fromHistory(userId, name);
        if (mine != null) return mine;
        Result r = categorize(name);
        return r != null ? r : fallback();
    }

    /**
     * 본인이 같은 이름으로 저장한 지출 중 가장 최근에 저장한 것의 카테고리(점수 1.0).
     * 없거나 그 카테고리(또는 대분류)가 꺼졌으면 null — 꺼진 카테고리는 드롭다운에도 없다.
     * 자동 분류가 틀려 사용자가 드롭다운에서 고쳐 저장하면 그 선택이 지출에 남는다 — 다음부터 그걸 먼저 쓴다.
     * 다른 사용자의 기록은 보지 않는다(같은 「타코」라도 사람마다 외식·배달이 다르다).
     */
    Result fromHistory(Long userId, String name) {
        if (userId == null) return null;
        String key = itemKey(name);
        if (key.isEmpty()) return null;
        List<Long> ids = expenseRepository.findRecentCategoryIdsByItemKey(userId, key, PageRequest.of(0, 1));
        if (ids.isEmpty()) return null;
        return categoryRepository.findById(ids.get(0))
                .filter(c -> c.getParentId() != null && Boolean.TRUE.equals(c.getIsActive()))
                .filter(c -> categoryRepository.findById(c.getParentId())
                        .map(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .orElse(false))
                .map(c -> toResult(c, 1.0, Source.HISTORY))
                .orElse(null);
    }

    /** 내 기록을 찾을 때 비교하는 이름 — 소문자, 띄어쓰기 없음(「문밸리 타코」=「문밸리타코」). */
    static String itemKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    // ───── 애매하면 묻기 ─────

    /** 무엇을 물을지. 질문 문구는 화면이 정한다. */
    public enum AskType {
        WHAT,              // 판매처만 썼다 — 무엇을 샀나
        DINE_OR_DELIVERY   // 매장에서 먹었나, 배달했나
    }

    /** 칩 하나 — label 은 화면에 보일 말, leafName 은 실제로 들어갈 카테고리. */
    public record Candidate(Long leafId, String label, String leafName) {}

    /** 분류 결과 + 물어볼지. askType 이 null 이면 묻지 않는다(candidates 는 빈 목록). */
    public record Suggestion(Result result, AskType askType, List<Candidate> candidates) {}

    private record Choice(String label, String leafName) {}

    private record AskRule(AskType type, List<Choice> choices) {}

    private static final String DELIVERY_LEAF = "배달";
    /** 매장과 포장은 둘 다 외식으로 저장되니 한 칩으로 — 포장한 사람도 고를 게 있게(10/6 채린). */
    private static final List<Choice> DINE_OR_DELIVERY = List.of(
            new Choice("매장·포장", "외식"), new Choice("배달", DELIVERY_LEAF));

    /** 칩 최대 개수. 화면이 끝에 「그 외」(전체 목록)를 하나 더 붙인다. */
    static final int MAX_CHIPS = 4;

    /**
     * 이름만으로는 무엇을 샀는지 모르는 입력 — 이름 전체가 이것과 같을 때만(askKey 로 비교) 묻는다.
     * 「GS25 물티슈」처럼 물건이 같이 적혀 있으면 묻지 않고 규칙·임베딩으로 정한다. 첫 칩이 기본값이다.
     * 칩은 대학생이 그 판매처에서 자주 사는 순서로 MAX_CHIPS 개까지(Gemini·ChatGPT 검토 반영, 10/6).
     */
    private static final Map<String, AskRule> ASK_RULES = askRules();

    private static Map<String, AskRule> askRules() {
        Map<String, AskRule> m = new HashMap<>();
        AskRule store = new AskRule(AskType.WHAT, List.of(new Choice("먹을 것", "편의점"),
                new Choice("생활용품", "생필품·잡화"), new Choice("상비약", "의료·약국"), new Choice("교통카드 충전", "대중교통")));
        for (String n : List.of("편의점", "GS25", "지에스25", "CU", "씨유", "세븐일레븐", "이마트24", "미니스톱")) m.put(askKey(n), store);
        AskRule mart = new AskRule(AskType.WHAT, List.of(new Choice("장보기", "마트·식자재"),
                new Choice("생활용품", "생필품·잡화"), new Choice("화장품", "화장품")));
        for (String n : List.of("마트", "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스", "노브랜드")) m.put(askKey(n), mart);
        m.put(askKey("다이소"), new AskRule(AskType.WHAT, List.of(new Choice("생활용품", "생필품·잡화"),
                new Choice("문구", "학용품·문구"), new Choice("취미·만들기", "취미·레저"), new Choice("간식", "마트·식자재"))));
        m.put(askKey("쿠팡"), new AskRule(AskType.WHAT, List.of(new Choice("생활용품", "생필품·잡화"),
                new Choice("장보기", "마트·식자재"), new Choice("옷", "의류"), new Choice("책", "도서"))));
        m.put(askKey("올리브영"), new AskRule(AskType.WHAT, List.of(new Choice("화장품", "화장품"),
                new Choice("생활용품", "생필품·잡화"), new Choice("상비약", "의료·약국"), new Choice("향수", "향수"))));
        AskRule dish = new AskRule(AskType.DINE_OR_DELIVERY, DINE_OR_DELIVERY);
        for (String n : List.of("치킨", "피자", "타코", "떡볶이", "족발", "보쌈", "짜장면", "짬뽕", "마라탕", "햄버거")) m.put(askKey(n), dish);
        return Map.copyOf(m);
    }

    /** 묻기 목록과 비교하는 이름 — 소문자, 띄어쓰기·기호 없음(「GS 25」「다이소.」도 같게). */
    static String askKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}·ㆍ•]", "");
    }

    /**
     * 화면용 분류 — 내 기록 → 규칙 → 임베딩으로 정한 결과에 「물어볼지」를 더한다.
     * 기본값을 미리 골라 두므로 묻더라도 저장은 막지 않는다. Gemini 호출은 늘지 않는다.
     *  (1) 판매처·음식 이름만 쓴 경우(ASK_RULES): 후보 칩을 준다. 내 기록이 있으면 그걸 기본값으로 두고,
     *      후보에 없으면 칩을 하나 더 붙인다(기본값과 선택 표시가 어긋나지 않게).
     *  (2) 임베딩이 배달로 정한 경우: deliveryCheck.
     */
    public Suggestion suggest(Long userId, String name) {
        if (name == null || name.isBlank()) return null;
        Result mine = fromHistory(userId, name);
        AskRule rule = ASK_RULES.get(askKey(name));
        if (rule != null) {
            List<Candidate> cands = candidates(rule.choices());
            if (mine != null && cands.stream().noneMatch(c -> c.leafId().equals(mine.leafId()))) {
                cands.add(0, new Candidate(mine.leafId(), mine.leafName(), mine.leafName()));
                if (cands.size() > MAX_CHIPS) cands = new ArrayList<>(cands.subList(0, MAX_CHIPS));
            }
            if (cands.size() >= 2) {
                Result def = mine != null ? mine : leafResult(cands.get(0).leafName(), 1.0, Source.ASK);
                if (def != null) return new Suggestion(def, rule.type(), List.copyOf(cands));
            }
        }
        Result r = mine != null ? mine : categorize(name);
        if (r == null) r = fallback();
        return r == null ? null : deliveryCheck(r);
    }

    /**
     * 임베딩이 배달로 정했으면 외식을 기본값으로 매장/배달을 묻는다.
     * 진짜 배달은 대개 배민·쿠팡이츠·배달 같은 낱말이 있어 규칙(RULES)에서 이미 정해진다. 그런데도 임베딩이
     * 배달로 보냈으면 가게 이름일 가능성이 크다(10/3 시연 「문밸리 타코」). 내 기록·규칙으로 정한 건 묻지 않는다.
     */
    Suggestion deliveryCheck(Result r) {
        if (r.source() == Source.EMBEDDING && DELIVERY_LEAF.equals(r.leafName())) {
            List<Candidate> cands = candidates(DINE_OR_DELIVERY);
            if (cands.size() == 2) {
                Result dineIn = leafResult(cands.get(0).leafName(), r.score(), Source.ASK);
                if (dineIn != null) return new Suggestion(dineIn, AskType.DINE_OR_DELIVERY, List.copyOf(cands));
            }
        }
        return new Suggestion(r, null, List.of());
    }

    /** 후보 칩 — 없거나 꺼진 카테고리는 뺀다. */
    private List<Candidate> candidates(List<Choice> choices) {
        List<Candidate> out = new ArrayList<>();
        for (Choice ch : choices) {
            categoryRepository.findFirstByName(ch.leafName())
                    .filter(c -> c.getParentId() != null && Boolean.TRUE.equals(c.getIsActive()))
                    .ifPresent(c -> out.add(new Candidate(c.getId(), ch.label(), c.getName())));
        }
        return out;
    }

    /** Gemini 없이 categories 테이블만으로 폴백 leaf 를 만든다. 없으면 null. */
    private Result fallback() {
        return leafResult(FALLBACK_LEAF_NAME, 0.0, Source.FALLBACK);
    }

    /** leaf 이름으로 결과를 만든다(DB 조회만, Gemini 없음). 없으면 null. */
    private Result leafResult(String leafName, double score, Source source) {
        return categoryRepository.findFirstByName(leafName)
                .filter(c -> c.getParentId() != null)
                .map(c -> toResult(c, score, source))
                .orElse(null);
    }

    private Result toResult(Category leaf, double score, Source source) {
        String parentName = categoryRepository.findById(leaf.getParentId())
                .map(Category::getName)
                .orElse("");
        return new Result(leaf.getId(), leaf.getName(), leaf.getParentId(), parentName, score, source);
    }

    /**
     * 상호·낱말 규칙 — 이름에 있으면 임베딩 없이 바로 그 leaf 로 정한다(점수 1.0).
     *
     * 임베딩은 짧은 이름에서 글자·소리가 비슷한 쪽으로 끌린다(「타코」→택시, 「김밥천국」→편의점, 2026-10-03 측정).
     * 자주 나오고 뜻이 하나뿐인 상호·낱말은 규칙으로 먼저 정해 그 흔들림을 없앤다. Gemini 가 안 돼도 동작한다.
     * 「편의점」「다이소」「쿠팡」「올리브영」「치킨」처럼 무엇을 샀는지 이름만으로 모르는 것은 넣지 않는다
     * — 그런 건 사용자에게 물어야 한다. 「커피」처럼 다른 물건 이름 안에도 들어가는 일반 낱말도 넣지 않는다
     * (커피머신이 카페로 확정되면 화면이 그대로 골라 버린다) — 상호와 장소·서비스 이름만 둔다.
     *
     * 위에서부터 처음 맞는 규칙을 쓴다. 그래서 더 구체적인 것을 먼저 둔다(물건 이름 → 판매처, 이마트24 → 이마트,
     * 고속버스 → 버스, 동물병원 → 병원). 영문 4자 이하(CU·KT·PT 등)는 앞뒤가 영문·숫자가 아닐 때만 맞춘다(CUP 은 아님).
     */
    private static final String[][] RULES = {
        // 물건 이름이 판매처보다 먼저 — 「홈플러스 세제」「GS25 물티슈」는 마트·편의점이 아니라 생필품이다(Codex 검토).
        // 큰 마트·편의점은 식자재 말고도 여러 물건을 팔아, 판매처만으로는 무엇을 샀는지 모른다.
        {"노트북", "생필품·잡화"}, {"충전기", "생필품·잡화"}, {"보조배터리", "생필품·잡화"}, {"휴지", "생필품·잡화"},
        {"물티슈", "생필품·잡화"}, {"세제", "생필품·잡화"},
        {"사료", "펫용품"}, {"형광펜", "학용품·문구"}, {"볼펜", "학용품·문구"},
        // 편의점 (이마트24 가 이마트보다 먼저)
        {"이마트24", "편의점"}, {"GS25", "편의점"}, {"지에스25", "편의점"}, {"CU", "편의점"}, {"씨유", "편의점"},
        {"세븐일레븐", "편의점"}, {"미니스톱", "편의점"},
        // 배달
        {"배달의민족", "배달"}, {"배민", "배달"}, {"쿠팡이츠", "배달"}, {"요기요", "배달"}, {"땡겨요", "배달"}, {"배달", "배달"},
        // 카페
        {"스타벅스", "카페"}, {"메가커피", "카페"}, {"컴포즈", "카페"}, {"빽다방", "카페"}, {"이디야", "카페"},
        {"투썸", "카페"}, {"할리스", "카페"}, {"커피빈", "카페"}, {"공차", "카페"},
        // 「커피」「라떼」「아메리카노」는 넣지 않는다 — 커피머신·커피믹스·라떼 파우더 같은 물건 이름에도 들어간다
        // 외식 — 프랜차이즈·학식
        {"학식", "외식"}, {"김밥천국", "외식"}, {"맥도날드", "외식"}, {"버거킹", "외식"}, {"롯데리아", "외식"},
        {"맘스터치", "외식"}, {"KFC", "외식"}, {"서브웨이", "외식"},
        // 마트·식자재
        {"이마트", "마트·식자재"}, {"홈플러스", "마트·식자재"}, {"롯데마트", "마트·식자재"}, {"코스트코", "마트·식자재"},
        {"트레이더스", "마트·식자재"}, {"노브랜드", "마트·식자재"}, {"장보기", "마트·식자재"},
        // 의류
        {"무신사", "의류"}, {"유니클로", "의류"}, {"에이블리", "의류"}, {"지그재그", "의류"}, {"탑텐", "의류"}, {"스파오", "의류"},
        // 헤어·미용실
        {"미용실", "헤어·미용실"}, {"헤어샵", "헤어·미용실"},
        // 문화
        {"CGV", "영화"}, {"메가박스", "영화"}, {"롯데시네마", "영화"}, {"영화", "영화"},
        {"뮤지컬", "공연·뮤지컬"}, {"콘서트", "공연·뮤지컬"}, {"연극", "공연·뮤지컬"},
        {"교보문고", "도서"}, {"알라딘", "도서"}, {"예스24", "도서"}, {"영풍문고", "도서"}, {"전공책", "도서"},
        // 여가
        {"넷플릭스", "게임·구독"}, {"티빙", "게임·구독"}, {"왓챠", "게임·구독"}, {"디즈니플러스", "게임·구독"}, {"웨이브", "게임·구독"},
        {"유튜브 프리미엄", "게임·구독"}, {"스포티파이", "게임·구독"}, {"멜론", "게임·구독"}, {"스팀", "게임·구독"}, {"닌텐도", "게임·구독"},
        {"PC방", "게임·구독"},
        {"노래방", "취미·레저"}, {"볼링", "취미·레저"}, {"방탈출", "취미·레저"},
        {"에어비앤비", "여행·숙박"}, {"야놀자", "여행·숙박"}, {"여기어때", "여행·숙박"}, {"호텔", "여행·숙박"}, {"펜션", "여행·숙박"},
        {"헬스장", "스포츠·헬스"}, {"필라테스", "스포츠·헬스"}, {"요가", "스포츠·헬스"}, {"클라이밍", "스포츠·헬스"}, {"PT", "스포츠·헬스"},
        // 생활 (고속·시외버스가 버스보다, 카카오택시가 택시보다, 동물병원이 병원보다 먼저)
        {"KTX", "장거리 교통"}, {"SRT", "장거리 교통"}, {"코레일", "장거리 교통"}, {"고속버스", "장거리 교통"}, {"시외버스", "장거리 교통"},
        {"카카오택시", "택시"}, {"카카오T", "택시"}, {"택시", "택시"}, {"우버", "택시"},
        {"교통카드", "대중교통"}, {"티머니", "대중교통"}, {"지하철", "대중교통"}, {"버스", "대중교통"},
        {"동물병원", "펫용품"}, {"펫용품", "펫용품"},
        {"약국", "의료·약국"}, {"치과", "의료·약국"}, {"한의원", "의료·약국"}, {"병원", "의료·약국"},
        {"문구", "학용품·문구"},
        // 「노트」는 넣지 않는다 — 갤럭시 노트 같은 기기 이름에도 들어간다
        // 고정비
        {"휴대폰 요금", "통신비"}, {"통신비", "통신비"}, {"인터넷 요금", "통신비"}, {"SKT", "통신비"}, {"KT", "통신비"}, {"LG U+", "통신비"},
        {"전기요금", "공과금"}, {"전기세", "공과금"}, {"가스비", "공과금"}, {"가스요금", "공과금"}, {"수도요금", "공과금"}, {"수도세", "공과금"},
        {"월세", "주거·관리비"}, {"관리비", "주거·관리비"}, {"전세", "주거·관리비"},
    };

    /** 규칙에 맞으면 그 leaf 이름, 아니면 null. */
    static String ruleLeafName(String name) {
        if (name == null) return null;
        String text = name.trim();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String[] rule : RULES) {
            String key = rule[0];
            if (key.matches("[A-Za-z0-9+ ]{1,4}")) {
                // 짧은 영문은 낱말로만 — "CUP" 안의 CU, "PPT" 안의 PT 는 아니다
                String pattern = "(?<![a-z0-9])" + java.util.regex.Pattern.quote(key.toLowerCase(java.util.Locale.ROOT)) + "(?![a-z0-9])";
                if (java.util.regex.Pattern.compile(pattern).matcher(lower).find()) return rule[1];
            } else if (lower.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                return rule[1];
            }
        }
        return null;
    }

    /**
     * 사용자 입력 → 규칙에 맞으면 그 leaf, 아니면 가장 가까운 leaf. 규칙에 없고 ready=false 거나 threshold 미달이면 null.
     */
    public Result categorize(String name) {
        if (name == null || name.isBlank()) return null;
        String ruled = ruleLeafName(name);
        if (ruled != null) {
            Result r = leafResult(ruled, 1.0, Source.RULE);
            if (r != null) return r;
        }
        if (!ready) return null;
        double[] queryVec = geminiClient.embed(name.trim());

        Long bestId = null;
        double bestScore = -1.0;
        for (Map.Entry<Long, double[]> e : leafEmbeddings.entrySet()) {
            double score = cosine(queryVec, e.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestId = e.getKey();
            }
        }
        if (bestId == null || bestScore < MATCH_THRESHOLD) return null;
        CachedLeaf m = leafMeta.get(bestId);
        return new Result(m.id, m.name, m.parentId, m.parentName, bestScore, Source.EMBEDDING);
    }

    private static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public boolean isReady() { return ready; }

    /** 어디서 정했는지 — 측정 스크립트가 나눠 센다. ASK 는 묻기 규칙이 고른 기본값(사용자가 칩으로 바꿀 수 있다). */
    public enum Source { HISTORY, RULE, EMBEDDING, ASK, FALLBACK }

    public record Result(Long leafId, String leafName, Long parentId, String parentName, double score, Source source) {}
}
