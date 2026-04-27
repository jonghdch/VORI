"""10명의 가상 페르소나 정의.

각 페르소나:
  - 기본 프로필 (직업, 수입, 자산, 카테고리별 월 예산)
  - 소비 스케일 (식비/쇼핑/여가별 배수 — 대학생 기준)
  - 특이 패턴 (외식빈도, 카페빈도, 쇼핑 주기)
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Persona:
    name: str
    job: str
    monthly_income: int
    total_assets: int
    category_avg: dict

    # 소비 패턴 계수 (대학생=1.0 기준)
    meal_scale: float = 1.0          # 기본 식사 금액
    dine_out_prob: float = 0.15       # 저녁 외식 확률
    cafe_prob: float = 0.30           # 카페 빈도
    shopping_prob: float = 0.10       # 일반 쇼핑
    shopping_big_prob: float = 0.03   # 큰 쇼핑
    leisure_prob: float = 0.15
    leisure_big_prob: float = 0.02

    # 고정비 (매달 금액 튜플 리스트: [(desc, amount, day)])
    fixed_costs: list = field(default_factory=list)

    @property
    def context(self) -> dict:
        return {
            "job": self.job,
            "monthly_income": self.monthly_income,
            "total_assets": self.total_assets,
            "category_avg": self.category_avg,
        }


def default_fixed(rent: int, utility: int, comm: int, sub: int = 17000) -> list:
    return [
        ("월세", rent, 1),
        ("통신비", comm, 3),
        ("공과금", utility, 5),
        ("넷플릭스 구독", sub, 10),
    ]


PERSONAS: list[Persona] = [
    Persona(
        name="p01_학부생",
        job="대학생",
        monthly_income=500_000,
        total_assets=3_000_000,
        category_avg={"식비": 200_000, "쇼핑뷰티": 100_000, "문화여가": 50_000, "생활고정": 400_000},
        meal_scale=1.0, dine_out_prob=0.15, cafe_prob=0.30,
        shopping_prob=0.08, shopping_big_prob=0.02,
        leisure_prob=0.15, leisure_big_prob=0.02,
        fixed_costs=default_fixed(300_000, 70_000, 60_000),
    ),
    Persona(
        name="p02_취준생",
        job="취업준비생",
        monthly_income=200_000,
        total_assets=5_000_000,
        category_avg={"식비": 180_000, "쇼핑뷰티": 50_000, "문화여가": 150_000, "생활고정": 250_000},
        meal_scale=0.7, dine_out_prob=0.05, cafe_prob=0.20,
        shopping_prob=0.05, shopping_big_prob=0.005,
        leisure_prob=0.25, leisure_big_prob=0.01,  # 학원비 많음
        fixed_costs=default_fixed(200_000, 50_000, 50_000),
    ),
    Persona(
        name="p03_대학원생",
        job="대학원생",
        monthly_income=1_200_000,
        total_assets=10_000_000,
        category_avg={"식비": 350_000, "쇼핑뷰티": 100_000, "문화여가": 150_000, "생활고정": 800_000},
        meal_scale=1.2, dine_out_prob=0.15, cafe_prob=0.50,  # 연구실 카페 많음
        shopping_prob=0.07, shopping_big_prob=0.02,
        leisure_prob=0.20, leisure_big_prob=0.02,
        fixed_costs=default_fixed(600_000, 100_000, 80_000),
    ),
    Persona(
        name="p04_신입직장인",
        job="신입 직장인",
        monthly_income=2_500_000,
        total_assets=8_000_000,
        category_avg={"식비": 500_000, "쇼핑뷰티": 250_000, "문화여가": 200_000, "생활고정": 1_200_000},
        meal_scale=1.8, dine_out_prob=0.35, cafe_prob=0.80,  # 점심·카페 자주
        shopping_prob=0.12, shopping_big_prob=0.04,
        leisure_prob=0.20, leisure_big_prob=0.03,
        fixed_costs=default_fixed(800_000, 150_000, 90_000, sub=40_000),
    ),
    Persona(
        name="p05_중견직장인",
        job="직장인 5년차",
        monthly_income=4_500_000,
        total_assets=30_000_000,
        category_avg={"식비": 700_000, "쇼핑뷰티": 400_000, "문화여가": 400_000, "생활고정": 2_000_000},
        meal_scale=2.5, dine_out_prob=0.45, cafe_prob=0.70,
        shopping_prob=0.15, shopping_big_prob=0.05,
        leisure_prob=0.25, leisure_big_prob=0.05,
        fixed_costs=default_fixed(1_200_000, 200_000, 100_000, sub=60_000),
    ),
    Persona(
        name="p06_고소득직장인",
        job="고소득 직장인",
        monthly_income=8_000_000,
        total_assets=120_000_000,
        category_avg={"식비": 1_000_000, "쇼핑뷰티": 800_000, "문화여가": 800_000, "생활고정": 3_500_000},
        meal_scale=4.0, dine_out_prob=0.55, cafe_prob=0.70,
        shopping_prob=0.18, shopping_big_prob=0.08,
        leisure_prob=0.30, leisure_big_prob=0.08,
        fixed_costs=default_fixed(2_500_000, 300_000, 200_000, sub=80_000),
    ),
    Persona(
        name="p07_프리랜서",
        job="프리랜서 디자이너",
        monthly_income=3_000_000,
        total_assets=20_000_000,
        category_avg={"식비": 550_000, "쇼핑뷰티": 300_000, "문화여가": 300_000, "생활고정": 1_500_000},
        meal_scale=2.0, dine_out_prob=0.30, cafe_prob=1.0,  # 카페 작업 매일
        shopping_prob=0.12, shopping_big_prob=0.04,
        leisure_prob=0.20, leisure_big_prob=0.03,
        fixed_costs=default_fixed(900_000, 150_000, 100_000, sub=50_000),
    ),
    Persona(
        name="p08_자영업자",
        job="카페 사장",
        monthly_income=5_000_000,
        total_assets=50_000_000,
        category_avg={"식비": 700_000, "쇼핑뷰티": 200_000, "문화여가": 200_000, "생활고정": 2_500_000},
        meal_scale=2.2, dine_out_prob=0.25, cafe_prob=0.20,  # 본인 가게라 덜
        shopping_prob=0.10, shopping_big_prob=0.04,
        leisure_prob=0.15, leisure_big_prob=0.03,
        fixed_costs=default_fixed(1_500_000, 250_000, 150_000, sub=30_000),
    ),
    Persona(
        name="p09_주부",
        job="주부",
        monthly_income=0,  # 개인 수입 없음
        total_assets=80_000_000,
        category_avg={"식비": 900_000, "쇼핑뷰티": 400_000, "문화여가": 300_000, "생활고정": 2_500_000},
        meal_scale=2.5, dine_out_prob=0.20, cafe_prob=0.40,
        shopping_prob=0.18, shopping_big_prob=0.05,  # 마트 장보기 많음
        leisure_prob=0.15, leisure_big_prob=0.02,
        fixed_costs=default_fixed(1_600_000, 350_000, 150_000, sub=50_000),
    ),
    Persona(
        name="p10_은퇴자",
        job="은퇴자",
        monthly_income=1_500_000,
        total_assets=200_000_000,
        category_avg={"식비": 400_000, "쇼핑뷰티": 100_000, "문화여가": 250_000, "생활고정": 1_500_000},
        meal_scale=1.5, dine_out_prob=0.10, cafe_prob=0.25,
        shopping_prob=0.05, shopping_big_prob=0.02,
        leisure_prob=0.25, leisure_big_prob=0.04,  # 여행·취미
        fixed_costs=default_fixed(1_000_000, 200_000, 100_000, sub=30_000),
    ),
]
