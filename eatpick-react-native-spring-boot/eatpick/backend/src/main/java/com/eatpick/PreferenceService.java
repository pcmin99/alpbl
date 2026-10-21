package com.eatpick;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PreferenceService {

    private final JdbcTemplate db;

    public PreferenceService(JdbcTemplate db) {
        this.db = db;
    }

    public record Preference(
        BigDecimal budgetPerServing,
        boolean conveniencePreferred,
        List<String> excludedProductIds
    ) {}

    public Preference load(String userId) {
        var rows = db.queryForList("""
            SELECT budget_per_serving, convenience_preferred
            FROM user_recommendation_preferences
            WHERE user_id = ?
            """, userId);

        BigDecimal budget = null;
        boolean convenience = false;

        if (!rows.isEmpty()) {
            var row = rows.get(0);
            Object rawBudget = row.get("budget_per_serving");

            if (rawBudget != null) {
                budget = new BigDecimal(rawBudget.toString());
            }

            convenience = asBoolean(
                row.get("convenience_preferred")
            );
        }

        List<String> excluded = db.queryForList("""
            SELECT product_id
            FROM user_excluded_products
            WHERE user_id = ?
            ORDER BY product_id
            """, String.class, userId);

        return new Preference(
            budget,
            convenience,
            excluded
        );
    }

    @Transactional
    public Preference save(
        String userId,
        BigDecimal budget,
        boolean conveniencePreferred,
        List<String> excludedProductIds
    ) {
        // 같은 회원의 동시 저장을 직렬화
        db.queryForObject("""
            SELECT id
            FROM users
            WHERE id = ?
            FOR UPDATE
            """, String.class, userId);

        List<String> uniqueIds = new ArrayList<>(
            new LinkedHashSet<>(excludedProductIds)
        );

        for (String productId : uniqueIds) {
            Integer count = db.queryForObject("""
                SELECT COUNT(*)
                FROM products
                WHERE id = ?
                """, Integer.class, productId);

            if (count == null || count == 0) {
                throw Store.error(
                    400,
                    "존재하지 않는 제외 상품입니다: " + productId
                );
            }
        }

        Integer count = db.queryForObject("""
            SELECT COUNT(*)
            FROM user_recommendation_preferences
            WHERE user_id = ?
            """, Integer.class, userId);

        if (count != null && count > 0) {
            db.update("""
                UPDATE user_recommendation_preferences
                SET budget_per_serving = ?,
                    convenience_preferred = ?
                WHERE user_id = ?
                """,
                budget,
                conveniencePreferred,
                userId
            );
        } else {
            db.update("""
                INSERT INTO user_recommendation_preferences (
                    user_id,
                    budget_per_serving,
                    convenience_preferred
                )
                VALUES (?, ?, ?)
                """,
                userId,
                budget,
                conveniencePreferred
            );
        }

        db.update("""
            DELETE FROM user_excluded_products
            WHERE user_id = ?
            """, userId);

        for (String productId : uniqueIds) {
            db.update("""
                INSERT INTO user_excluded_products (
                    user_id,
                    product_id
                )
                VALUES (?, ?)
                """,
                userId,
                productId
            );
        }

        return load(userId);
    }

    static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }

        if (value instanceof Number n) {
            return n.intValue() != 0;
        }

        return Boolean.parseBoolean(
            String.valueOf(value)
        );
    }
}