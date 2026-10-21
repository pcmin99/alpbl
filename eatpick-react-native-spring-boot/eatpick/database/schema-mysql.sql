-- MySQL 8.0.16+ / 8.4, 새 DB에 한 번 실행. 기존 DB 삭제 없음.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE DATABASE IF NOT EXISTS eatpick CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE eatpick;
CREATE TABLE users (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), nickname VARCHAR(255) NOT NULL,
 avatar_url TEXT, email VARCHAR(255), timezone VARCHAR(255) NOT NULL DEFAULT 'Asia/Seoul',
 status VARCHAR(255) NOT NULL DEFAULT 'active' CHECK(status IN ('active','suspended','withdrawn')),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)) ENGINE=InnoDB;
-- email은 식별자가 아님. provider + issuer + subject로 계정 식별.
CREATE TABLE social_accounts (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 provider VARCHAR(255) NOT NULL CHECK(provider IN ('kakao','naver','google','apple')),
 issuer VARCHAR(255) NOT NULL, subject VARCHAR(255) NOT NULL, provider_email VARCHAR(255), email_verified boolean NOT NULL DEFAULT false,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), last_login_at DATETIME(6),
 UNIQUE(provider,issuer,subject),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX social_accounts_user ON social_accounts(user_id);
CREATE TABLE auth_sessions (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 refresh_token_hash VARCHAR(255) NOT NULL UNIQUE, expires_at DATETIME(6) NOT NULL,
 revoked_at DATETIME(6), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(expires_at > created_at),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX auth_sessions_user ON auth_sessions(user_id);
CREATE TABLE user_preferences (
 user_id CHAR(36) PRIMARY KEY,
 convenience_food_preferred boolean NOT NULL DEFAULT false,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE nutrition_goals (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 goal_type VARCHAR(255) NOT NULL CHECK(goal_type IN ('diet','maintain','muscle_gain')),
 effective_date date NOT NULL,
  kcal numeric(12,3) NOT NULL CHECK (kcal >= 0),
  carb_g numeric(12,3) NOT NULL CHECK (carb_g >= 0),
  protein_g numeric(12,3) NOT NULL CHECK (protein_g >= 0),
  fat_g numeric(12,3) NOT NULL CHECK (fat_g >= 0),
  sugar_g numeric(12,3) NOT NULL CHECK (sugar_g >= 0),
  sodium_mg numeric(12,3) NOT NULL CHECK (sodium_mg >= 0),
  fiber_g numeric(12,3) NOT NULL CHECK (fiber_g >= 0),
 UNIQUE(user_id,effective_date),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE foods (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), code VARCHAR(255) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL,
 image_url TEXT, active boolean NOT NULL DEFAULT true) ENGINE=InnoDB;
CREATE INDEX foods_name ON foods(name);
CREATE TABLE food_versions (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), food_id CHAR(36) NOT NULL,
 version integer NOT NULL CHECK(version > 0), serving_label VARCHAR(255) NOT NULL,
 serving_grams numeric(10,3) CHECK(serving_grams > 0), source VARCHAR(255) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  kcal numeric(12,3) NOT NULL CHECK (kcal >= 0),
  carb_g numeric(12,3) NOT NULL CHECK (carb_g >= 0),
  protein_g numeric(12,3) NOT NULL CHECK (protein_g >= 0),
  fat_g numeric(12,3) NOT NULL CHECK (fat_g >= 0),
  sugar_g numeric(12,3) NOT NULL CHECK (sugar_g >= 0),
  sodium_mg numeric(12,3) NOT NULL CHECK (sodium_mg >= 0),
  fiber_g numeric(12,3) NOT NULL CHECK (fiber_g >= 0),
 UNIQUE(food_id,version),
 FOREIGN KEY (food_id) REFERENCES foods(id)
) ENGINE=InnoDB;
CREATE TABLE recognition_jobs (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 request_key VARCHAR(255) NOT NULL, storage_key TEXT, source VARCHAR(255) NOT NULL CHECK(source IN ('camera','upload','sample')),
 model_version VARCHAR(255) NOT NULL, status VARCHAR(255) NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','running','succeeded','failed')),
 error_code VARCHAR(255), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), completed_at DATETIME(6),
 UNIQUE(user_id,request_key), UNIQUE(id,user_id),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX recognition_jobs_user ON recognition_jobs(user_id,created_at);
CREATE TABLE recognition_candidates (
 job_id CHAR(36) NOT NULL,
 `rank` smallint NOT NULL CHECK(`rank` > 0), food_version_id CHAR(36) NOT NULL,
 confidence numeric(6,5) NOT NULL CHECK(confidence BETWEEN 0 AND 1),
 PRIMARY KEY(job_id,`rank`), UNIQUE(job_id,food_version_id),
 FOREIGN KEY (job_id) REFERENCES recognition_jobs(id) ON DELETE CASCADE,
 FOREIGN KEY (food_version_id) REFERENCES food_versions(id)
) ENGINE=InnoDB;
CREATE TABLE meals (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 eaten_at DATETIME(6) NOT NULL, local_date date NOT NULL,
 slot VARCHAR(255) NOT NULL CHECK(slot IN ('breakfast','lunch','dinner','snack')),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE(id,user_id),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX meals_user_date ON meals(user_id,local_date,slot);
CREATE TABLE meal_items (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), meal_id CHAR(36) NOT NULL,
 user_id CHAR(36) NOT NULL, food_version_id CHAR(36) NOT NULL,
 servings numeric(8,3) NOT NULL CHECK(servings > 0), recognition_job_id CHAR(36),
 source VARCHAR(255) NOT NULL CHECK(source IN ('manual','camera','upload','sample')),
 FOREIGN KEY(meal_id,user_id) REFERENCES meals(id,user_id) ON DELETE CASCADE,
 FOREIGN KEY(recognition_job_id,user_id) REFERENCES recognition_jobs(id,user_id),
 FOREIGN KEY (food_version_id) REFERENCES food_versions(id)
) ENGINE=InnoDB;
CREATE INDEX meal_items_meal ON meal_items(meal_id);
CREATE TABLE plans (
 code VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL, monthly_price_krw integer NOT NULL CHECK(monthly_price_krw >= 0),
 daily_scan_limit integer CHECK(daily_scan_limit >= 0), detailed_nutrition boolean NOT NULL,
 personalized_recommendations boolean NOT NULL, reports boolean NOT NULL, ai_coach boolean NOT NULL) ENGINE=InnoDB;
INSERT INTO plans VALUES ('free','무료',0,5,false,false,false,false),('premium','프리미엄',4900,NULL,true,true,true,true);
CREATE TABLE subscriptions (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 plan_code VARCHAR(255) NOT NULL, provider VARCHAR(255) NOT NULL, external_id VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL CHECK(status IN ('pending','active','past_due','expired','canceled')),
 period_start DATETIME(6) NOT NULL, period_end DATETIME(6) NOT NULL,
 cancel_at_period_end boolean NOT NULL DEFAULT false,
 UNIQUE(provider,external_id), CHECK(period_end > period_start),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY (plan_code) REFERENCES plans(code)
) ENGINE=InnoDB;
CREATE INDEX subscriptions_user ON subscriptions(user_id,period_end);
CREATE TABLE billing_events (
 provider VARCHAR(255) NOT NULL, event_id VARCHAR(255) NOT NULL, subscription_id CHAR(36),
 event_type VARCHAR(255) NOT NULL, received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), processed_at DATETIME(6),
 PRIMARY KEY(provider,event_id),
 FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE SET NULL
) ENGINE=InnoDB;
CREATE TABLE scan_daily_usage (
 user_id CHAR(36) NOT NULL, usage_date date NOT NULL,
 used_count integer NOT NULL DEFAULT 0 CHECK(used_count >= 0), PRIMARY KEY(user_id,usage_date),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE products (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), name VARCHAR(255) NOT NULL, package_label VARCHAR(255) NOT NULL,
 image_url TEXT, merchant VARCHAR(255) NOT NULL DEFAULT 'coupang', external_product_id VARCHAR(255) NOT NULL,
 affiliate_url TEXT NOT NULL, price_krw integer NOT NULL CHECK(price_krw >= 0),
 fast_delivery boolean NOT NULL DEFAULT false, basic_rank integer CHECK(basic_rank > 0),
 active boolean NOT NULL DEFAULT true, checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(merchant,external_product_id)) ENGINE=InnoDB;
