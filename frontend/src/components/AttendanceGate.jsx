import { useEffect, useState } from "react";
import { getAttendance } from "../api/attendance";
import AttendanceModal, {
  OPEN_ATTENDANCE_EVENT,
  markShownToday,
  wasShownToday,
} from "./AttendanceModal";

/**
 * 출석 팝업을 언제 띄울지 정한다. AppShell 이 모든 화면에 둔다.
 *
 * - 자동: 하루 한 번, 아직 출석하지 않았을 때. 닫으면 그날은 다시 안 뜬다(localStorage).
 * - 수동: 계정 메뉴의 "출석 확인" 이 OPEN_ATTENDANCE_EVENT 를 쏘면 언제든 다시 연다.
 *
 * 펫 이름 짓기 팝업(PetNameGate)과 겹치지 않게, 그 팝업이 떠 있을 땐 자동으로 열지 않는다.
 */
function AttendanceGate() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let alive = true;

    const openNow = () => alive && setOpen(true);
    window.addEventListener(OPEN_ATTENDANCE_EVENT, openNow);

    if (!wasShownToday()) {
      getAttendance()
        .then((status) => {
          if (!alive || status?.checkedIn) return;
          if (document.querySelector(".pet-name-backdrop")) return; // 이름 짓기가 먼저
          setOpen(true);
        })
        .catch(() => {}); // 조회 실패면 조용히 넘긴다 — 메뉴에서 다시 열 수 있다
    }

    return () => {
      alive = false;
      window.removeEventListener(OPEN_ATTENDANCE_EVENT, openNow);
    };
  }, []);

  if (!open) return null;
  return (
    <AttendanceModal
      onClose={() => {
        markShownToday();
        setOpen(false);
      }}
    />
  );
}

export default AttendanceGate;
