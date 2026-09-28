import { useEffect, useState } from "react";
import {
  adminClearMyPet,
  adminSetMyPetAppearance,
  adminSetMyPetStage,
  listPetSpecies,
} from "../api/admin";
import { notifyPetChanged } from "../api/user";
import { STAGE_LABEL, TIER_LABEL, VARIANT_LABEL } from "./petVisual";

const STAGES = ["INFANT", "JUVENILE", "ADULT"];
const VARIANTS = ["NORMAL", "IRO", "ALIEN"];

/**
 * 관리자 도구 — 사용자 화면(홈·상점·마이룸·도감) 오른쪽 아래에 떠 있는 패널.
 * 관리자 본인 계정의 펫 종족·변종·단계를 즉시 바꿔, 시연·QA 에서 모든 조합을
 * 실제 화면으로 확인한다. 일반 계정에는 렌더되지 않는다(AppShell 이 역할을 보고 붙인다).
 *
 * 값을 바꾼 뒤에는 PET_CHANGED 이벤트를 쏘아 열려 있는 화면이 다시 읽게 한다.
 */
function AdminTools() {
  const [open, setOpen] = useState(false);
  const [species, setSpecies] = useState([]);
  const [speciesId, setSpeciesId] = useState("");
  const [variant, setVariant] = useState("NORMAL");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null); // { kind: "ok"|"err", text }

  useEffect(() => {
    if (!open || species.length > 0) return;
    listPetSpecies()
      .then((list) => {
        setSpecies(list);
        if (list.length > 0) setSpeciesId(String(list[0].id));
      })
      .catch((e) => setMsg({ kind: "err", text: e.message }));
  }, [open, species.length]);

  const run = async (label, fn, { pet = false } = {}) => {
    setBusy(true);
    setMsg(null);
    try {
      await fn();
      if (pet) notifyPetChanged();
      setMsg({ kind: "ok", text: `${label} 완료` });
    } catch (e) {
      setMsg({ kind: "err", text: e.message || `${label} 실패` });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={`admin-tools ${open ? "is-open" : ""}`}>
      <button
        type="button"
        className="admin-tools-toggle"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls="admin-tools-panel"
        title="관리자 도구 — 펫·코인을 바꿔 화면을 확인"
      >
        🛠 관리자 도구
      </button>

      {open && (
        <div id="admin-tools-panel" className="admin-tools-panel" role="region" aria-label="관리자 도구">
          <p className="admin-tools-hint">
            내 계정에만 적용돼요. 바꾸면 홈·마이룸·도감이 바로 갱신됩니다.
          </p>

          <section className="admin-tools-section">
            <h3>펫 종족 · 변종</h3>
            <div className="admin-tools-row">
              <select
                value={speciesId}
                onChange={(e) => setSpeciesId(e.target.value)}
                disabled={busy || species.length === 0}
                aria-label="종족"
              >
                {species.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} · {TIER_LABEL[s.tier] ?? s.tier}
                  </option>
                ))}
              </select>
              <select
                value={variant}
                onChange={(e) => setVariant(e.target.value)}
                disabled={busy}
                aria-label="변종"
              >
                {VARIANTS.map((v) => (
                  <option key={v} value={v}>
                    {VARIANT_LABEL[v] ?? "일반"}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="home-btn home-btn-primary"
                disabled={busy || !speciesId}
                onClick={() =>
                  run("종족 변경", () => adminSetMyPetAppearance(Number(speciesId), variant), { pet: true })
                }
              >
                적용
              </button>
            </div>
            <small>활성 펫이 없으면 그 종족의 아기 펫이 새로 생겨요.</small>
          </section>

          <section className="admin-tools-section">
            <h3>성장 단계</h3>
            <div className="admin-tools-row">
              {STAGES.map((st, i) => (
                <button
                  key={st}
                  type="button"
                  className="home-btn"
                  disabled={busy}
                  onClick={() => run(`${i + 1}차(${STAGE_LABEL[st]})`, () => adminSetMyPetStage(st), { pet: true })}
                >
                  {i + 1}차 · {STAGE_LABEL[st]}
                </button>
              ))}
            </div>
            <small>스탯은 그 단계 최소값으로 맞춰져요. 3차로 두면 분양 버튼이 열립니다.</small>
          </section>

          <section className="admin-tools-section">
            <h3>펫 비우기</h3>
            <div className="admin-tools-row">
              <button
                type="button"
                className="home-btn"
                disabled={busy}
                onClick={() => {
                  if (!window.confirm("활성 펫을 비울까요? (보상 0 으로 분양 처리)")) return;
                  run("펫 비우기", () => adminClearMyPet(), { pet: true });
                }}
              >
                활성 펫 비우기
              </button>
            </div>
            <small>알 개봉 흐름을 다시 보려면 펫이 없어야 해요.</small>
          </section>

          {msg && (
            <p className={`admin-tools-msg ${msg.kind === "err" ? "is-err" : ""}`} role="status">
              {msg.text}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export default AdminTools;