CREATE TABLE recommendation_runs (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 mode VARCHAR(255) NOT NULL CHECK(mode IN ('basic','personalized')), window_start date NOT NULL, window_end date NOT NULL,
 model_version VARCHAR(255) NOT NULL, context JSON NOT NULL DEFAULT (JSON_OBJECT()), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(window_end >= window_start), UNIQUE(id,user_id),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE recommendation_items (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), run_id CHAR(36) NOT NULL,
 user_id CHAR(36) NOT NULL, product_id CHAR(36), food_id CHAR(36),
 `rank` integer NOT NULL CHECK(`rank` > 0), display_name VARCHAR(255) NOT NULL, reasons JSON NOT NULL DEFAULT (JSON_ARRAY()),
 FOREIGN KEY(run_id,user_id) REFERENCES recommendation_runs(id,user_id) ON DELETE CASCADE,
 CHECK(product_id IS NOT NULL OR food_id IS NOT NULL), CHECK(JSON_TYPE(reasons)='ARRAY'),
 UNIQUE(run_id,`rank`), UNIQUE(id,user_id),
 FOREIGN KEY (product_id) REFERENCES products(id),
 FOREIGN KEY (food_id) REFERENCES foods(id)
) ENGINE=InnoDB;
CREATE TABLE shopping_items (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 product_id CHAR(36), food_id CHAR(36), name VARCHAR(255) NOT NULL,
 quantity numeric(10,3) NOT NULL DEFAULT 1 CHECK(quantity > 0),
 source VARCHAR(255) NOT NULL CHECK(source IN ('manual','auto')), recommendation_item_id CHAR(36),
 status VARCHAR(255) NOT NULL DEFAULT 'active' CHECK(status IN ('active','dismissed','purchased')),
 reason TEXT, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(recommendation_item_id,user_id) REFERENCES recommendation_items(id,user_id),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY (product_id) REFERENCES products(id),
 FOREIGN KEY (food_id) REFERENCES foods(id)
) ENGINE=InnoDB;
ALTER TABLE shopping_items
 ADD COLUMN active_product_id CHAR(36) GENERATED ALWAYS AS
 (CASE WHEN status='active' THEN product_id ELSE NULL END) VIRTUAL,
 ADD COLUMN auto_food_id CHAR(36) GENERATED ALWAYS AS
 (CASE WHEN source='auto' THEN food_id ELSE NULL END) VIRTUAL,
 ADD UNIQUE KEY shopping_active_product(user_id,active_product_id),
 ADD UNIQUE KEY shopping_auto_food(user_id,auto_food_id);
