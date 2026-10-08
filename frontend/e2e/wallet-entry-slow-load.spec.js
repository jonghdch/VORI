/**
 * @spec       wallet-entry-slow-load
 * @hypothesis 가계부 작성 화면이 그날 기록을 읽어 오는 동안 추가한 입력 줄은, 응답이 온 뒤에도 지워지지 않는다
 * @scenario   /wallet/new 진입 → 기록 조회 응답을 붙잡아 둠 → 그 사이 「+ 지출 추가하기」 → 응답 도착 뒤에도 줄이 남아 있다
 * @asserts    1) 조회 요청이 실제로 붙잡혔다  2) 응답이 온 뒤에도 내역 칸이 있고 입력한 글자가 그대로다
 * @failure_mode 조회 응답이 폼을 [서버 행 + 진입 때 읽은 임시저장본] 으로 통째로 덮어써,
 *             그 사이 추가한 줄이 사라졌다(느린 네트워크에서 첫 입력이 날아감, 2026-10-09 수정)
 * @last_passed 2026-10-09
 */
const { test, expect } = require("@playwright/test");
const { signUpAndCompleteSurvey } = require("./helpers");

const isExpenseListRequest = (url) =>
  url.pathname.endsWith("/api/expenses") && url.searchParams.has("date");

test("기록을 읽는 동안 추가한 지출 줄이 지워지지 않는다", async ({ page }) => {
  await signUpAndCompleteSurvey(page);

  // 느린 네트워크 흉내 — 자체 백엔드는 진짜로 부르고 응답만 늦춘다
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  let held = 0;
  await page.route(isExpenseListRequest, async (route) => {
    held += 1;
    await gate;
    await route.continue();
  });
  const loaded = page.waitForResponse((r) => isExpenseListRequest(new URL(r.url())));

  await page.getByRole("button", { name: "첫 지출 기록하기" }).click();
  await page.getByRole("button", { name: "+ 지출 추가하기" }).click();
  const item = page.getByRole("textbox", { name: "예: GS25 삼각김밥, 학식, 지하철" });
  await item.fill("학식");
  // 줄을 추가하는 동안 응답이 정말 붙잡혀 있었는지 — 아니면 이 테스트는 아무것도 검증하지 못한다
  expect(held).toBeGreaterThan(0);

  release();
  await loaded;

  await expect(item).toHaveValue("학식");
  await expect(page.getByRole("textbox", { name: "금액" })).toBeVisible();
});
