// 로그인 화면 — 오류 표시와 로그인 뒤 갈 곳. 경진대회 관람객이 처음 만나는 화면이다.
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { login } from "../../api/auth";
import { getOnboardingStatus } from "../../api/onboarding";
import LoginPage from "./LoginPage";

// react-router-dom 7 은 package.json 의 main(dist/main.js)이 없는 파일이라 CRA 의 Jest 27(exports 를 못 읽음)이
// 모듈을 찾지 못한다. 이 화면은 라우터에서 useNavigate 만 쓰므로 그것만 가짜로 두고 어디로 보내는지 본다.
const mockNavigate = jest.fn();
jest.mock("react-router-dom", () => ({ useNavigate: () => mockNavigate }), { virtual: true });
jest.mock("../../api/auth", () => ({ login: jest.fn() }));
jest.mock("../../api/onboarding", () => ({ getOnboardingStatus: jest.fn() }));
jest.mock("../../components/GoogleSignInButton", () => ({ __esModule: true, default: () => null }));

const submitLogin = () => {
  userEvent.type(screen.getByPlaceholderText("vori@example.com"), "guest@vori.local");
  userEvent.type(screen.getByPlaceholderText("비밀번호를 입력하세요"), "Vori!2026");
  userEvent.click(screen.getByRole("button", { name: "로그인" }));
};

beforeEach(() => jest.clearAllMocks());

test("비밀번호가 틀리면 받은 문구를 보여 주고 로그인 화면에 남는다", async () => {
  login.mockRejectedValue(new Error("이메일 또는 비밀번호가 올바르지 않습니다"));
  const onLogin = jest.fn();
  render(<LoginPage onLogin={onLogin} />);

  submitLogin();

  expect(await screen.findByRole("alert")).toHaveTextContent("이메일 또는 비밀번호가 올바르지 않습니다");
  expect(login).toHaveBeenCalledWith("guest@vori.local", "Vori!2026");
  expect(onLogin).not.toHaveBeenCalled();
  expect(mockNavigate).not.toHaveBeenCalled();
});

test("관리자는 계정 상태를 묻지 않고 홈으로 간다 — 설문 대상이 아니다", async () => {
  login.mockResolvedValue({ id: 1, role: "ADMIN" });
  const onLogin = jest.fn();
  render(<LoginPage onLogin={onLogin} />);

  submitLogin();

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/home"));
  expect(getOnboardingStatus).not.toHaveBeenCalled();
  expect(onLogin).toHaveBeenCalledWith({ id: 1, role: "ADMIN" });
});

test.each([
  [{ profileCompleted: false, tutorialDone: false }, "/signup/profile"],
  [{ profileCompleted: true, tutorialDone: false }, "/onboarding"],
  [{ profileCompleted: true, tutorialDone: true }, "/home"],
])("일반 계정은 계정 상태 %o 이면 %s 로 간다", async (status, path) => {
  login.mockResolvedValue({ id: 2, role: "USER" });
  getOnboardingStatus.mockResolvedValue(status);
  render(<LoginPage onLogin={jest.fn()} />);

  submitLogin();

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith(path));
  expect(mockNavigate).toHaveBeenCalledTimes(1);
});

test("로그인은 됐지만 계정 상태를 못 읽으면 홈으로 보내지 않고, 「다시 시도」는 상태만 다시 묻는다", async () => {
  login.mockResolvedValue({ id: 2, role: "USER" });
  getOnboardingStatus
    .mockRejectedValueOnce(new Error("서버에 연결하지 못했어요."))
    .mockResolvedValueOnce({ profileCompleted: true, tutorialDone: true });
  const onLogin = jest.fn();
  render(<LoginPage onLogin={onLogin} />);

  submitLogin();

  expect(await screen.findByRole("alert")).toHaveTextContent("로그인은 됐지만 계정 상태를 확인하지 못했어요.");
  expect(onLogin).not.toHaveBeenCalled();
  expect(mockNavigate).not.toHaveBeenCalled();

  userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/home"));
  expect(login).toHaveBeenCalledTimes(1);
  expect(onLogin).toHaveBeenCalledTimes(1);
});
