# 프론트엔드 연동 가이드

> 아직 화면에 붙지 않은 도메인(**가구·테마·목표·예산**)을 React 에 연결하기 위한 문서.
> 칭호는 PR #22, 영수증은 PR #21 로 붙었다 — 아래 명세는 그대로 유효하고, 붙은 것의 실제 사용 예는
> `frontend/src/api/{titles,receipt}.js` 를 보면 된다.
> 아래 JSON 은 전부 실제로 서버를 띄워 호출해 받은 응답이고, 예시로 지어낸 값이 없다.

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
| 400 | 코인 부족 · 검증 실패 · 성체 아닌 펫 분양 | `코인이 부족합니다` | 토스트로 `message` 노출 |
| 401 | 로그인하지 않음 · 세션 만료 | (본문 없음) | 로그인 화면으로 이동 |
| 403 | 잠긴 테마 가구 구매 · 남의 데이터 접근 | `'절약 새싹' 칭호를 획득해야…` | 해금 조건 안내 |
| 404 | 없는 id 로 조회·수정 | `칭호를 찾을 수 없습니다` | 목록 새로고침 |
| 409 | 좌표 중복 · 펫 있는데 알 개봉 · 목표 중복 | `먼저 키우던 펫을 분양해주세요` | 안내 후 해당 동작 유도 |
| 413 | 업로드 용량 초과 | `이미지는 10MB 이하만…` | 파일 다시 선택 |
| 500 | 서버 오류 | (message 없음) | 일반 문구 + 채린에게 공유 |

**401 만 예외다.** 로그인 여부는 Spring Security 필터가 컨트롤러보다 먼저 판단하기 때문에 `message` 가 실리지 않고 본문이 비어 있다. `body?.message ?? '로그인이 필요합니다.'` 처럼 기본값을 둘 것.

---

## 3. 화면별 호출 순서

순서가 실제로 중요한 화면만 적었다. 번호는 반드시 이 차례로 호출해야 하는 흐름이라는 뜻이다.

### 마이룸 — 펫 · 가구 · 테마

세 API 가 한 화면에서 서로를 참조한다. 테마 현황이 분양가와 직결된다.

1. `GET /api/pets/active`
   키우는 펫. **펫이 없으면 본문 없이 200** 이므로 `null` 체크가 필요하다.
2. `GET /api/furniture`
   `placed: true` 인 것만 방에 그리고, 나머지는 인벤토리 서랍에 둔다.
3. `GET /api/themes`
   `placedCount / requiredCount` 로 "코지 2/3 — 하나만 더!" 를 그린다. `active: true` 면 지금 분양가에 보너스가 실제로 얹히는 중이다.
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

지출 한 건이 절약·코인·펫 스탯·목표·칭호를 전부 움직인다.

1. `GET /api/categories`
   카테고리 선택지. `categoryId` 가 `statType` 을 결정한다.
2. `POST /api/expenses`
   응답의 `signalFinal`(GREEN/GRAY/RED)로 신호등을, `savedAmount` 로 "얼마 아꼈어요" 를 띄운다.
3. `GET /api/titles`
   지출 등록 직후 칭호가 새로 붙었을 수 있다. `acquired` 가 바뀐 항목을 축하 연출에 쓴다.

### 영수증 스캔

응답까지 **18~31초** 걸린다. 로딩 UI 없이 붙이면 멈춘 것처럼 보인다.

1. `POST /api/receipts` (multipart)
   PNG · JPEG, 10MB 이하. 헤더에 `Content-Type` 을 **넣지 않는다.**
2. 응답의 `amount` · `date` · `item` 을 지출 폼에 채움
   `status: "SUCCESS"` 여도 개별 값은 `null` 일 수 있다(흐린 영수증). 읽힌 값만 채우고 나머지는 사용자가 입력하게 둔다.
3. `POST /api/expenses`
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
  "code": "DESKTOP_PC", "name": "컴퓨터", "category": "COMPUTER",
  "statTarget": "IQ", "releaseBonusPct": 4.00, "price": 12000,
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

### 칭호

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/titles` | 13개 전부 (획득 먼저, 미획득은 달성 근접 순) |
| PUT | `/api/titles/active` | 장착 · 204 · `titleId` null 이면 해제 |

```json
[
  { "id": 8, "code": "SAVER_SPROUT", "name": "절약 새싹", "description": "누적 절약 10만원",
    "acquired": true, "active": false,
    "current": 2160124, "threshold": 100000, "progressPct": 100,
    "acquiredAt": "2026-09-08T10:54:41" },
  { "id": null, "code": "RECORD_STEADY", "name": "꾸준한 기록가", "description": "지출 50건 기록",
    "acquired": false, "active": false,
    "current": 10, "threshold": 50, "progressPct": 20,
    "acquiredAt": null }
]
```

**미획득 칭호도 진행률과 함께 내려온다.** 잠금 아이콘만 띄우지 말고 `current / threshold` 진행 바를 그릴 것. 그게 다음 목표가 된다.
장착은 `{ "titleId": 8 }`, 해제는 `{ "titleId": null }`.

### 예산

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
| POST | `/api/receipts` | multipart · 필드명 `file` · 18~31초 |
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
| GET | `/api/pets/active` | 키우는 펫 (없으면 본문 없는 200) |
| POST | `/api/pets/{id}/release` | 분양 · 성체 아니면 400 |

```json
[ { "grade": "BASIC",     "name": "기본 알",   "price": 2500,
    "probabilities": { "S": 1,  "A": 5,  "B": 24, "C": 70 } },
  { "grade": "PREMIUM",   "name": "고급 알",   "price": 6000,
    "probabilities": { "S": 5,  "A": 20, "B": 45, "C": 30 } },
  { "grade": "LEGENDARY", "name": "최고급 알", "price": 15000,
    "probabilities": { "S": 20, "A": 50, "B": 30, "C": 0 } } ]
