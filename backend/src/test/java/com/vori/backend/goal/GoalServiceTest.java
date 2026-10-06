package com.vori.backend.goal;

import com.vori.backend.category.Category;
import com.vori.backend.category.CategoryRepository;
import com.vori.backend.common.StatType;
import com.vori.backend.goal.dto.GoalCreateRequest;
import com.vori.backend.goal.dto.GoalUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalServiceTest {

    private final GoalRepository goals = mock(GoalRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final GoalService service = new GoalService(goals, categories);

    @Test
    void createsCategoryGoalWithResolvedCategoryName() {
        when(categories.findById(3L)).thenReturn(Optional.of(category(3L, "식비")));
        when(goals.save(any(Goal.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.create(7L, new GoalCreateRequest("2026-10", 3L, 100_000));

        assertThat(result.categoryName()).isEqualTo("식비");
        assertThat(result.targetAmount()).isEqualTo(100_000);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateWholeMonthGoal() {
        when(goals.existsByUserIdAndYearMonthAndCategoryIdIsNull(7L, "2026-10"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                7L, new GoalCreateRequest("2026-10", null, 100_000)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cannotUpdateAnotherUsersGoal() {
        when(goals.findById(10L)).thenReturn(Optional.of(Goal.builder()
                .id(10L).userId(99L).yearMonth("2026-10").targetAmount(100_000).build()));

        assertThatThrownBy(() -> service.update(7L, 10L, new GoalUpdateRequest(200_000, false)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void listsWholeMonthGoalBeforeCategoryGoals() {
        Goal categoryGoal = Goal.builder().id(2L).userId(7L).yearMonth("2026-10")
                .categoryId(3L).targetAmount(50_000).build();
        Goal wholeGoal = Goal.builder().id(1L).userId(7L).yearMonth("2026-10")
                .targetAmount(100_000).build();
        when(goals.findByUserIdAndYearMonth(7L, "2026-10"))
                .thenReturn(List.of(categoryGoal, wholeGoal));
        when(categories.findAllById(List.of(3L))).thenReturn(List.of(category(3L, "식비")));

        var result = service.list(7L, "2026-10");

        assertThat(result).extracting(item -> item.categoryId()).containsExactly(null, 3L);
        verify(categories).findAllById(List.of(3L));
    }

    private static Category category(Long id, String name) {
        return Category.builder().id(id).name(name).statType(StatType.ENERGY).build();
    }
}
