import React, { useState } from "react";

function ReservationPage() {
  const [isPopupOpen, setIsPopupOpen] = useState(false);

  return (
    <section className="reservation-page">
      <div className="reservation-card">
        <img
          className="reservasi-img"
          src="https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=400&q=80"
          alt="Cafe barista"
        />

        <div className="reservasi-form-side">
          <div className="form-title">Formulir Reservasi</div>
          <div className="form-desc">Isi data reservasi kamu dan kami akan segera menghubungi kembali.</div>

          <div className="form-row">
            <input className="f-input" type="text" placeholder="Masukkan nama disini" />
            <input className="f-input" type="tel" placeholder="Nomor telepon" />
          </div>

          <div className="form-row">
            <div className="f-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
              <input type="number" placeholder="Jumlah orang" min="1" />
            </div>
            <div className="f-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                <rect x="3" y="4" width="18" height="18" rx="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              <input type="text" placeholder="DD/MM/YYYY" />
            </div>
            <div className="f-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                <circle cx="12" cy="12" r="10" />
                <polyline points="12 6 12 12 16 14" />
              </svg>
              <input type="text" placeholder="05:00 PM" />
            </div>
          </div>

          <div className="form-row single">
            <textarea className="f-input" placeholder="Catatan" />
          </div>

          <button type="button" className="btn-kirim" onClick={() => setIsPopupOpen(true)}>Kirim</button>
        </div>
      </div>

      <div className={`popup-overlay ${isPopupOpen ? "show" : ""}`} id="popup">
        <div className="popup-box">
          <div className="popup-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="#22c55e" strokeWidth="2.5">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </div>
          <div className="popup-title">Reservasi Berhasil!</div>
          <div className="popup-sub">Terima kasih! Reservasi Anda telah kami terima. Kami akan menghubungi Anda segera.</div>
          <button type="button" className="popup-close" onClick={() => setIsPopupOpen(false)}>Tutup</button>
        </div>
      </div>
    </section>
  );
}

export default ReservationPage;
