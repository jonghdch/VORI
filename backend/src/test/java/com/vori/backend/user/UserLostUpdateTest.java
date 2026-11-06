package com.vori.backend.user;

import com.vori.backend.auth.dto.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 잠금 없이 읽은 users 행을 고쳐 저장할 때, 그 사이 다른 트랜잭션이 커밋한 컬럼을 덮어쓰지 않는지 실제 DB 로 본다.
 * 지출 수정(totalSaved)이 진행되는 동안 알 구매·보상 지급(game_money)이 먼저 커밋되는 상황.
 * 목으로는 UPDATE 문에 어떤 컬럼이 실리는지 볼 수 없다.
 *
 * <p>실행은 임시 스키마로 — SPRING_DATASOURCE_URL 을 vori_wt_* 로 넘긴다.
 */
@SpringBootTest
class UserLostUpdateTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @Test
    @DisplayName("다른 트랜잭션이 먼저 커밋한 game_money 를 totalSaved 저장이 되돌리지 않는다")
    void savingOneColumnKeepsConcurrentlyCommittedColumn() {
        Long id = userService.signup(new SignupRequest(
                "lost-update-" + System.nanoTime() + "@vori.test", "test1234!", "갱신", "갱신", true, true, false)).getId();
        int before = jdbc.queryForObject("SELECT game_money FROM users WHERE id = ?", Integer.class, id);

        TransactionTemplate outer = new TransactionTemplate(txManager);
        TransactionTemplate other = new TransactionTemplate(txManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            outer.executeWithoutResult(s -> {
                User user = userRepository.findById(id).orElseThrow();
                // 이 트랜잭션이 아직 열려 있는 동안 다른 요청이 코인을 지급하고 커밋한다.
                other.executeWithoutResult(s2 ->
                        jdbc.update("UPDATE users SET game_money = game_money + 500 WHERE id = ?", id));
                user.addTotalSaved(100);
            });

            assertThat(jdbc.queryForObject("SELECT game_money FROM users WHERE id = ?", Integer.class, id))
                    .isEqualTo(before + 500);
            assertThat(jdbc.queryForObject("SELECT total_saved FROM users WHERE id = ?", Integer.class, id))
                    .isEqualTo(100);
        } finally {
            // 두 트랜잭션을 실제로 커밋해야 하는 테스트라 롤백되지 않는다 — 가입으로 생긴 행을 직접 지운다(자식 행은 CASCADE)
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }
}
