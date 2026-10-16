import { useEffect, useState } from "react";
import { getAttendance } from "../api/attendance";
import AttendanceModal, {
  OPEN_ATTENDANCE_EVENT,
  markShownToday,
  wasShownToday,
} from "./AttendanceModal";
import { PET_NAME_GATE_EVENT, getPetNameGateState } from "./PetNameGate";

/**
 * 출석 팝업을 언제 띄울지 정한다. AppShell 이 모든 화면에 둔다.
 *
 * - 자동: 하루 한 번, 아직 출석하지 않았을 때. 닫으면 그날은 다시 안 뜬다(localStorage, 로그인·로그아웃 때 지움).
 * - 수동: 계정 메뉴의 "출석 확인" 이 OPEN_ATTENDANCE_EVENT 를 쏘면 언제든 다시 연다.
 *
 * 펫 이름 짓기 팝업(PetNameGate)이 먼저다. 이름 지을 펫이 있는지 확인이 끝날 때까지 기다리고,
 * 이름 짓기 팝업이 떠 있으면 이름을 지은 뒤에 연다. 가입 직후 첫 화면에서 두 팝업이 겹치던 문제.
 */
function AttendanceGate() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let alive = true;
    let waiting = false; // 출석 전이라 자동으로 열어야 하는데 이름 짓기를 기다리는 중

    const openNow = () => alive && setOpen(true);
    window.addEventListener(OPEN_ATTENDANCE_EVENT, openNow);

    const openWhenPetNameDone = () => {
      if (!alive || !waiting) return;
      const petName = getPetNameGateState();
      if (!petName.checked || petName.open) return; // 확인이 끝나거나 이름을 지으면 이벤트로 다시 온다
      waiting = false;
      setOpen(true);
    };
    window.addEventListener(PET_NAME_GATE_EVENT, openWhenPetNameDone);

    if (!wasShownToday()) {
      getAttendance()
        .then((status) => {
          if (!alive || status?.checkedIn) return;
          waiting = true;
          openWhenPetNameDone();
        })
        .catch(() => {}); // 조회 실패면 조용히 넘긴다 — 메뉴에서 다시 열 수 있다
    }

    return () => {
      alive = false;
      window.removeEventListener(OPEN_ATTENDANCE_EVENT, openNow);
      window.removeEventListener(PET_NAME_GATE_EVENT, openWhenPetNameDone);
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
