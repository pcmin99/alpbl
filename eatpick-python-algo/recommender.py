from dataclasses import dataclass
from math import isfinite
from typing import Optional


@dataclass(frozen=True)
class Product:
    id: str
    name: str

    # 모두 1회 섭취량 기준
    protein: float       # g
    fiber: float         # g
    sodium: float        # mg
    sugar: float         # g
    kcal: float
    price_per_serving: float  # 원

    convenience: bool = False


@dataclass(frozen=True)
class UserContext:
    # 기존 Spring Boot의 nutrition_goals 필드명과 동일
    goal: dict[str, float]

    # 오늘 누적 섭취량
    today: dict[str, float]

    # 오늘을 제외한 최근 기록일들의 일별 합계
    # 미기록일을 0으로 채워 넣지 않음
    history: list[dict[str, float]]

    budget_per_serving: Optional[float] = None
    convenience_preferred: bool = False
    excluded_product_ids: tuple[str, ...] = ()


REQUIRED_NUTRIENTS = ("protein", "fiber", "sodium", "sugar", "kcal")

LABELS = {
    "protein": ("단백질", "g"),
    "fiber": ("식이섬유", "g"),
    "sodium": ("나트륨", "mg"),
    "sugar": ("당류", "g"),
    "kcal": ("열량", "kcal"),
}


def validate_number(value: float, name: str, positive: bool = False):
    if not isinstance(value, (int, float)):
        raise ValueError(f"{name}: 숫자가 필요합니다.")

    if isinstance(value, bool) or not isfinite(value):
        raise ValueError(f"{name}: 유효한 숫자가 필요합니다.")

    if value < 0 or (positive and value == 0):
        raise ValueError(f"{name}: 허용되지 않는 값입니다.")


def validate_context(context: UserContext):
    for nutrient in REQUIRED_NUTRIENTS:
        if nutrient not in context.goal:
            raise ValueError(f"목표 영양정보 누락: {nutrient}")

        if nutrient not in context.today:
            raise ValueError(f"오늘 영양정보 누락: {nutrient}")

        validate_number(
            context.goal[nutrient],
            f"goal.{nutrient}",
            positive=True,
        )
        validate_number(
            context.today[nutrient],
            f"today.{nutrient}",
        )

        for day in context.history:
            if nutrient not in day:
                raise ValueError(f"과거 영양정보 누락: {nutrient}")

            validate_number(day[nutrient], f"history.{nutrient}")

    if context.budget_per_serving is not None:
        validate_number(
            context.budget_per_serving,
            "budget_per_serving",
            positive=True,
        )


def validate_product(product: Product):
    if not product.id or not product.name:
        raise ValueError("상품 ID와 이름이 필요합니다.")

    for field in REQUIRED_NUTRIENTS:
        validate_number(getattr(product, field), f"{product.id}.{field}")

    validate_number(
        product.price_per_serving,
        f"{product.id}.price_per_serving",
        positive=True,
    )


def nutrient_deficit(
    context: UserContext,
    nutrient: str,
) -> float:
    """
    오늘 남은 목표량 60% + 최근 기록일 평균 부족량 40%.

    과거 기록이 없으면 오늘 남은 목표량만 사용.
    가중치는 초기 가설이며 실제 이용 데이터로 조정해야 함.
    """
    target = context.goal[nutrient]
    today_deficit = max(0.0, target - context.today[nutrient])

    if not context.history:
        return today_deficit

    average = sum(
        day[nutrient] for day in context.history
    ) / len(context.history)

    historical_deficit = max(0.0, target - average)

    return 0.6 * today_deficit + 0.4 * historical_deficit


def added_excess_ratio(
    current: float,
    added: float,
    target: float,
) -> float:
    """
    상품 섭취로 '추가 발생하는' 목표 초과량.

    이미 초과한 양을 상품마다 중복 감점하지 않음.
    """
    before = max(0.0, current - target)
    after = max(0.0, current + added - target)

    return (after - before) / target


