// E2E 테스트 설정. 실제 백엔드(+MySQL)와 빌드한 프론트를 띄워 놓고 브라우저로 돌린다.
//
// 로컬 실행
//   1. 백엔드를 :8080 으로 띄운다(평소처럼 bootRun). 테스트가 계정·지출을 만드니 버려도 되는 DB 를 쓴다.
//   2. cd frontend && npm run build
//   3. npx playwright install chromium   (처음 한 번)
//   4. npm run e2e                        (빌드한 프론트를 :3000 에 띄워 테스트)
// 개발 서버가 3000·8080 을 쓰고 있으면 포트를 바꾼다:
//   백엔드  SERVER_PORT=18080 CORS_ALLOWED_ORIGINS=http://localhost:13000
//   빌드    REACT_APP_API_BASE=http://localhost:18080/api npm run build
//   테스트  E2E_PORT=13000 E2E_API_BASE=http://localhost:18080/api npm run e2e
const { defineConfig, devices } = require("@playwright/test");

const port = Number(process.env.E2E_PORT || 3000);

module.exports = defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: `http://localhost:${port}`,
    // 날짜가 바뀌는 경계에서 프론트(브라우저)와 서버가 다른 날을 보지 않게 서비스 기준 시간대로 고정한다.
    timezoneId: "Asia/Seoul",
    locale: "ko-KR",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // 빌드한 프론트(build/)를 띄운다. 백엔드는 따로 띄워 둔다.
  webServer: {
    command: `npx serve -s build -l ${port}`,
    url: `http://localhost:${port}`,
    // 이미 떠 있는 서버(예: npm start 개발 서버)를 재사용하지 않는다 — 빌드 결과를 테스트해야 CI 와 같다.
    reuseExistingServer: false,
  },
});
