package com.vori.backend.budget;
import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.budget.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.YearMonth;
import java.util.List;
@RestController @RequestMapping("/api/spending-plan") @RequiredArgsConstructor
public class SpendingPlanController {
 private final SpendingPlanService service;
 @GetMapping public StatBudgetResponse plan(@AuthenticationPrincipal UserPrincipal p,@RequestParam(required=false) String yearMonth){ String ym=yearMonth==null?YearMonth.now().toString():yearMonth; service.ensurePlan(p.getId(),ym); return service.plan(p.getId(),ym); }
 @GetMapping("/fixed-expenses") public List<FixedExpenseResponse> fixed(@AuthenticationPrincipal UserPrincipal p){return service.listFixed(p.getId());}
 @PostMapping("/fixed-expenses") @ResponseStatus(HttpStatus.CREATED) public FixedExpenseResponse add(@AuthenticationPrincipal UserPrincipal p,@Valid @RequestBody FixedExpenseRequest r){return service.addFixed(p.getId(),r);}
 @DeleteMapping("/fixed-expenses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long id){service.removeFixed(p.getId(),id);}
}
