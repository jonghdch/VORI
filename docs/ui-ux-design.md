---
date: 2026-04-27
tags: [vori, design, ui-ux, graduation-project]
target_feature: vori 메인 흐름 (지출 입력 → 4 방식 판정 → 결과 표시 → 히스토리)
based_on:
  - ~/Projects/vori/CLAUDE.md
  - docs/system-design.md
stack: Next.js 16 (App Router) + React 19 + TypeScript + Tailwind v4 + shadcn/ui
---

# Vori UI/UX 설계

## 한 줄 목표

사용자가 **30초 안에** 지출을 입력하고 4 방식의 합리성 판정 결과를 받아 자기관리 동기를 얻는다.

## 사용자 흐름 (Primary Scenario)

1. 사용자가 메인 화면 진입 → 오늘의 누적 스탯 + "+ 지출 등록" 버튼 확인
2. "지출 등록" 클릭 → 입력 모달 (금액·카테고리·시간·사유)
3. 제출 → 로딩 (4 방식 동시 호출, 평균 5~8초)
4. 결과 화면: 시그널 색상 (🟢/⚪/🔴) + 4 방식 비교 카드 + 유사 사례
5. "확인" → 메인으로 복귀, 스탯 갱신 애니메이션

추가 시나리오:
- **히스토리 탐색**: 메인 → 히스토리 탭 → 일·주·월 단위 시그널 분포 차트
- **통계 분석**: 카테고리별 / 시간대별 합리성 패턴
- **첫 사용**: 온보딩 (시그널 의미·4 방식 차이 1분 설명)

---

## 와이어프레임

### 1) 메인 (Dashboard)

```
┌──────────────────────────────────────────┐
│  Vori                       [Profile]    │ ← Header
├──────────────────────────────────────────┤
│                                           │
│   📊  오늘의 합리성 스탯                    │
│   ┌─────────────────────────────────┐    │
│   │      [캐릭터 일러스트]            │    │
│   │       Lv. 12 · +47 pts            │    │
│   │   🟢 8건  ⚪ 3건  🔴 2건          │    │
│   └─────────────────────────────────┘    │
│                                           │
│   ┌─────────────────────────────────┐    │
│   │       + 지출 등록                 │    │ ← Primary CTA
│   └─────────────────────────────────┘    │
│                                           │
│   📅 오늘                                 │
│   ┌─────────────────────────────────┐    │
│   │ 🟢 점심 12,000원 · 사유 있음      │    │
│   │ ⚪ 커피 4,500원                    │    │
│   │ 🔴 야식 18,000원 · 02:30          │    │
│   └─────────────────────────────────┘    │
│                                           │
│   [메인]  [히스토리]  [통계]  [설정]       │ ← Bottom Nav
└──────────────────────────────────────────┘
```

### 2) 지출 입력 모달

```
┌──────────────────────────────────────────┐
│   지출 등록                          [×]  │
├──────────────────────────────────────────┤
│                                           │
│   💰 금액                                 │
│   ┌─────────────────────────────────┐    │
│   │ 12,000원                         │    │
│   └─────────────────────────────────┘    │
│                                           │
│   📂 카테고리                             │
│   [식비] [쇼핑] [교통] [여가] [기타]      │ ← Chip 선택
│                                           │
│   🕒 시간 (자동 채움, 수정 가능)          │
│   ┌─────────────────────────────────┐    │
│   │ 2026-04-27 19:30                 │    │
│   └─────────────────────────────────┘    │
│                                           │
│   📝 사유 (선택)                          │
│   ┌─────────────────────────────────┐    │
│   │ 회식 자리에서 점심 못 먹어서...   │    │
│   └─────────────────────────────────┘    │
│   💡 사유를 적으면 합리성 +1 보너스        │
│                                           │
│   [    취소    ]  [    판정 받기    ]    │
└──────────────────────────────────────────┘
```

### 3) 판정 결과

