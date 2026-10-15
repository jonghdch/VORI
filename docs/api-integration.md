# 프론트엔드 연동 가이드

> 가구·테마·목표·예산 같은 도메인을 React 에 연결하기 위한 문서.
> 칭호는 PR #22, 영수증은 PR #21, 예산은 PR #82 로 붙었고 아직 화면이 없는 건 **목표**뿐이다. 아래 명세는 그대로
> 유효하다. 붙은 것의 실제 사용 예는 `frontend/src/api/{titles,receipt,furniture,theme,budget}.js` 를 보면 된다.
> 아래 JSON 은 전부 실제로 서버를 띄워 호출해 받은 응답이고, 예시로 지어낸 값이 없다.
> [가계부](#가계부) 절의 일괄 저장만 예외다. 2026-10-01 에 코드를 읽고 적었고 응답 JSON 은 싣지 않았다.

| | |
|---|---|
| Base URL | `http://localhost:8080` |
| 검증 시점 | 2026-09-08 · `chaerin` @ `f669a02` |
| 작성 | 황채린 |

응답 모양이 이 문서와 다르면 브랜치가 다를 가능성이 크다.

---

## 1. 시작하기

인증은 세션 쿠키(`JSESSIONID`)다. 토큰이 아니라 쿠키라서 **`credentials: 'include'` 를 빠뜨리면 모든 요청이 401** 이 된다. 연동할 때 제일 자주 막히는 지점이다.

### 공통 래퍼

```js
const API = 'http://localhost:8080';

export async function api(path, options = {}) {
  const res = await fetch(API + path, {
    credentials: 'include',   // 세션 쿠키 — 빠뜨리면 전부 401
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });

  if (res.status === 204) return null;   // 칭호 장착 등 본문 없는 성공
  const body = await res.json().catch(() => null);

  if (!res.ok) {
    // message 는 사용자에게 그대로 보여줄 수 있는 한글 문구다.
    // 401 은 본문이 없으므로 기본 문구로 대체한다.
    throw new Error(body?.message ?? '로그인이 필요합니다.');
  }
  return body;
}
```

**파일 업로드는 이 래퍼를 쓰지 않는다.** `FormData` 를 보낼 때 `Content-Type` 을 직접 지정하면 boundary 가 빠져 서버가 파싱하지 못한다. 헤더를 비워서 브라우저가 채우게 둔다([영수증](#영수증) 참조).

### 로그인

```
POST /api/auth/login   { "email": "...", "password": "..." }
```

```json
{ "id": 26, "email": "user@vori.local", "nickname": "채린", "role": "USER" }
```

회원가입(`POST /api/auth/signup`)도 같은 모양으로 응답하고, **이때 시작 펫(강아지)이 함께 지급된다.**
`role` 이 `ADMIN` 이면 어드민 화면 진입을 허용하면 된다.

---

## 2. 에러 처리

에러 본문에는 **사용자에게 그대로 보여줄 수 있는 한글 `message`** 가 들어 있다. 토스트나 팝업에 `message` 를 그대로 띄우면 된다. 상태코드로 문구를 추측해 하드코딩하지 말 것.

```json
{
  "timestamp": "2026-09-08T11:04:12.318",
  "status": 403,
  "error": "Forbidden",
  "message": "'절약 새싹' 칭호를 획득해야 살 수 있습니다",
  "path": "/api/furniture/buy"
}
```

### 상태코드가 뜨는 시점

| 코드 | 언제 | message 예시 | 화면에서 |
|---|---|---|---|
| 400 | 코인 부족 · 검증 실패 · 30레벨 미만 펫 배웅 | `코인이 부족합니다` | 토스트로 `message` 노출 |
| 401 | 로그인하지 않음 · 세션 만료 | (본문 없음) | 로그인 화면으로 이동 |
| 403 | 잠긴 테마 가구 구매 · 남의 데이터 접근 | `'절약 새싹' 칭호를 획득해야…` | 해금 조건 안내 |
| 404 | 없는 id 로 조회·수정 | `칭호를 찾을 수 없습니다` | 목록 새로고침 |
| 409 | 좌표 중복 · 펫 있는데 알 개봉 · 목표 중복 | `먼저 키우던 펫을 배웅해주세요` | 안내 후 해당 동작 유도 |
| 413 | 업로드 용량 초과 | `이미지는 10MB 이하만…` | 파일 다시 선택 |
| 500 | 서버 오류 | (message 없음) | 일반 문구 + 채린에게 공유 |

**401 만 예외다.** 로그인 여부는 Spring Security 필터가 컨트롤러보다 먼저 판단하기 때문에 `message` 가 실리지 않고 본문이 비어 있다. `body?.message ?? '로그인이 필요합니다.'` 처럼 기본값을 둘 것.

---

## 3. 화면별 호출 순서

순서가 실제로 중요한 화면만 적었다. 번호는 반드시 이 차례로 호출해야 하는 흐름이라는 뜻이다.

### 마이룸 — 펫 · 가구 · 테마

세 API 가 한 화면에서 서로를 참조한다. 테마 현황이 배웅 선물과 직결된다.

1. `GET /api/pets/active`
   키우는 펫. **펫이 없으면 본문 없이 200** 이므로 `null` 체크가 필요하다.
2. `GET /api/furniture`
   `placed: true` 인 것만 방에 그리고, 나머지는 인벤토리 서랍에 둔다.
3. `GET /api/themes`
   `placedCount / requiredCount` 로 "코지 2/3 — 하나만 더!" 를 그린다. `active: true` 면 지금 배웅 선물에 보너스가 실제로 얹히는 중이다.
4. `PATCH /api/furniture/{id}/place`
   배치 후 **3번을 다시 호출**해야 세트 발동 여부가 갱신된다. 좌표가 겹치면 409.

### 상점 — 가구

잠긴 상품을 숨기지 말고 조건과 함께 보여줄 것. 그게 목표가 된다.

1. `GET /api/furniture/products`
   `locked: true` 면 흐리게 + `unlockTitleName`("절약 새싹 필요")을 배지로. `themeSetBonusPct` 로 "우드 세트 +8%" 안내.
2. `GET /api/users/me`
   보유 코인. 가격보다 모자라면 구매 버튼을 비활성화한다.
3. `POST /api/furniture/buy?item=BOOKSHELF`
   잠긴 상품이면 403, 코인이 모자라면 400. 성공하면 **인벤토리** 상태(좌표 null)로 들어간다.

### 가계부 — 지출 등록

지출은 절약액·누적 절약액·목표·칭호를 움직인다. **코인과 펫 스탯은 지출 때 나오지 않는다.** 하루에 한 번 하는
소비 판정(`POST /api/daily-judgments`) 때 지급된다([6절](#코인과-스탯이-생기는-공식)).

1. `GET /api/categories`
   카테고리 선택지. `categoryId` 가 `statType` 을 결정한다.
2. `POST /api/ledger/entries`
   작성 화면의 지출·수입·저축을 **한 번에** 보낸다. 서버가 한 트랜잭션으로 저장하므로, 하나라도 실패하면
   아무것도 저장되지 않는다([가계부](#가계부) 엔드포인트 참조). 한 건만 넣을 때는 `POST /api/expenses` 를 써도 된다.
3. `GET /api/achievements`
   지출 등록 직후 업적이 새로 붙었을 수 있다. `acquired` 가 바뀐 항목을 축하 연출에 쓴다.

### 영수증 스캔

응답까지 **6~13초** 걸린다(축소해 보낼 때. 원본을 그대로 보내면 9~31초). 로딩 UI 없이 붙이면 멈춘 것처럼 보인다.

1. **업로드 전에 `prepareReceiptImage()` 로 긴 변 2048px 로 줄인다** (`api/receipt.js`)
   전송 용량이 76~89% 줄고 인식 결과는 같았다. 10MB 검사는 축소한 뒤에 한다.
2. `POST /api/receipts` (multipart)
   PNG · JPEG, 10MB 이하. 헤더에 `Content-Type` 을 **넣지 않는다.**
3. 응답의 `amount` · `date` · `item` 을 지출 폼에 채움
   `status: "SUCCESS"` 여도 개별 값은 `null` 일 수 있다(흐린 영수증). 읽힌 값만 채우고 나머지는 사용자가 입력하게 둔다.
4. `POST /api/ledger/entries` (또는 `POST /api/expenses`)
   사용자가 확인·수정한 뒤 등록. **OCR 이 지출을 자동 생성하지는 않는다.**

---

## 4. 엔드포인트

### 테마 *(신규)*

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/themes` | 해금 여부 · 배치 개수 · 세트 발동 |

```json
[
  { "id": 3, "name": "스터디", "setBonusPct": 15.00, "requiredCount": 2,
    "placedCount": 2, "active": true,  "unlocked": true,  "unlockTitleName": "기록의 시작" },
  { "id": 1, "name": "우드",   "setBonusPct": 8.00,  "requiredCount": 3,
    "placedCount": 2, "active": false, "unlocked": true,  "unlockTitleName": null },
  { "id": 2, "name": "코지",   "setBonusPct": 12.00, "requiredCount": 3,
    "placedCount": 0, "active": false, "unlocked": false, "unlockTitleName": "절약 새싹" }
]
```

발동 중인 테마가 배열 앞쪽에 온다. `unlockTitleName` 이 `null` 이면 조건 없이 누구나 쓸 수 있는 테마다.

### 가구

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/furniture/products` | 상점 (가격 오름차순, 잠긴 것 포함) |
| GET | `/api/furniture` | 보유 가구 (배치된 것 먼저) |
| POST | `/api/furniture/buy?item={code}` | 구매 · 잠김 403 · 코인 부족 400 |
| PATCH | `/api/furniture/{id}/place` | 배치 · 좌표 중복 409 |
| PATCH | `/api/furniture/{id}/unplace` | 인벤토리로 회수 |

```json
// GET /api/furniture/products — 잠긴 상품 한 건
{
  "code": "DESK", "name": "책상", "category": "DESK",
  "statTarget": "IQ", "releaseBonusPct": 2.00, "price": 500,
  "themeName": "스터디", "themeSetBonusPct": 15.00,
  "locked": true, "unlockTitleName": "기록의 시작"
}
```

```json
// PATCH /api/furniture/11/place — 요청 (좌표는 방 크기 대비 %, 0~100)
{ "positionX": 15, "positionY": 74 }
```

**좌표는 픽셀이 아니라 백분율이다.** `PetPage` 의 배치 UI 가 이미 퍼센트로 좌표를 들고 있어
(`INITIAL_FURNITURE_POSITIONS`) 그 단위를 그대로 받는다. 비율이라 방 이미지 크기나 화면 폭이
바뀌어도 배치가 깨지지 않는다 — 반응형이라 픽셀로 저장하면 창 크기마다 가구가 다른 자리에 놓인다.
범위를 벗어나면 400 (`좌표는 100 이하여야 합니다. (방 크기 대비 %)`).

벽지·바닥(`PLAIN_WALLPAPER`, `WOOD_FLOOR`)은 `themeName` 이 `null` 이다. 좌표 처리가 아직 정해지지 않아 일부러 테마에서 뺐다.

### 업적과 칭호

업적은 유저가, 칭호는 펫이 얻는다(V41). 업적 서버 코드·테이블 이름은 옛 "칭호" 시절의 `titles` 를 그대로 쓴다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/achievements` | 업적 전부 (획득 먼저, 미획득은 달성 근접 순). 못 딴 히든은 설명 `"???"`, `current`·`threshold` 0, `progressPct` 만 |
| PUT | `/api/achievements/equipped` | 업적 장착 · body `{ "ids": [...] }` (획득한 업적 id, 칸 순서대로, 최대 3개, 빈 배열이면 모두 해제) · 갱신된 목록 |
| GET | `/api/pet-titles` | 키우는 펫의 칭호 과제와 진행도. 펫이 없으면 `petId` null + 과제 0% |
| PUT | `/api/pets/{id}/equipped-title` | 칭호 장착 · body `{ "awardId": n }` (null 이면 해제) · 배웅한 펫이면 409 |

```json
[
  { "id": 8, "code": "SAVER_SPROUT", "name": "절약 새싹", "description": "누적 절약 10만원",
    "acquired": true, "current": 2160124, "threshold": 100000, "progressPct": 100,
    "acquiredAt": "2026-09-08T10:54:41", "hidden": false, "equipOrder": 1 }
]
```

펫 응답(`GET /api/pets`, `/api/pets/active`)에는 그 펫이 딴 칭호 `titles` 와 장착 칭호 `equippedTitle` 이 실린다.

**미획득 업적·칭호도 진행률과 함께 내려온다.** 잠금 아이콘만 띄우지 말고 `current / threshold` 진행 바를 그릴 것. 그게 다음 목표가 된다.
단, **못 딴 히든(`hidden: true`)은 조건을 가린 채 내려온다** — 이름은 보이지만 설명은 `"???"`, `current`·`threshold` 는 0, 칭호는 `metricType` 도 null 이다. `progressPct` 로 달성률만 그린다. 지금 히든은 업적 「비밀 발견」, 펫 칭호 「행운의 매력」이다.
장착은 `{ "titleId": 8 }`, 해제는 `{ "titleId": null }`.

### 가계부

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/ledger?yearMonth=2026-10` | 그 달 지출+수입 통합 목록(날짜 오름차순) |
| POST | `/api/ledger/entries` | 작성 화면 일괄 저장 — 지출(등록·수정)·수입·저축을 한 트랜잭션으로 |
| DELETE | `/api/ledger/expenses/{id}` | 본인 지출 삭제 · 204 |
| POST | `/api/expenses` | 지출 1건 등록 |
| PUT | `/api/expenses/{id}` | 지출 1건 수정 |

```json
// POST /api/ledger/entries — 세 목록 모두 생략 가능(생략하면 빈 목록). 목록마다 최대 100건
{
  "expenses": [
    { "id": null, "item": "점심", "amount": 8000, "categoryId": 3,
      "paymentMethod": "CREDIT", "spentAt": "2026-10-01T00:00:00" },
    { "id": 41, "item": "저녁", "amount": 12000, "categoryId": 3, "paymentMethod": "DEBIT" }
  ],
  "incomes":  [ { "item": "알바", "amount": 50000, "source": "PART_TIME", "receivedAt": "2026-10-01" } ],
  "savings":  [ { "item": "적금", "amount": 30000, "savingType": "DEPOSIT", "savedAt": "2026-10-01" } ]
}
```

- 지출 행은 **`id` 가 없으면 등록, 있으면 수정**이다. 수정 행은 `paymentMethod` 가 필수다(없으면 400).
  남의 지출이거나 없는 `id` 면 403 / 400 이 나고 요청 전체가 되돌아간다.
- 수입·저축 행의 모양은 `POST /api/incomes` · `POST /api/savings` 요청과 같다.
- 응답은 `{ expenses, incomes, savings }` 이고 각 목록은 요청과 같은 순서로 저장된 행을 담는다.
  단건 API 의 응답 모양과 같다.
- 실패하면 아무것도 저장되지 않으므로, 화면은 입력값을 그대로 둔 채 다시 보내면 된다(중복 저장 걱정 없음).

**지출을 고치거나 지우면** 그 지출이 더해 둔 절약액만큼 `users.total_saved`(누적 절약액)도 맞춰진다.
수정은 바뀐 차이만큼, 삭제는 전부 빠지고 0 아래로는 내려가지 않는다. EMA 와 목표 누적액은 되돌리지 않는다([`domain.md`](domain.md)).

### 예산

화면은 가계부의 "예산 현황" 카드(`frontend/src/pages/Wallet/BudgetCard.jsx`)다. 우측 "예산 설정" 바로가기는
`/wallet#budget` 으로 들어가 입력 칸을 바로 연다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/budgets?yearMonth=2026-09` | 그 달 예산 + 사용액 |
| PUT | `/api/budgets` | 설정 (upsert — 존재 여부 조회 불필요) |
| DELETE | `/api/budgets/{yearMonth}` | 해제 |

```json
// 미설정 — budgetSet 으로 구분할 것. amount 0 을 "예산 0원"으로 읽으면 안 된다.
{ "id": null, "yearMonth": "2026-09", "amount": 0, "spent": 509000,
  "remaining": 0, "usagePct": 0, "exceeded": false, "budgetSet": false }
```

```json
// 설정 후 — 초과 상태
{ "id": 2, "yearMonth": "2026-09", "amount": 300000, "spent": 509000,
  "remaining": -209000, "usagePct": 169, "exceeded": true, "budgetSet": true }
```

`usagePct` 는 **100 을 넘는다**(169%). 진행 바는 100 에서 잘라 그리되 숫자는 그대로 노출할 것. `remaining` 은 초과 시 음수다.

### 목표

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/goals?yearMonth=2026-09` | 그 달 목표 + 진행률 |
| POST | `/api/goals` | 생성 · 201 · 같은 대상 중복이면 409 |
| PATCH | `/api/goals/{id}` | 금액 수정 · `abandon:true` 로 포기 |
| DELETE | `/api/goals/{id}` | 삭제 |

```json
// POST /api/goals — categoryId 를 생략하면 그 달 전체가 대상
{ "yearMonth": "2026-09", "categoryId": null, "targetAmount": 50000 }
```

```json
// 응답
{ "id": 5, "yearMonth": "2026-09", "categoryId": null, "categoryName": null,
  "targetAmount": 50000, "currentAmount": 0, "progressPct": 0, "status": "ACTIVE" }
```

`currentAmount` 에 쌓이는 것은 지출액이 아니라 **절약액**이다. 목표치를 넘으면 `status` 가 `DONE` 으로 바뀐다.

### 영수증

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/receipts` | multipart · 필드명 `file` · 6~13초(축소 후) |
| GET | `/api/receipts` | 내 인식 이력 |
| GET | `/api/receipts/{id}` | 단건 + 품목 상세 |

```js
const form = new FormData();
form.append('file', file);            // 필드명은 반드시 'file'

const res = await fetch('http://localhost:8080/api/receipts', {
  method: 'POST',
  credentials: 'include',
  body: form,                         // headers 를 넣지 말 것 (boundary 가 깨진다)
});
```

```json
// 응답 — amount / item 이 응답 필드명이다 (extracted 안쪽 이름과 다르다)
{
  "id": 4, "status": "SUCCESS",
  "amount": 8000, "date": "2026-09-03", "item": "삼각김밥 참치마요",
  "extracted": {
    "storeName": "GS25 동양대점", "totalAmount": 8000, "time": "18:42",
    "items": [ { "name": "삼각김밥 참치마요", "quantity": 2, "amount": 3000 } ],
    "paymentMethod": "CREDIT"
  },
  "errorMessage": null, "expenseId": null
}
```

### 알 · 펫

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/eggs/products` | 등급별 가격 · 확률 |
| POST | `/api/eggs/buy?grade=BASIC` | 구매 (펫이 있어도 가능) |
| POST | `/api/eggs/{id}/open` | 개봉 · **펫이 있으면 409** |
| GET | `/api/pets/active` | 키우는 펫 (없으면 본문 없는 200) · `exp`(스탯 합 × 10)·`level`·`levelExp`·`levelExpNeeded`·`maxLevel` 포함 |
| POST | `/api/pets/{id}/release` | 배웅 · 30레벨 미만이면 400 |
| PUT | `/api/pets/{id}/name` | 펫 이름 짓기 · 요청 `{ name }`(1~10자, 앞뒤 공백 제외) · 응답 펫 · 규칙 위반 400 · 배웅한 펫 409 |
| POST | `/api/pets/active/interact` | 상호작용(쓰다듬기 등) 1회 · 1% 확률로 매력 +1 · 응답 `{ charmUp, pet, newTitles }` — `newTitles` 는 이번 상호작용으로 받은 칭호 `[{ name, hidden }]`(대개 빈 배열) · 펫 없으면 400 |

```json
[ { "grade": "BASIC",     "name": "기본 알",   "price": 250,
    "probabilities": { "S": 1,  "A": 5,  "B": 24, "C": 70 } },
  { "grade": "PREMIUM",   "name": "고급 알",   "price": 600,
    "probabilities": { "S": 5,  "A": 20, "B": 45, "C": 30 } },
  { "grade": "LEGENDARY", "name": "최고급 알", "price": 1500,
    "probabilities": { "S": 20, "A": 50, "B": 30, "C": 0 } } ]
```

```json
// GET /api/pets/active
{ "id": 22, "speciesName": "강아지", "tier": "C", "appearanceKey": "puppy",
  "variant": "NORMAL", "stage": "ADULT",
  "statEnergy": 2156, "statCharm": 0, "statIq": 0, "statEndurance": 0,
  "statTotal": 2156, "releasedAt": null, "releaseValue": null }
```

`appearanceKey` 로 이미지를 고르고, `variant`(`NORMAL` / `IRO` / `ALIEN`)로 색을 바꾼다.
`stage` 는 `INFANT` → `JUVENILE`(스탯합 200) → `ADULT`(300)이고 **되돌아가지 않는다.** 배웅은 `ADULT` 에서만 가능하다.

---

## 5. 함정 6가지

실제로 서버를 띄워 호출하다 나온 것들이다. 문서만 보고는 알 수 없어 따로 적는다.

### 1. ~~영수증 업로드 1MB 제한~~ → 해결됨 (2026-09-09)

`f5a78ac`(multipart 상한 10MB)가 PR #15 로 main 에 들어왔다. **휴대폰 사진 그대로 올려도 된다** — 3024×4032 · 4.4MB 실측 통과.

10MB 를 넘기면 **413** 이 뜨고 `"이미지는 10MB 이하만 업로드할 수 있습니다"` 가 내려온다.

> 같은 커밋으로 `bootRun.workingDir` 도 고쳐져서, 이제 `backend/` 에서 `gradlew bootRun` 이 그냥 된다. 예전처럼 repo 루트에서 띄우거나 환경변수를 손으로 넣을 필요가 없다.

### 2. 영수증 응답까지 6~13초 걸린다

AI 가 이미지를 읽는 시간이라 크게 줄이기 어렵다. **진행 표시가 반드시 필요**하고, `fetch` 에 타임아웃을 건다면 **60초 이상**으로 잡을 것.

실측(2026-09-17, 각 2회):

| 이미지 | 원본 | 2048px 축소 |
|---|---|---|
| 실물 영수증 iPhone 1.99MB | 12.2초 | **6.0초** (전송 0.47MB) |
| 합성 3.72MB | 13.8초 | 11.6초 (전송 0.40MB) |
| 합성 노이즈 5.2MB | 25.5초 | 10.7초 |

인식 결과는 크기와 무관하게 같았다. **단축 폭은 사진마다 다르고, 확실한 이득은 전송 용량이다.**
그래서 화면은 업로드 전에 항상 줄여 보낸다(`prepareReceiptImage`, PR #26).

### 3. 목표는 만든 이후의 지출부터 쌓인다

절약액은 지출을 등록하는 순간 그 달의 `ACTIVE` 목표에 더해진다. **이미 등록된 지출은 소급되지 않는다.** 목표를 만들자마자 `currentAmount: 0` 이 뜨는 건 정상이다.

시연할 때는 **목표를 먼저 만들고 그다음에 지출을 넣어야** 진행률이 오른다.

### 4. 배치해야 배웅 선물 보너스에 들어간다

사두기만 하고 마이룸에 놓지 않은 가구(`placed: false`)는 개별 보너스도, 세트 계산도 전부 제외된다. "꾸며야 이득" 이 보상 설계 의도다.

화면에서 **인벤토리와 배치를 시각적으로 구분**해주지 않으면 사용자는 왜 배웅 선물이 안 오르는지 알 수 없다.

### 5. 펫이 있으면 알을 깔 수 없다 (409)

한 번에 한 마리만 키우는 구조라, 펫을 보유한 채로 개봉하면 `"먼저 키우던 펫을 배웅해주세요"` 가 돌아온다. **구매는 막지 않으므로** 알을 쟁여두는 건 가능하다.

개봉 버튼 옆에 현재 펫이 있으면 안내를 미리 띄워주면 좋다.

### 6. `GET /api/pets/active` 는 펫이 없으면 본문 없이 200 이다

404 가 아니라 **빈 200** 이다. `res.json()` 이 그대로 터지므로 위 래퍼처럼 `.catch(() => null)` 로 받아 `null` 체크를 할 것. 펫을 배웅한 직후가 이 상태다.

---

## 6. 시연 데이터 만들기

9/30 중간발표용. 새 계정은 코인 0 · 펫 INFANT 라 아무것도 보여줄 수 없다.

### 코인과 스탯이 생기는 공식

PR #56(#58 로 머지)부터 코인과 펫 스탯은 **지출 저장 때가 아니라 하루 판정 때** 나온다. 지출의 `stat_delta` 는 항상 0 이다.
하루 판정은 그날 지출 중 가장 강한 신호(RED > GRAY > GREEN)로 정하고 날짜마다 한 번만 지급한다.

| 그날 판정 | 코인 | 펫 스탯 (4개 스탯 각각) |
|---|---|---|
| GREEN | 500 | +11 |
| GRAY | 250 | +6 |
| RED | 100 | +2 |

배치한 가구에 배웅 보너스(%)가 있으면 해당 스탯에 그만큼 더 붙는다. 일반 계정은 **그날만, `vori.ai-judge.open-hour`(기본 20시) 이후에**
판정할 수 있고, 관리자는 날짜와 시각 제한 없이 할 수 있다.

예전처럼 지출 두 건으로 코인·스탯을 한 번에 만드는 방법은 더 이상 통하지 않는다. 시연 계정의 펫은 관리자 도구로 키운다.

```
1) 하루 판정 — 판정 대상은 로그인한 본인이다. 관리자 계정으로 시연하면 지난 날짜도 날짜마다 1회 판정할 수 있다
POST /api/daily-judgments?date=2026-09-28
   → 예외 지출(답 안 한 AI 질문)이 있으면 1차 판정(status=PENDING)만 하고 보상은 아직 없다 (docs/judgment-flow.md)
POST /api/daily-judgments/finalize?date=2026-09-28
   → 사유를 답했거나 건너뛸 때 최종 판정(status=FINALIZED). 보상은 그날 자정에 한 번 지급된다(관리자의 지난 날짜 판정은 바로)

2) 어드민 치트 — 펫을 스탯 정확히 300 인 ADULT 로 (어드민 계정으로 호출)
POST /api/admin/users/{userId}/pet/grow?stage=ADULT
```

**시연 순서 주의.** 목표는 지출보다 먼저 만들어야 진행률이 오르고(함정 3), 가구는 배치까지 해야 배웅 선물에 반영된다(함정 4). 알 개봉을 보여줄 거라면 그 전에 펫을 배웅해둘 것(함정 5).

---

## 관련 문서

- [`domain.md`](domain.md) — 도메인 규칙 (신호등 판정, AI 질문 트리거 등)
- [`db-spec.md`](db-spec.md) — 테이블 정의
- [`backend-flow.md`](backend-flow.md) — 레이어별 책임
- [`architecture.md`](architecture.md) — 전체 구조
