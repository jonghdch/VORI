package com.vori.backend.category;

import com.vori.backend.gemini.GeminiClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자가 입력한 내역(name) 의 의도를 추론해 카테고리 leaf 를 결정.
 *
 * 동작:
 *  1. 시작 직후 비동기로 categories 테이블의 leaf 전체 embedding 을 미리 계산해 캐시
 *  2. 사용자 입력이 오면 input embedding 을 한 번만 받아 cached leaf 들과 cosine 비교
 *  3. 최고 점수 leaf 반환. threshold 미달이면 null (호출자가 "기타" 처리)
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
     * 분류 시도 후 실패하면 폴백 leaf("기타 생활")로 떨어뜨린다.
     * → 자동 분류가 안 돼도(예: Gemini 미연결) 사용자가 입력을 이어갈 수 있게.
     */
    public Result categorizeOrFallback(String name) {
        if (name == null || name.isBlank()) return null;
        Result r = categorize(name);
        return r != null ? r : fallback();
    }

    /** Gemini 없이 categories 테이블만으로 폴백 leaf 를 만든다. 없으면 null. */
    private Result fallback() {
        return leafResult(FALLBACK_LEAF_NAME, 0.0);
    }

    /** leaf 이름으로 결과를 만든다(DB 조회만, Gemini 없음). 없으면 null. */
    private Result leafResult(String leafName, double score) {
        return categoryRepository.findFirstByName(leafName)
                .filter(c -> c.getParentId() != null)
                .map(c -> {
                    String parentName = categoryRepository.findById(c.getParentId())
                            .map(Category::getName)
                            .orElse("");
                    return new Result(c.getId(), c.getName(), c.getParentId(), parentName, score);
                })
                .orElse(null);
    }

    /**
     * 상호·낱말 규칙 — 이름에 있으면 임베딩 없이 바로 그 leaf 로 정한다(점수 1.0).
     *
     * 임베딩은 짧은 이름에서 글자·소리가 비슷한 쪽으로 끌린다(「타코」→택시, 「김밥천국」→편의점, 2026-10-03 측정).
     * 자주 나오고 뜻이 하나뿐인 상호·낱말은 규칙으로 먼저 정해 그 흔들림을 없앤다. Gemini 가 안 돼도 동작한다.
     * 「편의점」「다이소」「쿠팡」「올리브영」「치킨」처럼 무엇을 샀는지 이름만으로 모르는 것은 넣지 않는다
     * — 그런 건 사용자에게 물어야 한다.
     *
     * 위에서부터 처음 맞는 규칙을 쓴다. 그래서 더 구체적인 것을 먼저 둔다(이마트24 → 이마트, 고속버스 → 버스,
     * 동물병원 → 병원, 노트북 → 노트). 영문 4자 이하(CU·KT·PT 등)는 앞뒤가 영문·숫자가 아닐 때만 맞춘다(CUP 은 아님).
     */
    private static final String[][] RULES = {
        // 편의점 (이마트24 가 이마트보다 먼저)
        {"이마트24", "편의점"}, {"GS25", "편의점"}, {"지에스25", "편의점"}, {"CU", "편의점"}, {"씨유", "편의점"},
        {"세븐일레븐", "편의점"}, {"미니스톱", "편의점"},
        // 배달
        {"배달의민족", "배달"}, {"배민", "배달"}, {"쿠팡이츠", "배달"}, {"요기요", "배달"}, {"땡겨요", "배달"}, {"배달", "배달"},
        // 카페
        {"스타벅스", "카페"}, {"메가커피", "카페"}, {"컴포즈", "카페"}, {"빽다방", "카페"}, {"이디야", "카페"},
        {"투썸", "카페"}, {"할리스", "카페"}, {"커피빈", "카페"}, {"공차", "카페"}, {"아메리카노", "카페"}, {"라떼", "카페"}, {"커피", "카페"},
        // 외식 — 프랜차이즈·학식
        {"학식", "외식"}, {"김밥천국", "외식"}, {"맥도날드", "외식"}, {"버거킹", "외식"}, {"롯데리아", "외식"},
        {"맘스터치", "외식"}, {"KFC", "외식"}, {"서브웨이", "외식"},
        // 마트·식자재
        {"이마트", "마트·식자재"}, {"홈플러스", "마트·식자재"}, {"롯데마트", "마트·식자재"}, {"코스트코", "마트·식자재"},
        {"트레이더스", "마트·식자재"}, {"노브랜드", "마트·식자재"}, {"장보기", "마트·식자재"},
        // 의류
        {"무신사", "의류"}, {"유니클로", "의류"}, {"에이블리", "의류"}, {"지그재그", "의류"}, {"탑텐", "의류"}, {"스파오", "의류"},
        // 생필품·잡화 (노트북이 노트보다 먼저)
        {"노트북", "생필품·잡화"}, {"충전기", "생필품·잡화"}, {"보조배터리", "생필품·잡화"}, {"휴지", "생필품·잡화"},
        {"물티슈", "생필품·잡화"}, {"세제", "생필품·잡화"},
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
        {"동물병원", "펫용품"}, {"사료", "펫용품"}, {"펫용품", "펫용품"},
        {"약국", "의료·약국"}, {"치과", "의료·약국"}, {"한의원", "의료·약국"}, {"병원", "의료·약국"},
        {"형광펜", "학용품·문구"}, {"볼펜", "학용품·문구"}, {"문구", "학용품·문구"}, {"노트", "학용품·문구"},
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
            Result r = leafResult(ruled, 1.0);
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
        return new Result(m.id, m.name, m.parentId, m.parentName, bestScore);
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

    public record Result(Long leafId, String leafName, Long parentId, String parentName, double score) {}
}
