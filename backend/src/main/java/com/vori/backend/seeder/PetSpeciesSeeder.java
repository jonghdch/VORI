package com.vori.backend.seeder;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(3)
@RequiredArgsConstructor
public class PetSpeciesSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    /** 회원가입 시 자동 지급하는 시작 펫. V7 마이그레이션과 값이 일치해야 한다. */
    private static final String STARTER_NAME = "강아지";

    private record Species(String name, String tier, String appearanceKey) {}

    // 등급 표기: S=레전드, A=에픽, B=희귀, C=일반 (프론트 petCatalog.js 와 동일하게 유지)
    private static final List<Species> SPECIES = List.of(
        new Species("용",     "S", "dragon"),
        new Species("늑대",   "S", "wolf"),
        new Species("뱀",     "S", "snake"),
        new Species("판다",   "A", "panda"),
        new Species("너구리", "A", "raccoon"),
        new Species("펭귄",   "A", "penguin"),
        new Species("사자",   "A", "lion"),
        new Species("사슴",   "B", "deer"),
        new Species("여우",   "B", "fox"),
        new Species("양",     "B", "sheep"),
        new Species("원숭이", "B", "monkey"),
        new Species("다람쥐", "B", "squirrel"),
        new Species("고양이", "C", "kitten"),
        new Species("강아지", "C", "puppy"),
        new Species("토끼",   "C", "rabbit"),
        new Species("거북이", "C", "turtle")
    );

    @Override
    public void run(String... args) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pet_species", Integer.class
        );
        if (count != null && count > 0) return;

        for (Species s : SPECIES) {
            jdbc.update(
                "INSERT INTO pet_species (name, tier, is_starter, appearance_key) " +
                "VALUES (?, ?, ?, ?)",
                s.name(), s.tier(), s.name().equals(STARTER_NAME), s.appearanceKey()
            );
        }
    }
}
