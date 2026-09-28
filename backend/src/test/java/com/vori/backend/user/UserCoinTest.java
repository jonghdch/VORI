package com.vori.backend.user;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserCoinTest {
    @Test
    void adminCanSpendRepeatedlyWithNoBalance() {
        User admin = User.builder().role(Role.ADMIN).gameMoney(0).build();
        admin.spendGameMoney(Integer.MAX_VALUE);
        admin.spendGameMoney(Integer.MAX_VALUE);
        assertEquals(0, admin.getGameMoney());
        assertThrows(IllegalArgumentException.class, () -> admin.spendGameMoney(-1));
    }

    @Test
    void regularUserStillPaysAndCannotOverspend() {
        User user = User.builder().role(Role.USER).gameMoney(100).build();
        user.spendGameMoney(70);
        assertEquals(30, user.getGameMoney());
        assertThrows(IllegalStateException.class, () -> user.spendGameMoney(31));
        assertEquals(30, user.getGameMoney());
    }
}
