package com.eatpick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class RecommendationService {

    private static final List<String> NUTRIENTS = List.of(
            "protein",
            "fiber",
            "sodium",
            "sugar",
            "kcal");

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final PreferenceService preferences;

    private final String apiUrl;
    private final String apiKey;
    private final boolean demo;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public RecommendationService(
            JdbcTemplate db,
            ObjectMapper json,
            PreferenceService preferences,

            @Value("${app.recommendation.url:http://127.0.0.1:8000}") String apiUrl,

            @Value("${app.recommendation.key:}") String apiKey,

            @Value("${app.demo:false}") boolean demo) {
        this.db = db;
        this.json = json;
        this.preferences = preferences;
        this.apiUrl = apiUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.demo = demo;
    }

    public List<Map<String, Object>> recommend(
            String userId) {
        if (apiKey.isBlank()) {
            throw Store.error(
                    503,
                    "Python 추천 서비스 키 설정이 필요합니다.");
        }

        LocalDate today = LocalDate.now(ZONE);

        var goalRows = db.queryForList("""
                SELECT protein, fiber, sodium, sugar, kcal
                FROM nutrition_goals
                WHERE user_id = ?
                  AND effective_date <= ?
                ORDER BY effective_date DESC
                LIMIT 1
                """, userId, today);

        if (goalRows.isEmpty()) {
            throw Store.error(
                    422,
                    "영양 목표를 먼저 설정해주세요.");
        }

        Map<String, Double> goal = nutrition(
                goalRows.get(0));

        for (String nutrient : NUTRIENTS) {
            if (goal.get(nutrient) <= 0) {
                throw Store.error(
                        422,
                        "개인 추천을 위해 " + nutrient
                                + " 목표를 0보다 크게 설정해주세요.");
            }
        }

        // DB 원본을 사용하므로 무료 응답용 필드 마스킹에 영향받지 않음
        Map<String, Double> todayNutrition = nutrition(
                db.queryForMap("""
                        SELECT
                            COALESCE(SUM(protein), 0) AS protein,
                            COALESCE(SUM(fiber), 0) AS fiber,
                            COALESCE(SUM(sodium), 0) AS sodium,
                            COALESCE(SUM(sugar), 0) AS sugar,
                            COALESCE(SUM(kcal), 0) AS kcal
                        FROM meals
                        WHERE user_id = ?
                          AND local_date = ?
                        """, userId, today));

        // 오늘 제외 최근 7일. 미기록일은 0으로 만들지 않음
        List<Map<String, Double>> history = db.queryForList("""
                SELECT
                    SUM(protein) AS protein,
                    SUM(fiber) AS fiber,
                    SUM(sodium) AS sodium,
                    SUM(sugar) AS sugar,
                    SUM(kcal) AS kcal
                FROM meals
                WHERE user_id = ?
                  AND local_date >= ?
                  AND local_date < ?
                GROUP BY local_date
                ORDER BY local_date
                """,
                userId,
                today.minusDays(7),
                today)
                .stream()
                .map(this::nutrition)
                .toList();

        var preference = preferences.load(userId);

        // 실제 모드에서는 시연용 영양정보를 추천에 사용하지 않음
        var catalog = db.queryForList("""
                SELECT
                    p.*,
                    n.protein,
                    n.fiber,
                    n.sodium,
                    n.sugar,
                    n.kcal,
                    n.price_per_serving,
                    n.convenience,
                    n.is_demo
                FROM products p
                JOIN product_recommendation_profiles n
                  ON n.product_id = p.id
                WHERE n.recommendation_enabled = TRUE
                  AND (n.is_demo = FALSE OR ? = TRUE)
                ORDER BY p.id
                """, demo);

        if (catalog.isEmpty()) {
            throw Store.error(
                    503,
                    "추천 가능한 상품 영양정보가 없습니다.");
        }

        Map<String, Object> context = new LinkedHashMap<>();

        context.put("goal", goal);
        context.put("today", todayNutrition);
        context.put("history", history);
        context.put(
                "budget_per_serving",
                preference.budgetPerServing());
        context.put(
                "convenience_preferred",
                preference.conveniencePreferred());
        context.put(
                "excluded_product_ids",
                preference.excludedProductIds());

        List<Map<String, Object>> pythonProducts = new ArrayList<>();

        Map<String, Map<String, Object>> productsById = new HashMap<>();

        for (var product : catalog) {
            String id = product.get("id").toString();

            productsById.put(id, product);

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("id", id);
            item.put("name", product.get("name"));

            for (String nutrient : NUTRIENTS) {
                item.put(
                        nutrient,
                        number(product.get(nutrient)));
            }

            item.put(
                    "price_per_serving",
                    product.get("price_per_serving"));
            item.put(
                    "convenience",
                    PreferenceService.asBoolean(
                            product.get("convenience")));

            pythonProducts.add(item);
        }

        Map<String, Object> payload = Map.of(
                "context", context,
                "products", pythonProducts,
                "top_k", Math.min(20, pythonProducts.size()));

        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(apiUrl + "/recommend"))
                    .timeout(Duration.ofSeconds(10))
                    .header(
                            "Content-Type",
                            "application/json")
                    .header(
                            "X-Service-Key",
                            apiKey)
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    json.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw Store.error(
                        503,
                        "추천 서비스 인증 설정을 확인해주세요.");
            }

            if (response.statusCode() != 200) {
                throw Store.error(
                        502,
                        "Python 추천 서비스가 요청을 처리하지 못했습니다.");
            }

            JsonNode root = json.readTree(response.body());

            JsonNode recommendations = root.path("recommendations");

            if (!recommendations.isArray()) {
                throw Store.error(
                        502,
                        "추천 응답 형식이 올바르지 않습니다.");
            }

            List<Map<String, Object>> result = new ArrayList<>();

            Set<String> seen = new HashSet<>();

            for (JsonNode recommendation : recommendations) {
                String productId = recommendation.path("productId").asText();

                var original = productsById.get(productId);

                if (original == null
                        || !seen.add(productId)
                        || preference.excludedProductIds()
                                .contains(productId)) {
                    throw Store.error(
                            502,
                            "추천 응답의 상품 정보가 올바르지 않습니다.");
                }

                Map<String, Object> item = new LinkedHashMap<>(original);

                List<String> reasons = strings(
                        recommendation.path("reasons"));

                boolean demoNutrition = PreferenceService.asBoolean(
                        original.get("is_demo"));

                if (demoNutrition) {
                    reasons.add("시연용 영양정보 기준");
                }

                item.put("reasons", reasons);
                item.put(
                        "warnings",
                        strings(
                                recommendation.path("warnings")));
                item.put(
                        "score",
                        recommendation.path("score").asDouble());
                item.put(
                        "rank",
                        recommendation.path("rank").asInt());
                item.put(
                        "breakdown",
                        json.convertValue(
                                recommendation.path("breakdown"),
                                Map.class));
                item.put(
                        "algorithmVersion",
                        root.path("algorithmVersion").asText());
                item.put(
                        "nutritionDataIsDemo",
                        demoNutrition);

                result.add(item);
            }

            return result;

        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();

            throw Store.error(
                    503,
                    "추천 요청이 중단되었습니다.");

        } catch (ResponseStatusException exc) {
            throw exc;

        } catch (Exception exc) {
            throw Store.error(
                    503,
                    "Python 추천 서버 연결을 확인해주세요.");
        }
    }

    private Map<String, Double> nutrition(
            Map<String, Object> row) {
        Map<String, Double> result = new LinkedHashMap<>();

        for (String nutrient : NUTRIENTS) {
            result.put(
                    nutrient,
                    number(row.get(nutrient)));
        }

        return result;
    }

    private double number(Object value) {
        if (value == null) {
            throw Store.error(
                    422,
                    "추천 계산에 필요한 영양정보가 누락되었습니다.");
        }

        double result = ((Number) value).doubleValue();

        if (!Double.isFinite(result) || result < 0) {
            throw Store.error(
                    422,
                    "유효하지 않은 영양정보입니다.");
        }

        return result;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            throw Store.error(
                    502,
                    "추천 설명 형식이 올바르지 않습니다.");
        }

        List<String> values = new ArrayList<>();

        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw Store.error(
                        502,
                        "추천 설명은 문자열이어야 합니다.");
            }

            values.add(value.asText());
        }

        return values;
    }
}