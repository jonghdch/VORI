import { useEffect, useId, useRef, useState } from "react";
import { getActivePet, namePet } from "../api/pet";
import { PET_CHANGED_EVENT, notifyPetChanged } from "../api/user";
import { PetArt } from "./petVisual";
import "./PetNameGate.css";

export const PET_NAME_MAX = 10;

// 출석 팝업(AttendanceGate)이 이 팝업과 겹쳐 뜨지 않게 지금 상태를 알린다.
// checked: 이름 지을 펫이 있는지 확인을 마쳤는지, open: 이름 짓기 팝업이 떠 있는지.
// 출석 쪽이 DOM 에 이 팝업이 있는지 보고 판단하던 방식은, 펫 조회가 출석 조회보다 늦게 끝나면 둘 다 띄웠다.
export const PET_NAME_GATE_EVENT = "vori:pet-name-gate";
let gateState = { checked: false, open: false };
export const getPetNameGateState = () => gateState;
function publishGateState(next) {
  gateState = next;
  window.dispatchEvent(new Event(PET_NAME_GATE_EVENT));
}

/**
 * 이름 없는 펫을 키우고 있으면 이름 짓기 팝업을 띄운다. AppShell 이 모든 화면에 둔다.
 *
 * 이름은 필수라 팝업을 닫는 길이 없다(닫기 버튼·Esc·바깥 클릭 모두 없음) — 이름을 지어야 사라진다.
 * 뜨는 경우: 알을 막 개봉했을 때(상점이 PET_CHANGED 를 쏜다), 가입 때 받은 시작 펫,
 * 이름 기능이 생기기 전부터 키우던 펫.
 */
function PetNameGate() {
  const [pet, setPet] = useState(null); // 이름을 지어야 하는 펫. 없으면 null

  useEffect(() => {
    let alive = true;
    // 조회 실패는 조용히 넘긴다 — 로그인 만료·서버 오류 안내는 각 화면이 이미 한다.
    // 실패해도 확인은 끝난 것으로 알린다. 그래야 출석 팝업이 이 확인을 기다리다 영영 안 뜨는 일이 없다.
    const load = () =>
      getActivePet()
        .then((p) => {
          if (!alive) return;
          const needsName = Boolean(p && !p.name);
          setPet(needsName ? p : null);
          publishGateState({ checked: true, open: needsName });
        })
        .catch(() => alive && publishGateState({ checked: true, open: false }));
    load();
    window.addEventListener(PET_CHANGED_EVENT, load);
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, load);
      // 화면을 옮기면 AppShell 과 함께 새로 그려진다 — 다음 화면은 다시 확인한 뒤에 출석 팝업을 연다
      gateState = { checked: false, open: false };
    };
  }, []);

  if (!pet) return null;
  return (
    <PetNameModal
      pet={pet}
      onNamed={() => {
        setPet(null);
        // 이름 짓기를 기다리던 출석 팝업이 이제 뜬다
        publishGateState({ checked: true, open: false });
        // 홈·마이룸이 새 이름으로 다시 그리게 한다
        notifyPetChanged();
      }}
    />
  );
}

export function PetNameModal({ pet, onNamed }) {
  const titleId = useId();
  const hintId = useId();
  const dialogRef = useRef(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const trimmed = name.trim();
  const tooLong = trimmed.length > PET_NAME_MAX;
  const species = pet.speciesName ?? "펫";

  // 팝업이 떠 있는 동안 뒤 화면이 스크롤되지 않게 한다.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  // Tab 이 팝업 밖(뒤 화면의 메뉴·버튼)으로 나가지 않게 팝업 안에서만 돈다.
  const keepFocusInside = (event) => {
    if (event.key !== "Tab") return;
    const focusable = dialogRef.current?.querySelectorAll("input, button:not(:disabled)");
    if (!focusable || focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    if (!trimmed) {
      setError("펫 이름을 입력해 주세요");
      return;
    }
    if (tooLong) {
      setError(`펫 이름은 ${PET_NAME_MAX}자 이내로 입력해 주세요`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await namePet(pet.id, trimmed);
      onNamed();
    } catch (e) {
      // 서버가 보여줄 문구를 message 로 준다(GlobalExceptionHandler)
      setError(e.message || "이름을 저장하지 못했어요. 다시 시도해 주세요.");
      setBusy(false);
    }
  };

  return (
    <div className="pet-name-backdrop">
      <div
        ref={dialogRef}
        className="pet-name-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={keepFocusInside}
      >
        <span className="pet-name-art">
          <PetArt
            appearanceKey={pet.appearanceKey}
            stage={pet.stage}
            name={species}
            className="pet-name-image"
            emojiClassName="pet-name-emoji"
          />
        </span>
        <h2 id={titleId} className="pet-name-title">
          {species}의 이름을 지어 주세요
        </h2>
        <p className="pet-name-lead">앞으로 이 이름으로 불러요.</p>
        <form className="pet-name-form" onSubmit={submit} noValidate>
          <label className="pet-name-label" htmlFor={`${titleId}-input`}>
            펫 이름
          </label>
          <input
            id={`${titleId}-input`}
            className="pet-name-input"
            type="text"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setError(null);
            }}
            placeholder="예: 보리"
            autoComplete="off"
            autoFocus
            aria-describedby={hintId}
            aria-invalid={error ? true : undefined}
            disabled={busy}
          />
          <p
            id={hintId}
            className={`pet-name-hint ${error || tooLong ? "is-error" : ""}`}
            role={error ? "alert" : undefined}
          >
            <span>{error ?? `${PET_NAME_MAX}자까지 쓸 수 있어요`}</span>
            <span className="pet-name-count">
              {trimmed.length}/{PET_NAME_MAX}
            </span>
          </p>
          <button
            type="submit"
            className="home-btn home-btn-primary home-btn-block"
            disabled={busy}
          >
            {busy ? "저장 중…" : "이름 짓기"}
          </button>
        </form>
      </div>
    </div>
  );
}

export default PetNameGate;
