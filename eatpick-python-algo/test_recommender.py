import os
import unittest

from dataclasses import replace
from unittest.mock import patch

from fastapi.testclient import TestClient

from api import app
from demo import PRODUCTS, make_context
from recommender import recommend


class RecommendationTests(unittest.TestCase):

    def test_users_have_different_top_products(self):
        protein_result = recommend(
            make_context("protein"),
            PRODUCTS,
        )

        fiber_result = recommend(
            make_context("fiber"),
            PRODUCTS,
        )

        protein_top = (
            protein_result["recommendations"][0]["productId"]
        )
        fiber_top = (
            fiber_result["recommendations"][0]["productId"]
        )

        self.assertNotEqual(protein_top, fiber_top)

    def test_excluded_product_is_not_returned(self):
        context = replace(
            make_context("fiber"),
            excluded_product_ids=("demo-beans",),
        )

        result = recommend(context, PRODUCTS)

        ids = [
            item["productId"]
            for item in result["recommendations"]
        ]

        self.assertNotIn("demo-beans", ids)

    def test_duplicate_product_ids_are_rejected(self):
        with self.assertRaises(ValueError):
            recommend(
                make_context("protein"),
                [PRODUCTS[0], PRODUCTS[0]],
            )

    def test_empty_history_uses_today_deficit(self):
        result = recommend(
            make_context("protein"),
            PRODUCTS,
        )

        self.assertEqual(
            result["recordedHistoryDays"],
            0,
        )

        self.assertEqual(
            result["deficits"]["protein"],
            60,
        )

    def test_excess_sugar_is_penalized(self):
        context = make_context("protein")

        context = replace(
            context,
            today={
                **context.today,
                "sugar": 49,
            },
        )

        low_sugar = replace(
            PRODUCTS[0],
            id="low-sugar",
            sugar=0,
        )

        high_sugar = replace(
            PRODUCTS[0],
            id="high-sugar",
            sugar=30,
        )

        result = recommend(
            context,
            [low_sugar, high_sugar],
        )

        scores = {
            item["productId"]: item["score"]
            for item in result["recommendations"]
        }

        self.assertGreater(
            scores["low-sugar"],
            scores["high-sugar"],
        )


class RecommendationApiTests(unittest.TestCase):

    def setUp(self):
        self.env = patch.dict(
            os.environ,
            {"RECOMMENDATION_API_KEY": "test-key"},
        )
        self.env.start()

        self.client = TestClient(app)

    def tearDown(self):
        self.client.close()
        self.env.stop()

    def payload(self):
        context = make_context("fiber")

        return {
            "context": {
                "goal": context.goal,
                "today": context.today,
                "history": [],
                "budget_per_serving": 2500,
                "convenience_preferred": True,
                "excluded_product_ids": [],
            },
            "products": [
                {
                    "id": product.id,
                    "name": product.name,
                    "protein": product.protein,
                    "fiber": product.fiber,
                    "sodium": product.sodium,
                    "sugar": product.sugar,
                    "kcal": product.kcal,
                    "price_per_serving":
                        product.price_per_serving,
                    "convenience": product.convenience,
                }
                for product in PRODUCTS
            ],
            "top_k": 3,
        }

    def test_api_returns_recommendations(self):
        response = self.client.post(
            "/recommend",
            headers={"X-Service-Key": "test-key"},
            json=self.payload(),
        )

        self.assertEqual(response.status_code, 200)

        body = response.json()

        self.assertEqual(
            len(body["recommendations"]),
            3,
        )

        self.assertIn(
            "reasons",
            body["recommendations"][0],
        )

    def test_wrong_service_key_is_rejected(self):
        response = self.client.post(
            "/recommend",
            headers={"X-Service-Key": "wrong-key"},
            json=self.payload(),
        )

        self.assertEqual(response.status_code, 401)

    def test_missing_nutrition_is_rejected(self):
        payload = self.payload()

        del payload["products"][0]["fiber"]

        response = self.client.post(
            "/recommend",
            headers={"X-Service-Key": "test-key"},
            json=payload,
        )

        self.assertEqual(response.status_code, 422)


if __name__ == "__main__":
    unittest.main()