```

```json
// GET /api/pets/active
{ "id": 22, "speciesName": "강아지", "tier": "B", "appearanceKey": "puppy",
  "variant": "NORMAL", "stage": "ADULT",
  "statEnergy": 2156, "statCharm": 0, "statIq": 0, "statEndurance": 0,
  "statTotal": 2156, "releasedAt": null, "releaseValue": null }
```

`appearanceKey` 로 이미지를 고르고, `variant`(`NORMAL` / `IRO` / `ALIEN`)로 색을 바꾼다.
`stage` 는 `INFANT` → `JUVENILE`(스탯합 200) → `ADULT`(300)이고 **되돌아가지 않는다.** 분양은 `ADULT` 에서만 가능하다.

---

## 5. 함정 6가지

실제로 서버를 띄워 호출하다 나온 것들이다. 문서만 보고는 알 수 없어 따로 적는다.

### 1. ~~영수증 업로드 1MB 제한~~ → 해결됨 (2026-09-09)

`f5a78ac`(multipart 상한 10MB)가 PR #15 로 main 에 들어왔다. **휴대폰 사진 그대로 올려도 된다** — 3024×4032 · 4.4MB 실측 통과.

10MB 를 넘기면 **413** 이 뜨고 `"이미지는 10MB 이하만 업로드할 수 있습니다"` 가 내려온다.

> 같은 커밋으로 `bootRun.workingDir` 도 고쳐져서, 이제 `backend/` 에서 `gradlew bootRun` 이 그냥 된다. 예전처럼 repo 루트에서 띄우거나 환경변수를 손으로 넣을 필요가 없다.

### 2. 영수증 응답까지 18~31초 걸린다

4.4MB 사진 실측이 30.5초였다. AI 가 이미지를 읽는 시간이라 줄이기 어렵다. **진행 표시가 반드시 필요**하고, `fetch` 에 타임아웃을 건다면 **60초 이상**으로 잡을 것.

### 3. 목표는 만든 이후의 지출부터 쌓인다

절약액은 지출을 등록하는 순간 그 달의 `ACTIVE` 목표에 더해진다. **이미 등록된 지출은 소급되지 않는다.** 목표를 만들자마자 `currentAmount: 0` 이 뜨는 건 정상이다.

시연할 때는 **목표를 먼저 만들고 그다음에 지출을 넣어야** 진행률이 오른다.

### 4. 배치해야 분양가 보너스에 들어간다

사두기만 하고 마이룸에 놓지 않은 가구(`placed: false`)는 개별 보너스도, 세트 계산도 전부 제외된다. "꾸며야 이득" 이 보상 설계 의도다.

화면에서 **인벤토리와 배치를 시각적으로 구분**해주지 않으면 사용자는 왜 분양가가 안 오르는지 알 수 없다.

### 5. 펫이 있으면 알을 깔 수 없다 (409)

한 번에 한 마리만 키우는 구조라, 펫을 보유한 채로 개봉하면 `"먼저 키우던 펫을 분양해주세요"` 가 돌아온다. **구매는 막지 않으므로** 알을 쟁여두는 건 가능하다.

개봉 버튼 옆에 현재 펫이 있으면 안내를 미리 띄워주면 좋다.

### 6. `GET /api/pets/active` 는 펫이 없으면 본문 없이 200 이다

404 가 아니라 **빈 200** 이다. `res.json()` 이 그대로 터지므로 위 래퍼처럼 `.catch(() => null)` 로 받아 `null` 체크를 할 것. 펫을 분양한 직후가 이 상태다.

---

## 6. 시연 데이터 만들기

9/30 중간발표용. 새 계정은 코인 0 · 펫 INFANT 라 아무것도 보여줄 수 없다.

### 코인과 스탯이 생기는 공식

| 값 | 계산 | 예시 |
|---|---|---|
| 절약액 | 직전까지의 평균 − 이번 지출 | 400,000 − 1,000 = 399,000 |
| 코인 | 절약액 ÷ 10 | 39,900 |
| 펫 스탯 | 절약액 ÷ 1,000 | 399 |

그래서 **큰 지출 한 건을 먼저 넣어 평균을 올리고, 그다음 작은 지출**을 넣으면 코인과 스탯이 한 번에 확보된다. 시연 계정을 만드는 가장 빠른 방법이다.

```
1) 기준선 올리기 — 표본이 적어 신호등은 GREEN, 절약은 0
POST /api/expenses { "categoryId": 2, "amount": 400000, "item": "노트북", … }

2) 크게 아끼기 — 코인 39,900 + 펫 스탯 399 (성체 기준 300 돌파)
POST /api/expenses { "categoryId": 2, "amount": 1000, "item": "커피", … }

3) 어드민 치트 — 펫을 스탯 정확히 300 인 ADULT 로 (어드민 계정으로 호출)
POST /api/admin/users/{userId}/pet/grow?stage=ADULT
```

**시연 순서 주의.** 목표는 지출보다 먼저 만들어야 진행률이 오르고(함정 3), 가구는 배치까지 해야 분양가에 반영된다(함정 4). 알 개봉을 보여줄 거라면 그 전에 펫을 분양해둘 것(함정 5).

---

## 관련 문서

- [`domain.md`](domain.md) — 도메인 규칙 (신호등 판정, AI 질문 트리거 등)
- [`db-spec.md`](db-spec.md) — 테이블 정의
- [`backend-flow.md`](backend-flow.md) — 레이어별 책임
- [`architecture.md`](architecture.md) — 전체 구조
