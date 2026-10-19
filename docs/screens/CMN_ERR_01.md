# CMN_ERR_01 · 공통 오류 문구

| 화면ID | 화면명 | 경로 | 파일 | 인증 | 버전 | 수정일 | 이미지 출처 |
|---|---|---|---|---|---|---|---|
| `CMN_ERR_01` | 공통 오류 문구 | `(공통 — 앱 화면 위)` | `frontend/src/api/http.js` | — | v0.3 | 2026-10-15 | 와이어프레임 (코드 기준 ASCII) |

**한 줄 목표**: 서버가 message 를 주지 않아도 사용자가 무엇이 잘못됐는지 알 수 있어야 한다.

서버가 message 를 주지 않을 때만 쓰는 대체 문구 (`api/http.js`).

## 화면

와이어프레임 없음 — 실제 화면 캡처 예정

## Description

| # | 요소 | 기능 | 동작 (누르면·입력하면) | 상태 | 예외·정책 |
|---|---|---|---|---|---|
| ① | 대체 문구 | 서버가 message 를 주지 않을 때만 쓰는 대체 문구 | 요청 실패 시 오류에 문구와 상태 코드를 실어 호출한 화면에 넘긴다 | 실패: 서버 message 가 있으면 그것, 없으면 아래 표 문구, 표에 없는 코드는 "요청 실패 ({코드})" | 401 은 앱 화면 데이터 조회일 때 `/login` 으로 이동 |

## 세부

| 코드 | 문구 |
|---|---|
| 400 | 요청을 처리할 수 없어요 |
| 401 | 로그인이 필요합니다 (앱 화면 데이터 조회는 `/login` 으로 이동) |
| 403 | 권한이 없어요 |
| 404 | 찾을 수 없어요 |
| 409 | 이미 처리된 요청이에요 |

## 상태별 화면

| 상태 | 보이는 것 | 사용자가 할 수 있는 것 |
|---|---|---|
| 실패 | 각 화면이 오류 문구를 자기 자리에 보여 준다 (표시 위치는 화면마다 다름) | 화면마다 다름 |

## 접근성

- 키보드: 해당 없음 (문구만 정함)
- 스크린 리더 라벨: 확인 필요 (`role="alert"` 여부는 각 화면이 정한다)
- 포커스: 해당 없음

## 참고

- 기타: 호출부에서 상태 코드로 문구를 정하지 말라는 원칙이 코드 주석에 있다 (`frontend/src/api/http.js:11`). 업로드 10MB 초과 413 은 message 가 없을 수 있다 (`frontend/src/api/http.js:70-71`).

## 의문·미해결

- README 8-2 에 이 화면 줄 없음.
- 코드와 다름 (0c0a9e4): `/login` 이동은 `http.js` 가 아니라 각 화면이 한다 (예: `frontend/src/pages/Wallet/WalletPage.jsx:274`, `frontend/src/pages/Report/ReportPage.jsx:230`, `frontend/src/pages/Shop/ShopPage.jsx:102`, `frontend/src/pages/Pet/PetPage.jsx:150`, `frontend/src/pages/PetDex/PetDexPage.jsx:58`, `frontend/src/pages/PetDex/PetDetailPage.jsx:45`). `frontend/src/pages/Home/HomeDashboard.jsx`·`frontend/src/pages/Settings/SettingsPage.jsx` 에는 401 → `/login` 처리가 없다 (grep 결과 없음).
- 코드와 다름 (0c0a9e4): 표에 없는 코드는 "요청 실패 ({코드})" 로 나간다 (`frontend/src/api/http.js:24`). 어드민(`frontend/src/api/admin.js`)·로그인(`frontend/src/api/auth.js`)은 `http.js` 를 쓰지 않고 자기 문구를 쓴다 (예: 로그인 401 "이메일 또는 비밀번호가 올바르지 않습니다", `frontend/src/api/auth.js:32-33`).
