import { useEffect, useState } from "react";
import "./App.css";
import LandingPage from "./pages/Landing/LandingPage";
import LoginPage from "./pages/Login/LoginPage";
import SignupPage from "./pages/Signup/SignupPage";
import StoryPage from "./pages/Story/StoryPage";

// 앱의 진입점.
// page 값에 따라 보여줄 페이지를 바꿉니다.
//   "landing" → 로그인 전 메인 페이지
//   "login"   → 로그인 페이지
//   "signup"  → 회원가입 페이지
//   "story"   → 스토리(서비스 소개) 페이지
// onNavigate(pageName, sectionId?) — 페이지 이동과 동시에 그 페이지 안의
// 특정 섹션으로 스크롤하고 싶을 때 두 번째 인자에 섹션 id 를 넘기세요.
function App() {
  const [page, setPage] = useState("landing");
  const [scrollTo, setScrollTo] = useState(null);

  const navigate = (nextPage, sectionId) => {
    setPage(nextPage);
    setScrollTo(sectionId || null);
  };

  // 페이지가 바뀌면 항상 맨 위로 스크롤하고,
  // 섹션 id 가 지정되어 있다면 그 위치로 부드럽게 이동합니다.
  useEffect(() => {
    if (scrollTo) {
      // 페이지 mount 직후라 약간의 지연을 둡니다.
      const timer = setTimeout(() => {
        const el = document.getElementById(scrollTo);
        if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
        setScrollTo(null);
      }, 60);
      return () => clearTimeout(timer);
    }
    window.scrollTo({ top: 0, behavior: "auto" });
  }, [page, scrollTo]);

  if (page === "login") return <LoginPage onNavigate={navigate} />;
  if (page === "signup") return <SignupPage onNavigate={navigate} />;
  if (page === "story") return <StoryPage onNavigate={navigate} />;
  return <LandingPage onNavigate={navigate} />;
}

export default App;
