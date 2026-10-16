// 첫 화면 팝업 순서 — 펫 이름 짓기(PetNameGate)가 먼저, 출석(AttendanceGate)은 그다음.
// 가입 직후 첫 화면에서 두 팝업이 겹쳐 떴고, 시연 때는 첫 클릭을 팝업이 가로챘다(#122 에서 고침).
// 펫 조회가 출석 조회보다 늦게 끝나도 순서가 지켜지는지가 핵심이다.
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { getActivePet, namePet } from "../api/pet";
import { getAttendance } from "../api/attendance";
import AttendanceGate from "./AttendanceGate";
import PetNameGate from "./PetNameGate";

jest.mock("../api/pet", () => ({ getActivePet: jest.fn(), namePet: jest.fn() }));
jest.mock("../api/attendance", () => ({
  getAttendance: jest.fn(),
  checkInAttendance: jest.fn(),
  getAttendanceMonth: jest.fn(),
}));
jest.mock("../api/user", () => ({ PET_CHANGED_EVENT: "vori:pet-changed", notifyPetChanged: jest.fn() }));
jest.mock("./petVisual", () => ({ PetArt: () => null }));
// 출석 팝업 내용(달력·보상)은 이 테스트의 관심이 아니라 자리만 둔다. 「오늘 이미 띄웠나」 기록 함수는 진짜를 쓴다.
jest.mock("./AttendanceModal", () => {
  const React = require("react");
  return {
    ...jest.requireActual("./AttendanceModal"),
    __esModule: true,
    default: () => React.createElement("div", { "data-testid": "attendance-modal" }, "출석 팝업"),
  };
});

const UNNAMED_PET = { id: 7, name: null, speciesName: "양" };
const NAMED_PET = { id: 7, name: "양이", speciesName: "양" };

// 바깥에서 끝낼 수 있는 Promise — 조회가 끝나는 순서를 테스트가 정한다.
function deferred() {
  let resolve;
  const promise = new Promise((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const renderGates = () =>
  render(
    <>
      <PetNameGate />
      <AttendanceGate />
    </>,
  );

beforeEach(() => {
  localStorage.clear();
  jest.clearAllMocks();
});

test("이름 없는 펫이 있으면 이름 짓기 팝업만 뜨고, 이름을 지은 뒤에 출석 팝업이 뜬다", async () => {
  getActivePet.mockResolvedValue(UNNAMED_PET);
  getAttendance.mockResolvedValue({ checkedIn: false });
  namePet.mockResolvedValue({});

  renderGates();

  expect(await screen.findByText("양의 이름을 지어 주세요")).toBeInTheDocument();
  expect(screen.queryByTestId("attendance-modal")).not.toBeInTheDocument();

  userEvent.type(screen.getByPlaceholderText("예: 보리"), "양이");
  userEvent.click(screen.getByRole("button", { name: "이름 짓기" }));

  expect(await screen.findByTestId("attendance-modal")).toBeInTheDocument();
  expect(namePet).toHaveBeenCalledWith(7, "양이");
  expect(screen.queryByText("양의 이름을 지어 주세요")).not.toBeInTheDocument();
});

test("출석 조회가 먼저 끝나도 펫 확인이 끝날 때까지 출석 팝업을 띄우지 않는다", async () => {
  const pet = deferred();
  getActivePet.mockReturnValue(pet.promise);
  getAttendance.mockResolvedValue({ checkedIn: false });

  renderGates();
  await waitFor(() => expect(getAttendance).toHaveBeenCalled());
  await act(async () => {}); // 출석 조회 응답을 처리하게 한 번 돌린다

  expect(screen.queryByTestId("attendance-modal")).not.toBeInTheDocument();

  await act(async () => pet.resolve(UNNAMED_PET));

  expect(await screen.findByText("양의 이름을 지어 주세요")).toBeInTheDocument();
  expect(screen.queryByTestId("attendance-modal")).not.toBeInTheDocument();
});

test("펫 이름이 이미 있으면 펫 확인이 끝나는 대로 출석 팝업이 뜬다", async () => {
  getActivePet.mockResolvedValue(NAMED_PET);
  getAttendance.mockResolvedValue({ checkedIn: false });

  renderGates();

  expect(await screen.findByTestId("attendance-modal")).toBeInTheDocument();
  expect(screen.queryByText(/이름을 지어 주세요/)).not.toBeInTheDocument();
});

test("펫 조회가 실패해도 출석 팝업은 뜬다 — 확인을 기다리다 영영 안 뜨지 않게", async () => {
  getActivePet.mockRejectedValue(new Error("서버에 연결하지 못했어요."));
  getAttendance.mockResolvedValue({ checkedIn: false });

  renderGates();

  expect(await screen.findByTestId("attendance-modal")).toBeInTheDocument();
});

test("오늘 이미 출석했으면 출석 팝업을 띄우지 않는다", async () => {
  getActivePet.mockResolvedValue(NAMED_PET);
  getAttendance.mockResolvedValue({ checkedIn: true });

  renderGates();
  await waitFor(() => expect(getAttendance).toHaveBeenCalled());
  await act(async () => {});

  expect(screen.queryByTestId("attendance-modal")).not.toBeInTheDocument();
});