CREATE INDEX shopping_user ON shopping_items(user_id,status);
CREATE TABLE diet_plans (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36),
 goal_type VARCHAR(255) NOT NULL CHECK(goal_type IN ('diet','maintain','muscle_gain')),
 title VARCHAR(255) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE diet_plan_items (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), plan_id CHAR(36) NOT NULL,
 day_offset integer NOT NULL DEFAULT 0 CHECK(day_offset >= 0),
 slot VARCHAR(255) NOT NULL CHECK(slot IN ('breakfast','lunch','dinner','snack')),
 food_version_id CHAR(36) NOT NULL, servings numeric(8,3) NOT NULL CHECK(servings > 0),
 FOREIGN KEY (plan_id) REFERENCES diet_plans(id) ON DELETE CASCADE,
 FOREIGN KEY (food_version_id) REFERENCES food_versions(id)
) ENGINE=InnoDB;
CREATE TABLE coach_feedback (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), user_id CHAR(36) NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL,
 kind VARCHAR(255) NOT NULL CHECK(kind IN ('simple','detailed','coach')), content TEXT NOT NULL,
 model_version VARCHAR(255) NOT NULL, context JSON NOT NULL DEFAULT (JSON_OBJECT()), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(period_end >= period_start),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX feedback_user ON coach_feedback(user_id,period_end);
CREATE VIEW daily_nutrition AS
SELECT m.user_id,m.local_date,
 sum(v.kcal * i.servings) AS kcal,
 sum(v.carb_g * i.servings) AS carb_g,
 sum(v.protein_g * i.servings) AS protein_g,
 sum(v.fat_g * i.servings) AS fat_g,
 sum(v.sugar_g * i.servings) AS sugar_g,
 sum(v.sodium_mg * i.servings) AS sodium_mg,
 sum(v.fiber_g * i.servings) AS fiber_g
FROM meals m JOIN meal_items i ON i.meal_id=m.id JOIN food_versions v ON v.id=i.food_version_id
GROUP BY m.user_id,m.local_date;
-- v2: 무료 자체 온디바이스 AI / 유료 외부 LLM API
CREATE TABLE ai_models (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), code VARCHAR(255) NOT NULL UNIQUE,
 backend VARCHAR(255) NOT NULL CHECK(backend IN ('self_hosted','llm_api')),
 execution_location VARCHAR(255) NOT NULL CHECK(execution_location IN ('on_device','server','external_api')),
 provider VARCHAR(255) NOT NULL, model_name VARCHAR(255) NOT NULL, version VARCHAR(255) NOT NULL,
 active boolean NOT NULL DEFAULT false,
 CHECK((backend='self_hosted' AND execution_location IN ('on_device','server'))
    OR (backend='llm_api' AND execution_location='external_api')),
 UNIQUE(id,backend)) ENGINE=InnoDB;
