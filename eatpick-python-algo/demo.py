import json

from recommender import Product, UserContext, recommend


# 모두 가상 시연 데이터
PRODUCTS = [
    Product(
        id="demo-chicken",
        name="시연용 닭가슴살",
        protein=23,
        fiber=0,
        sodium=380,
        sugar=0,
        kcal=110,
        price_per_serving=1900,
        convenience=True,
    ),
    Product(
        id="demo-beans",
        name="시연용 콩 샐러드",
        protein=12,
        fiber=9,
        sodium=180,
        sugar=3,
        kcal=220,
        price_per_serving=2400,
        convenience=True,
    ),
    Product(
        id="demo-drink",
        name="시연용 단백질 음료",
        protein=30,
        fiber=1,
        sodium=150,
        sugar=15,
        kcal=230,
        price_per_serving=2800,
        convenience=True,
    ),
]

GOAL = {
    "protein": 100,
    "fiber": 25,
    "sodium": 2000,
    "sugar": 50,
    "kcal": 2300,
}


def make_context(kind: str) -> UserContext:
    if kind == "protein":
        today = {
            "protein": 40,
            "fiber": 24,
            "sodium": 1000,
            "sugar": 20,
            "kcal": 1400,
        }
    elif kind == "fiber":
        today = {
            "protein": 95,
            "fiber": 5,
            "sodium": 1900,
            "sugar": 45,
            "kcal": 1900,
        }
    else:
        raise ValueError("알 수 없는 사용자 유형")

    return UserContext(
        goal=GOAL.copy(),
        today=today,
        history=[],
        budget_per_serving=2500,
        convenience_preferred=True,
    )


if __name__ == "__main__":
    for kind in ("protein", "fiber"):
        result = recommend(
            make_context(kind),
            PRODUCTS,
            top_k=3,
        )

        print(f"\n=== {kind} 사용자 ===")
        print(json.dumps(
            result,
            ensure_ascii=False,
            indent=2,
        ))