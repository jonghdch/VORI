import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import Icon from "../../components/Icon";
import { categoryIcon } from "../../components/SignalChip";
import {
  CATEGORIES_BY_TYPE,
  PAYMENT_METHODS,
  categorize,
  parseIsoDate,
  toIsoDate,
} from "./utils";
import { categorizeRemote } from "../../api/categorize";
import { MAX_RECEIPT_BYTES, prepareReceiptImage, uploadReceipt } from "../../api/receipt";
import {
  saveLedgerEntries,
  deleteExpense,
  listCategoryTree,
  listExpensesByDate,
  listIncomesByDate,
  listSavingsByDate,
} from "../../api/ledger";
import { loadUserSettings } from "../Settings/SettingsPage";
import { listInstruments } from "../../api/paymentInstruments";
import { INSTRUMENT_KINDS, issuerOf } from "../../constants/issuers";
import "./WalletRecord.css";

// 지출 기록 — 한 건씩 적는다. 날짜는 다른 화면에서 ?date= 로 정해 들어오고, 머리의 날짜 칩으로 바꿀 수 있다.
// 폰: 한 줄 + 아래 고정 버튼. PC: 가운데 입력 카드 한 장. 그날 기록은 머리의 「기록 내역」 버튼으로 여는 창에서 본다.
// 지출 수정(?edit=true)으로 들어오면 그날 첫 지출을 불러와 고치고, 목록에서 다른 지출을 눌러 바꿔 고친다.

const TYPES = [
  { id: "expense", label: "지출" },
  { id: "income", label: "수입" },
  { id: "savings", label: "저축" },
];

// 지출 메모 최대 길이 — 서버(expenses.memo VARCHAR(200))와 같다.
const MEMO_MAX = 200;

// 자동 분류가 애매하다고 돌려준 경우(askType)의 질문 문구. 판정 문구처럼 중립으로 둔다.
const ASK_TEXT = {
  WHAT: {
    question: "무엇을 샀나요?",
    hint: "산 것을 같이 쓰면 바로 분류돼요 (예: 편의점 컵라면)",
    other: true, // 칩에 없는 카테고리를 고를 「그 외」
  },
  // 매장·포장 / 배달 — 포장한 사람도 고를 게 있게 묻는다
  DINE_OR_DELIVERY: {
    question: "어떻게 먹었나요?",
  },
};

