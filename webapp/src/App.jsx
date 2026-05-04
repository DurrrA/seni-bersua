import React, { useEffect, useMemo, useState } from "react";

const path = window.location.pathname;
const search = new URLSearchParams(window.location.search);
const tableMatch = path.match(/^\/t\/([^/]+)$/);
const isTablePage = Boolean(tableMatch);
const isDashboardPage = path === "/dashboard";
const tableUuid = tableMatch ? tableMatch[1] : "";
const initialOutletId = search.get("outlet") || "default";
const MIN_RECOMMENDATION_TRANSACTIONS = 3;
const MIN_TOP_QTY = 5;
const MAX_LOW_QTY = 2;
const idNumberFormatter = new Intl.NumberFormat("id-ID", {
  maximumFractionDigits: 0,
});

function normalizeWholeNumber(value) {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? Math.round(numeric) : 0;
}

function formatNumber(value) {
  return idNumberFormatter.format(normalizeWholeNumber(value));
}

function formatRp(value) {
  return `Rp ${formatNumber(value)}`;
}

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function paymentConfirmationLabel(value) {
  if (!value) return "-";
  if (value === "CASHIER") return "At Cashier";
  return value;
}

function normalizeRecapItem(item) {
  if (!item) return null;
  return {
    itemId: item.itemId || item.item_id || null,
    itemName: item.itemName || item.item_name || "Unknown Item",
    qtySold: Number(item.qtySold || item.qty_sold || 0),
    revenue: Number(item.revenue || 0),
  };
}

function selectTopSeller(totalTransactions, items) {
  if (totalTransactions < MIN_RECOMMENDATION_TRANSACTIONS) return null;
  return items.find((item) => item.qtySold >= MIN_TOP_QTY) || null;
}

function selectNeedsAttention(totalTransactions, items) {
  if (totalTransactions < MIN_RECOMMENDATION_TRANSACTIONS) return null;
  return items.find((item) => item.qtySold > 0 && item.qtySold <= MAX_LOW_QTY) || null;
}

function buildProductInsight(totalTransactions, topSeller, needsAttention) {
  if (totalTransactions < MIN_RECOMMENDATION_TRANSACTIONS) {
    return `Need at least ${MIN_RECOMMENDATION_TRANSACTIONS} transactions before product recommendations are shown.`;
  }
  if (topSeller && needsAttention && topSeller.itemId !== needsAttention.itemId) {
    return `Bundle or upsell ${needsAttention.itemName} with ${topSeller.itemName} to lift low-performing sales.`;
  }
  if (topSeller && needsAttention) {
    return `${topSeller.itemName} is leading right now. Wait for more item variation before pairing a slow-mover recommendation.`;
  }
  if (!topSeller && !needsAttention) {
    return `No items meet the current recommendation standard yet. Top seller needs at least ${MIN_TOP_QTY} qty and slow movers must stay at ${MAX_LOW_QTY} qty or below.`;
  }
  if (!topSeller) {
    return `Need a stronger top seller first. An item must sell at least ${MIN_TOP_QTY} qty in this period.`;
  }
  return `Top seller is established. Wait until another item drops to ${MAX_LOW_QTY} qty or below for a bundle recommendation.`;
}

