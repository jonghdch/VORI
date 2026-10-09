/**
 * @spec       golden-path
 * @hypothesis 새 사용자가 이메일로 가입 → 소비 설문 → 첫 지출 기록까지 막힘 없이 끝내고, 기록이 서버에 저장된다
 * @scenario   /signup 가입 → /signup/profile 설문 6단계 → /onboarding → /wallet/new 지출 1건 → 확인 → /wallet
 * @asserts    1) 설문을 마치면 온보딩 화면에 입력한 수입이 보인다
 *             2) 「학식」이 규칙 분류로 식비 카테고리를 받는다(Gemini 없이)
 *             3) 확인 화면·지갑에 기록이 보이고, /api/expenses 에 같은 기록이 저장돼 있다
 * @failure_mode 가입·설문 가드(ProfileRequiredGuard·OnboardingRequiredFilter), CORS·세션 쿠키,
 *             카테고리 자동 분류, 가계부 일괄 저장(/api/ledger/entries) 중 하나라도 깨지면 실패한다
 * @note       AI 판정은 Gemini 를 실제로 불러야 해서 넣지 않았다(백엔드 단위 테스트가 mock 으로 검증)
 * @last_passed 2026-10-09
 */
const { test, expect } = require("@playwright/test");
const { API_BASE, todayInSeoul, signUpAndCompleteSurvey } = require("./helpers");

test("가입부터 첫 지출 기록까지", async ({ page }) => {
  await signUpAndCompleteSurvey(page, { monthlyIncome: "1000000" });

  // 온보딩 — 설문 답이 반영됐다
  await expect(page.getByText("1,000,000원")).toBeVisible();
  await page.getByRole("button", { name: "첫 지출 기록하기" }).click();

  // 지출 기록 — 「학식」은 규칙 분류로 식비 카테고리가 붙는다
  await expect(page).toHaveURL(/\/wallet\/new/);
  await page.getByRole("button", { name: "+ 지출 추가하기" }).click();
  await page.getByRole("textbox", { name: "예: GS25 삼각김밥, 학식, 지하철" }).fill("학식");
  await page.getByRole("textbox", { name: "금액" }).fill("5000");
  await expect(page.getByRole("button", { name: "식비 · 외식" })).toBeVisible();
  await page.getByRole("button", { name: "다음 단계" }).click();

  // 확인 → 지갑
  await expect(page).toHaveURL(/\/wallet\/new\/confirm/);
  await expect(page.getByText("학식")).toBeVisible();
  await page.getByRole("button", { name: "완료하기" }).click();
  await expect(page).toHaveURL(/\/wallet\?date=/);
  await expect(page.getByText("학식").first()).toBeVisible();

  // 서버에도 저장됐다 — 브라우저와 같은 세션 쿠키로 조회
  const res = await page.request.get(`${API_BASE}/expenses?date=${todayInSeoul()}`);
  expect(res.ok()).toBeTruthy();
  expect(await res.json()).toEqual(
    expect.arrayContaining([expect.objectContaining({ item: "학식", amount: 5000 })]),
  );
});