```
┌──────────────────────────────────────────┐
│   판정 결과                          [<]  │
├──────────────────────────────────────────┤
│                                           │
│   ┌───────────────────────────────────┐  │
│   │       🔴   합리성 낮음             │  │
│   │       야식 · 18,000원               │  │
│   │       사유 보너스 +1 (적용됨)       │  │
│   │                                    │  │
│   │       스탯  −2 (+1 보너스) = −1    │  │
│   └───────────────────────────────────┘  │
│                                           │
│   📊 4 방식 비교                          │
│   ┌────────┬────────┬────────┬────────┐  │
│   │ A      │ B      │ C      │ D      │  │
│   │ Gemini │ kNN    │ Rules  │ Hybrid │  │
│   │ 🔴     │ 🔴     │ ⚪     │ 🔴     │  │
│   │ red    │ red    │ gray   │ red    │  │
│   └────────┴────────┴────────┴────────┘  │
│                                           │
│   🔍 가장 비슷한 과거 사례 (방식 B)         │
│   ・새벽 야식 15,000원 — 🔴 (유사도 0.84) │
│   ・심야 배달 20,000원 — 🔴 (유사도 0.78) │
│                                           │
│   📋 적용된 룰 (방식 C)                    │
│   ・심야 시간대 (00~04시) 가산              │
│   ・식비 평균 1.5배 초과                    │
│                                           │
│   [          확인          ]             │
└──────────────────────────────────────────┘
```

### 4) 히스토리

```
┌──────────────────────────────────────────┐
│   히스토리                                │
├──────────────────────────────────────────┤
│   [일] [주] [월]                  📅      │ ← Range tab
│                                           │
│   ┌─── 시그널 분포 차트 ────┐             │
│   │   🟢 ████████ 60%        │             │
│   │   ⚪ ████ 25%             │             │
│   │   🔴 ███ 15%              │             │
│   └─────────────────────────┘             │
│                                           │
│   📅 4월 27일                             │
│   ┌─ 🔴 야식 18,000 ────────────┐         │
│   │  사유: 회식 → 늦은 식사       │         │
│   │  스탯 −1                    │         │
│   └────────────────────────────┘         │
│   ┌─ 🟢 점심 12,000 ────────────┐         │
│   │  스탯 +3                    │         │
│   └────────────────────────────┘         │
│   ...                                     │
└──────────────────────────────────────────┘
```

---

## 컴포넌트 스펙

### `<DashboardHeader />`
- **데이터**: `username`, `level`, `totalScore`
- **인터랙션**: Profile 아이콘 클릭 → Settings
- **자식**: `<ProfileAvatar />`

### `<StatCard />`
- **데이터**: `level`, `score`, `signals: { green, gray, red }`
- **인터랙션**: 캐릭터 탭 시 레벨 디테일 모달
- **상태**: 정상 / 로딩 (스켈레톤)

### `<ExpenseInputModal />`
- **데이터**: 없음 (생성 form)
- **Props**: `onSubmit(payload)`, `onCancel()`
- **인터랙션**:
  - 카테고리 chip 선택 (단일 선택)
  - 사유 textarea (선택, 1~500자)
  - 제출 버튼 → 로딩 → 결과 화면
- **검증**: 금액 1원 이상, 카테고리 필수

### `<JudgmentResult />`
- **데이터**: `expense`, `signal`, `statDelta`, `byMethod: {a, b, c, d}`, `similarCases[]`, `appliedRules[]`
- **인터랙션**: "확인" → 메인으로 navigate
- **자식**: `<SignalBadge />`, `<MethodComparisonGrid />`, `<SimilarCasesList />`, `<AppliedRulesList />`

### `<MethodComparisonGrid />`
- **데이터**: `byMethod: { a, b, c, d }` 각각 `{ signal, reason }`
- **인터랙션**: 각 방식 카드 호버 → 상세 추론
- **레이아웃**: 데스크탑 4 col / 모바일 2×2 grid

### `<HistoryRangeView />`
- **데이터**: `range: 'day'|'week'|'month'`, `entries[]`, `distribution`
- **인터랙션**: 탭 전환, 날짜 선택, 무한 스크롤

