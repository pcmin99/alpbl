import os
import secrets
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from recommender import Product, UserContext, recommend


app = FastAPI(
    title="Eatpick Recommendation API",
    version="1.0.0",
)

NonNegative = Annotated[
    float,
    Field(ge=0, allow_inf_nan=False),
]

Positive = Annotated[
    float,
    Field(gt=0, allow_inf_nan=False),
]


class InputModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class NutritionInput(InputModel):
    protein: NonNegative
    fiber: NonNegative
    sodium: NonNegative
    sugar: NonNegative
    kcal: NonNegative


class GoalInput(InputModel):
    protein: Positive
    fiber: Positive
    sodium: Positive
    sugar: Positive
    kcal: Positive


class ProductInput(NutritionInput):
    id: str = Field(min_length=1, max_length=36)
    name: str = Field(min_length=1, max_length=100)

    price_per_serving: Positive
    convenience: bool = Field(default=False, strict=True)


class ContextInput(InputModel):
    goal: GoalInput
    today: NutritionInput

    # 오늘 제외 최근 7일 중 기록이 있는 날짜만 전달
    history: list[NutritionInput] = Field(
        default_factory=list,
        max_length=7,
    )

    budget_per_serving: Positive | None = None

    convenience_preferred: bool = Field(
        default=False,
        strict=True,
    )

    excluded_product_ids: list[str] = Field(
        default_factory=list,
        max_length=100,
    )


class RecommendationInput(InputModel):
    context: ContextInput

    products: list[ProductInput] = Field(
        min_length=1,
        max_length=500,
    )

    top_k: int = Field(
        default=5,
        ge=1,
        le=100,
        strict=True,
    )


def verify_service_key(
    supplied_key: Annotated[
        str | None,
        Header(alias="X-Service-Key"),
    ] = None,
):
    expected_key = os.getenv("RECOMMENDATION_API_KEY", "")

    if not expected_key:
        raise HTTPException(
            status_code=503,
            detail="추천 서비스 키가 설정되지 않았습니다.",
        )

    if (
        supplied_key is None
        or not secrets.compare_digest(
            supplied_key.encode("utf-8"),
            expected_key.encode("utf-8"),
        )
    ):
        raise HTTPException(
            status_code=401,
            detail="추천 서비스 인증에 실패했습니다.",
        )


@app.get("/health")
def health():
    return {
        "status": "ok",
        "algorithmVersion": "nutrition-rule-v1",
    }


@app.post(
    "/recommend",
    dependencies=[Depends(verify_service_key)],
)
def recommend_products(body: RecommendationInput):
    context = UserContext(
        goal=body.context.goal.model_dump(),
        today=body.context.today.model_dump(),
        history=[
            day.model_dump()
            for day in body.context.history
        ],
        budget_per_serving=body.context.budget_per_serving,
        convenience_preferred=body.context.convenience_preferred,
        excluded_product_ids=tuple(
            body.context.excluded_product_ids
        ),
    )

    products = [
        Product(**item.model_dump())
        for item in body.products
    ]

    try:
        return recommend(
            context=context,
            products=products,
            top_k=body.top_k,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=422,
            detail=str(exc),
        ) from exc