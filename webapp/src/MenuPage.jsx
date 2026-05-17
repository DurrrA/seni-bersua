import React, { useState } from "react";
import rendaMenuImage from "./assets/images/renda_menu.png";

const filters = ["Semua", "Coffee", "Milk Based", "Fresh"];
const categories = filters.filter((filter) => filter !== "Semua");

const menuItems = [
  {
    name: "Signature Espresso",
    category: "Coffee",
    ingredients: "Espresso shot, susu segar, crema lembut",
    price: "Rp 35.000",
    accent: "#f0e6d3",
    emoji: "☕",
  },
  {
    name: "Caramel Latte",
    category: "Milk Based",
    ingredients: "Espresso, susu whole milk, sirup karamel",
    price: "Rp 42.000",
    accent: "#e8d5c4",
    emoji: "🥛",
  },
  {
    name: "Iced Americano",
    category: "Coffee",
    ingredients: "Double espresso, air dingin, es batu",
    price: "Rp 32.000",
    accent: "#d4e8f0",
    emoji: "🧊",
  },
  {
    name: "Vanilla Cappuccino",
    category: "Milk Based",
    ingredients: "Espresso, foam susu, vanilla bean",
    price: "Rp 45.000",
    accent: "#f5e6d0",
    emoji: "🥐",
  },
  {
    name: "Matcha Sesua",
    category: "Milk Based",
    ingredients: "Matcha premium, susu oat, madu",
    price: "Rp 48.000",
    accent: "#e8f0e4",
    emoji: "🍵",
  },
  {
    name: "Mocha Velvet",
    category: "Milk Based",
    ingredients: "Espresso, dark chocolate, susu, whip",
    price: "Rp 50.000",
    accent: "#f0d5e8",
    emoji: "🍫",
  },
  {
    name: "Cold Brew Classic",
    category: "Coffee",
    ingredients: "Kopi Arabica, diseduh 12 jam, es",
    price: "Rp 38.000",
    accent: "#f5ecd4",
    emoji: "🥤",
  },
  {
    name: "Blue Sky Latte",
    category: "Fresh",
    ingredients: "Butterfly pea, lemon, susu segar",
    price: "Rp 43.000",
    accent: "#dce8f5",
    emoji: "💙",
  },
];

function MenuPage() {
  const [activeFilter, setActiveFilter] = useState("Semua");
  const visibleCategories = activeFilter === "Semua" ? categories : [activeFilter];

  return (
    <section className="menu-page">
    
      <div className="menu-filter-bar" aria-label="Filter menu">
        {filters.map((filter) => (
          <button
            key={filter}
            type="button"
            className={`menu-filter ${activeFilter === filter ? "is-active" : ""}`}
            onClick={() => setActiveFilter(filter)}
          >
            {filter}
          </button>
        ))}
      </div>

      <div className="menu-category-list">
        {visibleCategories.map((category) => {
          const categoryItems = menuItems.filter((item) => item.category === category);

          return (
            <div key={category} className="menu-grid-container">
              <div className="menu-grid-image">
                <div className="menu-grid-photo"></div>
                <div className="menu-grid-label">{category}</div>
              </div>

              <div className="menu-page-grid">
                {categoryItems.map((item) => (
                  <article key={item.name} className="menu-page-card">
                    <div className="menu-page-card-img">
                      <div className="coffee-placeholder" style={{ background: item.accent }}>{item.emoji}</div>
                    </div>
                    <div className="menu-page-card-body">
                      <div className="menu-card-name">{item.name}</div>
                      <div className="menu-card-ingredients">{item.ingredients}</div>
                      <div className="menu-card-price">{item.price}</div>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

export default MenuPage;
