package com.vori.backend.user;

import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.user.dto.WithdrawalRequest;
import com.vori.backend.user.dto.WithdrawalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 탈퇴 — 유예 후 영구 삭제.
 *
 * <p>탈퇴하면 바로 지우지 않고 {@code users.deletion_requested_at} 만 남긴다. 실수로 탈퇴해도
 * 유예 기간(기본 30일) 안에 다시 로그인하면 그대로 복구된다({@link #restoreOnLogin}).
 * 대기 중에는 다른 기기의 세션도 AccountStatusFilter 가 끊고, 배치 작업(판정 알림·AI 일일 코멘트·
 * 월간 리포트)이 대상에서 뺀다. 기간이 지나면 {@link #purgeExpired} 가 매일 새벽 계정과 기록을 지운다.
 *
 * <p>관리자 계정은 탈퇴할 수 없다 — 시연·운영 계정이 사라지면 복구할 길이 없다.
 */
@Slf4j
@Service
public class AccountDeletionService {

    /** 구글 가입자(비밀번호 없음)가 본인 확인으로 입력하는 문구 */
    static final String CONFIRM_TEXT = "탈퇴";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final NotificationService notificationService;
    private final int graceDays;

    public AccountDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  JdbcTemplate jdbc, PlatformTransactionManager txManager,
                                  NotificationService notificationService,
                                  @Value("${account.deletion.grace-days:30}") int graceDays) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.notificationService = notificationService;
        this.graceDays = graceDays;
    }

    /** 탈퇴를 접수한다. 세션 정리는 호출부(UserController)가 한다. */
    @Transactional
    public WithdrawalResponse request(Long userId, WithdrawalRequest req, LocalDateTime now) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        if (user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 계정은 탈퇴할 수 없어요");
        }
        if (user.isPendingDeletion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 탈퇴를 신청한 계정이에요");
        }
        verifyOwner(user, req);

        user.requestDeletion(now);
        log.info("회원 탈퇴 접수 — userId={}, graceDays={}", userId, graceDays);
        return new WithdrawalResponse(now.plusDays(graceDays));
    }

    /**
     * 로그인 직전 — 탈퇴 대기 계정이면 복구한다. AuthController 가 세션을 만들기 전에 부른다.
     *
     * <p>행을 잠그고(SELECT ... FOR UPDATE) 판정한다. purge 도 같은 행을 잠그므로 둘은 차례로 돈다 —
     * 삭제가 먼저 끝났으면 행이 없어 401, 복구가 먼저면 purge 가 대기 해제를 보고 건너뛴다.
     * 유예 기간이 이미 지났으면 새벽 삭제 전이라도 복구하지 않는다. 기준이 purge 와 같아야
     * "30일" 이 정확히 30일이 된다.
     *
     * @return 이번 로그인으로 복구됐으면 true
     */
    @Transactional
    public boolean restoreOnLogin(Long userId, LocalDateTime now) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "탈퇴 처리된 계정이에요"));
        if (!user.isPendingDeletion()) return false;
        if (!user.getDeletionRequestedAt().isAfter(cutoff(now))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "탈퇴 처리된 계정이에요");
        }
        user.cancelDeletion();
        log.info("탈퇴 취소(로그인 복구) — userId={}", userId);
        return true;
    }

    /** 이 시각 이전(포함)에 탈퇴를 신청한 계정은 유예 기간이 끝났다. purge 와 로그인 복구가 같이 쓴다. */
    private LocalDateTime cutoff(LocalDateTime now) {
        return now.minusDays(graceDays);
    }

    /** 이메일 가입자는 비밀번호, 구글 가입자는 확인 문구. */
    private void verifyOwner(User user, WithdrawalRequest req) {
        if (user.getPasswordHash() != null) {
            if (req.password() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않아요");
            }
            return;
        }
        if (req.confirmText() == null || !CONFIRM_TEXT.equals(req.confirmText().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "확인 문구로 '" + CONFIRM_TEXT + "'를 입력해 주세요");
        }
    }

    /**
     * 유예 기간이 지난 탈퇴 대기 계정을 영구 삭제한다. 매일 04:00(한국 시간).
     * 계정마다 트랜잭션을 따로 둬 한 명이 실패해도 나머지는 지운다. 실패하면 관리자에게 알림을 보내고
     * (로그만으로는 아무도 못 본다 — 그러면 처리방침의 "30일 뒤 파기" 를 조용히 어기게 된다)
     * 다음 날 다시 시도된다(대상 조건이 그대로라서).
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void purgeExpiredOnSchedule() {
        purgeExpired(LocalDateTime.now());
    }

    public int purgeExpired(LocalDateTime now) {
        List<Long> ids = userRepository.findIdsDeletionRequestedBefore(cutoff(now));
        int done = 0;
        int failed = 0;
        for (Long id : ids) {
            try {
                Boolean deleted = tx.execute(status -> purge(id));
                if (Boolean.TRUE.equals(deleted)) done++;
            } catch (RuntimeException e) {
                failed++;
                log.error("탈퇴 계정 영구 삭제 실패 — userId={}", id, e);
            }
        }
        if (!ids.isEmpty()) log.info("탈퇴 계정 영구 삭제 — 대상 {}명, 삭제 {}명, 실패 {}명", ids.size(), done, failed);
        if (failed > 0) alertAdmins(failed, now);
        return done;
    }

    /** 영구 삭제 실패를 관리자 알림으로 남긴다. 하루 한 번(dedupe). 알림 자체가 실패해도 삭제 작업은 끝낸다. */
    private void alertAdmins(int failed, LocalDateTime now) {
        try {
            for (User admin : userRepository.findAllByRole(Role.ADMIN)) {
                notificationService.notify(admin.getId(), NotificationType.ADMIN_ALERT,
                        "탈퇴 계정 영구 삭제 실패 " + failed + "건",
                        "서버 로그의 '탈퇴 계정 영구 삭제 실패' 를 확인해 주세요. 내일 다시 시도해요.",
                        null, "account-purge-failed:" + now.toLocalDate());
            }
        } catch (RuntimeException e) {
            log.error("탈퇴 계정 삭제 실패 알림을 보내지 못함", e);
        }
    }

    /**
     * 한 계정을 지운다. 대부분 테이블은 users 를 ON DELETE CASCADE 로 물고 있어 마지막 DELETE 로 같이 지워진다.
     * 그렇지 않은 것만 먼저 정리한다.
     * <ul>
     *   <li>users.active_title_id·pets.equipped_title_award_id — 자기 자식 행(user_titles·pet_title_awards)을
     *       다시 가리켜, cascade 순서에 따라 지우는 도중 참조가 걸릴 수 있어 먼저 비운다.</li>
     *   <li>attendance_checkins·monthly_reports·notifications·user_stat_items — FK 가 NO ACTION 이라
     *       남아 있으면 users 삭제가 막힌다.</li>
     * </ul>
     * 새 테이블이 users 를 NO ACTION 으로 물면 여기서 FK 오류가 나고 purgeExpired 가 ERROR 로 남긴다.
     *
     * @return 지웠으면 true. 그 사이 복구돼(대기 해제) 지울 대상이 아니면 false.
     */
    boolean purge(Long userId) {
        // 대기 상태를 잠그고 확인 — 스케줄러가 도는 사이 로그인으로 복구됐으면 지우지 않는다
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND deletion_requested_at IS NOT NULL FOR UPDATE",
                Integer.class, userId);
        if (pending == null || pending == 0) return false;

        jdbc.update("UPDATE users SET active_title_id = NULL WHERE id = ?", userId);
        jdbc.update("UPDATE pets SET equipped_title_award_id = NULL WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM attendance_checkins WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM monthly_reports WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_stat_items WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        log.info("탈퇴 계정 영구 삭제 완료 — userId={}", userId);
        return true;
    }
}