---

## 인터랙션 디테일

- **클릭 시**: 모든 primary 버튼 ripple 효과 (Tailwind `transition`)
- **호버 시**: 카드 elevation 살짝 상승 (`shadow-md → shadow-lg`)
- **키보드**: Enter 로 모달 제출, Esc 로 취소·닫기, Tab 순서: 금액 → 카테고리 → 시간 → 사유 → 제출
- **드래그·드롭**: 사용 안 함

---

## 상태 정의 (5종 필수)

| 상태 | 메인 화면 표시 | 사용자 액션 |
|------|--------------|-----------|
| **정상** | 스탯 카드 + 오늘 지출 리스트 | 새 지출 등록·히스토리 탐색 |
| **로딩** | 스켈레톤 카드 (스탯·리스트 모두) — 판정 중에는 4 방식 카드 영역에 spinner 4개 | 대기 |
| **에러** | "잠시 후 다시 시도해주세요" 메시지 + 새로고침 버튼 (`Gemini API 실패` 시 방식 A 만 disabled, B/C/D 결과는 표시) | 재시도 / 닫기 |
| **빈** | "첫 지출을 등록해보세요" 안내 + 큰 CTA 버튼 | + 지출 등록 |
| **권한 없음** | 로그인 화면 (Google OAuth 단일 버튼) | Google 계정 로그인 |

---

## 접근성 (WCAG AA 기준)

- **키보드 네비게이션**: 모든 주요 동작이 Tab/Enter/Esc 로 가능. focus ring 명시 (Tailwind `focus-visible:ring-2`)
- **색 대비**: 시그널 색상 단독으로 의미 전달 X — 항상 아이콘(🟢⚪🔴) 또는 텍스트("합리성 낮음") 동반
- **스크린 리더 라벨**: 모든 아이콘 버튼에 `aria-label` (예: "지출 등록 모달 열기")
- **포커스 시각화**: 모달 진입 시 첫 입력 필드 자동 포커스 + 모달 close 시 트리거 버튼으로 복귀
- **반응형**: 모바일(360px) ~ 데스크탑(1280px) 모두 대응. 모바일은 bottom nav, 데스크탑은 sidebar

---

## 디자인 시스템 적용

- **P-001 AI 적극성**: **Suggest 레벨** — 판정 결과는 AI 의 "제안" 으로 표기, 사용자가 "이 판정 무시" 옵션 보유
- **AI 배지**: 4 방식 비교 그리드의 각 카드에 "🤖 AI" 또는 "📐 Rules" 라벨 명시 (방식 A·B = AI, C = Rules, D = Hybrid)
- **일괄 Undo**: 지출 등록 후 5초 내 "되돌리기" 토스트 표시
- **디자인 토큰** (CSS variable / Tailwind theme):
  - `--color-signal-green: #22c55e`
  - `--color-signal-gray: #94a3b8`
  - `--color-signal-red: #ef4444`
  - `--color-accent: #6366f1` (브랜드)
  - `--radius-card: 12px`
  - `--font-sans: 'Pretendard', system-ui`
- **기존 컴포넌트 재사용**: shadcn/ui Button, Dialog, Select, Tabs, Card

---

## 초안 코드 (Next.js + TypeScript)

> ⚠️ **초안임을 명시** — Engineer (메인 세션) 가 통합·테스트·다듬기 진행. 시드 데이터·API 라우터는 별도 작업.

### `<ExpenseInputModal />` 초안

