import Icon from "../../components/Icon";
import SignalChip from "../../components/SignalChip";
import { issuerOf } from "../../constants/issuers";

// 목록 탭에서 고른 내역 한 건의 상세 — 폰은 아래 시트, 넓은 화면은 오른쪽 패널 안에 들어간다.
// 모양은 날짜 탭 하루 카드의 펼친 항목(.lw-day-list > li.is-open)과 같은 클래스를 쓴다.
// 하루 카드와 다른 점: 결제수단에 등록 카드 이름·색 점, 수입 카테고리는 한글 라벨.
function ListTxDetail({ row, iconName, categoryLabel, instrument, onOpenDate, onEdit }) {
  const isIncome = row.type === "INCOME";
  return (
    <>
      <div className="lw-tx-detail">
        <div className="lw-day-row lw-tx-detail-head">
          <span className="hm-tx-icon">
            <Icon name={iconName} size={18} />
          </span>
          <span className="hm-tx-mid">
            <span className="hm-tx-name">{row.name}</span>
            <span className="hm-tx-cat">{categoryLabel}</span>
          </span>
          <span className="hm-tx-right">
            <span className={`hm-tx-amount ${isIncome ? "lw-amt-in" : ""}`}>
              {isIncome ? "+" : "-"}
              {row.amount}
            </span>
            {row.aiStatus && <SignalChip signal={row.signal} />}
          </span>
        </div>
        <div className="lw-detail">
          <dl className="lw-detail-grid">
            <div>
              <dt>구분</dt>
              <dd>{isIncome ? "수입" : "지출"}</dd>
            </div>
            <div>
              <dt>{isIncome ? "받은 곳" : "결제수단"}</dt>
              <dd>
                {instrument ? (
                  <span className="lw-list-pay lw-list-pay--plain">
                    <span className="lw-list-dot" style={{ background: issuerOf(instrument.issuer).color }} aria-hidden />
                    {instrument.name}
                  </span>
                ) : (
                  row.payment || "—"
                )}
              </dd>
            </div>
            <div>
              <dt>날짜</dt>
              <dd>{row.date}</dd>
            </div>
            <div>
              <dt>카테고리</dt>
              <dd>{categoryLabel}</dd>
            </div>
          </dl>
          {row.memo && (
            <p className="lw-detail-note">
              <span>메모</span>
              {row.memo}
            </p>
          )}
          {row.reason && (
            <p className="lw-detail-note">
              <span>소비 사유</span>
              {row.reason}
            </p>
          )}
          {row.aiStatus && (
            <p className="lw-detail-ai">
              AI 판정 <SignalChip signal={row.signal} />
            </p>
          )}
        </div>
      </div>
      <div className={`lw-tx-detail-actions ${isIncome ? "is-single" : ""}`}>
        <button type="button" className="hm-btn lw-btn-soft" onClick={onOpenDate}>
          <Icon name="calendar" size={16} />
          날짜에서 보기
        </button>
        {!isIncome && (
          <button type="button" className="hm-btn hm-btn--dark" onClick={onEdit}>
            <Icon name="pencil" size={16} strokeWidth={2.2} />이 날 지출 수정
          </button>
        )}
      </div>
    </>
  );
}

export default ListTxDetail;
