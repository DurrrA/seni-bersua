import React, { useRef } from "react";
import logoImage from "./assets/images/logo.png";

const menuItems = [
  {
    name: "Signature Espresso",
    ingredients: "Espresso shot, susu segar, crema lembut",
    price: "Rp 35.000",
    accent: "#f0e6d3",
    emoji: "☕",
  },
  {
    name: "Caramel Latte",
    ingredients: "Espresso, susu whole milk, sirup karamel",
    price: "Rp 42.000",
    accent: "#e8d5c4",
    emoji: "🥛",
  },
  {
    name: "Iced Americano",
    ingredients: "Double espresso, air dingin, es batu",
    price: "Rp 32.000",
    accent: "#d4e8f0",
    emoji: "🧊",
  },
  {
    name: "Vanilla Cappuccino",
    ingredients: "Espresso, foam susu, vanilla bean",
    price: "Rp 45.000",
    accent: "#f5e6d0",
    emoji: "🫧",
  },
  {
    name: "Matcha Sesua",
    ingredients: "Matcha premium, susu oat, madu",
    price: "Rp 48.000",
    accent: "#e8f0e4",
    emoji: "🍵",
  },
  {
    name: "Mocha Velvet",
    ingredients: "Espresso, dark chocolate, susu, whip",
    price: "Rp 50.000",
    accent: "#f0d5e8",
    emoji: "🍫",
  },
  {
    name: "Cold Brew Classic",
    ingredients: "Kopi Arabica, diseduh 12 jam, es",
    price: "Rp 38.000",
    accent: "#f5ecd4",
    emoji: "🧇",
  },
  {
    name: "Blue Sky Latte",
    ingredients: "Butterfly pea, lemon, susu segar",
    price: "Rp 43.000",
    accent: "#dce8f5",
    emoji: "💙",
  },
];

const bundles = [
  {
    badge: "Paling Populer",
    title: "Morning Starter Pack",
    description:
      "Mulai pagi Anda dengan sempurna - Signature Espresso pilihan barista kami, dipadu croissant butter hangat dan sepotong banana bread lembut. Energi terbaik untuk produktivitas maksimal.",
    price: "Rp 85.000",
    originalPrice: "Rp 110.000",
   
    accent: "linear-gradient(135deg, #f5e6d0, #e8d5c4)",
  },
  {
    badge: "Hemat 30%",
    title: "Afternoon Chill Bundle",
    description:
      "Nikmati sore santai bersama sahabat. Dua gelas Iced Americano segar, french fries crispy, dan dessert pudding coklat - sempurna untuk nongkrong atau work-from-cafe.",
    price: "Rp 110.000",
    originalPrice: "Rp 158.000",
  
    accent: "linear-gradient(135deg, #e8f0e4, #d4e8f0)",
  },
];

const stats = [
  { value: "5+", label: "Tahun Berdiri" },
  { value: "40+", label: "Menu Pilihan" },
  { value: "10K+", label: "Pelanggan Setia" },
];

