CREATE TABLE IF NOT EXISTS users (
 id VARCHAR(36) PRIMARY KEY, nickname VARCHAR(80) NOT NULL, goal_type VARCHAR(20) NOT NULL DEFAULT 'muscle_gain',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS social_accounts (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, provider VARCHAR(20) NOT NULL,
 subject VARCHAR(255) NOT NULL, UNIQUE(provider,subject), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS auth_sessions (
 token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, expires_at TIMESTAMP NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS auth_codes (
 code_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, challenge VARCHAR(64) NOT NULL,
 expires_at TIMESTAMP NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS nutrition_goals (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, effective_date DATE NOT NULL,
 kcal DECIMAL(12,3) NOT NULL, carb DECIMAL(12,3) NOT NULL, protein DECIMAL(12,3) NOT NULL,
 fat DECIMAL(12,3) NOT NULL, sugar DECIMAL(12,3) NOT NULL, sodium DECIMAL(12,3) NOT NULL, fiber DECIMAL(12,3) NOT NULL,
 UNIQUE(user_id,effective_date), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS foods (
 id VARCHAR(36) PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(80) NOT NULL,
 emoji VARCHAR(20) NOT NULL, serving_label VARCHAR(80) NOT NULL,
 kcal DECIMAL(12,3) NOT NULL, carb DECIMAL(12,3) NOT NULL, protein DECIMAL(12,3) NOT NULL,
 fat DECIMAL(12,3) NOT NULL, sugar DECIMAL(12,3) NOT NULL, sodium DECIMAL(12,3) NOT NULL, fiber DECIMAL(12,3) NOT NULL
);
CREATE TABLE IF NOT EXISTS subscriptions (
 user_id VARCHAR(36) PRIMARY KEY, plan_code VARCHAR(20) NOT NULL CHECK(plan_code IN ('free','premium')),
 period_end TIMESTAMP NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS scan_daily_usage (
 user_id VARCHAR(36) NOT NULL, usage_date DATE NOT NULL, used_count INTEGER NOT NULL CHECK(used_count>=0),
 PRIMARY KEY(user_id,usage_date), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS recognition_jobs (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, request_key VARCHAR(80) NOT NULL,
 plan_code VARCHAR(20) NOT NULL, backend VARCHAR(20) NOT NULL, model_version VARCHAR(120) NOT NULL,
 simulated BOOLEAN NOT NULL, status VARCHAR(20) NOT NULL, error_code VARCHAR(80),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,request_key), UNIQUE(id,user_id), FOREIGN KEY(user_id) REFERENCES users(id),
 CHECK((plan_code='free' AND backend='self_hosted') OR (plan_code='premium' AND backend='llm_api'))
);
CREATE TABLE IF NOT EXISTS recognition_candidates (
 job_id VARCHAR(36) NOT NULL, food_id VARCHAR(36) NOT NULL, candidate_rank INTEGER NOT NULL,
 confidence DECIMAL(8,6), PRIMARY KEY(job_id,candidate_rank), UNIQUE(job_id,food_id),
 CHECK(confidence IS NULL OR confidence BETWEEN 0 AND 1), FOREIGN KEY(job_id) REFERENCES recognition_jobs(id),
 FOREIGN KEY(food_id) REFERENCES foods(id)
);
CREATE TABLE IF NOT EXISTS llm_api_attempts (
 id VARCHAR(36) PRIMARY KEY, job_id VARCHAR(36) NOT NULL, model_version VARCHAR(120) NOT NULL,
 status VARCHAR(20) NOT NULL, input_tokens INTEGER, output_tokens INTEGER, error_code VARCHAR(80),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(job_id) REFERENCES recognition_jobs(id)
);
CREATE TABLE IF NOT EXISTS meals (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, food_id VARCHAR(36) NOT NULL,
 recognition_job_id VARCHAR(36), slot VARCHAR(20) NOT NULL CHECK(slot IN ('breakfast','lunch','dinner','snack')),
 servings DECIMAL(8,3) NOT NULL CHECK(servings>0), eaten_at TIMESTAMP NOT NULL, local_date DATE NOT NULL,
 food_name VARCHAR(80) NOT NULL, emoji VARCHAR(20) NOT NULL,
 kcal DECIMAL(12,3) NOT NULL, carb DECIMAL(12,3) NOT NULL, protein DECIMAL(12,3) NOT NULL,
 fat DECIMAL(12,3) NOT NULL, sugar DECIMAL(12,3) NOT NULL, sodium DECIMAL(12,3) NOT NULL, fiber DECIMAL(12,3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(food_id) REFERENCES foods(id),
 FOREIGN KEY(recognition_job_id,user_id) REFERENCES recognition_jobs(id,user_id)
);
CREATE TABLE IF NOT EXISTS products (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL, package_label VARCHAR(80) NOT NULL,
 price INTEGER NOT NULL, fast_delivery BOOLEAN NOT NULL, basic_rank INTEGER NOT NULL,
 affiliate_url VARCHAR(2048), category VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS shopping_items (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, item_key VARCHAR(80) NOT NULL,
 product_id VARCHAR(36), name VARCHAR(100) NOT NULL, source VARCHAR(10) NOT NULL,
 reason VARCHAR(255), dismissed BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(user_id,item_key), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(product_id) REFERENCES products(id)
);
