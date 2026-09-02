package com.vori.backend.goal;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.goal.dto.GoalCreateRequest;
import com.vori.backend.goal.dto.GoalResponse;
import com.vori.backend.goal.dto.GoalUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 절약 목표 관리.
 *
 * 진행률(current_amount)은 여기서 올리지 않는다 — 지출이 등록될 때 ExpenseService 가
 * 절약액을 누적한다. 이 서비스는 목표를 만들고 고치고 조회하는 것만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final CategoryRepository categoryRepository;

    /** 해당 월의 목표 목록. 전체 목표가 먼저, 그다음 카테고리 목표. */
    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId, String yearMonth) {
        List<Goal> goals = goalRepository.findByUserIdAndYearMonth(userId, yearMonth);
        Map<Long, String> names = categoryNames(goals);
        return goals.stream()
                .sorted((a, b) -> {
                    // categoryId == null(전체 목표)을 맨 앞으로
                    if (a.getCategoryId() == null) return b.getCategoryId() == null ? 0 : -1;
                    if (b.getCategoryId() == null) return 1;
                    return Long.compare(a.getCategoryId(), b.getCategoryId());
                })
                .map(g -> GoalResponse.of(g, names.get(g.getCategoryId())))
                .toList();
    }

    @Transactional
    public GoalResponse create(Long userId, GoalCreateRequest req) {
        String categoryName = validateCategory(req.categoryId());
        rejectDuplicate(userId, req.yearMonth(), req.categoryId());

        Goal goal = goalRepository.save(Goal.builder()
                .userId(userId)
                .yearMonth(req.yearMonth())
                .categoryId(req.categoryId())
                .targetAmount(req.targetAmount())
                .build());

        return GoalResponse.of(goal, categoryName);
    }

    @Transactional
    public GoalResponse update(Long userId, Long goalId, GoalUpdateRequest req) {
        Goal goal = findOwned(userId, goalId);

        if (Boolean.TRUE.equals(req.abandon())) {
            goal.abandon();
        }
        if (req.targetAmount() != null) {
            goal.updateTargetAmount(req.targetAmount());
        }

        return GoalResponse.of(goal, validateCategory(goal.getCategoryId()));
    }

    @Transactional
    public void delete(Long userId, Long goalId) {
        goalRepository.delete(findOwned(userId, goalId));
    }

    // ───── 내부 ─────

    private Goal findOwned(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다"));
        if (!goal.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 목표만 수정할 수 있습니다");
        }
        return goal;
    }

    /**
     * 같은 달·같은 대상 목표 중복 차단.
     * 전체 목표(category_id = NULL)는 DB UNIQUE 가 막지 못하므로 여기서 검사해야 한다.
     */
    private void rejectDuplicate(Long userId, String yearMonth, Long categoryId) {
        boolean exists = (categoryId == null)
                ? goalRepository.existsByUserIdAndYearMonthAndCategoryIdIsNull(userId, yearMonth)
                : goalRepository.existsByUserIdAndYearMonthAndCategoryId(userId, yearMonth, categoryId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    categoryId == null
                            ? "해당 월의 전체 목표가 이미 있습니다"
                            : "해당 월의 같은 카테고리 목표가 이미 있습니다");
        }
    }

    /** categoryId 가 있으면 실재하는 카테고리인지 확인하고 이름을 돌려준다. null 이면 전체 목표. */
    private String validateCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .map(Category::getName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다"));
    }

    /** 목록 조회용 카테고리명 일괄 로딩 (N+1 방지). */
    private Map<Long, String> categoryNames(List<Goal> goals) {
        List<Long> ids = goals.stream()
                .map(Goal::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            categoryRepository.findAllById(ids).forEach(c -> names.put(c.getId(), c.getName()));
        }
        return names;
    }
}
