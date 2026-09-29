package com.vori.backend.attendance;
import com.vori.backend.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.YearMonth;
@RestController @RequestMapping("/api/attendance") @RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;
    @GetMapping public AttendanceResponse today(@AuthenticationPrincipal UserPrincipal principal) { return attendanceService.today(principal.getUser().getId()); }
    @PostMapping public AttendanceResponse checkIn(@AuthenticationPrincipal UserPrincipal principal) { return attendanceService.checkIn(principal.getUser().getId()); }
    @GetMapping("/month") public List<AttendanceHistoryResponse> month(@AuthenticationPrincipal UserPrincipal principal, @RequestParam String month) { return attendanceService.month(principal.getUser().getId(), YearMonth.parse(month)); }
    @GetMapping("/items") public List<StatItemResponse> items(@AuthenticationPrincipal UserPrincipal principal) { return attendanceService.listItems(principal.getUser().getId()); }
    @PostMapping("/items/{id}/use") public void use(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) { attendanceService.useItem(principal.getUser().getId(), id); }
}
