package com.mungroute.place.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCuratedPlaceMenuRepository implements CuratedPlaceMenuRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCuratedPlaceMenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CuratedPlaceMenu> findByContentId(String contentId) {
        return jdbcTemplate.query("""
                        SELECT content_id, place_name, source_label, source_url,
                               verified_on, verification_note
                        FROM curated_place_menu
                        WHERE content_id = ?
                        """,
                (resultSet, rowNumber) -> new CuratedPlaceMenu(
                        resultSet.getString("content_id"),
                        resultSet.getString("place_name"),
                        resultSet.getString("source_label"),
                        resultSet.getString("source_url"),
                        resultSet.getObject("verified_on", java.time.LocalDate.class),
                        resultSet.getString("verification_note"),
                        findItems(contentId)
                ),
                contentId
        ).stream().findFirst();
    }

    private List<CuratedMenuItem> findItems(String contentId) {
        return jdbcTemplate.query("""
                        SELECT menu_name, price_won, is_specialty, image_url
                        FROM curated_place_menu_item
                        WHERE content_id = ?
                        ORDER BY display_order, menu_item_id
                        """,
                (resultSet, rowNumber) -> new CuratedMenuItem(
                        resultSet.getString("menu_name"),
                        resultSet.getObject("price_won", Long.class),
                        resultSet.getBoolean("is_specialty"),
                        resultSet.getString("image_url")
                ),
                contentId
        );
    }
}
