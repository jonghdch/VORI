package com.vori.backend.expense;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.expense.dto.ExpenseCreateRequest;
import com.vori.backend.expense.dto.ExpenseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
