package com.vori.backend.category;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.gemini.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.*;

/** 내 기록 → 상호·낱말 규칙 → 임베딩 순서. 기록·규칙은 Gemini 없이 정한다. */
class CategorizeServiceTest {

    private final CategoryRepository repo = mock(CategoryRepository.class);
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);

    @BeforeEach
    void 카테고리_준비() {
        when(repo.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).name("식비").build()));
        for (Category c : List.of(leaf(2L, "외식", true), leaf(3L, "카페", true), leaf(4L, "배달", true),
                leaf(6L, "편의점", true), leaf(12L, "생필품·잡화", true), leaf(25L, "대중교통", true),
                leaf(30L, "의료·약국", true), leaf(40L, "기타 생활", true))) {
            when(repo.findFirstByName(c.getName())).thenReturn(Optional.of(c));
            when(repo.findById(c.getId())).thenReturn(Optional.of(c));
        }
        when(expenses.findRecentCategoryIdsByItemKey(anyLong(), anyString(), any())).thenReturn(List.of());
    }

    private static Category leaf(Long id, String name, boolean active) {
        return Category.builder().id(id).parentId(1L).name(name).isActive(active).build();
    }

    @Test
    void 자주_나오는_상호는_규칙으로_정한다() {
        assertEquals("편의점", CategorizeService.ruleLeafName("GS25 삼각김밥"));
        assertEquals("편의점", CategorizeService.ruleLeafName("CU컵라면"));
        assertEquals("배달", CategorizeService.ruleLeafName("배민 치킨"));
        assertEquals("카페", CategorizeService.ruleLeafName("메가커피 아메리카노"));
        assertEquals("게임·구독", CategorizeService.ruleLeafName("넷플릭스"));
        assertEquals("의료·약국", CategorizeService.ruleLeafName("약국 감기약"));
    }

    @Test
    void 더_구체적인_규칙이_먼저다() {
        assertEquals("편의점", CategorizeService.ruleLeafName("이마트24 도시락"));
        assertEquals("마트·식자재", CategorizeService.ruleLeafName("이마트 장보기"));
        assertEquals("장거리 교통", CategorizeService.ruleLeafName("고속버스 대전"));
        assertEquals("대중교통", CategorizeService.ruleLeafName("버스비"));
        assertEquals("펫용품", CategorizeService.ruleLeafName("동물병원 진료"));
        assertEquals("생필품·잡화", CategorizeService.ruleLeafName("노트북 거치대"));
        assertEquals("통신비", CategorizeService.ruleLeafName("SKT 요금"));
        // 물건 이름이 판매처보다 먼저 — 큰 마트·편의점은 식자재 말고도 판다(Codex 검토)
        assertEquals("생필품·잡화", CategorizeService.ruleLeafName("홈플러스 세제"));
        assertEquals("생필품·잡화", CategorizeService.ruleLeafName("GS25 물티슈"));
        assertEquals("펫용품", CategorizeService.ruleLeafName("이마트 고양이 사료"));
    }

    @Test
    void 짧은_영문은_낱말일_때만_맞춘다() {
        assertNull(CategorizeService.ruleLeafName("CUP 홀더"), "CUP 안의 CU 는 편의점이 아니다");
        assertNull(CategorizeService.ruleLeafName("PPT 템플릿"), "PPT 안의 PT 는 헬스가 아니다");
        assertEquals("장거리 교통", CategorizeService.ruleLeafName("KTX 부산"), "KTX 안의 KT 는 통신비가 아니다");
    }

    @Test
    void 물건_이름_안에_들어가는_일반_낱말로는_정하지_않는다() {
        // 커피머신이 카페로 확정되면 화면이 그대로 골라 버린다(Codex 검토)
        for (String name : new String[]{"커피머신", "커피믹스", "라떼 파우더", "갤럭시 노트"}) {
            assertNull(CategorizeService.ruleLeafName(name), name);
        }
        assertEquals("카페", CategorizeService.ruleLeafName("스타벅스 라떼"), "상호가 있으면 그대로 카페");
    }

    @Test
    void 무엇을_샀는지_모르는_이름은_규칙으로_정하지_않는다() {
        for (String name : new String[]{"편의점", "다이소", "쿠팡", "올리브영", "치킨", "문밸리 타코"}) {
            assertNull(CategorizeService.ruleLeafName(name), name);
        }
    }

    @Test
    void 규칙에_맞으면_임베딩을_부르지_않고_준비_전에도_분류한다() {
        CategorizeService service = new CategorizeService(repo, gemini, expenses); // 임베딩 캐시를 안 만든 상태(ready=false)

        CategorizeService.Result r = service.categorize("GS25 삼각김밥");

        assertEquals("편의점", r.leafName());
        assertEquals("식비", r.parentName());
        assertEquals(1.0, r.score());
        assertEquals(CategorizeService.Source.RULE, r.source());
        verify(gemini, never()).embed(anyString());
    }

    // ───── 내 기록(사용자가 고친 분류 기억하기) ─────

    @Test
    void 내가_전에_고른_카테고리가_규칙보다_먼저다() {
        when(expenses.findRecentCategoryIdsByItemKey(eq(7L), eq("gs25삼각김밥"), any())).thenReturn(List.of(2L));
        CategorizeService service = new CategorizeService(repo, gemini, expenses);

        CategorizeService.Result r = service.categorizeOrFallback(7L, "GS25 삼각김밥");

        assertEquals("외식", r.leafName());
        assertEquals("식비", r.parentName());
        assertEquals(CategorizeService.Source.HISTORY, r.source());
        verify(gemini, never()).embed(anyString());
    }

    @Test
    void 내_기록은_띄어쓰기와_대소문자를_무시하고_찾는다() {
        assertEquals("문밸리타코", CategorizeService.itemKey("문밸리 타코"));
        assertEquals("moonvalley타코", CategorizeService.itemKey(" Moon Valley 타코 "));

        new CategorizeService(repo, gemini, expenses).categorizeOrFallback(7L, "문밸리 타코");

        verify(expenses).findRecentCategoryIdsByItemKey(eq(7L), eq("문밸리타코"), any());
    }

    @Test
    void 내_기록의_카테고리가_꺼졌으면_건너뛰고_규칙으로_정한다() {
        when(repo.findById(2L)).thenReturn(Optional.of(leaf(2L, "외식", false)));
        when(expenses.findRecentCategoryIdsByItemKey(eq(7L), anyString(), any())).thenReturn(List.of(2L));

        CategorizeService.Result r = new CategorizeService(repo, gemini, expenses).categorizeOrFallback(7L, "GS25 삼각김밥");

        assertEquals("편의점", r.leafName());
        assertEquals(CategorizeService.Source.RULE, r.source());
    }

    @Test
    void 내_기록의_대분류가_꺼졌어도_건너뛴다() {
        when(repo.findById(30L)).thenReturn(Optional.of(Category.builder().id(30L).name("여가").isActive(false).build()));
        when(repo.findById(31L)).thenReturn(Optional.of(Category.builder().id(31L).parentId(30L).name("취미·레저").build()));
        when(expenses.findRecentCategoryIdsByItemKey(eq(7L), anyString(), any())).thenReturn(List.of(31L));

        CategorizeService.Result r = new CategorizeService(repo, gemini, expenses).categorizeOrFallback(7L, "GS25 삼각김밥");

        assertEquals("편의점", r.leafName());
        assertEquals(CategorizeService.Source.RULE, r.source());
    }

    @Test
    void 로그인_정보가_없으면_내_기록을_보지_않는다() {
        CategorizeService.Result r = new CategorizeService(repo, gemini, expenses).categorizeOrFallback(null, "GS25 삼각김밥");

        assertEquals(CategorizeService.Source.RULE, r.source());
        verifyNoInteractions(expenses);
    }

    @Test
    void 내_기록은_내_사용자_id_로만_찾는다() {
        new CategorizeService(repo, gemini, expenses).categorizeOrFallback(7L, "GS25 삼각김밥");

        // 쿼리 조건 e.userId = :userId 에 넘기는 값 — 다른 사용자 id 로는 부르지 않는다
        verify(expenses).findRecentCategoryIdsByItemKey(eq(7L), anyString(), any());
        verify(expenses, never()).findRecentCategoryIdsByItemKey(longThat(id -> id != 7L), anyString(), any());
    }

    // ───── 애매하면 묻기 ─────

    private static List<String> labels(CategorizeService.Suggestion s) {
        return s.candidates().stream().map(CategorizeService.Candidate::label).toList();
    }

    @Test
    void 판매처만_쓰면_후보_칩을_주고_첫_칩을_기본값으로_둔다() {
        CategorizeService.Suggestion s = new CategorizeService(repo, gemini, expenses).suggest(7L, "편의점");

        assertEquals(CategorizeService.AskType.WHAT, s.askType());
        assertEquals(List.of("먹을 것", "생활용품", "상비약", "교통카드 충전"), labels(s));
        assertEquals("편의점", s.result().leafName());
        assertEquals(CategorizeService.Source.ASK, s.result().source());
        verify(gemini, never()).embed(anyString());
    }

    @Test
    void 묻기_목록은_띄어쓰기_기호_대소문자를_무시한다() {
        assertEquals("gs25", CategorizeService.askKey(" G S-25 "));
        assertEquals("다이소", CategorizeService.askKey("다이소."));
        assertEquals(CategorizeService.AskType.WHAT,
                new CategorizeService(repo, gemini, expenses).suggest(7L, "gs 25").askType());
    }

    @Test
    void 물건이_같이_적혀_있으면_묻지_않는다() {
        CategorizeService.Suggestion s = new CategorizeService(repo, gemini, expenses).suggest(7L, "GS25 물티슈");

        assertNull(s.askType());
        assertTrue(s.candidates().isEmpty());
        assertEquals("생필품·잡화", s.result().leafName());
        assertEquals(CategorizeService.Source.RULE, s.result().source());
    }

    @Test
    void 판매처만_썼어도_내_기록이_있으면_그걸_기본값으로_둔다() {
        when(expenses.findRecentCategoryIdsByItemKey(eq(7L), eq("편의점"), any())).thenReturn(List.of(12L));

        CategorizeService.Suggestion s = new CategorizeService(repo, gemini, expenses).suggest(7L, "편의점");

        assertEquals(CategorizeService.AskType.WHAT, s.askType());
        assertEquals(List.of("먹을 것", "생활용품", "상비약", "교통카드 충전"), labels(s));
        assertEquals("생필품·잡화", s.result().leafName());
        assertEquals(CategorizeService.Source.HISTORY, s.result().source());
    }

    @Test
    void 내_기록이_후보에_없으면_맨_앞에_칩을_붙이고_최대_개수를_지킨다() {
        when(expenses.findRecentCategoryIdsByItemKey(eq(7L), eq("편의점"), any())).thenReturn(List.of(3L));

        CategorizeService.Suggestion s = new CategorizeService(repo, gemini, expenses).suggest(7L, "편의점");

        assertEquals(List.of("카페", "먹을 것", "생활용품", "상비약"), labels(s));
        assertEquals(CategorizeService.MAX_CHIPS, s.candidates().size());
        assertEquals("카페", s.result().leafName());
    }

    @Test
    void 후보_카테고리가_꺼져_칩이_하나뿐이면_묻지_않는다() {
        when(repo.findFirstByName("생필품·잡화")).thenReturn(Optional.of(leaf(12L, "생필품·잡화", false)));
        when(repo.findFirstByName("의료·약국")).thenReturn(Optional.of(leaf(30L, "의료·약국", false)));
        when(repo.findFirstByName("대중교통")).thenReturn(Optional.empty());

        CategorizeService.Suggestion s = new CategorizeService(repo, gemini, expenses).suggest(7L, "편의점");

        assertNull(s.askType());
        assertEquals("기타 생활", s.result().leafName(), "규칙·임베딩도 못 정하면 지금처럼 기본값");
    }

    @Test
    void 음식_이름만_쓰면_매장_포장과_배달을_묻고_외식을_기본값으로_둔다() {
        CategorizeService.Suggestion s = new CategorizeService(repo, gemini, expenses).suggest(7L, "치킨");

        assertEquals(CategorizeService.AskType.DINE_OR_DELIVERY, s.askType());
        assertEquals(List.of("매장·포장", "배달"), labels(s));
        assertEquals("외식", s.result().leafName());
    }

    @Test
    void 임베딩이_배달로_정하면_외식을_기본값으로_묻는다() {
        CategorizeService service = new CategorizeService(repo, gemini, expenses);
        CategorizeService.Result embedded = new CategorizeService.Result(4L, "배달", 1L, "식비", 0.617,
                CategorizeService.Source.EMBEDDING); // 10/3 「문밸리 타코」 실측

        CategorizeService.Suggestion s = service.deliveryCheck(embedded);

        assertEquals(CategorizeService.AskType.DINE_OR_DELIVERY, s.askType());
        assertEquals("외식", s.result().leafName());
        assertEquals(0.617, s.result().score());
    }

    @Test
    void 규칙이나_내_기록으로_배달이면_묻지_않는다() {
        CategorizeService service = new CategorizeService(repo, gemini, expenses);
        for (CategorizeService.Source src : List.of(CategorizeService.Source.RULE, CategorizeService.Source.HISTORY)) {
            CategorizeService.Result r = new CategorizeService.Result(4L, "배달", 1L, "식비", 1.0, src);
            assertNull(service.deliveryCheck(r).askType(), src.name());
        }
        // 「배민 치킨」은 음식 이름만 쓴 게 아니고 배민 규칙으로 정해진다
        when(repo.findFirstByName("배달")).thenReturn(Optional.of(leaf(4L, "배달", true)));
        CategorizeService.Suggestion s = service.suggest(7L, "배민 치킨");
        assertNull(s.askType());
        assertEquals("배달", s.result().leafName());
    }
}