```tsx
// src/components/ExpenseInputModal.tsx
'use client'

import { useState } from 'react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'

const CATEGORIES = ['식비', '쇼핑', '교통', '여가', '기타'] as const
type Category = (typeof CATEGORIES)[number]

interface Payload {
  amount: number
  category: Category
  occurredAt: string
  reason: string
}

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (payload: Payload) => Promise<void>
}

export function ExpenseInputModal({ open, onOpenChange, onSubmit }: Props) {
  const [amount, setAmount] = useState('')
  const [category, setCategory] = useState<Category>('식비')
  const [occurredAt] = useState(new Date().toISOString().slice(0, 16))
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async () => {
    const numAmount = Number(amount.replace(/,/g, ''))
    if (!Number.isFinite(numAmount) || numAmount < 1) return

    setSubmitting(true)
    try {
      await onSubmit({ amount: numAmount, category, occurredAt, reason })
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>지출 등록</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <label className="block">
            <span className="text-sm font-medium">💰 금액</span>
            <Input
              type="text"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="12,000"
              autoFocus
            />
          </label>

          <div>
            <span className="text-sm font-medium block mb-2">📂 카테고리</span>
            <div className="flex flex-wrap gap-2">
              {CATEGORIES.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setCategory(c)}
                  className={`px-3 py-1 rounded-full border text-sm transition ${
                    category === c
                      ? 'bg-accent text-white border-accent'
                      : 'border-border hover:bg-muted'
                  }`}
                >
                  {c}
                </button>
              ))}
            </div>
          </div>

          <label className="block">
            <span className="text-sm font-medium">📝 사유 (선택)</span>
            <Textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="왜 썼는지 한 줄..."
              rows={2}
            />
            <span className="text-xs text-muted-foreground">
              💡 사유를 적으면 합리성 +1 보너스
            </span>
          </label>
        </div>

        <div className="flex gap-2 justify-end">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button onClick={handleSubmit} disabled={submitting || !amount}>
            {submitting ? '판정 중...' : '판정 받기'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
```

배치 위치 제안: `src/components/ExpenseInputModal.tsx` + 페이지에서 `<ExpenseInputModal open={open} onOpenChange={setOpen} onSubmit={handleSubmit} />`

다른 컴포넌트(`<JudgmentResult />`, `<MethodComparisonGrid />` 등) 도 같은 stack 으로 작성 가능 — 발표 후 본 빌드 시 진행.

---

## 의문·미해결 (User Gate 후보)

- [ ] **인증 방식**: Google OAuth 만 / 카카오 OAuth 추가 / 익명 닉네임 모드 — 시연 20명 기준 어느 정도 깊이?
- [ ] **카테고리 커스터마이징**: 고정 5개로 시작 vs 사용자 추가 허용
- [ ] **공유·소셜**: 친구와 합리성 점수 비교 기능 — 졸작 범위 안 / 밖?
- [ ] **모바일 우선 vs 데스크탑 우선**: PWA 시연 환경 결정 (시연 시 발표자 노트북 / 사용자 폰?)
- [ ] **온보딩 깊이**: 첫 사용자 튜토리얼 — 1분 / 5분 / 생략

## 다음 액션

- [필수] Critic [Design 모드] 검토 (와이어프레임 누락·접근성·상태 5종)
- [필수] User Gate 5개 결정 (위 의문 섹션)
- [필수] 발표 자료 슬라이드 변환 (Notion / PPT / Keynote)
- [권장] 시연 시나리오 스크립트 (5분 발표 기준)
- [선택] Researcher [디자인 벤치마크] — 지출 추적 앱 5개 (토스가계부, 뱅크샐러드, 머니매니저 등) 와의 차별점

---

## 발표용 핵심 메시지 (3 슬라이드 분량)

1. **문제 정의** — "왜 썼는지 모르고 후회한다" → 사유 기반 합리성 판정으로 자기 인식
2. **차별점** — 단순 LLM 래퍼 X, **4 방식 비교** (Gemini · 한국어 임베딩 · 룰엔진 · 하이브리드) 로 시스템 사고 어필
3. **시연 흐름** — 30초 입력 → 5초 판정 → 4 방식 비교 + 유사 사례 + 적용 룰 표시

## 출처

- vori 프로토타입: `~/Projects/vori/CLAUDE.md`
- 시스템 설계: `docs/system-design.md`
- (참고 가능) 디자인 영감: 토스가계부 / 뱅크샐러드 (직접 복제 X)
