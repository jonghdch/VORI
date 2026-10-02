import { useEffect, useId, useRef, useState } from "react";
import { chatWithPet, getPetChatStatus } from "../../api/pet";
import { petTmiLines } from "../../components/petLines";

const MESSAGE_MAX = 200;
// 서버가 받는 지난 대화 최대 턴 수(PetChatRequest.history @Size)
const HISTORY_MAX = 12;
const storageKey = (petId) => `vori:pet-chat:${petId}`;

// 대화는 서버에 저장하지 않는다. 같은 탭에서 다른 메뉴를 봤다 돌아와도 이어지도록 sessionStorage 에만 둔다.
function loadMessages(petId) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey(petId)));
    return Array.isArray(saved) ? saved : null;
  } catch {
    return null;
  }
}

function saveMessages(petId, messages) {
  try {
    sessionStorage.setItem(storageKey(petId), JSON.stringify(messages));
  } catch {}
}

// 첫 대사 — 홈 말풍선과 같은 펫 TMI(종·단계별) 중 무작위 한 줄. 서버 호출이 아니라 대화 횟수를 쓰지 않는다.
function greeting(pet) {
  const lines = petTmiLines(pet);
  return { role: "pet", text: lines[Math.floor(Math.random() * lines.length)] };
}

/**
 * 마이룸 "대화방" 탭 — 키우는 펫과 자유 대화.
 * 성격은 서버가 종·스탯 비율·단계로 정하고(PetPersonality), 하루 대화 횟수가 있어 남은 횟수를 보여 준다.
 */
export default function PetChatPanel({ pet }) {
  const inputRef = useRef(null);
  const listRef = useRef(null);
  const [messages, setMessages] = useState(() => loadMessages(pet.id) ?? [greeting(pet)]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(null); // { personality, remaining, dailyLimit }
  // 제목 옆 (i) 안내 팝업 — 바깥을 누르거나 Esc 로 닫는다
  const infoId = useId();
  const infoRef = useRef(null);
  const [infoOpen, setInfoOpen] = useState(false);

  useEffect(() => {
    if (!infoOpen) return undefined;
    const onPointer = (e) => {
      if (!infoRef.current?.contains(e.target)) setInfoOpen(false);
    };
    const onKey = (e) => e.key === "Escape" && setInfoOpen(false);
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [infoOpen]);

  useEffect(() => {
    let alive = true;
    getPetChatStatus()
      .then((s) => alive && setStatus(s))
      .catch((e) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => saveMessages(pet.id, messages), [pet.id, messages]);

  // 새 말이 붙으면 맨 아래로
  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, busy]);

  const outOfTurns = status != null && status.remaining <= 0;
  const text = draft.trim();
  const canSend = !busy && !outOfTurns && text.length > 0 && text.length <= MESSAGE_MAX;

  const send = async (event) => {
    event.preventDefault();
    if (!canSend) return;
    const history = messages.slice(-HISTORY_MAX).map(({ role, text: t }) => ({ role, text: t }));
    setMessages((prev) => [...prev, { role: "user", text }]);
    setDraft("");
    setBusy(true);
    setError(null);
    try {
      const res = await chatWithPet(text, history);
      setMessages((prev) => [...prev, { role: "pet", text: res.reply }]);
      setStatus(res);
    } catch (e) {
      setError(e.message);
      // 횟수 소진(429)이면 남은 횟수를 0 으로 맞춰 입력을 막는다
      if (e.status === 429) setStatus((s) => (s ? { ...s, remaining: 0 } : s));
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  };

  return (
    <div className="pet-tab-panel pet-chat-panel">
      <div className="pet-panel-head">
        <div className="pet-chat-title" ref={infoRef}>
          <h2 className="home-card-title home-card-title--sm">대화방</h2>
          <button
            type="button"
            className="pet-chat-info-btn"
            aria-label="대화방 안내"
            aria-expanded={infoOpen}
            aria-controls={infoId}
            onClick={() => setInfoOpen((v) => !v)}
          >
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden>
              <circle cx="8" cy="8" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
              <path d="M8 7.2v4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
              <circle cx="8" cy="4.9" r="0.9" fill="currentColor" />
            </svg>
          </button>
          {infoOpen && (
            <div id={infoId} className="pet-chat-info" role="note">
              {/* 범위는 서버 PetLedgerSummary 가 넘기는 것과 같아야 한다 */}
              <p>대화 내용은 저장되지 않아요.</p>
              <p>
                오늘 지출 합계와 건수, 이번 달 카테고리별 합계, 최근 지출 몇 건의 항목명과 판정 신호,
                이번 달 판정 분포를 기반으로 답해요!
              </p>
              <p>
                이외의 메모, 사유 답변 원문, 결제수단, 가맹점 상세 같은 사적인 내용은 넣지 않아요.
              </p>
            </div>
          )}
        </div>
        {status && (
          <span className="pet-chat-remaining">
            오늘 {status.remaining} / {status.dailyLimit}번
          </span>
        )}
      </div>
      <p className="pet-chat-personality">
        {pet.speciesName} {pet.name}
        {status?.personality ? ` · ${status.personality}` : ""}
      </p>

      <ol className="pet-chat-list" ref={listRef} aria-live="polite" aria-label={`${pet.name}와의 대화`}>
        {messages.map((m, i) => (
          <li key={i} className={`pet-chat-msg pet-chat-msg--${m.role}`}>
            {m.text}
          </li>
        ))}
        {busy && (
          <li className="pet-chat-msg pet-chat-msg--pet pet-chat-typing" aria-label={`${pet.name}가 답하는 중`}>
            <span />
            <span />
            <span />
          </li>
        )}
      </ol>

      {error && (
        <p className="pet-chat-error" role="alert">
          {error}
        </p>
      )}

      <form className="pet-chat-form" onSubmit={send}>
        <input
          ref={inputRef}
          className="pet-chat-input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={MESSAGE_MAX}
          placeholder={outOfTurns ? "오늘은 대화를 다 했어요" : `${pet.name}에게 말 걸기`}
          disabled={outOfTurns}
          aria-label="메시지"
        />
        <button type="submit" className="pet-chat-send" disabled={!canSend}>
          보내기
        </button>
      </form>
    </div>
  );
}
