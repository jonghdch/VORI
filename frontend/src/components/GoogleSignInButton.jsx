import { useEffect, useRef, useState } from "react";
import { loginWithGoogle } from "../api/auth";

const CLIENT_ID = process.env.REACT_APP_GOOGLE_CLIENT_ID;
const GSI_SRC = "https://accounts.google.com/gsi/client";

// Google Identity Services 스크립트를 한 번만 로드한다. 여러 화면이 동시에 불러도 태그는 하나.
function loadGsi() {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!loadGsi.promise) {
    loadGsi.promise = new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = GSI_SRC;
      s.async = true;
      s.defer = true;
      s.onload = resolve;
      s.onerror = () => reject(new Error("구글 로그인 스크립트를 불러오지 못했어요"));
      document.head.appendChild(s);
    });
  }
  return loadGsi.promise;
}

/**
 * 구글 로그인 버튼 — 구글이 그려 주는 공식 버튼을 자리에 렌더한다.
 * 버튼을 누르면 구글이 credential(ID 토큰)을 주고, 그걸 백엔드 POST /api/auth/google 로 보내
 * 이메일 로그인과 같은 세션을 만든다.
 *
 * REACT_APP_GOOGLE_CLIENT_ID 가 없으면 아무것도 그리지 않는다 — 동작 없는 버튼을 노출하지 않는다.
 *
 * @param {(user:object)=>void} onLogin  세션 수립 후 사용자 객체 전달
 * @param {(message:string)=>void} [onError]
 */
function GoogleSignInButton({ onLogin, onError }) {
  const slotRef = useRef(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!CLIENT_ID || !slotRef.current) return undefined;
    let alive = true;

    loadGsi()
      .then(() => {
        if (!alive || !slotRef.current) return;
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: async ({ credential }) => {
            setBusy(true);
            try {
              const user = await loginWithGoogle(credential);
              onLogin?.(user);
            } catch (e) {
              onError?.(e.message || "구글 로그인에 실패했어요");
            } finally {
              if (alive) setBusy(false);
            }
          },
          // 브라우저 자동 로그인 팝업(One Tap)은 쓰지 않는다 — 버튼을 눌렀을 때만.
          auto_select: false,
          cancel_on_tap_outside: true,
        });
        window.google.accounts.id.renderButton(slotRef.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: "continue_with",
          shape: "rectangular",
          logo_alignment: "left",
          width: slotRef.current.clientWidth || 320,
          locale: "ko",
        });
      })
      .catch((e) => onError?.(e.message));

    return () => {
      alive = false;
    };
    // onLogin/onError 는 부모가 매 렌더 새로 만드는 함수라 의존성에 넣으면 버튼이 계속 다시 그려진다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!CLIENT_ID) return null;

  return (
    <div className="login-google" aria-busy={busy}>
      <div ref={slotRef} className="login-google-slot" />
      {busy && <p className="login-google-status">구글 계정으로 로그인하는 중…</p>}
      <p className="login-google-consent">
        구글로 계속하면 <a href="/terms" target="_blank" rel="noopener noreferrer">이용약관</a>과{" "}
        <a href="/privacy" target="_blank" rel="noopener noreferrer">개인정보처리방침</a>에 동의한 것으로 봐요.
      </p>
    </div>
  );
}

export default GoogleSignInButton;
