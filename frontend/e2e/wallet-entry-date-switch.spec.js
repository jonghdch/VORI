/**
 * @spec       wallet-entry-date-switch
 * @hypothesis 가계부 작성 화면에 머문 채 날짜(?date=)만 바뀌면, 이전 날짜의 저장 안 된 줄은 새 날짜 폼에 넘어가지 않는다
 * @scenario   /wallet/new?date=어제 에서 지출 줄 입력(저장 X) → 앱 안에서 /wallet/new?date=오늘 로 이동 → 다시 어제로
 * @asserts    1) 오늘 폼에 어제 줄이 없다  2) 어제로 돌아가면 어제 줄이 임시저장에서 복원된다
 * @failure_mode 같은 컴포넌트가 계속 mount 돼 있어 이전 날짜의 줄이 새 날짜 폼·임시저장으로 넘어가고,
 *             제출하면 엉뚱한 날짜로 저장됐다(2026-10-09 수정, WalletEntryRoute 의 key)
 * @last_passed 2026-10-09
 */
const { test, expect } = require("@playwright/test");
const { dayInSeoul, navigateInApp, signUpAndCompleteSurvey } = require("./helpers");

test("날짜를 바꾸면 이전 날짜의 저장 안 된 줄이 넘어가지 않는다", async ({ page }) => {
  await signUpAndCompleteSurvey(page);
  const yesterday = dayInSeoul(-1);
  const today = dayInSeoul(0);
  const item = page.getByRole("textbox", { name: "예: GS25 삼각김밥, 학식, 지하철" });

  await page.goto(`/wallet/new?date=${yesterday}`);
  await expect(page.getByText("모든 항목의 내역과 금액을 입력해주세요")).toBeVisible();
  await page.getByRole("button", { name: "+ 지출 추가하기" }).click();
  await item.fill("어제 학식");
  await expect(item).toHaveValue("어제 학식");

  await navigateInApp(page, `/wallet/new?date=${today}`);
  await expect(page.getByRole("heading", { level: 1 })).toContainText(today.replaceAll("-", "."));
  await expect(page.getByText("모든 항목의 내역과 금액을 입력해주세요")).toBeVisible();
  await expect(item).toHaveCount(0);

  await navigateInApp(page, `/wallet/new?date=${yesterday}`);
  await expect(page.getByRole("heading", { level: 1 })).toContainText(yesterday.replaceAll("-", "."));
  await expect(item).toHaveValue("어제 학식");
});
