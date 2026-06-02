package com.vori.backend.title;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTitleRepository extends JpaRepository<UserTitle, Long> {

    List<UserTitle> findByUserId(Long userId);

    Optional<UserTitle> findByUserIdAndName(Long userId, String name);
}
