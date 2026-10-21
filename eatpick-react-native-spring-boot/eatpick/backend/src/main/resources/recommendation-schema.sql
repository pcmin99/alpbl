-- MySQL에서는 eatpick DB를 선택한 상태에서 실행
-- USE eatpick;

CREATE TABLE IF NOT EXISTS product_recommendation_profiles (
    product_id VARCHAR(36) PRIMARY KEY,

    -- 모두 1회 섭취량 기준
    protein DECIMAL(12,3) NOT NULL CHECK (protein >= 0),
    fiber DECIMAL(12,3) NOT NULL CHECK (fiber >= 0),
    sodium DECIMAL(12,3) NOT NULL CHECK (sodium >= 0),
    sugar DECIMAL(12,3) NOT NULL CHECK (sugar >= 0),
    kcal DECIMAL(12,3) NOT NULL CHECK (kcal >= 0),

    price_per_serving DECIMAL(12,2) NOT NULL
        CHECK (price_per_serving > 0),

    convenience BOOLEAN NOT NULL DEFAULT FALSE,

    recommendation_enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- 시연 데이터와 실제 상품 데이터를 구분
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,

    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS user_recommendation_preferences (
    user_id VARCHAR(36) PRIMARY KEY,

    budget_per_serving DECIMAL(12,2)
        CHECK (
            budget_per_serving IS NULL
            OR budget_per_serving > 0
        ),

    convenience_preferred BOOLEAN NOT NULL DEFAULT FALSE,

    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_excluded_products (
    user_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,

    PRIMARY KEY (user_id, product_id),

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);