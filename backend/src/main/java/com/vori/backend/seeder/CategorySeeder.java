package com.vori.backend.seeder;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
public class CategorySeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    private record Group(String name, String statType, List<String> details) {}

    private static final List<Group> GROUPS = List.of(
        new Group("식비", "ENERGY", List.of(
            "외식", "카페", "배달", "마트·식자재", "편의점"
        )),
        new Group("쇼핑", "CHARM", List.of(
            "의류", "신발·잡화", "가방", "액세서리", "생필품·잡화"
        )),
        new Group("뷰티", "CHARM", List.of(
            "화장품", "향수", "헤어·미용실", "네일·시술"
        )),
        new Group("문화", "IQ", List.of(
            "영화", "공연·뮤지컬", "전시·박물관", "도서"
        )),
        new Group("여가", "IQ", List.of(
            "게임·구독", "취미·레저", "여행·숙박", "스포츠·헬스"
        )),
        new Group("생활", "ENDURANCE", List.of(
            "대중교통", "택시", "주유", "주차·통행료", "장거리 교통",
            "의료·약국", "펫용품", "학용품·문구", "기타 생활"
        )),
        new Group("고정비", "ENDURANCE", List.of(
            "통신비", "공과금", "주거·관리비", "보험", "구독료", "대출 상환"
        ))
    );

    @Override
    public void run(String... args) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM categories", Integer.class
        );
        if (count != null && count > 0) return;

        int groupSort = 1;
        for (Group g : GROUPS) {
            jdbc.update(
                "INSERT INTO categories (parent_id, name, stat_type, sort_order) " +
                "VALUES (NULL, ?, ?, ?)",
                g.name(), g.statType(), groupSort
            );
            Long parentId = jdbc.queryForObject(
                "SELECT id FROM categories WHERE parent_id IS NULL AND name = ?",
                Long.class, g.name()
            );
            int detailSort = 1;
            for (String detail : g.details()) {
                jdbc.update(
                    "INSERT INTO categories (parent_id, name, stat_type, sort_order) " +
                    "VALUES (?, ?, ?, ?)",
                    parentId, detail, g.statType(), detailSort
                );
                detailSort++;
            }
            groupSort++;
        }
    }
}
