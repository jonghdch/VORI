// E2E 공용 헬퍼. 매 테스트가 새 계정을 만들어 서로의 데이터에 기대지 않게 한다.
const { expect } = require("@playwright/test");

const API_BASE = process.env.E2E_API_BASE || "http://localhost:8080/api";

/** 서비스 기준(한국 시간) 오늘 날짜 YYYY-MM-DD. */
function todayInSeoul() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
}

/** 이메일로 가입하고 소비 설문 6단계를 마쳐 /onboarding 까지 간다. */
async function signUpAndCompleteSurvey(page, { monthlyIncome = "1000000" } = {}) {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;

  await page.goto("/signup");
  await page.getByLabel("닉네임 (화면 표시용)").fill("이투이");
  await page.getByLabel("이름 (실명)").fill("테스트");
  await page.getByLabel("이메일").fill(email);
  await page.getByPlaceholder("8자 이상 입력하세요").fill("password123!");
  await page.getByLabel("비밀번호 확인").fill("password123!");
  await page.getByLabel("전체 동의").check();
  await page.getByRole("button", { name: /가입/ }).click();

  // 설문을 마치기 전에는 다른 화면으로 못 나간다
  await expect(page).toHaveURL(/\/signup\/profile$/);
  await page.getByRole("spinbutton", { name: "월 수입 원" }).fill(monthlyIncome);
  await page.getByRole("button", { name: "다음" }).click();
  for (const answer of [
    "20만~40만원",
    "7,000~10,000원",
    "식비·카페",
    "계획한 것만 사는 편",
    "지출 기록 습관 만들기",
  ]) {
    await page.getByRole("button", { name: answer }).click();
    await page.getByRole("button", { name: /다음|시작|완료|저장/ }).last().click();
  }
  await expect(page).toHaveURL(/\/onboarding$/);
  return { email };
}

module.exports = { API_BASE, todayInSeoul, signUpAndCompleteSurvey };
