import AppShell from "../../components/AppShell";
import eggImage from "../../assets/shop/egg.png";
import shopBackgroundImage from "../../assets/shop/shop-background.png";
import "../Home/HomeDashboard.css";
import "./ShopPage.css";

const SHOP_ITEMS = [
  {
    id: "basic-egg-1",
    name: "기본 알",
    type: "랜덤 알",
    price: "2,500 코인",
    description: "어떤 펫이 태어날지 모르는 특별한 알이에요.",
    image: eggImage,
  },
  {
    id: "basic-egg-2",
    name: "기본 알",
    type: "랜덤 알",
    price: "2,500 코인",
    description: "어떤 펫이 태어날지 모르는 특별한 알이에요",
    image: eggImage,
  },
  {
    id: "basic-egg-3",
    name: "기본 알",
    type: "랜덤 알",
    price: "2,500 코인",
    description: "어떤 펫이 태어날지 모르는 특별한 알이에요",
    image: eggImage,
  },
];

function ShopPage({ user, onLogout }) {
  const nickname = user?.nickname || "사용자";

  return (
    <AppShell
      activeTop="shop"
      activeSide="shop"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main shop-main">
        <section
          className="shop-hero"
          style={{ backgroundImage: `url(${shopBackgroundImage})` }}
          aria-label="VORI 상점"
        >
          <div className="shop-hero-panel">
            <p className="shop-eyebrow">VORI SHOP</p>
            <h1>{nickname}님, 어떤 알을 데려갈까요?</h1>
            <p>
              보리가 함께할 새 친구와 아이템을 구매할 수 있는 상점이에요.
            </p>
          </div>

          <div className="shop-coin-badge">보유 코인 12,400</div>

          <div className="shop-display-shelf" aria-label="판매 상품">
            {SHOP_ITEMS.map((item) => (
              <article key={item.id} className="shop-display-item">
                <div className="shop-display-image-wrap">
                  <img
                    src={item.image}
                    alt={item.name}
                    className="shop-display-image"
                  />
                </div>
                <div className="shop-display-info">
                  <h2>{item.name}</h2>
                  <p>{item.description}</p>
                  <strong>{item.price}</strong>
                  <button type="button" className="home-btn home-btn-primary shop-buy-btn">
                    구매하기
                  </button>
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    </AppShell>
  );
}

export default ShopPage;
