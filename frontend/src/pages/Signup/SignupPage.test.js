// 가입 화면 — 입력 검사·약관 동의·서버 오류 표시·가입 뒤 갈 곳.
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { login, signup } from "../../api/auth";
import SignupPage from "./SignupPage";

// react-router-dom 7 은 CRA 의 Jest 27 이 모듈을 찾지 못한다(LoginPage.test.js 참조). useNavigate 만 가짜로 둔다.
const mockNavigate = jest.fn();
jest.mock("react-router-dom", () => ({ useNavigate: () => mockNavigate }), { virtual: true });
jest.mock("../../api/auth", () => ({ signup: jest.fn(), login: jest.fn() }));

const fillForm = ({ password = "Vori!2026", confirm = password } = {}) => {
  userEvent.type(screen.getByPlaceholderText("다른 사용자에게 보일 별명 (2~12자)"), "게스트");
  userEvent.type(screen.getByPlaceholderText("실명 (한글 또는 영문, 30자 이내)"), "김게스트");
  userEvent.type(screen.getByPlaceholderText("vori@example.com"), "guest@vori.local");
  userEvent.type(screen.getByPlaceholderText("8자 이상 입력하세요"), password);
  userEvent.type(screen.getByPlaceholderText("비밀번호를 한 번 더 입력하세요"), confirm);
};

const agreeRequired = () => {
  userEvent.click(screen.getByRole("checkbox", { name: /이용약관 동의/ }));
  userEvent.click(screen.getByRole("checkbox", { name: /개인정보 수집·이용 동의/ }));
};

const submitButton = () => screen.getByRole("button", { name: "회원가입" });

beforeEach(() => jest.clearAllMocks());

test("필수 약관 두 개에 동의해야 가입 버튼이 켜진다 — 마케팅 동의는 선택", () => {
  render(<SignupPage onLogin={jest.fn()} />);
  fillForm();
  expect(submitButton()).toBeDisabled();

  userEvent.click(screen.getByRole("checkbox", { name: /이용약관 동의/ }));
  expect(submitButton()).toBeDisabled();

  userEvent.click(screen.getByRole("checkbox", { name: /개인정보 수집·이용 동의/ }));
  expect(submitButton()).toBeEnabled();
});

test("비밀번호 확인이 다르면 알려 주고 가입 버튼을 끈다", () => {
  render(<SignupPage onLogin={jest.fn()} />);
  fillForm({ confirm: "Vori!2027" });
  agreeRequired();

  expect(screen.getByText("비밀번호가 일치하지 않아요.")).toBeInTheDocument();
  expect(submitButton()).toBeDisabled();
});

test("서버가 거절하면 서버 문구를 보여 주고 로그인은 시도하지 않는다", async () => {
  signup.mockRejectedValue(new Error("이미 사용 중인 이메일입니다"));
  render(<SignupPage onLogin={jest.fn()} />);
  fillForm();
  agreeRequired();

  userEvent.click(submitButton());

  expect(await screen.findByRole("alert")).toHaveTextContent("이미 사용 중인 이메일입니다");
  expect(signup).toHaveBeenCalledWith(
    expect.objectContaining({
      email: "guest@vori.local",
      termsAgreed: true,
      privacyAgreed: true,
      marketingAgreed: false,
    }),
  );
  expect(login).not.toHaveBeenCalled();
  expect(mockNavigate).not.toHaveBeenCalled();
});

test("가입하면 바로 로그인해 설문 화면으로 간다", async () => {
  signup.mockResolvedValue({ id: 3 });
  login.mockResolvedValue({ id: 3, role: "USER" });
  const onLogin = jest.fn();
  render(<SignupPage onLogin={onLogin} />);
  fillForm();
  agreeRequired();

  userEvent.click(submitButton());

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/signup/profile"));
  expect(login).toHaveBeenCalledWith("guest@vori.local", "Vori!2026");
  expect(onLogin).toHaveBeenCalledWith({ id: 3, role: "USER" });
});

test("가입은 됐는데 자동 로그인이 실패하면 다시 가입하지 않게 로그인 화면으로 보낸다", async () => {
  signup.mockResolvedValue({ id: 3 });
  login.mockRejectedValue(new Error("서버에 연결하지 못했어요."));
  const alertSpy = jest.spyOn(window, "alert").mockImplementation(() => {});
  render(<SignupPage onLogin={jest.fn()} />);
  fillForm();
  agreeRequired();

  userEvent.click(submitButton());

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/login"));
  expect(alertSpy).toHaveBeenCalledWith("가입은 완료됐어요. 로그인 페이지에서 다시 로그인해 주세요.");
  alertSpy.mockRestore();
});
