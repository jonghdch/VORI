package com.vori.backend.attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UserStatItemRepository extends JpaRepository<UserStatItem, Long> {
    List<UserStatItem> findByUserIdOrderByAcquiredAtDesc(Long userId);
}