INSERT INTO ai_models(code,backend,execution_location,provider,model_name,version,active) VALUES
 ('eatpick-mobilenet-v3','self_hosted','on_device','eatpick','MobileNetV3','unconfigured',false),
 ('premium-llm','llm_api','external_api','unconfigured','unconfigured','unconfigured',false);
ALTER TABLE plans ADD COLUMN ai_backend VARCHAR(255);
UPDATE plans SET ai_backend=CASE code WHEN 'free' THEN 'self_hosted' WHEN 'premium' THEN 'llm_api' END;
ALTER TABLE plans MODIFY COLUMN ai_backend VARCHAR(255) NOT NULL;
ALTER TABLE plans ADD CONSTRAINT plans_ai_backend_check CHECK(
 (code='free' AND ai_backend='self_hosted') OR (code='premium' AND ai_backend='llm_api'));
ALTER TABLE plans ADD CONSTRAINT plans_code_backend_unique UNIQUE(code,ai_backend);
CREATE TABLE plan_ai_routes (
 plan_code VARCHAR(255) PRIMARY KEY, backend VARCHAR(255) NOT NULL, model_id CHAR(36) NOT NULL,
 FOREIGN KEY(plan_code,backend) REFERENCES plans(code,ai_backend),
 FOREIGN KEY(model_id,backend) REFERENCES ai_models(id,backend)) ENGINE=InnoDB;
INSERT INTO plan_ai_routes SELECT p.code,p.ai_backend,m.id FROM plans p
JOIN ai_models m ON m.code=CASE p.code WHEN 'free' THEN 'eatpick-mobilenet-v3' ELSE 'premium-llm' END;
-- 새 DB용: 요청 시점 플랜과 모델은 필수입니다.
ALTER TABLE recognition_jobs ADD COLUMN plan_code_at_request VARCHAR(255) NOT NULL,
 ADD FOREIGN KEY (plan_code_at_request) REFERENCES plans(code);
ALTER TABLE recognition_jobs ADD COLUMN ai_backend VARCHAR(255) NOT NULL;
ALTER TABLE recognition_jobs ADD COLUMN model_id CHAR(36) NOT NULL;
ALTER TABLE recognition_jobs ADD CONSTRAINT recognition_model_backend_fk
 FOREIGN KEY(model_id,ai_backend) REFERENCES ai_models(id,backend);
-- 무료/유료와 실제 모델 backend가 일치해야 합니다.
ALTER TABLE recognition_jobs ADD CONSTRAINT recognition_routing_check CHECK(
 plan_code_at_request IS NOT NULL AND ai_backend IS NOT NULL AND model_id IS NOT NULL AND
 ((plan_code_at_request='free' AND ai_backend='self_hosted') OR
  (plan_code_at_request='premium' AND ai_backend='llm_api')));
CREATE INDEX recognition_jobs_model ON recognition_jobs(model_id,created_at);
-- LLM이 실제 확률을 제공하지 않을 수 있으므로 신뢰도 미제공은 NULL.
ALTER TABLE recognition_candidates MODIFY COLUMN confidence NUMERIC(6,5) NULL;
CREATE TABLE llm_api_attempts (
 id CHAR(36) PRIMARY KEY DEFAULT (UUID()), job_id CHAR(36) NOT NULL,
 attempt_no integer NOT NULL CHECK(attempt_no > 0), provider_request_id VARCHAR(255),
 status VARCHAR(255) NOT NULL CHECK(status IN ('pending','running','succeeded','failed','unknown')),
 input_tokens integer CHECK(input_tokens >= 0), output_tokens integer CHECK(output_tokens >= 0),
 cost_amount numeric(18,8) CHECK(cost_amount >= 0), cost_currency varchar(3),
 latency_ms integer CHECK(latency_ms >= 0), error_code VARCHAR(255),
 started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), completed_at DATETIME(6),
 UNIQUE(job_id,attempt_no), CHECK((cost_amount IS NULL)=(cost_currency IS NULL)),
 FOREIGN KEY (job_id) REFERENCES recognition_jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER llm_attempt_backend_insert BEFORE INSERT ON llm_api_attempts
FOR EACH ROW
BEGIN
 IF NOT EXISTS (SELECT 1 FROM recognition_jobs WHERE id=NEW.job_id AND ai_backend='llm_api') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LLM attempts require an llm_api recognition job';
 END IF;
END$$
CREATE TRIGGER llm_attempt_backend_update BEFORE UPDATE ON llm_api_attempts
FOR EACH ROW
BEGIN
 IF NOT EXISTS (SELECT 1 FROM recognition_jobs WHERE id=NEW.job_id AND ai_backend='llm_api') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LLM attempts require an llm_api recognition job';
 END IF;
END$$
DELIMITER ;