def recommend(
    context: UserContext,
    products: list[Product],
    top_k: int = 5,
) -> dict:
    validate_context(context)

    if top_k < 1:
        raise ValueError("top_k는 1 이상이어야 합니다.")

    ids = [product.id for product in products]
    if len(ids) != len(set(ids)):
        raise ValueError("상품 ID가 중복되었습니다.")

    deficits = {
        nutrient: nutrient_deficit(context, nutrient)
        for nutrient in ("protein", "fiber")
    }

    # 단백질과 식이섬유 부족 정도에 따라 중요도를 동적으로 결정.
    # 식이섬유가 상대적으로 더 부족하면 식이섬유 비중이 커짐.
    relative_deficits = {
        nutrient: deficits[nutrient] / context.goal[nutrient]
        for nutrient in deficits
    }
    total_relative_deficit = sum(relative_deficits.values())

    ranked = []

    for product in products:
        validate_product(product)

        if product.id in context.excluded_product_ids:
            continue

        reasons = []
        warnings = []
        nutrient_fit = 0.0

        for nutrient, deficit in deficits.items():
            if deficit <= 0 or total_relative_deficit == 0:
                continue

            weight = (
                relative_deficits[nutrient]
                / total_relative_deficit
            )

            amount = getattr(product, nutrient)
            coverage = min(amount / deficit, 1.0)
            nutrient_fit += weight * coverage

            if amount > 0:
                label, unit = LABELS[nutrient]
                reasons.append(
                    f"{label} 부족량 {deficit:.1f}{unit} 중 "
                    f"{min(amount, deficit):.1f}{unit} 보충"
                )

        # 선택한 선호 항목만 점수 계산에 포함.
        components = [(0.75, nutrient_fit)]

        if context.budget_per_serving is not None:
            price_fit = min(
                context.budget_per_serving
                / product.price_per_serving,
                1.0,
            )
            components.append((0.15, price_fit))

            if product.price_per_serving <= context.budget_per_serving:
                reasons.append(
                    f"1회 {product.price_per_serving:,.0f}원으로 예산 이내"
                )
            else:
                warnings.append(
                    f"1회 예산보다 "
                    f"{product.price_per_serving - context.budget_per_serving:,.0f}원 높음"
                )

        if context.convenience_preferred:
            components.append(
                (0.10, 1.0 if product.convenience else 0.0)
            )

            if product.convenience:
                reasons.append("간편식 선호와 일치")

        base_score = 100 * (
            sum(weight * value for weight, value in components)
            / sum(weight for weight, _ in components)
        )

        # 당류·나트륨·열량의 추가 초과분을 감점.
        excess_weights = {
            "sodium": 0.45,
            "sugar": 0.35,
            "kcal": 0.20,
        }

        excess_penalty = 0.0

        for nutrient, weight in excess_weights.items():
            ratio = added_excess_ratio(
                current=context.today[nutrient],
                added=getattr(product, nutrient),
                target=context.goal[nutrient],
            )

            excess_penalty += weight * min(ratio, 1.0)

            if ratio > 0:
                label, unit = LABELS[nutrient]
                additional_excess = ratio * context.goal[nutrient]

                warnings.append(
                    f"섭취 시 {label} 목표 초과량이 "
                    f"{additional_excess:.1f}{unit} 증가"
                )

        penalty_points = 50 * excess_penalty

        # 순위 점수는 확률이나 임상적 건강 점수가 아님.
        score = base_score - penalty_points

        if not reasons:
            reasons.append("현재 부족 영양소에 대한 보충 효과가 낮음")

        ranked.append({
            "productId": product.id,
            "name": product.name,
            "score": round(score, 2),
            "reasons": reasons,
            "warnings": warnings,
            "breakdown": {
                "nutrientFit": round(nutrient_fit, 4),
                "baseScore": round(base_score, 2),
                "excessPenalty": round(penalty_points, 2),
            },
        })

    ranked.sort(
        key=lambda item: (-item["score"], item["productId"])
    )

    for rank, item in enumerate(ranked[:top_k], start=1):
        item["rank"] = rank

    return {
        "algorithmVersion": "nutrition-rule-v1",
        "recordedHistoryDays": len(context.history),
        "deficits": {
            key: round(value, 2)
            for key, value in deficits.items()
        },
        "recommendations": ranked[:top_k],
    }