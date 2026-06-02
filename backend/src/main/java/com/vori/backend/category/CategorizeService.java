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
        return categoryRepository.findFirstByName(FALLBACK_LEAF_NAME)
                .filter(c -> c.getParentId() != null)
                .map(c -> {
                    String parentName = categoryRepository.findById(c.getParentId())
                            .map(Category::getName)
                            .orElse("");
                    return new Result(c.getId(), c.getName(), c.getParentId(), parentName, 0.0);
                })
                .orElse(null);
    }

    /**
     * 사용자 입력 → 가장 가까운 leaf 반환. ready=false 또는 threshold 미달이면 null.
     */
    public Result categorize(String name) {
        if (!ready) return null;
        if (name == null || name.isBlank()) return null;
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
