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

    private static final List<Species> SPECIES = List.of(
        new Species("용",     "S", "dragon"),
        new Species("사자",   "S", "lion"),
        new Species("뱀",     "S", "snake"),
        new Species("여우",   "S", "fox"),
        new Species("사슴",   "A", "deer"),
        new Species("펭귄",   "A", "penguin"),
        new Species("늑대",   "A", "wolf"),
        new Species("거북이", "A", "turtle"),
        new Species("강아지", "B", "puppy"),
        new Species("고양이", "B", "kitten"),
        new Species("토끼",   "B", "rabbit"),
        new Species("양",     "B", "sheep"),
        new Species("개구리", "C", "frog"),
        new Species("다람쥐", "C", "squirrel"),
        new Species("원숭이", "C", "monkey"),
        new Species("팬더",   "C", "panda")
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
