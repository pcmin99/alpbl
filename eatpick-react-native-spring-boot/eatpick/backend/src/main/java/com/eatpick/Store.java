package com.eatpick;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class Store {
    final JdbcTemplate db;
    final RecommendationService recommendations;

    static final List<String> N = List.of("kcal", "carb", "protein", "fat", "sugar", "sodium", "fiber");
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public Store(
            JdbcTemplate db,
            RecommendationService recommendations) {
        this.db = db;
        this.recommendations = recommendations;
    }

    JdbcTemplate db() {
        return db;
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    static ResponseStatusException error(int code, String msg) {
        return new ResponseStatusException(HttpStatus.valueOf(code), msg);
    }

    static double num(Object n) {
        return n instanceof Number ? ((Number) n).doubleValue() : Double.parseDouble(n.toString());
    }

    List<Map<String, Object>> rows(String sql, Object... args) {
        return db.queryForList(sql, args);
    }

    Map<String, Object> one(String sql, Object... args) {
        var r = rows(sql, args);
        if (r.isEmpty())
            throw error(404, "데이터를 찾을 수 없어요");
        return r.get(0);
    }

    boolean premium(String u) {
        return !rows("SELECT user_id FROM subscriptions WHERE user_id=? AND plan_code='premium' AND period_end>?", u,
                Timestamp.from(Instant.now())).isEmpty();
    }

    void requirePremium(String u) {
        if (!premium(u))
            throw error(403, "프리미엄 기능이에요");
    }

    @Bean
    ApplicationRunner seed(ObjectMapper json) {
        return args -> {
            if (db.queryForObject("SELECT COUNT(*) FROM foods", Integer.class) == 0) {
                var in = Store.class.getResourceAsStream("/catalog.json");
                List<Map<String, Object>> fs = json.readValue(in, new TypeReference<>() {
                });
                for (var f : fs)
                    db.update(
                            "INSERT INTO foods(id,code,name,emoji,serving_label,kcal,carb,protein,fat,sugar,sodium,fiber) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            f.get("id"), f.get("code"), f.get("name"), f.get("emoji"), f.get("servingLabel"),
                            f.get("kcal"), f.get("carb"), f.get("protein"), f.get("fat"), f.get("sugar"),
                            f.get("sodium"), f.get("fiber"));
            }
            if (db.queryForObject("SELECT COUNT(*) FROM products", Integer.class) == 0) {
                db.update("INSERT INTO products VALUES('hans','한스바커 닭가슴살','10팩 묶음',19900,TRUE,3,NULL,'protein')");
                db.update("INSERT INTO products VALUES('harim','하림 닭가슴살','10팩 묶음',17900,TRUE,1,NULL,'protein')");
                db.update("INSERT INTO products VALUES('hankki','한끼통살 닭가슴살','10팩 묶음',18500,FALSE,2,NULL,'protein')");
            }
        };
    }

    @Transactional
    String createUser(String name) {
        String u = id();
        db.update("INSERT INTO users(id,nickname) VALUES(?,?)", u, name);
        db.update("INSERT INTO nutrition_goals VALUES(?,?,?,?,?,?,?,?,?,?)", id(), u, LocalDate.now(ZONE), 2300, 320,
                100, 65, 50, 2000, 25);
        return u;
    }

    Map<String, Object> mask(Map<String, Object> input, boolean paid) {
        var out = new LinkedHashMap<>(input);
        if (!paid) {
            out.remove("sugar");
            out.remove("sodium");
            out.remove("fiber");
        }
        return out;
    }

    Map<String, Object> goal(String u, LocalDate date) {
        return one(
                "SELECT * FROM nutrition_goals WHERE user_id=? AND effective_date<=? ORDER BY effective_date DESC LIMIT 1",
                u, date);
    }

    Map<String, Object> me(String u) {
        var m = new LinkedHashMap<>(one("SELECT id,nickname,goal_type FROM users WHERE id=?", u));
        boolean paid = premium(u);
        m.put("premium", paid);
        m.put("aiBackend", paid ? "llm_api" : "self_hosted");
        m.put("goal", mask(goal(u, LocalDate.now(ZONE)), paid));
        var use = rows("SELECT used_count FROM scan_daily_usage WHERE user_id=? AND usage_date=?", u,
                LocalDate.now(ZONE));
        m.put("scansLeft", paid ? -1 : Math.max(0, 5 - (use.isEmpty() ? 0 : (int) num(use.get(0).get("used_count")))));
        return m;
    }

    List<Map<String, Object>> foods(String u, String q) {
        boolean paid = premium(u);
        return rows("SELECT * FROM foods WHERE name LIKE ? ORDER BY name", "%" + q + "%").stream()
                .map(f -> mask(f, paid)).toList();
    }

    Map<String, Object> home(String u, LocalDate date) {
        boolean paid = premium(u);
        var meals = rows("SELECT * FROM meals WHERE user_id=? AND local_date=? ORDER BY eaten_at", u, date);
        Map<String, Object> total = new LinkedHashMap<>();
        for (String n : N)
            total.put(n, meals.stream().mapToDouble(m -> num(m.get(n))).sum());
        return Map.of("date", date, "meals", meals.stream().map(m -> mask(m, paid)).toList(), "total",
                mask(total, paid), "goal", mask(goal(u, date), paid));
    }

    @Transactional
    Map<String, Object> saveMeal(String u, Api.MealInput in) {
        one("SELECT id FROM users WHERE id=? FOR UPDATE", u);
        if (in.recognitionJobId() != null) {
            var j = one("SELECT * FROM recognition_jobs WHERE id=? AND user_id=?", in.recognitionJobId(), u);
            if (!"succeeded".equals(j.get("status")))
                throw error(409, "분석 완료 후 기록해주세요");
        }
        var f = one("SELECT * FROM foods WHERE code=?", in.foodCode());
        var now = in.eatenAt() == null ? Instant.now() : in.eatenAt();
        String id = id();
        db.update(
                "INSERT INTO meals(id,user_id,food_id,recognition_job_id,slot,servings,eaten_at,local_date,food_name,emoji,kcal,carb,protein,fat,sugar,sodium,fiber) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, u, f.get("id"), in.recognitionJobId(), in.slot(), in.servings(), Timestamp.from(now),
                now.atZone(ZONE).toLocalDate(), f.get("name"), f.get("emoji"), num(f.get("kcal")) * in.servings(),
                num(f.get("carb")) * in.servings(), num(f.get("protein")) * in.servings(),
                num(f.get("fat")) * in.servings(), num(f.get("sugar")) * in.servings(),
                num(f.get("sodium")) * in.servings(), num(f.get("fiber")) * in.servings());
        return Map.of("id", id);
    }

    void deleteMeal(String u, String id) {
        if (db.update("DELETE FROM meals WHERE id=? AND user_id=?", id, u) == 0)
            throw error(404, "기록을 찾을 수 없어요");
    }

    @Transactional
    void setGoal(String u, Api.GoalInput g) {
        one("SELECT id FROM users WHERE id=? FOR UPDATE", u);
        var d = LocalDate.now(ZONE);
        db.update("DELETE FROM nutrition_goals WHERE user_id=? AND effective_date=?", u, d);
        db.update("INSERT INTO nutrition_goals VALUES(?,?,?,?,?,?,?,?,?,?)", id(), u, d, g.kcal(), g.carb(),
                g.protein(), g.fat(), g.sugar(), g.sodium(), g.fiber());
        db.update("UPDATE users SET goal_type=? WHERE id=?", g.goalType(), u);
    }

    Map<String, Object> report(String u, int days) {
        requirePremium(u);
        var end = LocalDate.now(ZONE);
        var start = end.minusDays(days - 1);
        var daily = rows(
                "SELECT local_date,SUM(kcal) kcal,SUM(protein) protein,SUM(carb) carb,SUM(fat) fat,SUM(sugar) sugar,SUM(sodium) sodium,SUM(fiber) fiber FROM meals WHERE user_id=? AND local_date BETWEEN ? AND ? GROUP BY local_date ORDER BY local_date",
                u, start, end);
        var avg = new LinkedHashMap<String, Object>();
        for (String n : N)
            avg.put(n, daily.stream().mapToDouble(x -> num(x.get(n))).average().orElse(0));
        return Map.of("days", days, "recordedDays", daily.size(), "daily", daily, "average", avg, "goal", goal(u, end),
                "feedback",
                daily.isEmpty() ? "아직 기록이 없어요. 식사를 기록하면 분석을 시작해요." : "기록한 날의 평균을 기준으로 목표와 비교해보세요. 미기록일은 평균에서 제외했어요.");
    }

    List<Map<String, Object>> products(
            String userId,
            String mode) {
        if ("ai".equals(mode)) {
            requirePremium(userId);

            // 고정 순위 대신 Python 개인화 추천 호출
            return recommendations.recommend(userId);
        }

        // 무료 기본 추천은 기존 순위 유지
        var products = rows("""
                SELECT *
                FROM products
                ORDER BY basic_rank, id
                """);

        var home = home(
                userId,
                LocalDate.now(ZONE));

        var consumed = (Map<?, ?>) home.get("total");

        var target = (Map<?, ?>) home.get("goal");

        double gap = Math.max(
                0,
                num(target.get("protein"))
                        - num(consumed.get("protein")));

        for (var product : products) {
            product.put(
                    "reasons",
                    List.of(
                            "오늘 단백질 목표까지 "
                                    + Math.round(gap) + "g",
                            "기본 추천 순위"));
        }

        return products;
    }

    @Transactional
    List<Map<String, Object>> shopping(String u) {
        one("SELECT id FROM users WHERE id=? FOR UPDATE", u);
        for (var entry : List.of(new String[] { "chicken", "닭가슴살", "단백질 보충" },
                new String[] { "egg", "달걀", "아침 단백질 보충" }, new String[] { "yogurt", "그릭요거트", "간식으로 단백질 보충" })) {
            if (rows("SELECT id FROM shopping_items WHERE user_id=? AND item_key=?", u, "auto-" + entry[0]).isEmpty())
                db.update("INSERT INTO shopping_items(id,user_id,item_key,name,source,reason) VALUES(?,?,?,?,?,?)",
                        id(), u, "auto-" + entry[0], entry[1], "auto", entry[2]);
        }
        return rows("SELECT * FROM shopping_items WHERE user_id=? AND dismissed=FALSE ORDER BY source,name", u);
    }

    @Transactional
    void addShopping(String u, String product) {
        one("SELECT id FROM users WHERE id=? FOR UPDATE", u);
        var p = one("SELECT * FROM products WHERE id=?", product);
        var old = rows("SELECT id FROM shopping_items WHERE user_id=? AND item_key=?", u, "product-" + product);
        if (old.isEmpty())
            db.update("INSERT INTO shopping_items(id,user_id,item_key,product_id,name,source) VALUES(?,?,?,?,?,?)",
                    id(), u, "product-" + product, product, p.get("name"), "manual");
        else
            db.update("UPDATE shopping_items SET dismissed=FALSE WHERE user_id=? AND item_key=?", u,
                    "product-" + product);
    }

    void dismissShopping(String u, String id) {
        if (db.update("UPDATE shopping_items SET dismissed=TRUE WHERE id=? AND user_id=?", id, u) == 0)
            throw error(404, "항목을 찾을 수 없어요");
    }

    @Transactional
    void demoPlan(String u, boolean paid) {
        one("SELECT id FROM users WHERE id=? FOR UPDATE", u);
        db.update("DELETE FROM subscriptions WHERE user_id=?", u);
        db.update("INSERT INTO subscriptions VALUES(?,?,?)", u, paid ? "premium" : "free",
                Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
    }
}
