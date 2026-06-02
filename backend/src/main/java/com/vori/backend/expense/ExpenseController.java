package com.vori.backend.expense;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.expense.dto.ExpenseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ExpenseCreateRequest req
    ) {
        ExpenseResponse response = expenseService.createExpense(principal.getUser().getId(), req);
        return ResponseEntity.ok(response);
    }

    /** ?date=YYYY-MM-DD 의 해당 날짜 지출 목록. 가계부 작성 화면 mount 용. */
    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> listByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(expenseService.listByDate(principal.getUser().getId(), date));
    }
}