function App() {
  const menuCarouselRef = useRef(null);

  function scrollCarousel(direction) {
    const element = menuCarouselRef.current;
    if (!element) return;
    element.scrollBy({ left: direction * 244, behavior: "smooth" });
  }

  function scrollToSection(sectionId) {
    const section = document.getElementById(sectionId);
    if (section) {
      section.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }

  return (
    <div className="page-shell">
      <nav className="topbar">
        <div className="logo-area">
          <div className="logo-mark">
            <img src={logoImage} alt="Sesua Cafe Logo" className="logo" />
          </div>
        </div>
        <div className="nav-links">
          <button type="button" onClick={() => scrollToSection("beranda")}>Beranda</button>
          <button type="button" onClick={() => scrollToSection("menu")}>Menu</button>
          <button type="button" onClick={() => scrollToSection("tentang")}>Tentang Kami</button>
          <button type="button" onClick={() => scrollToSection("reservasi")}>Reservasi</button>
          <button type="button" onClick={() => scrollToSection("event")}>Event</button>
          <button type="button" onClick={() => scrollToSection("kontak")}>Kontak Kami</button>
        </div>
      </nav>

      <main>
        <section className="hero" id="beranda">
          
          <div className="hero-image-fallback" />
          
        </section>

        <section className="menu-section" id="menu">
          <div className="menu-header">
            <div className="section-label">Menu Pilihan</div>
            <div className="section-title">Eksplorasi Rasa di Setiap Cangkir</div>
            <div className="section-sub">Nikmati keseimbangan rasa sempurna dalam setiap menu pilihan kami.</div>
          </div>

          <div className="menu-carousel-wrap">
            <button type="button" className="carousel-btn prev" onClick={() => scrollCarousel(-1)} aria-label="Scroll menu ke kiri">
              &#8249;
            </button>
            <div className="menu-carousel" ref={menuCarouselRef}>
              {menuItems.map((item) => (
                <article key={item.name} className="menu-card">
                  <div className="menu-card-img">
                    <div className="coffee-placeholder" style={{ background: item.accent }}>{item.emoji}</div>
                  </div>
                  <div className="menu-card-body">
                    <div className="menu-card-name">{item.name}</div>
                    <div className="menu-card-ingredients">{item.ingredients}</div>
                    <div className="menu-card-price">{item.price}</div>
                  </div>
                </article>
              ))}
            </div>
            <button type="button" className="carousel-btn next" onClick={() => scrollCarousel(1)} aria-label="Scroll menu ke kanan">
              &#8250;
            </button>

          </div>

          <div className="menu-footer">
            <button type="button" className="btn-blue">Lihat di Menu →</button>
          </div>
        </section>

        <section className="bundle-section" id="event">
          <div className="bundle-header">
            <div className="section-label">Promo Spesial</div>
            <div className="section-title">Temukan Favoritmu</div>
            <div className="section-sub">Pilih kategori menu kami untuk melihat detail racikan spesial yang kami siapkan khusus untukmu.</div>
          </div>

          {bundles.map((bundle) => (
            <article key={bundle.title} className="bundle-card">
              <div className="bundle-img">
                <div className="bundle-img-placeholder" style={{ background: bundle.accent }}></div>
              </div>
              <div className="bundle-body">
                <span className="bundle-badge">{bundle.badge}</span>
                <div className="bundle-title">{bundle.title}</div>
                <div className="bundle-desc">{bundle.description}</div>
                <div className="bundle-footer">
                  <div>
                    <div className="bundle-price">
                      {bundle.price} <span>{bundle.originalPrice}</span>
                    </div>
                  </div>
                  <button type="button" className="btn-blue" onClick={() => scrollToSection("menu")}>Lihat di Menu →</button>
                </div>
              </div>
            </article>
          ))}
        </section>

        <section className="about-section" id="tentang">
          <div className="about-overlay" />
          <div className="about-content">
            <div className="section-label">Tentang Kami</div>
            <div className="section-title">Lebih dari Sekadar Kopi</div>
            <div className="section-sub">
              Sesua lahir dari kecintaan mendalam terhadap kopi dan keramahan. Kami percaya setiap cangkir adalah pengalaman - bukan sekadar minuman.
            </div>
            <div className="about-stats">
              {stats.map((stat) => (
                <div key={stat.label} className="stat-item">
                  <div className="stat-num">{stat.value}</div>
                  <div className="stat-label">{stat.label}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="cta-banner" id="reservasi">

          <div className="cta-overlay" />
          <div className="cta-content">
            
            <button type="button" className="btn-cta">Buat Reservasi Sekarang</button>
          </div>
        </section>
      </main>

      <footer id="kontak">
        <p>
          &copy; 2025 <span>Sesua Cafe</span>. All rights reserved. Crafted with ☕ and love.
        </p>
      </footer>
    </div>
  );
}

export default App;
