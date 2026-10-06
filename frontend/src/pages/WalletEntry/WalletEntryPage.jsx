import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import StepIndicator from "./StepIndicator";
import {
  CATEGORIES_BY_TYPE,
  PAYMENT_METHODS,
  categorize,
  formatToday,
  isPastDate,
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
import "./WalletEntry.css";

// Step 1 — 가계부 작성. 날짜는 다른 페이지에서 정해 ?date= 쿼리로 진입.
function WalletEntryPage({ user }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const todayStr = toIsoDate();
  const requestedDate = params.get("date");
  const requestedDateValue = parseIsoDate(requestedDate);
  const requestedDateIsValid =
    requestedDate &&
    /^\d{4}-\d{2}-\d{2}$/.test(requestedDate) &&
    toIsoDate(requestedDateValue) === requestedDate;
  // 쿼리를 직접 수정해도 미래 날짜에는 가계부를 작성할 수 없다.
  const dateStr =
    requestedDateIsValid && requestedDate <= todayStr ? requestedDate : todayStr;
  const isEditMode = params.get("edit") === "true";
  const past = isPastDate(dateStr);

  // 같은 날짜의 입력값을 sessionStorage 에 보관 — Step 2/3 갔다 와도 유지.
  // 성공적으로 POST 한 뒤에는 clearDraft() 로 비움 (중복 저장 방지).
  //
  // 키에 사용자 id 를 넣는다. 날짜만 쓰면 같은 브라우저에서 계정을 바꿔 로그인했을 때
  // 앞 사람의 입력이 그대로 떠오르고, 그 행이 dbId 를 달고 있어 "저장됨" 으로까지 표시된다
  // (실제로는 이 계정에 없는 지출이다). 시연 리허설을 다른 계정으로 해 본 뒤 무대 계정으로
  // 로그인하면 바로 겪는다.
  const storageKey = `ledger-entry-${user?.id ?? "anon"}-${dateStr}${isEditMode ? "-edit" : ""}`;
  const loadDraft = () => {
    try {
      const raw = sessionStorage.getItem(storageKey);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      // 저장된 행의 dbId 를 임시저장하면 삭제 뒤에도 과거 행이 되살아나 404가 난다.
      // 임시저장은 아직 서버에 저장하지 않은 행만 보관한다.
      return {
        income: (parsed.income || []).filter((row) => !row.dbId),
        expense: (parsed.expense || []).filter((row) => !row.dbId),
        savings: (parsed.savings || []).filter((row) => !row.dbId),
      };
    } catch {
      return null;
    }
  };
  const draft = loadDraft();

  // 행 id 시퀀스 — 복원된 데이터가 있으면 그 max id 다음, 없으면 1 부터.
  const draftRows = (draft?.income || []).concat(
    draft?.expense || [],
    draft?.savings || [],
  );
  const maxDraftId = draftRows.reduce((m, r) => Math.max(m, r.id || 0), 0);
  const nextId = useRef(1 + maxDraftId);
  // 사용자 설정의 기본 결제수단 — 없으면 CREDIT
  const defaultPayment =
    loadUserSettings().defaultPaymentMethod || "CREDIT";
  const newRow = () => ({
    id: nextId.current++,
    paymentMethod: defaultPayment,
    name: "",
    amount: "",
  });

  // 초기엔 빈 행을 강제하지 않음 (행 0개로 시작). 사용자가 "+ 추가"로 직접 넣음.
  const [income, setIncome] = useState(draft?.income || []);
  const [expense, setExpense] = useState(draft?.expense || []);
  const [savings, setSavings] = useState(draft?.savings || []);

  // 지출 카테고리 편집 드롭다운 옵션 — 백엔드 카테고리 트리를 평면화 (value=leafId).
  const [expenseCatOptions, setExpenseCatOptions] = useState([]);
  useEffect(() => {
    let alive = true;
    listCategoryTree()
      .then((tree) => {
        if (!alive) return;
        const opts = [];
        (tree || []).forEach((p) =>
          (p.children || []).forEach((leaf) =>
            opts.push({ value: leaf.id, label: `${p.name} · ${leaf.name}` }),
          ),
        );
        setExpenseCatOptions(opts);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 아직 서버에 없는 행만 임시저장한다. DB 행은 매 진입 때 서버에서 새로 읽는다.
  useEffect(() => {
    try {
      const draftRows = {
        income: income.filter((row) => !row.dbId),
        expense: expense.filter((row) => !row.dbId),
        savings: savings.filter((row) => !row.dbId),
      };
      if (draftRows.income.length || draftRows.expense.length || draftRows.savings.length) {
        sessionStorage.setItem(storageKey, JSON.stringify(draftRows));
      } else {
        sessionStorage.removeItem(storageKey);
      }
    } catch {}
  }, [income, expense, savings, storageKey]);

  // draft 비우는 helper. 성공적으로 POST 한 뒤 호출 → 다음 mount 에서는 DB fetch 로 폼 채움.
  const clearDraft = () => {
    try {
      sessionStorage.removeItem(storageKey);
    } catch {}
  };

  // mount (또는 dateStr 변경) 시 DB 에서 그 날짜 기존 데이터를 항상 fetch한다.
  // 서버 행과 아직 저장하지 않은 임시 행을 함께 보여 준다.
  useEffect(() => {
    const fresh = loadDraft();
    let cancelled = false;
    (async () => {
      try {
        const [exps, incs, savs] = await Promise.all([
          listExpensesByDate(dateStr),
          isEditMode ? Promise.resolve([]) : listIncomesByDate(dateStr),
          isEditMode ? Promise.resolve([]) : listSavingsByDate(dateStr),
        ]);
        if (cancelled) return;
        const next = (e) => nextId.current++;
        setExpense(
          [...exps.map((e) => ({
            id: next(),
            dbId: e.id,
            paymentMethod: e.paymentMethod || "CREDIT",
            name: e.item,
            amount: String(e.amount),
            categoryId: e.categoryId,
            categoryTouched: true, // 저장된 카테고리 — 자동분류로 덮지 않음
            // 같은 날짜를 다시 열면 저장된 지출도 바로 고칠 수 있게 한다.
            // 수정 링크의 쿼리가 사라져도 읽기 전용 행으로 잠기지 않는다.
            isEditing: true,
          })), ...(fresh?.expense || [])],
        );
        setIncome(
          [...incs.map((i) => ({
            id: next(),
            dbId: i.id,
            name: i.item,
            amount: String(i.amount),
            categoryEnum: i.source,
            sourceTouched: true,
          })), ...(fresh?.income || [])],
        );
        setSavings(
          [...savs.map((s) => ({
            id: next(),
            dbId: s.id,
            name: s.item,
            amount: String(s.amount),
            categoryEnum: s.savingType,
            sourceTouched: true,
          })), ...(fresh?.savings || [])],
        );
      } catch {
        // fetch 실패는 무시 — 빈 폼으로 시작
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateStr, isEditMode]);


  const addRow = (setter) => setter((rows) => [...rows, newRow()]);
  const removeRow = (setter, id) =>
    setter((rows) => rows.filter((r) => r.id !== id));

  const deleteExpenseRow = async (row) => {
    if (row.dbId) {
      if (!window.confirm(`'${row.name || "이 지출"}'을 삭제할까요?`)) return;
      try {
        await deleteExpense(row.dbId);
      } catch (error) {
        setSubmitError(error.message || "지출을 삭제하지 못했어요.");
        return;
      }
    }
    removeRow(setExpense, row.id);
  };

  // 현재 카드의 금액 입력이 끝나면 이미 만들어져 있는 다음 빈 카드로만 이동한다.
  // 새 카드를 자동 생성하지는 않는다. 섹션 표시 순서(수입 → 지출 → 저축)를 그대로 따른다.
  const focusNextEmptyCard = (currentType, currentId) => {
    const orderedRows = [
      ...income.map((row) => ({ ...row, entryType: "income" })),
      ...expense.map((row) => ({ ...row, entryType: "expense" })),
      ...savings.map((row) => ({ ...row, entryType: "savings" })),
    ];
    const currentIndex = orderedRows.findIndex(
      (row) => row.entryType === currentType && row.id === currentId,
    );
    if (currentIndex < 0) return;
    const current = orderedRows[currentIndex];
    if (!(current.name || "").trim() || !String(current.amount || "").trim()) return;

    const nextEmpty = orderedRows
      .slice(currentIndex + 1)
      .find((row) => !row.dbId && !(row.name || "").trim() && !String(row.amount || "").trim());
    if (!nextEmpty) return;

    const input = document.querySelector(
      `[data-entry-type="${nextEmpty.entryType}"][data-row-id="${nextEmpty.id}"] [data-entry-field="name"]`,
    );
    input?.focus();
  };

  // ── 영수증 OCR ──────────────────────────────────────────────────────────
  // 인식 결과를 바로 저장하지 않고 지출 행으로 채워만 준다. 흐린 영수증은 일부만
  // 읽히므로(모든 필드가 null 일 수 있다) 사용자가 확인·수정하는 단계를 반드시 남긴다.
  const receiptInput = useRef(null);
  const [receiptBusy, setReceiptBusy] = useState(false);
  const [receiptNotice, setReceiptNotice] = useState(null);

  const onReceiptPick = async (e) => {
    const file = e.target.files?.[0];
    // 같은 파일을 다시 골라도 change 가 발생하도록 값을 비운다.
    e.target.value = "";
    if (!file) return;

    setReceiptBusy(true);
    setReceiptNotice({ kind: "info", text: "사진을 읽는 중이에요. 10~15초쯤 걸려요." });
    try {
      // 업로드 전에 긴 변 2048px 로 줄인다. 용량이 큰 원본일수록 인식이 느려진다 — prepareReceiptImage 참조.
      const photo = await prepareReceiptImage(file);

      // 서버 상한을 넘으면 413 이 오는데 message 가 실리지 않는다. 미리 걸러야 안내가 된다.
      // 축소한 뒤에 검사한다 — 10MB 가 넘는 원본도 줄이고 나면 대부분 올라간다.
      if (photo.size > MAX_RECEIPT_BYTES) {
        const mb = (photo.size / 1024 / 1024).toFixed(1);
        setReceiptNotice({ kind: "err", text: `사진이 너무 커요 (${mb}MB). 10MB 이하로 올려주세요.` });
        return;
      }

      const r = await uploadReceipt(photo);

      // 영수증이 아니거나 판독 불가여도 200 이 온다 — 값이 비었는지로 판단한다.
      // 사진 종류(sourceType)로 이유를 나눠 안내한다. 여러 건이 찍힌 캡처는 서버가 값을 비워 보낸다.
      if (r.amount == null && !r.item) {
        const source = r.extracted?.sourceType;
        const text =
          r.errorMessage ||
          (source === "MULTIPLE"
            ? "결제가 여러 건 보여요. 한 건만 보이게 잘라서 다시 올려주세요."
            : source === "NOT_PAYMENT"
              ? "결제 내역이 보이지 않아요. 취소·환불·입금 내역은 지출로 채우지 않아요."
              : "사진을 읽지 못했어요. 더 밝은 곳에서 다시 찍거나 캡처를 다시 올려주세요.");
        setReceiptNotice({ kind: "err", text });
        return;
      }

      setExpense((rows) => [
        ...rows,
        {
          ...newRow(),
          name: r.item || r.extracted?.storeName || "",
          amount: r.amount != null ? String(r.amount) : "",
          // 프롬프트가 결제수단을 우리 enum 으로 뽑으므로 그대로 쓴다. UNKNOWN·미인식이면 기본값 유지.
          paymentMethod: PAYMENT_METHODS.some((p) => p.value === r.extracted?.paymentMethod)
            ? r.extracted.paymentMethod
            : defaultPayment,
        },
      ]);

      // 사진 속 날짜가 지금 보는 날짜와 다르면 알려만 준다. 날짜를 임의로 바꾸면
      // 이미 입력한 다른 행들이 엉뚱한 날짜로 저장된다.
      const other = r.date && r.date !== dateStr;
      setReceiptNotice({
        kind: other ? "warn" : "ok",
        text: other
          ? `사진 속 날짜는 ${r.date} 예요. 지금은 ${dateStr} 을 작성 중이라 금액만 채웠어요.`
          : "사진을 읽었어요. 금액과 항목을 확인해주세요.",
      });
    } catch (err) {
      setReceiptNotice({ kind: "err", text: err.message });
    } finally {
      setReceiptBusy(false);
    }
  };
  // autoCategory: 자동 분류 응답에서 온 패치. 응답을 기다리는 사이 사용자가 직접 골랐으면
  // (categoryTouched) 그 선택을 덮지 않는다 — 최신 행 상태를 여기서 봐야 늦게 온 응답도 걸러진다.
  const updateRow = (setter, id, patch) =>
    setter((rows) =>
      rows.map((r) => {
        if (r.id !== id) return r;
        const { autoCategory, ...rest } = patch;
        if (autoCategory && r.categoryTouched) delete rest.categoryId;
        return { ...r, ...rest };
      }),
    );

  // 빈 행(내역·금액 둘 다 비어있음)은 제출 시 무시한다 → 강제 삭제 불필요.
  const isRowEmpty = (r) =>
    !(r.name && r.name.trim()) && !(r.amount && String(r.amount).trim());
  const isRowComplete = (r) =>
    r.name && r.name.trim().length > 0 && r.amount && String(r.amount).length > 0;
  const allRows = [...income, ...expense, ...savings];
  const filledRows = allRows.filter((r) => !isRowEmpty(r));
  // 비어있지 않은 지출 행이 자동 분류 중이면 대기.
  const isCategorizing = expense.some((r) => !isRowEmpty(r) && r.categorizing);
  // 채워진 행이 하나 이상 있고, 그 행들이 전부 완성됐을 때만 진행.
  const canProceed =
    filledRows.length > 0 && filledRows.every(isRowComplete) && !isCategorizing;

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // 지출·수입·저축을 한 요청으로 저장한다. 서버가 한 트랜잭션으로 처리하므로
  // 하나라도 실패하면 아무것도 저장되지 않고, 고쳐서 다시 눌러도 중복되지 않는다.
  const goNext = async () => {
    if (!canProceed || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const expenseRows = expense.filter(
        (r) => !isRowEmpty(r) && (!r.dbId || r.isEditing),
      );
      const unclassified = expenseRows.find((r) => !r.categoryId);
      if (unclassified) {
        throw new Error(`"${unclassified.name}" 카테고리를 분류하지 못했어요. 잠시 후 다시 시도해주세요.`);
      }
      const incomeRows = income.filter((r) => !r.dbId && !isRowEmpty(r));
      const savingRows = savings.filter((r) => !r.dbId && !isRowEmpty(r));

      if (expenseRows.length + incomeRows.length + savingRows.length > 0) {
        await saveLedgerEntries({
          expenses: expenseRows.map((r) => ({
            id: r.isEditing ? r.dbId : null,
            item: r.name.trim(),
            amount: Number(r.amount),
            categoryId: r.categoryId,
            paymentMethod: r.paymentMethod,
            spentAt: `${dateStr}T00:00:00`,
          })),
          incomes: incomeRows.map((r) => ({
            item: r.name.trim(),
            amount: Number(r.amount),
            source: r.categoryEnum || "OTHER",
            paymentMethod: null, // 수입은 결제수단 없음 — 출처(source)만
            receivedAt: dateStr,
          })),
          savings: savingRows.map((r) => ({
            item: r.name.trim(),
            amount: Number(r.amount),
            savingType: r.categoryEnum || "DEPOSIT",
            savedAt: dateStr,
          })),
        });
      }
      // 성공 — draft 비움. 다음 mount 에서는 DB fetch 로 폼 채움 (정확한 dbId 포함).
      clearDraft();
      // 소비 분석은 더 이상 작성 흐름의 단계가 아니다. 입력 후 바로 확인으로.
      // (분석은 /wallet 의 ledger-ai-card 에서 오후 8시~자정 이벤트로 진행)
      navigate(`/wallet/new/confirm?date=${dateStr}`);
    } catch (e) {
      setSubmitError(e.message || "저장 중 오류가 발생했어요");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="ledger">
      <header className="ledger-entry-header">
        <button
          type="button"
          className="ledger-logo-btn"
          onClick={() => navigate("/home")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
      </header>

      <main className="ledger-entry-main">
        <StepIndicator current={1} includeAnalysis={false} />

        <div className="ledger-title-block">
          <h1 className="ledger-title">
            {formatToday(parseIsoDate(dateStr))}
          </h1>
          <p className="ledger-subtitle">
            {isEditMode
              ? "지출 내역을 수정해주세요. 내역을 바꾸면 카테고리도 다시 분류해요."
              : past
              ? "이전 날짜의 지출과 수입을 입력해주세요."
              : "오늘의 지출과 수입을 입력해주세요."}
          </p>
        </div>

        {/* 영수증·결제 캡처 OCR. 줄여서 보내도 인식에 6~13초 걸려서 진행 표시가 필수다 —
            아무 표시 없이 기다리게 하면 멈춘 것처럼 보인다. 종이 영수증이 없는 결제가 많아
            카드 승인 문자·결제 완료·이체 완료 캡처도 받는다(중간발표 상호평가). */}
        {!isEditMode && <section className="ledger-receipt">
          <input
            ref={receiptInput}
            type="file"
            accept="image/*"
            hidden
            onChange={onReceiptPick}
          />
          {/* .ledger-upload 는 예전 업로드 버튼 스타일이 CSS 에 그대로 남아 있던 것을 다시 쓴다. */}
          <button
            type="button"
            className="ledger-upload"
            onClick={() => receiptInput.current?.click()}
            disabled={receiptBusy}
          >
            {receiptBusy ? "사진 읽는 중…" : "📷 영수증·결제 캡처로 채우기"}
          </button>
          {receiptNotice && (
            <p className={`ledger-receipt-notice ledger-receipt-notice--${receiptNotice.kind}`}>
              {receiptNotice.text}
            </p>
          )}
        </section>}

        {income.length > 0 && (
          <section className="ledger-section">
            <div className="ledger-section-title">수입</div>
            {income.map((row, idx) => (
              <EntryRow
                key={row.id}
                num={idx + 1}
                row={row}
                type="income"
                onChange={(patch) => updateRow(setIncome, row.id, patch)}
                onDelete={() => removeRow(setIncome, row.id)}
                onComplete={() => focusNextEmptyCard("income", row.id)}
              />
            ))}
          </section>
        )}

        {expense.length > 0 && (
          <section className="ledger-section">
            <div className="ledger-section-title">지출</div>
            {expense.map((row, idx) => (
              <EntryRow
                key={row.id}
                num={idx + 1}
                row={row}
                type="expense"
                expenseCatOptions={expenseCatOptions}
                onChange={(patch) => updateRow(setExpense, row.id, patch)}
                onDelete={() => deleteExpenseRow(row)}
                onComplete={() => focusNextEmptyCard("expense", row.id)}
              />
            ))}
          </section>
        )}

        {savings.length > 0 && (
          <section className="ledger-section">
            <div className="ledger-section-title">저축</div>
            {savings.map((row, idx) => (
              <EntryRow
                key={row.id}
                num={idx + 1}
                row={row}
                type="savings"
                onChange={(patch) => updateRow(setSavings, row.id, patch)}
                onDelete={() => removeRow(setSavings, row.id)}
                onComplete={() => focusNextEmptyCard("savings", row.id)}
              />
            ))}
          </section>
        )}

        {!isEditMode && <div className="ledger-add-row">
          <button
            type="button"
            className="ledger-entry-add-btn"
            onClick={() => addRow(setIncome)}
          >
            + 수입 추가하기
          </button>
          <button
            type="button"
            className="ledger-entry-add-btn"
            onClick={() => addRow(setExpense)}
          >
            + 지출 추가하기
          </button>
          <button
            type="button"
            className="ledger-entry-add-btn"
            onClick={() => addRow(setSavings)}
          >
            + 저축 추가하기
          </button>
        </div>}

        <div className="ledger-actions">
          {!canProceed && (
            <p className="ledger-hint">
              {isCategorizing
                ? "지출 카테고리를 자동 분류하는 중이에요…"
                : "모든 항목의 내역과 금액을 입력해주세요"}
            </p>
          )}
          {submitError && <p className="ledger-hint">{submitError}</p>}
          <div className="ledger-actions-row">
            <button
              type="button"
              className="ledger-back"
              onClick={() => navigate("/wallet")}
              disabled={submitting}
            >
              돌아가기
            </button>
            <button
              type="button"
              className="ledger-next"
              onClick={goNext}
              disabled={!canProceed || submitting}
            >
              {submitting ? "저장 중…" : isEditMode ? "수정 완료" : "다음 단계"}
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}

// 자동 분류가 애매하다고 돌려준 경우(askType)의 질문 문구. 판정 문구처럼 중립으로 둔다.
const ASK_TEXT = {
  WHAT: {
    question: "무엇을 샀나요?",
    hint: "산 것을 같이 쓰면 바로 분류돼요 (예: 편의점 컵라면)",
  },
  DINE_OR_DELIVERY: {
    question: "매장에서 먹었나요, 배달했나요?",
  },
};

function EntryRow({
  num,
  row,
  type,
  expenseCatOptions = [],
  onChange,
  onDelete,
  onComplete,
}) {
  const amountInputRef = useRef(null);
  // 이미 DB 에 저장된 행 — 수정/삭제 API 가 없어서 여기서 고쳐도 반영되지 않는다.
  // 수정 가능한 척하지 않도록 읽기 전용으로 잠그고 "저장됨" 표시.
  const saved = Boolean(row.dbId);
  const locked = saved && !row.isEditing;

  // 이름이 바뀌면 자동 분류로 카테고리/출처를 "제안"한다.
  // 단, 사용자가 드롭다운에서 직접 고른 경우(*Touched)엔 그 선택을 덮지 않는다.
  useEffect(() => {
    if (locked) return;
    const name = (row.name || "").trim();
    if (!name) {
      onChange({
        categoryId: null,
        categoryEnum: null,
        categorizing: false,
        categoryTouched: false,
        sourceTouched: false,
        askType: null,
        candidates: [],
      });
      return;
    }
    if (type !== "expense") {
      // income/savings — 로컬 키워드 룰로 출처/유형 제안 (미선택 시에만)
      if (row.sourceTouched) return;
      onChange({ categoryEnum: categorize(name, type), categorizing: false });
      return;
    }
    // expense — 사용자가 직접 고른 경우 자동 분류로 덮지 않음
    if (row.categoryTouched) {
      onChange({ categorizing: false });
      return;
    }
    // 백엔드 분류(내 기록·규칙·Gemini). debounce 400ms. 진행 중엔 categorizing=true.
    // 이름만으로 애매하면 askType·candidates 가 같이 와서 칩으로 묻는다(기본값은 이미 골라져 있다).
    onChange({ categorizing: true });
    let cancelled = false;
    const handle = setTimeout(async () => {
      const r = await categorizeRemote(name);
      if (cancelled) return;
      onChange(
        r == null
          ? { autoCategory: true, categoryId: null, categorizing: false, askType: null, candidates: [] }
          : {
              autoCategory: true,
              categoryId: r.leafId,
              categorizing: false,
              askType: r.askType ?? null,
              candidates: r.candidates ?? [],
            },
      );
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [row.name, type]);

  // 이름만으로 애매할 때 내역 아래에 칩으로 묻는다. 분류를 다시 하는 동안엔 예전 칩을 숨긴다.
  const ask = ASK_TEXT[row.askType];
  const showAsk =
    type === "expense" && !locked && !row.categorizing && Boolean(ask) &&
    (row.candidates?.length ?? 0) > 1;

  // 카테고리/출처 선택 컨트롤 (눌러서 수정 가능한 드롭다운).
  const categoryControl =
    type === "expense" ? (
      <Dropdown
        value={row.categoryId ?? null}
        options={expenseCatOptions}
        onChange={(v) => onChange({ categoryId: v, categoryTouched: true })}
        placeholder={row.categorizing ? "분류 중…" : "카테고리"}
        align="right"
        disabled={locked}
      />
    ) : (
      <Dropdown
        value={row.categoryEnum ?? null}
        options={CATEGORIES_BY_TYPE[type]}
        onChange={(v) => onChange({ categoryEnum: v, sourceTouched: true })}
        placeholder={type === "income" ? "수입 출처" : "저축 유형"}
        align="right"
        disabled={locked}
      />
    );

  return (
    <div
      className="ledger-entry-row"
      data-entry-type={type}
      data-row-id={row.id}
    >
      <div className="ledger-row-head">
        <span className="ledger-row-num">{String(num).padStart(2, "0")}</span>
        {/* 결제수단은 지출만. 수입은 "어디서 받았나"(출처), 저축은 유형만 고른다. */}
        {type === "expense" && (
          <Dropdown
            value={row.paymentMethod}
            options={PAYMENT_METHODS}
            onChange={(v) => onChange({ paymentMethod: v })}
            placeholder="결제수단"
            disabled={locked}
          />
        )}
        <span className="ledger-row-spacer" />
        {saved && (
          <span className="ledger-chip ledger-chip-readonly ledger-chip-saved">
            {row.isEditing ? "수정 중" : "저장됨"}
          </span>
        )}
        {categoryControl}
        {(type === "expense" || !saved) && (
          <button
            type="button"
            className="ledger-row-del"
            aria-label="삭제"
            onClick={onDelete}
          >
            ×
          </button>
        )}
      </div>
      <div className="ledger-row-field">
        <span className="ledger-row-label">내역</span>
        <input
          type="text"
          className="ledger-row-input"
          data-entry-field="name"
          value={row.name}
          onChange={(e) => onChange({
            name: e.target.value,
            ...(type === "expense" ? { categoryTouched: false } : {}),
          })}
          disabled={locked}
          enterKeyHint="next"
          onKeyDown={(e) => {
            if (e.key !== "Enter" || e.nativeEvent?.isComposing) return;
            e.preventDefault();
            amountInputRef.current?.focus();
          }}
          placeholder={
            type === "income"
              ? "예: 6월 월급, 엄마 용돈"
              : "예: GS25 삼각김밥, 학식, 지하철"
          }
        />
      </div>
      {showAsk && (
        <div className="ledger-ask" role="group" aria-label={ask.question}>
          <span className="ledger-ask-question">{ask.question}</span>
          {row.candidates.map((c) => {
            const selected = row.categoryId === c.leafId;
            return (
              <button
                key={c.leafId}
                type="button"
                className={`ledger-chip${selected ? " ledger-chip-selected" : ""}`}
                aria-pressed={selected}
                onClick={() => onChange({ categoryId: c.leafId, categoryTouched: true })}
              >
                {c.label}
              </button>
            );
          })}
          {ask.hint && <p className="ledger-ask-hint">{ask.hint}</p>}
        </div>
      )}
      <div className="ledger-row-field">
        <span className="ledger-row-label">금액</span>
        <div className="ledger-row-input-wrap">
          <input
            ref={amountInputRef}
            type="text"
            inputMode="numeric"
            className="ledger-row-input"
            data-entry-field="amount"
            value={row.amount ? Number(row.amount).toLocaleString("ko-KR") : ""}
            onChange={(e) =>
              onChange({ amount: e.target.value.replace(/[^\d]/g, "") })
            }
            disabled={locked}
            enterKeyHint="next"
            onKeyDown={(e) => {
              if (e.key !== "Enter" || e.nativeEvent?.isComposing) return;
              e.preventDefault();
              onComplete?.();
            }}
          />
          <span className="ledger-row-unit">원</span>
        </div>
      </div>
    </div>
  );
}

// 클릭 chip + 펼침 메뉴 (결제수단용).
function Dropdown({ value, options, onChange, placeholder, align = "left", disabled = false }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const onDocMouseDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    const onEsc = (e) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDocMouseDown);
    document.addEventListener("keydown", onEsc);
    return () => {
      document.removeEventListener("mousedown", onDocMouseDown);
      document.removeEventListener("keydown", onEsc);
    };
  }, [open]);

  const selected = options.find((o) => o.value === value);
  const label = selected?.label || placeholder;

  return (
    <div className="ledger-dropdown" ref={wrapRef}>
      <button
        type="button"
        className={`ledger-chip ${selected ? "ledger-chip-selected" : ""}`}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        disabled={disabled}
      >
        <span className="ledger-chip-text">{label}</span>
        {!disabled && (
          <span className="ledger-chip-caret" aria-hidden>
            ▾
          </span>
        )}
      </button>
      {open && (
        <ul
          className={`ledger-dropdown-menu ledger-dropdown-menu-${align}`}
          role="listbox"
        >
          {options.length === 0 && (
            <li className="ledger-dropdown-empty">불러오는 중…</li>
          )}
          {options.map((o) => (
            <li key={o.value}>
              <button
                type="button"
                className={`ledger-dropdown-option ${
                  o.value === value ? "ledger-dropdown-option-selected" : ""
                }`}
                onClick={() => {
                  onChange(o.value);
                  setOpen(false);
                }}
                role="option"
                aria-selected={o.value === value}
              >
                {o.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default WalletEntryPage;