const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];
function dateChipLabel(iso) {
  const d = parseIsoDate(iso);
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})`;
}

const won = (n) => `${Number(n || 0).toLocaleString("ko-KR")}원`;
const labelOf = (list, value) => list.find((o) => o.value === value)?.label;

function WalletEntryPage({ user }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const todayStr = toIsoDate();
  const requestedDate = params.get("date");
  const requestedDateIsValid =
    requestedDate &&
    /^\d{4}-\d{2}-\d{2}$/.test(requestedDate) &&
    toIsoDate(parseIsoDate(requestedDate)) === requestedDate;
  // 쿼리를 직접 수정해도 미래 날짜에는 가계부를 작성할 수 없다.
  const dateStr = requestedDateIsValid && requestedDate <= todayStr ? requestedDate : todayStr;
  const isEditMode = params.get("edit") === "true";

  // 사용자 설정의 기본 결제수단 — 없으면 CREDIT
  const defaultPayment = loadUserSettings().defaultPaymentMethod || "CREDIT";

  // 설정 > 카드·계좌에 등록한 결제수단. 지출은 결제 수단 칩 앞에(예적금 제외), 수입은 받은 곳 칩으로
  // (신용카드 제외 — 체크카드는 연결 통장으로 받는 것). 못 불러와도 칩 없이 그대로 적을 수 있으니 조용히 넘어간다.
  const [allInstruments, setAllInstruments] = useState([]);
  const instruments = allInstruments.filter((i) => i.group !== "SAVINGS");
  const incomeAccounts = allInstruments.filter((i) => i.kind !== "CREDIT");
  const savingsAccounts = allInstruments.filter((i) => i.kind === "SAVINGS"); // 저축의 모은 곳
  useEffect(() => {
    let alive = true;
    listInstruments()
      .then((list) => {
        if (alive) setAllInstruments(list);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  const emptyForm = useCallback(
    (keep = {}) => ({
      name: "",
      amount: "",
      categoryId: null, // 지출 — 소분류 id
      categoryTouched: false, // 지출 — 직접 골랐으면 자동 분류로 덮지 않는다
      categoryEnum: null, // 수입 출처 / 저축 유형
      sourceTouched: false,
      paymentMethod: defaultPayment,
      paymentInstrumentId: null, // 설정 > 카드·계좌에서 등록한 카드를 골랐으면 그 id
      memo: "",
      memoOpen: false,
      dbId: null, // 저장된 지출을 고치는 중이면 그 id
      askType: null,
      candidates: [],
      categorizing: false,
      ...keep,
    }),
    [defaultPayment],
  );

  // 쓰다 만 한 건은 sessionStorage 에 둔다 — 다른 화면에 갔다 와도 남게.
  // 키에 사용자 id 를 넣는다. 날짜만 쓰면 같은 브라우저에서 계정을 바꿨을 때 앞 사람의 입력이 떠오른다.
  // 저장된 지출을 고치는 중(dbId)이면 보관하지 않는다 — 서버 값이 기준이다.
  const storageKey = `ledger-entry-${user?.id ?? "anon"}-${dateStr}`;
  const readDraft = () => {
    try {
      const parsed = JSON.parse(sessionStorage.getItem(storageKey) || "null");
      return parsed?.form && !parsed.form.dbId ? parsed : null;
    } catch {
      return null;
    }
  };
  const [type, setType] = useState(() => (isEditMode ? "expense" : readDraft()?.type || "expense"));
  const [form, setForm] = useState(() =>
    isEditMode ? emptyForm() : { ...emptyForm(), ...(readDraft()?.form || {}), categorizing: false },
  );
  const patch = (p) => setForm((f) => ({ ...f, ...p }));
  const [notice, setNotice] = useState(null); // { kind: info|ok|warn|err, text } — 영수증·저장 안내
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    try {
      const hasContent = form.name.trim() || form.amount;
      if (!form.dbId && hasContent) {
        sessionStorage.setItem(storageKey, JSON.stringify({ type, form }));
      } else if (!form.dbId) {
        sessionStorage.removeItem(storageKey);
      }
    } catch {}
  }, [form, type, storageKey]);
  const clearDraft = () => {
    try {
      sessionStorage.removeItem(storageKey);
    } catch {}
  };

  // ── 카테고리 트리 (지출) ─────────────────────────────────
  const [tree, setTree] = useState([]);
  useEffect(() => {
    let alive = true;
    listCategoryTree()
      .then((t) => alive && setTree(Array.isArray(t) ? t : []))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  const leafById = useMemo(() => {
    const map = new Map();
    tree.forEach((p) => (p.children || []).forEach((leaf) => map.set(leaf.id, { name: leaf.name, parent: p.name })));
    return map;
  }, [tree]);

  // ── 그날 기록 ────────────────────────────────────────────
  // null = 불러오는 중. 저장·삭제 뒤 다시 읽는다.
  const [day, setDay] = useState(null);
  const [dayError, setDayError] = useState(false);
  const loadDay = useCallback(async () => {
    try {
      const [exps, incs, savs] = await Promise.all([
        listExpensesByDate(dateStr),
        listIncomesByDate(dateStr),
        listSavingsByDate(dateStr),
      ]);
      const next = { expenses: exps || [], incomes: incs || [], savings: savs || [] };
      setDay(next);
      setDayError(false);
      return next;
    } catch {
      setDay({ expenses: [], incomes: [], savings: [] });
      setDayError(true);
      return null;
    }
  }, [dateStr]);

  // 저장된 지출을 폼으로 불러와 고친다.
  const editExpense = useCallback(
    (e) => {
      setType("expense");
      setForm(
        emptyForm({
          dbId: e.id,
          name: e.item || "",
          amount: String(e.amount ?? ""),
          categoryId: e.categoryId ?? null,
          categoryTouched: true, // 저장된 카테고리 — 자동 분류로 덮지 않음
          paymentMethod: e.paymentMethod || defaultPayment,
          paymentInstrumentId: e.paymentInstrumentId ?? null,
          memo: e.memo ?? "", // 다시 보낼 때 그대로 돌려줘야 지워지지 않는다
          memoOpen: Boolean(e.memo),
        }),
      );
      setNotice(null);
      setSubmitError(null);
    },
    [emptyForm, defaultPayment],
  );

  useEffect(() => {
    let alive = true;
    setDay(null);
    loadDay().then((d) => {
      // 지출 수정으로 들어왔으면 그날 첫 지출을 바로 연다
      if (alive && isEditMode && d?.expenses?.length) editExpense(d.expenses[0]);
    });
    return () => {
      alive = false;
    };
  }, [loadDay, isEditMode, editExpense]);

  // ── 내역 → 자동 분류 ─────────────────────────────────────
  // 지출: 백엔드 분류(내 기록·규칙·Gemini). 400ms 쉬었다 묻는다. 직접 고른 카테고리는 덮지 않는다.
  // 수입·저축: 로컬 키워드 룰로 출처/유형 제안 (직접 고르기 전까지).
  useEffect(() => {
    const name = form.name.trim();
    if (type !== "expense") {
      setForm((f) => (f.sourceTouched || !name ? f : { ...f, categoryEnum: categorize(name, type) }));
      return undefined;
    }
    if (!name) {
      setForm((f) => (f.categoryTouched ? f : { ...f, categoryId: null, categorizing: false, askType: null, candidates: [] }));
      return undefined;
    }
    if (form.categoryTouched) return undefined;
    patch({ categorizing: true });
    let cancelled = false;
    const handle = setTimeout(async () => {
      const r = await categorizeRemote(name);
      if (cancelled) return;
      // 기다리는 사이 직접 골랐으면 그 선택을 덮지 않는다
      setForm((f) =>
        f.categoryTouched
          ? { ...f, categorizing: false, askType: null, candidates: [] }
          : {
              ...f,
              categorizing: false,
              categoryId: r?.leafId ?? null,
              askType: r?.askType ?? null,
              candidates: r?.candidates ?? [],
            },
      );
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.name, type]);

  const changeType = (next) => {
    if (next === type || form.dbId) return;
    setType(next);
    // 내역·금액은 그대로, 분류만 새 종류에 맞게 다시
    setForm((f) => emptyForm({ name: f.name, amount: f.amount, paymentMethod: f.paymentMethod, memo: f.memo, memoOpen: f.memoOpen }));
  };

  // ── 영수증 OCR ──────────────────────────────────────────
  // 인식 결과를 바로 저장하지 않고 폼을 채워만 준다. 흐린 영수증은 일부만 읽히므로 확인 단계를 남긴다.
  const receiptInput = useRef(null);
  const [receiptBusy, setReceiptBusy] = useState(false);

  const onReceiptPick = async (e) => {
    const file = e.target.files?.[0];
    // 같은 파일을 다시 골라도 change 가 발생하도록 값을 비운다.
    e.target.value = "";
    if (!file) return;

    setReceiptBusy(true);
    setNotice({ kind: "info", text: "사진을 읽는 중이에요. 10~15초쯤 걸려요." });
    try {
      // 업로드 전에 긴 변 2048px 로 줄인다 — prepareReceiptImage 참조.
      const photo = await prepareReceiptImage(file);
      // 서버 상한을 넘으면 413 이 오는데 message 가 실리지 않는다. 축소한 뒤에 미리 거른다.
      if (photo.size > MAX_RECEIPT_BYTES) {
        const mb = (photo.size / 1024 / 1024).toFixed(1);
        setNotice({ kind: "err", text: `사진이 너무 커요 (${mb}MB). 10MB 이하로 올려주세요.` });
        return;
      }
      const r = await uploadReceipt(photo);

      // 영수증이 아니거나 판독 불가여도 200 이 온다 — 값이 비었는지로 판단한다.
      if (r.amount == null && !r.item) {
        const source = r.extracted?.sourceType;
        const text =
          r.errorMessage ||
          (source === "MULTIPLE"
            ? "결제가 여러 건 보여요. 한 건만 보이게 잘라서 다시 올려주세요."
            : source === "NOT_PAYMENT"
              ? "결제 내역이 보이지 않아요. 취소·환불·입금 내역은 지출로 채우지 않아요."
              : "사진을 읽지 못했어요. 더 밝은 곳에서 다시 찍거나 캡처를 다시 올려주세요.");
        setNotice({ kind: "err", text });
        return;
      }

      setType("expense");
      setForm(
        emptyForm({
          name: r.item || r.extracted?.storeName || "",
          amount: r.amount != null ? String(r.amount) : "",
          // 프롬프트가 결제수단을 우리 enum 으로 뽑으므로 그대로 쓴다. UNKNOWN·미인식이면 기본값 유지.
          paymentMethod: PAYMENT_METHODS.some((p) => p.value === r.extracted?.paymentMethod)
            ? r.extracted.paymentMethod
            : defaultPayment,
        }),
      );
      // 사진 속 날짜가 지금 날짜와 다르면 알려만 준다.
      const other = r.date && r.date !== dateStr;
      setNotice({
        kind: other ? "warn" : "ok",
        text: other
          ? `사진 속 날짜는 ${r.date} 예요. 지금은 ${dateStr} 을 작성 중이라 금액만 채웠어요.`
          : "사진을 읽었어요. 금액과 내역을 확인해주세요.",
      });
    } catch (err) {
      setNotice({ kind: "err", text: err.message });
    } finally {
      setReceiptBusy(false);
    }
  };

  // ── 카테고리 선택 창 ─────────────────────────────────────
  const [sheetOpen, setSheetOpen] = useState(false);
  // 그날 기록 창
  const [dayOpen, setDayOpen] = useState(false);

  // ── 메모 ────────────────────────────────────────────────
  const memoRef = useRef(null);
  const focusMemoNext = useRef(false);
  const showMemo = form.memoOpen || Boolean(form.memo.trim());
  useEffect(() => {
    if (showMemo && focusMemoNext.current) {
      focusMemoNext.current = false;
      memoRef.current?.focus();
    }
  }, [showMemo]);

  // ── 저장 ────────────────────────────────────────────────
  const amountRef = useRef(null);
  const nameRef = useRef(null);
  const [submitting, setSubmitting] = useState(false);

  const amountNum = Number(form.amount || 0);
  const missing = !form.amount || amountNum <= 0
    ? "금액을 입력해 주세요"
    : !form.name.trim()
      ? "내역을 입력해 주세요"
      : type === "expense" && form.categorizing
        ? "카테고리를 분류하는 중이에요…"
        : type === "expense" && !form.categoryId
          ? "카테고리를 골라 주세요"
          : null;
  const canSave = !missing && !submitting;

  const save = async (andMore) => {
    if (!canSave) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const item = form.name.trim();
      await saveLedgerEntries({
        expenses:
          type === "expense"
            ? [{
                id: form.dbId ?? null,
                item,
                amount: amountNum,
                categoryId: form.categoryId,
                paymentMethod: form.paymentMethod,
                paymentInstrumentId: form.paymentInstrumentId ?? null,
                spentAt: `${dateStr}T00:00:00`,
                memo: form.memo ?? "", // 빈 글자 = 메모 없음(수정이면 지움)
              }]
            : [],
        incomes:
          type === "income"
            ? [{
                item,
                amount: amountNum,
                source: form.categoryEnum || "OTHER",
                paymentInstrumentId: form.paymentInstrumentId ?? null, // 받은 곳(계좌·현금)
                paymentMethod: null, // 수입은 결제수단 없음 — 출처(source)만
                receivedAt: dateStr,
              }]
            : [],
        savings:
          type === "savings"
            ? [{
                item,
                amount: amountNum,
                savingType: form.categoryEnum || "DEPOSIT",
                savedAt: dateStr,
                // 모은 곳은 예적금에서만 (자동 분류가 투자로 바꿔 놓았으면 보내지 않는다)
                paymentInstrumentId: (form.categoryEnum || "DEPOSIT") === "DEPOSIT" ? form.paymentInstrumentId ?? null : null,
              }]
            : [],
      });
      clearDraft();
      if (!andMore) {
        navigate(`/wallet?date=${dateStr}`);
        return;
      }
      // 하나 더 — 종류·결제수단은 그대로 두고 비운 뒤 금액부터 다시
      setForm(emptyForm({ paymentMethod: form.paymentMethod, paymentInstrumentId: form.paymentInstrumentId }));
      setNotice({ kind: "ok", text: `'${item}' ${won(amountNum)}을 저장했어요. 다음 것을 적어 주세요.` });
      loadDay();
      amountRef.current?.focus();
    } catch (e) {
      setSubmitError(e.message || "저장 중 오류가 발생했어요");
    } finally {
      setSubmitting(false);
    }
  };

  const removeEditing = async () => {
    if (!form.dbId) return;
    if (!window.confirm(`'${form.name || "이 지출"}'을 삭제할까요?`)) return;
    try {
      await deleteExpense(form.dbId);
      setForm(emptyForm({ paymentMethod: form.paymentMethod, paymentInstrumentId: form.paymentInstrumentId }));
      setNotice({ kind: "ok", text: "삭제했어요." });
      loadDay();
    } catch (error) {
      setSubmitError(error.message || "지출을 삭제하지 못했어요.");
    }
  };

  const goBack = () => {
    if (window.history.length > 1) navigate(-1);
    else navigate("/home");
  };

  // ── 표시용 ──────────────────────────────────────────────
  const selectedLeaf = form.categoryId != null ? leafById.get(form.categoryId) : null;
  const ask = ASK_TEXT[form.askType];
  const showAsk = type === "expense" && !form.categorizing && Boolean(ask) && (form.candidates?.length ?? 0) > 1;
  const otherSelected =
    form.categoryId != null && !(form.candidates ?? []).some((c) => c.leafId === form.categoryId);

  const dayRows = useMemo(() => {
    if (!day) return [];
    return [
      ...day.expenses.map((e) => {
        const leaf = leafById.get(e.categoryId);
        return {
          key: `e-${e.id}`,
          raw: e,
          kind: "expense",
          name: e.item,
          sub: leaf ? leaf.name : "지출",
          icon: categoryIcon(leaf ? `${leaf.parent} ${leaf.name}` : ""),
          amount: -e.amount,
        };
      }),
      ...day.incomes.map((i) => ({
        key: `i-${i.id}`,
        kind: "income",
        name: i.item,
        sub: `수입 · ${labelOf(CATEGORIES_BY_TYPE.income, i.source) || "기타"}`,
        icon: "wallet",
        amount: i.amount,
      })),
      ...day.savings.map((s) => ({
        key: `s-${s.id}`,
        kind: "savings",
        name: s.item,
        sub: `저축 · ${labelOf(CATEGORIES_BY_TYPE.savings, s.savingType) || "예금"}`,
        icon: "piggy",
        amount: s.amount,
      })),
    ];
  }, [day, leafById]);
  const dayExpenseTotal = (day?.expenses || []).reduce((s, e) => s + Number(e.amount || 0), 0);

  const editing = Boolean(form.dbId);
  const saveLabel = submitting ? "저장 중…" : editing ? "수정 완료" : "저장";

  return (
    <div className="wr">
      <header className="wr-top">
        <button type="button" className="wr-icon-btn" aria-label="뒤로" onClick={goBack}>
          <Icon name="chevronLeft" size={22} />
        </button>
        <h1 className="wr-top-title">{editing ? "지출 수정" : `${TYPES.find((t) => t.id === type).label} 기록`}</h1>
        {/* 그날 기록 — 눌러서 창으로 본다. 건수가 있으면 숫자를 붙인다 */}
        <button
          type="button"
          className="wr-day-open"
          onClick={() => setDayOpen(true)}
          aria-haspopup="dialog"
          aria-label={`기록 내역 ${dayRows.length}건 보기`}
        >
          <Icon name="ledger" size={15} />
          <span className="wr-day-open-text">기록 내역</span>
          {dayRows.length > 0 && <span className="wr-day-open-count">{dayRows.length}</span>}
        </button>
        {/* 날짜 칩 — 누르면 달력이 열린다. 미래는 고를 수 없다 */}
        <label className="wr-date">
          <Icon name="calendar" size={14} />
          {dateChipLabel(dateStr)}
          <input
            type="date"
            value={dateStr}
            max={todayStr}
            aria-label="기록할 날짜"
            onChange={(e) => e.target.value && navigate(`/wallet/new?date=${e.target.value}`, { replace: true })}
          />
        </label>
      </header>

      <div className="wr-body">
        <main className="wr-card wr-form">
          {/* 지출 · 수입 · 저축 */}
          <div className="wr-seg" role="tablist" aria-label="기록 종류">
            {TYPES.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={type === t.id}
                className={type === t.id ? "is-active" : ""}
                disabled={editing && t.id !== type}
                onClick={() => changeType(t.id)}
              >
                {type === t.id && <Icon name="check" size={14} strokeWidth={2.4} />}
                {t.label}
              </button>
            ))}
          </div>

          {editing && (
            <div className="wr-editing">
              <span>저장된 지출을 고치는 중이에요</span>
              <button type="button" onClick={() => setForm(emptyForm({ paymentMethod: form.paymentMethod, paymentInstrumentId: form.paymentInstrumentId }))}>
                새로 쓰기
              </button>
            </div>
          )}

          {/* 금액 — 이 화면의 핵심 숫자 */}
          <label className="wr-amount">
            <span className="wr-sr">금액</span>
            <input
              ref={amountRef}
              type="text"
              inputMode="numeric"
              autoFocus={!isEditMode}
              placeholder="0"
              value={form.amount ? Number(form.amount).toLocaleString("ko-KR") : ""}
              onChange={(e) => patch({ amount: e.target.value.replace(/[^\d]/g, "").slice(0, 12) })}
              enterKeyHint="next"
              onKeyDown={(e) => {
                if (e.key !== "Enter" || e.nativeEvent?.isComposing) return;
                e.preventDefault();
                nameRef.current?.focus();
              }}
              style={{ "--len": Math.max(1, (form.amount ? Number(form.amount).toLocaleString("ko-KR") : "0").length) }}
            />
            <span className="wr-amount-unit">원</span>
          </label>

          {/* 영수증·결제 캡처 OCR — 인식에 6~13초 걸려 진행 표시가 필수다.
              종이 영수증이 없는 결제가 많아 카드 승인 문자·결제 완료 캡처도 받는다. */}
          {type === "expense" && !editing && (
            <>
              <input ref={receiptInput} type="file" accept="image/*" hidden onChange={onReceiptPick} />
              <button
                type="button"
                className="wr-receipt"
                onClick={() => receiptInput.current?.click()}
                disabled={receiptBusy}
              >
                <Icon name="camera" size={17} />
                {receiptBusy ? "사진 읽는 중…" : "영수증·결제 캡처로 채우기"}
              </button>
            </>
          )}
          {notice && (
            <p className={`wr-notice wr-notice--${notice.kind}`} role="status">
              {notice.text}
            </p>
          )}

          {/* 내역 */}
          <div className="wr-field">
            <label className="wr-label" htmlFor="wr-name">
              내역
            </label>
            <input
              id="wr-name"
              ref={nameRef}
              className="wr-input"
              type="text"
              value={form.name}
              onChange={(e) =>
                patch({ name: e.target.value, ...(type === "expense" ? { categoryTouched: false } : { sourceTouched: false }) })
              }
              enterKeyHint="done"
              onKeyDown={(e) => {
                if (e.key !== "Enter" || e.nativeEvent?.isComposing) return;
                e.preventDefault();
                e.currentTarget.blur();
              }}
              placeholder={
                type === "income" ? "예: 10월 월급, 엄마 용돈" : type === "savings" ? "예: 청년적금" : "예: GS25 삼각김밥, 학식, 지하철"
              }
            />
            {showAsk && (
              <div className="wr-ask" role="group" aria-label={ask.question}>
                <span className="wr-ask-q">{ask.question}</span>
                <div className="wr-chips">
                  {form.candidates.map((c) => {
                    const selected = form.categoryId === c.leafId;
                    return (
                      <button
                        key={c.leafId}
                        type="button"
                        className={`wr-chip ${selected ? "is-on" : ""}`}
                        aria-pressed={selected}
                        onClick={() => patch({ categoryId: c.leafId, categoryTouched: true })}
                      >
                        {selected && <Icon name="check" size={13} strokeWidth={2.4} />}
                        {c.label}
                      </button>
                    );
                  })}
                  {ask.other && (
                    <button
                      type="button"
                      className={`wr-chip ${otherSelected ? "is-on" : ""}`}
                      aria-pressed={otherSelected}
                      onClick={() => setSheetOpen(true)}
                    >
                      그 외
                    </button>
                  )}
                </div>
                {ask.hint && <p className="wr-ask-hint">{ask.hint}</p>}
              </div>
            )}
          </div>

          {/* 카테고리 (지출) / 출처 (수입) / 유형 (저축) */}
          {type === "expense" ? (
            <div className="wr-field">
              <span className="wr-label">카테고리</span>
              <button type="button" className="wr-cat" onClick={() => setSheetOpen(true)} aria-haspopup="dialog">
                {form.categorizing ? (
                  <span className="wr-cat-wait">분류 중…</span>
                ) : selectedLeaf ? (
                  <>
                    <span className="wr-chip is-on wr-chip--static">{selectedLeaf.name}</span>
                    <span className="wr-cat-meta">{form.categoryTouched ? selectedLeaf.parent : "자동 분류"}</span>
                  </>
                ) : (
                  <span className="wr-cat-wait">{form.name.trim() ? "분류하지 못했어요 — 골라 주세요" : "내역을 쓰면 자동으로 골라요"}</span>
                )}
                <span className="wr-cat-change">
                  바꾸기 <Icon name="chevronRight" size={14} />
                </span>
              </button>
            </div>
          ) : (
            <div className="wr-field">
              <span className="wr-label">{type === "income" ? "어디서 받았나요" : "저축 유형"}</span>
              <div className="wr-chips" role="radiogroup">
                {CATEGORIES_BY_TYPE[type].map((o) => {
                  const on = form.categoryEnum === o.value;
                  return (
                    <button
                      key={o.value}
                      type="button"
                      role="radio"
                      aria-checked={on}
                      className={`wr-chip ${on ? "is-on" : ""}`}
                      onClick={() =>
                        patch({
                          categoryEnum: o.value,
                          sourceTouched: true,
                          // 투자는 모은 곳을 고르지 않는다 — 예적금에서 골라 둔 통장을 비운다
                          ...(type === "savings" && o.value === "INVEST" ? { paymentInstrumentId: null } : {}),
                        })
                      }
                    >
                      {on && <Icon name="check" size={13} strokeWidth={2.4} />}
                      {o.label}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* 받은 곳 (수입만) — 등록한 입출금·예적금·현금. 고르지 않아도 된다 */}
          {type === "income" && (
            <div className="wr-field">
              <div className="wr-label-row">
                <span className="wr-label">받은 곳</span>
                <button type="button" className="wr-label-link" onClick={() => navigate("/settings/payment")}>
                  카드·계좌 관리 <Icon name="chevronRight" size={13} />
                </button>
              </div>
              {incomeAccounts.length === 0 ? (
                <p className="wr-hint">통장·체크카드·현금을 등록하면 어디로 받았는지 고를 수 있어요.</p>
              ) : (
                <div className="wr-chips" role="radiogroup" aria-label="받은 곳">
                  {[{ id: null, name: "고르지 않음" }, ...incomeAccounts].map((acc) => {
                    const on = (form.paymentInstrumentId ?? null) === acc.id;
                    return (
                      <button
                        key={acc.id ?? "none"}
                        type="button"
                        role="radio"
                        aria-checked={on}
                        className={`wr-chip ${acc.id ? "wr-chip--mine" : ""} ${on ? "is-on" : ""}`}
                        onClick={() => patch({ paymentInstrumentId: acc.id })}
                      >
                        {on && <Icon name="check" size={13} strokeWidth={2.4} />}
                        {acc.id && (
                          <span className="wr-chip-dot" style={{ background: issuerOf(acc.issuer).color }} aria-hidden />
                        )}
                        {acc.name}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* 모은 곳 (저축 · 예적금만) — 등록한 저축 통장. 투자는 계좌를 고르지 않는다 */}
          {type === "savings" && form.categoryEnum !== "INVEST" && (
            <div className="wr-field">
              <div className="wr-label-row">
                <span className="wr-label">모은 곳</span>
                <button type="button" className="wr-label-link" onClick={() => navigate("/settings/payment")}>
                  카드·계좌 관리 <Icon name="chevronRight" size={13} />
                </button>
              </div>
              {savingsAccounts.length === 0 ? (
                <p className="wr-hint">저축 통장을 등록하면 어디에 모았는지 고를 수 있어요.</p>
              ) : (
                <div className="wr-chips" role="radiogroup" aria-label="모은 곳">
                  {[{ id: null, name: "고르지 않음" }, ...savingsAccounts].map((acc) => {
                    const on = (form.paymentInstrumentId ?? null) === acc.id;
                    return (
                      <button
                        key={acc.id ?? "none"}
                        type="button"
                        role="radio"
                        aria-checked={on}
                        className={`wr-chip ${acc.id ? "wr-chip--mine" : ""} ${on ? "is-on" : ""}`}
                        onClick={() => patch({ paymentInstrumentId: acc.id })}
                      >
                        {on && <Icon name="check" size={13} strokeWidth={2.4} />}
                        {acc.id && (
                          <span className="wr-chip-dot" style={{ background: issuerOf(acc.issuer).color }} aria-hidden />
                        )}
                        {acc.name}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* 결제 수단 (지출만) */}
          {type === "expense" && (
            <div className="wr-field">
              <div className="wr-label-row">
                <span className="wr-label">결제 수단</span>
                <button type="button" className="wr-label-link" onClick={() => navigate("/settings/payment")}>
                  카드·계좌 관리 <Icon name="chevronRight" size={13} />
                </button>
              </div>
              <div className="wr-chips" role="radiogroup" aria-label="결제 수단">
                {/* 내 카드·계좌 — 고르면 종류(paymentMethod)도 그 카드에 맞춘다 */}
                {instruments.map((inst) => {
                  const on = form.paymentInstrumentId === inst.id;
                  return (
                    <button
                      key={`inst-${inst.id}`}
                      type="button"
                      role="radio"
                      aria-checked={on}
                      className={`wr-chip wr-chip--mine ${on ? "is-on" : ""}`}
                      onClick={() =>
                        patch({ paymentInstrumentId: inst.id, paymentMethod: INSTRUMENT_KINDS[inst.kind].paymentMethod })
                      }
                    >
                      {on && <Icon name="check" size={13} strokeWidth={2.4} />}
                      {/* 카드사·은행 색 점 — 같은 이름이라도 어느 회사 카드인지 한눈에 */}
                      <span className="wr-chip-dot" style={{ background: issuerOf(inst.issuer).color }} aria-hidden />
                      {inst.name}
                    </button>
                  );
                })}
                {instruments.length > 0 && <span className="wr-chip-divider" aria-hidden />}
                {PAYMENT_METHODS.map((p) => {
                  // 등록한 카드를 고른 동안은 종류 칩을 켜지 않는다. 보관해서 목록에 없는 카드면 종류로 보여 준다.
                  const pickedMine = instruments.some((i) => i.id === form.paymentInstrumentId);
                  const on = !pickedMine && form.paymentMethod === p.value;
                  return (
                    <button
                      key={p.value}
                      type="button"
                      role="radio"
                      aria-checked={on}
                      className={`wr-chip ${on ? "is-on" : ""}`}
                      onClick={() => patch({ paymentMethod: p.value, paymentInstrumentId: null })}
                    >
                      {on && <Icon name="check" size={13} strokeWidth={2.4} />}
                      {p.label}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* 메모 (지출만) — 평소엔 「+ 메모 추가」만. 사용자만 보는 기록(분류·AI 에 안 씀) */}
          {type === "expense" &&
            (showMemo ? (
              <div className="wr-field">
                <label className="wr-label" htmlFor="wr-memo">
                  메모
                </label>
                <input
                  id="wr-memo"
                  ref={memoRef}
                  className="wr-input"
                  type="text"
                  value={form.memo}
                  maxLength={MEMO_MAX}
                  onChange={(e) => patch({ memo: e.target.value })}
                  onBlur={() => !form.memo.trim() && patch({ memoOpen: false })}
                  placeholder="예: 학교 앞에서 동기들이랑"
                />
              </div>
            ) : (
              <button
                type="button"
                className="wr-memo-add"
                onClick={() => {
                  focusMemoNext.current = true;
                  patch({ memoOpen: true });
                }}
              >
                <Icon name="plus" size={15} /> 메모 추가
              </button>
            ))}

          {editing && (
            <button type="button" className="wr-delete" onClick={removeEditing}>
              <Icon name="trash" size={15} /> 이 지출 삭제
            </button>
          )}

          {/* 저장 — 폰에서는 화면 아래에 붙고, PC 에서는 카드 아래에 놓인다 */}
          <div className="wr-actions">
            {(missing || submitError) && (
              <p className={`wr-hint ${submitError ? "is-error" : ""}`} role={submitError ? "alert" : undefined}>
                {submitError || missing}
              </p>
            )}
            <div className="wr-actions-row">
              {!editing && (
                <button type="button" className="wr-btn wr-btn--ghost" onClick={() => save(true)} disabled={!canSave}>
                  저장하고 하나 더
                </button>
              )}
              <button type="button" className="wr-btn wr-btn--dark" onClick={() => save(false)} disabled={!canSave}>
                {saveLabel}
              </button>
            </div>
          </div>
        </main>
      </div>

      {sheetOpen && (
        <CategorySheet
          tree={tree}
          value={form.categoryId}
          currentLabel={selectedLeaf?.name}
          onClose={() => setSheetOpen(false)}
          onPick={(id) => {
            patch({ categoryId: id, categoryTouched: true, askType: null, candidates: [] });
            setSheetOpen(false);
          }}
        />
      )}

      {dayOpen && (
        <DayRecordsSheet
          title={`${dateChipLabel(dateStr)} 기록`}
          rows={dayRows}
          loading={day === null}
          error={dayError}
          expenseTotal={dayExpenseTotal}
          editingId={form.dbId}
          onClose={() => setDayOpen(false)}
          onEdit={(raw) => {
            editExpense(raw);
            setDayOpen(false);
          }}
        />
      )}
    </div>
  );
}

// 창 틀 — 폰은 아래에서 올라오는 시트, PC 는 가운데 창. Esc·바깥 누르기·X 로 닫는다.
// 열려 있는 동안 뒤 화면은 스크롤되지 않는다.
function Sheet({ title, onClose, children, footer }) {
  const panelRef = useRef(null);
  const titleId = useRef(`wr-sheet-${Math.random().toString(36).slice(2, 8)}`).current;

  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus();
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [onClose]);

  return (
    <div className="wr-sheet-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="wr-sheet" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1} ref={panelRef}>
        <span className="wr-sheet-grip" aria-hidden />
        <div className="wr-sheet-head">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="wr-icon-btn" aria-label="닫기" onClick={onClose}>
            <Icon name="close" size={20} />
          </button>
        </div>
        {children}
        {footer && <div className="wr-sheet-foot">{footer}</div>}
      </div>
    </div>
  );
}

// 카테고리 선택 — 칩을 눌러 고르고 「선택 완료」로 확정한다. 닫기만 하면 바꾸지 않는다.
function CategorySheet({ tree, value, currentLabel, onClose, onPick }) {
  const [picked, setPicked] = useState(value);
  return (
    <Sheet
      title="카테고리 선택"
      onClose={onClose}
      footer={
        <button
          type="button"
          className="wr-btn wr-btn--dark wr-btn--block"
          disabled={picked == null}
          onClick={() => onPick(picked)}
        >
          선택 완료
        </button>
      }
    >
      <p className="wr-sheet-current">
        <span>
          현재 분류: <strong>{currentLabel || "없음"}</strong>
        </span>
        <span>변경할 카테고리를 터치하세요</span>
      </p>
      <div className="wr-sheet-body">
        {tree.length === 0 && <p className="wr-day-empty">불러오는 중…</p>}
        {tree.map((group) => (
          <section key={group.id} className="wr-sheet-group" aria-label={group.name}>
            <h3>{group.name}</h3>
            <div className="wr-chips">
              {(group.children || []).map((leaf) => {
                const on = picked === leaf.id;
                return (
                  <button
                    key={leaf.id}
                    type="button"
                    className={`wr-chip ${on ? "is-on" : ""}`}
                    aria-pressed={on}
                    onClick={() => setPicked(leaf.id)}
                    onDoubleClick={() => onPick(leaf.id)}
                  >
                    {on && <Icon name="check" size={13} strokeWidth={2.4} />}
                    {leaf.name}
                  </button>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </Sheet>
  );
}

// 그날 기록 — 저장한 것을 확인하고, 지출은 눌러서 폼으로 불러와 고친다.
// 수입·저축은 서버에 수정 기능이 없어 보기만 한다.
function DayRecordsSheet({ title, rows, loading, error, expenseTotal, editingId, onClose, onEdit }) {
  return (
    <Sheet title={title} onClose={onClose}>
      <p className="wr-sheet-current">
        <span>
          <strong>{rows.length}건</strong>
          {expenseTotal > 0 && <> · 지출 {won(expenseTotal)}</>}
        </span>
        <span>지출을 누르면 고칠 수 있어요</span>
      </p>
      <div className="wr-sheet-body">
        {loading ? (
          <p className="wr-day-empty">불러오는 중…</p>
        ) : error ? (
          <p className="wr-day-empty">기록을 불러오지 못했어요.</p>
        ) : rows.length === 0 ? (
          <p className="wr-day-empty">아직 이 날 기록이 없어요.</p>
        ) : (
          <ul className="wr-day-list">
            {rows.map((r) => {
              const isEditingThis = r.kind === "expense" && editingId === r.raw.id;
              const body = (
                <>
                  <span className="wr-day-icon">
                    <Icon name={r.icon} size={17} />
                  </span>
                  <span className="wr-day-mid">
                    <span className="wr-day-name">{r.name}</span>
                    <span className="wr-day-sub">{isEditingThis ? "고치는 중" : r.sub}</span>
                  </span>
                  <span className={`wr-day-amt ${r.amount > 0 ? "is-plus" : ""}`}>
                    {r.amount > 0 ? "+" : "-"}
                    {won(Math.abs(r.amount))}
                  </span>
                </>
              );
              return (
                <li key={r.key}>
                  {r.kind === "expense" ? (
                    <button
                      type="button"
                      className={`wr-day-row ${isEditingThis ? "is-current" : ""}`}
                      onClick={() => onEdit(r.raw)}
                      aria-label={`${r.name} 고치기`}
                    >
                      {body}
                      <Icon name="pencil" size={15} className="wr-day-edit" />
                    </button>
                  ) : (
                    <div className="wr-day-row">{body}</div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Sheet>
  );
}

export default WalletEntryPage;