async function api(urlPath, options = {}) {
  const response = await fetch(urlPath, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const contentType = response.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");
  const body = isJson ? await response.json() : await response.text();
  const isApiRoute = urlPath.startsWith("/api/");

  if (isJson && body && typeof body === "object" && ("data" in body || "error" in body)) {
    if (!response.ok || body.error) {
      throw new Error(body.error || body.message || `HTTP ${response.status}`);
    }
    return body.data;
  }

  if (isApiRoute) {
    const text = typeof body === "string" ? body : JSON.stringify(body);
    throw new Error(`Invalid API envelope response: ${text || `HTTP ${response.status}`}`);
  }

  if (!response.ok) {
    const text = typeof body === "string" ? body : JSON.stringify(body);
    throw new Error(text || `HTTP ${response.status}`);
  }
  return body;
}

function HomePage() {
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(true);
  const [seeding, setSeeding] = useState(false);
  const [message, setMessage] = useState("");

  async function refreshTables() {
    setLoading(true);
    try {
      const data = await api("/api/tables");
      setTables(Array.isArray(data) ? data : []);
      setMessage("");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refreshTables();
  }, []);

  async function seedTables() {
    setSeeding(true);
    setMessage("");
    try {
      const seeded = await api("/api/tables/seed", {
        method: "POST",
        body: JSON.stringify({
          data: { count: 10, outlet_id: "default" },
          message: "Seed 10 tables",
          error: null,
        }),
      });
      setTables(Array.isArray(seeded) ? seeded : []);
      setMessage("10 table QR targets are ready.");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setSeeding(false);
    }
  }

  return (
    <main className="container">
      <section className="hero">
        <h1>SuCash</h1>
        <p>Table ordering gateway for QR menu and cashier workflow.</p>
        <div className="subtext">Total table target: {formatNumber(tables.length)}</div>
        <div className="hero-links">
          <a href="/dashboard" className="btn">Open Cashier Dashboard</a>
          <button className="btn" onClick={seedTables} disabled={seeding}>
            {seeding ? "Seeding..." : "Generate 10 Tables"}
          </button>
          <button className="btn" onClick={refreshTables} disabled={loading}>
            {loading ? "Loading..." : "Refresh Tables"}
          </button>
        </div>
        {message ? <p className="message">{message}</p> : null}
      </section>

      <section className="card">
        <h2>Seeded Table QR Targets</h2>
        <p>Each row is a table UUID route (`/t/{'{'}uuid{'}'}`).</p>
        {loading ? <p>Loading tables...</p> : (
          <ul className="list table-list">
            {tables.map((table) => (
              <li key={table.uuid} className="list-row table-row">
                <div>
                  <strong>{table.name}</strong>
                  <div className="subtext">{table.uuid}</div>
                </div>
                <a className="btn btn-primary" href={`/t/${table.uuid}`}>Open Order</a>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

function TableOrderingPage() {
  const [table, setTable] = useState(null);
  const [menu, setMenu] = useState([]);
  const [cart, setCart] = useState({});
  const [note, setNote] = useState("");
  const [paymentConfirmation, setPaymentConfirmation] = useState("");
  const [outletId, setOutletId] = useState(initialOutletId);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api(`/api/tables/${tableUuid}`)
      .then(setTable)
      .catch((e) => setMessage(e.message));
    api(`/api/menu?outlet=${encodeURIComponent(outletId)}`)
      .then(setMenu)
      .catch((e) => {
        setMenu([]);
        setMessage(e.message);
      });
  }, [outletId]);

  const cartLines = useMemo(() => {
    return Object.entries(cart)
      .map(([menuId, qty]) => {
        const item = menu.find((it) => it.id === menuId);
        if (!item || qty <= 0) return null;
        return { item, qty };
      })
      .filter(Boolean);
  }, [cart, menu]);

  const total = cartLines.reduce((sum, line) => sum + line.item.price * line.qty, 0);

  function addToCart(menuId) {
    setCart((prev) => ({ ...prev, [menuId]: (prev[menuId] || 0) + 1 }));
  }

  function updateQty(menuId, nextQty) {
    setCart((prev) => {
      const copy = { ...prev };
      if (nextQty <= 0) {
        delete copy[menuId];
      } else {
        copy[menuId] = nextQty;
      }
      return copy;
    });
  }

  async function checkout() {
    if (cartLines.length === 0) {
      setMessage("Cart is empty.");
      return;
    }

    setBusy(true);
    setMessage("");
    try {
      const payload = {
        customerUuid: tableUuid,
        outlet_id: outletId,
        note: note.trim() || null,
        paymentConfirmation: paymentConfirmation || null,
        items: cartLines.map((line) => ({
          menuId: line.item.id,
          qty: line.qty,
        })),
      };

      const created = await api("/api/orders", {
        method: "POST",
        body: JSON.stringify({
          data: payload,
          message: "Create order request",
          error: null,
        }),
      });

      setMessage(`Order ${created.id} created. Waiting for cashier acceptance.`);
      setCart({});
      setNote("");
      setPaymentConfirmation("");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="container">
      <section className="hero">
        <h1>Table Ordering</h1>
        <p>{table ? `${table.name}` : "Loading table..."}</p>
        <div className="subtext">Table UUID: {tableUuid}</div>
        <div className="subtext">Outlet: {outletId}</div>
      </section>

      <section className="card">
        <h2>Menu</h2>
        <div className="menu-grid">
          {menu.length === 0 ? (
            <div className="empty-state">
              <strong>Belum ada menu tersinkron</strong>
              <p className="subtext">Push menu dari mobile lalu refresh halaman.</p>
            </div>
          ) : menu.map((item) => (
            <div key={item.id} className="menu-item">
              <div>
                <strong>{item.name}</strong>
                <div className="subtext">{formatRp(item.price)}</div>
              </div>
              <button className="btn" onClick={() => addToCart(item.id)}>Add</button>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h2>Checkout</h2>
        {cartLines.length === 0 ? <p>No items yet.</p> : (
          <ul className="list">
            {cartLines.map(({ item, qty }) => (
              <li key={item.id} className="list-row">
                <div>
                  <strong>{item.name}</strong>
                  <div className="subtext">{formatRp(item.price)} each</div>
                </div>
                <div className="qty-row">
                  <button className="btn btn-small" onClick={() => updateQty(item.id, qty - 1)}>-</button>
                  <span>{formatNumber(qty)}</span>
                  <button className="btn btn-small" onClick={() => updateQty(item.id, qty + 1)}>+</button>
                </div>
              </li>
            ))}
          </ul>
        )}
        <label className="label">Note for cashier</label>
        <textarea
          className="input"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={3}
          placeholder="Extra spicy, no ice, etc"
        />
        <label className="label">Payment Confirmation</label>
        <select
          className="input"
          value={paymentConfirmation}
          onChange={(e) => setPaymentConfirmation(e.target.value)}
        >
          <option value="">Leave Blank</option>
          <option value="CASHIER">Pay at Cashier</option>
        </select>
        <label className="label">Outlet</label>
        <input
          className="input"
          value={outletId}
          onChange={(e) => setOutletId(e.target.value || "default")}
          placeholder="default"
        />
        <div className="checkout-row">
          <strong>Total:</strong>
          <strong className="amount">{formatRp(total)}</strong>
        </div>
        <button className="btn btn-primary" onClick={checkout} disabled={busy}>
          {busy ? "Submitting..." : "Checkout"}
        </button>
        {message ? <p className="message">{message}</p> : null}
      </section>
    </main>
  );
}

function DashboardPage() {
  const [orders, setOrders] = useState([]);
  const [recap, setRecap] = useState(null);
  const [recapRange, setRecapRange] = useState("TODAY");
  const [anchorDate, setAnchorDate] = useState(todayIsoDate());
  const [outletId, setOutletId] = useState(initialOutletId);
  const [message, setMessage] = useState("");

  async function loadOrders() {
    try {
      const data = await api(`/api/orders?status=NEW,ACCEPTED,PREPARING,SERVED&outlet=${encodeURIComponent(outletId)}`);
      setOrders(Array.isArray(data) ? data : []);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function loadRecap() {
    try {
      const data = await api(
        `/api/recap/summary?range=${encodeURIComponent(recapRange)}&date=${encodeURIComponent(anchorDate)}&outlet=${encodeURIComponent(outletId)}`
      );
      setRecap(data || null);
    } catch (error) {
      setMessage(error.message);
    }
  }

  const recapAnchorDate = recap?.anchorDate ?? recap?.anchor_date ?? "-";
  const recapTransaksiCount = recap?.transaksiCount ?? recap?.transaksi_count ?? 0;
  const recapGrossTotal = recap?.grossTotal ?? recap?.gross_total ?? 0;
  const recapTotalDiscount = recap?.totalDiscount ?? recap?.total_discount ?? 0;
  const recapAverageTicket = recap?.averageTicket ?? recap?.average_ticket ?? 0;
  const recapPaymentBreakdown = Array.isArray(recap?.paymentBreakdown)
    ? recap.paymentBreakdown
    : (Array.isArray(recap?.payment_breakdown) ? recap.payment_breakdown : []);
  const recapTopItems = Array.isArray(recap?.topItems)
    ? recap.topItems
    : (Array.isArray(recap?.top_items) ? recap.top_items : []);
  const recapSlowItems = Array.isArray(recap?.slowItems)
    ? recap.slowItems
    : (Array.isArray(recap?.slow_items) ? recap.slow_items : []);
  const normalizedTopItems = recapTopItems.map(normalizeRecapItem).filter(Boolean);
  const normalizedSlowItems = recapSlowItems.map(normalizeRecapItem).filter(Boolean);
  const recommendedTopSeller = selectTopSeller(recapTransaksiCount, normalizedTopItems);
  const recommendedSlowMover = selectNeedsAttention(recapTransaksiCount, normalizedSlowItems);
  const productInsight = buildProductInsight(
    recapTransaksiCount,
    recommendedTopSeller,
    recommendedSlowMover,
  );

  useEffect(() => {
    loadOrders();
    loadRecap();
    const timer = setInterval(() => {
      loadOrders();
      loadRecap();
    }, 5000);
    return () => clearInterval(timer);
  }, [outletId, recapRange, anchorDate]);

  async function setStatus(orderId, status) {
    try {
      await api(`/api/orders/${orderId}/status`, {
        method: "POST",
        body: JSON.stringify({
          data: { status, outlet_id: outletId },
          message: "Update order status request",
          error: null,
        }),
      });
      await loadOrders();
    } catch (error) {
      setMessage(error.message);
    }
  }

  return (
    <main className="container">
      <section className="hero dashboard-hero">
        <div className="section-header">
          <div>
            <h1>Cashier Dashboard</h1>
            <p>Incoming orders, recap transaksi, dan rekomendasi item dalam satu layar.</p>
          </div>
          <div className="metric-chip">{formatNumber(orders.length)} order aktif</div>
        </div>
        <div className="dashboard-toolbar">
          <div className="filter-grid">
            <div className="field-block">
              <label className="label">Outlet</label>
              <input
                className="input"
                value={outletId}
                onChange={(e) => setOutletId(e.target.value || "default")}
                placeholder="default"
              />
            </div>
            <div className="field-block">
              <label className="label">Anchor Date</label>
              <input
                className="input"
                type="date"
                value={anchorDate}
                onChange={(e) => setAnchorDate(e.target.value || todayIsoDate())}
              />
            </div>
          </div>
          <div className="actions">
            {["TODAY", "WEEK", "MONTH"].map((range) => (
              <button
                key={range}
                className={`btn btn-small ${recapRange === range ? "btn-primary" : ""}`}
                onClick={() => setRecapRange(range)}
              >
                {range}
              </button>
            ))}
          </div>
          <p className="toolbar-note">
            Nilai rupiah memakai format lokal Indonesia dan diperbarui otomatis setiap 5 detik.
          </p>
        </div>
      </section>

      <section className="card">
        <div className="section-header">
          <div>
            <h2>Recap Summary</h2>
            {recap ? (
              <p className="subtext">Range: {recap.range} • Anchor: {recapAnchorDate}</p>
            ) : (
              <p className="subtext">Menyiapkan ringkasan transaksi dan cashflow item.</p>
            )}
          </div>
          {recap ? <div className="metric-chip">{formatNumber(recapTransaksiCount)} transaksi</div> : null}
        </div>
        {recap ? (
          <>
            <div className="metrics-grid">
              <div className="metric-card">
                <div className="subtext">Transactions</div>
                <strong className="metric-value">{formatNumber(recapTransaksiCount)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Gross Total</div>
                <strong className="metric-value amount">{formatRp(recapGrossTotal)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Discount</div>
                <strong className="metric-value amount">{formatRp(recapTotalDiscount)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Average Ticket</div>
                <strong className="metric-value amount">{formatRp(recapAverageTicket)}</strong>
              </div>
            </div>

            <div className="dashboard-grid">
              <div className="panel-card">
                <h3>Payment Breakdown</h3>
                {recapPaymentBreakdown.length === 0 ? (
                  <p>No payment data.</p>
                ) : (
                  <ul className="list">
                    {recapPaymentBreakdown.map((row) => (
                      <li className="list-row" key={row.methodId || row.method_id || "method"}>
                        <span>{row.methodName || row.method_name} ({formatNumber(row.transactionCount || row.transaction_count || 0)})</span>
                        <strong className="amount">{formatRp(row.total)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="panel-card">
                <h3>Recommendation</h3>
                <div className="mini-summary-grid">
                  <div className="mini-summary-card">
                    <div className="mini-summary-title">Top Seller</div>
                    {!recommendedTopSeller ? (
                      <p>Belum ada item yang memenuhi minimum top seller.</p>
                    ) : (
                      <ul className="list compact">
                        <li className="list-row" key={`${recommendedTopSeller.itemId || "item"}-${recommendedTopSeller.itemName}`}>
                          <span>{recommendedTopSeller.itemName} x{formatNumber(recommendedTopSeller.qtySold)}</span>
                          <strong className="amount">{formatRp(recommendedTopSeller.revenue)}</strong>
                        </li>
                      </ul>
                    )}
                  </div>
                  <div className="mini-summary-card">
                    <div className="mini-summary-title">Needs Attention</div>
                    {!recommendedSlowMover ? (
                      <p>Belum ada slow mover yang memenuhi minimum evaluasi.</p>
                    ) : (
                      <ul className="list compact">
                        <li className="list-row" key={`${recommendedSlowMover.itemId || "item"}-${recommendedSlowMover.itemName}`}>
                          <span>{recommendedSlowMover.itemName} x{formatNumber(recommendedSlowMover.qtySold)}</span>
                          <strong className="amount">{formatRp(recommendedSlowMover.revenue)}</strong>
                        </li>
                      </ul>
                    )}
                  </div>
                </div>
                <div className="insight-card">
                  <div className="insight-label">Suggested Action</div>
                  <p>{productInsight}</p>
                  <div className="subtext">
                    Standar: minimal {formatNumber(MIN_RECOMMENDATION_TRANSACTIONS)} transaksi, top seller qty {formatNumber(MIN_TOP_QTY)}+, slow mover qty {formatNumber(MAX_LOW_QTY)} ke bawah.
                  </div>
                </div>
              </div>
            </div>

            <div className="dashboard-grid">
              <div className="panel-card">
                <h3>Top Movers</h3>
                {recapTopItems.length === 0 ? (
                  <p>No top items.</p>
                ) : (
                  <ul className="list compact">
                    {recapTopItems.map((item) => (
                      <li className="list-row" key={`${item.itemId || item.item_id || "item"}-${item.itemName || item.item_name}`}>
                        <span>{item.itemName || item.item_name} x{formatNumber(item.qtySold || item.qty_sold || 0)}</span>
                        <strong className="amount">{formatRp(item.revenue)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="panel-card">
                <h3>Slow Movers</h3>
                {recapSlowItems.length === 0 ? (
                  <p>No slow items.</p>
                ) : (
                  <ul className="list compact">
                    {recapSlowItems.map((item) => (
                      <li className="list-row" key={`${item.itemId || item.item_id || "item"}-${item.itemName || item.item_name}`}>
                        <span>{item.itemName || item.item_name} x{formatNumber(item.qtySold || item.qty_sold || 0)}</span>
                        <strong className="amount">{formatRp(item.revenue)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </>
        ) : (
          <p>Loading recap...</p>
        )}
      </section>

      <section className="card">
        <div className="section-header">
          <div>
            <h2>Orders</h2>
            <p className="subtext">Pantau order masuk dan pindahkan statusnya dari satu tempat.</p>
          </div>
          <div className="metric-chip">{formatNumber(orders.length)} order</div>
        </div>
        {orders.length === 0 ? <p>No orders yet.</p> : (
          <ul className="list">
            {orders.map((order) => (
              <li key={order.id} className="order-card">
                <div className="list-row">
                  <div>
                    <strong>{order.customerName}</strong>
                    <div className="subtext">Table: {order.customerUuid}</div>
                  </div>
                  <span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>
                </div>
                <div className="subtext">Order ID: {order.id}</div>
                {order.note ? <div className="subtext">Note: {order.note}</div> : null}
                <div className="subtext">
                  Payment Confirmation: {paymentConfirmationLabel(order.paymentConfirmation)}
                </div>
                <ul className="list compact">
                  {(Array.isArray(order.items) ? order.items : []).map((item) => (
                    <li key={item.id} className="list-row">
                      <span>{item.itemName} x{formatNumber(item.qty)}</span>
                      <span className="amount">{formatRp(item.lineTotal)}</span>
                    </li>
                  ))}
                </ul>
                <div className="checkout-row">
                  <strong>Total</strong>
                  <strong className="amount">{formatRp(order.total)}</strong>
                </div>
                <div className="actions">
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "ACCEPTED")}>Accept</button>
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "PREPARING")}>Preparing</button>
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "SERVED")}>Served</button>
                  <button className="btn btn-small" onClick={() => setStatus(order.id, "DONE")}>Done</button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {message ? <p className="message">{message}</p> : null}
      </section>
    </main>
  );
}

export default function App() {
  if (isDashboardPage) return <DashboardPage />;
  if (isTablePage) return <TableOrderingPage />;
  return <HomePage />;
}
