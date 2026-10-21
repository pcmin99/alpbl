package com.eatpick;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class RecommendationDemoSeed {

    private final JdbcTemplate db;

    public RecommendationDemoSeed(JdbcTemplate db) {
        this.db = db;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        // 기존 상품 외에 영양 구성이 다른 시연 상품 추가
        addProduct(
                "demo-beans",
                "시연용 콩 샐러드",
                2400,
                4,
                "fiber");

        addProduct(
                "demo-yogurt",
                "시연용 그릭요거트",
                1800,
                5,
                "protein");

        // 실제 브랜드의 검증된 수치가 아닌 시연용 값
        addProfile(
                "hans",
                23, 0, 380, 0, 110,
                1990,
                true);

        addProfile(
                "harim",
                22, 0, 450, 1, 120,
                1790,
                true);

        addProfile(
                "hankki",
                25, 0, 620, 2, 140,
                1850,
                true);

        addProfile(
                "demo-beans",
                12, 9, 180, 3, 220,
                2400,
                true);

        addProfile(
                "demo-yogurt",
                13, 0, 55, 7, 135,
                1800,
                true);
    }

    private void addProduct(
            String id,
            String name,
            int price,
            int basicRank,
            String category) {
        Integer count = db.queryForObject("""
                SELECT COUNT(*)
                FROM products
                WHERE id = ?
                """, Integer.class, id);

        if (count != null && count > 0) {
            return;
        }

        db.update("""
                INSERT INTO products (
                    id,
                    name,
                    package_label,
                    price,
                    fast_delivery,
                    basic_rank,
                    affiliate_url,
                    category
                )
                VALUES (?, ?, '1회분', ?, FALSE, ?, NULL, ?)
                """,
                id,
                name,
                price,
                basicRank,
                category);
    }

    private void addProfile(
            String productId,
            double protein,
            double fiber,
            double sodium,
            double sugar,
            double kcal,
            double pricePerServing,
            boolean convenience) {
        Integer productCount = db.queryForObject("""
                SELECT COUNT(*)
                FROM products
                WHERE id = ?
                """, Integer.class, productId);

        if (productCount == null || productCount == 0) {
            return;
        }

        Integer profileCount = db.queryForObject("""
                SELECT COUNT(*)
                FROM product_recommendation_profiles
                WHERE product_id = ?
                """, Integer.class, productId);

        // 이미 등록한 데이터는 덮어쓰지 않음
        if (profileCount != null && profileCount > 0) {
            return;
        }

        db.update("""
                INSERT INTO product_recommendation_profiles (
                    product_id,
                    protein,
                    fiber,
                    sodium,
                    sugar,
                    kcal,
                    price_per_serving,
                    convenience,
                    recommendation_enabled,
                    is_demo
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE)
                """,
                productId,
                protein,
                fiber,
                sodium,
                sugar,
                kcal,
                pricePerServing,
                convenience);
    }
}