// 알 등급 → 이미지. 상점 진열대·보유 알·구매 내역이 같은 판정을 쓴다.
// EggResponse 에는 등급 코드가 없고 등급 이름(gradeName)만 오므로 이름으로도 판별한다.
import eggBasicImage from "../assets/shop/egg-basic.png";
import eggPremiumImage from "../assets/shop/egg-premium.png";
import eggSupremeImage from "../assets/shop/egg-supreme.png";

export const EGG_IMAGE = {
  BASIC: eggBasicImage,
  PREMIUM: eggPremiumImage,
  LEGENDARY: eggSupremeImage,
};

/** 등급 코드가 있으면 그걸로, 없으면 등급 이름("최고급 알"·"고급 알")으로, 그래도 모르면 기본 알. */
export const eggImageFor = (grade, gradeName) => {
  if (EGG_IMAGE[grade]) return EGG_IMAGE[grade];
  const name = gradeName || "";
  if (name.includes("최고급")) return eggSupremeImage;
  if (name.includes("고급")) return eggPremiumImage;
  return eggBasicImage;
};
