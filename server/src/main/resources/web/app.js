const { useEffect, useMemo, useState } = React;

const path = window.location.pathname;
const search = new URLSearchParams(window.location.search);
const tableMatch = path.match(/^\/t\/([^/]+)$/);
const isTablePage = Boolean(tableMatch);
const isDashboardPage = path === "/dashboard";
const tableCustomerUuid = tableMatch ? tableMatch[1] : "";
const initialOutletId = search.get("outlet") || "default";

function formatRp(value) {
  return `Rp ${Number(value || 0).toLocaleString("id-ID")}`;
}

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function paymentConfirmationLabel(value) {
  if (!value) return "-";
  if (value === "CASHIER") return "At Cashier";
  return value;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.json();
}

function HomePage() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api("/api/customers")
      .then(setCustomers)
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="container">
      <section className="hero">
        <h1>SuCash</h1>
        <p>Table-ordering demo with UUID barcode tokens.</p>
        <div className="hero-links">
          <a href="/dashboard" className="btn">Open Cashier Dashboard</a>
        </div>
      </section>

      <section className="card">
        <h2>Seeded Customer QR Targets</h2>
        <p>Scan barcode UUID and redirect to customer ordering page.</p>
        {loading ? <p>Loading customers...</p> : (
          <ul className="list">
            {customers.map((customer) => (
              <li key={customer.uuid} className="list-row">
                <div>
                  <strong>{customer.name}</strong>
                  <div className="subtext">{customer.uuid}</div>
                </div>
                <a className="btn" href={`/t/${customer.uuid}`}>Open</a>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

function TableOrderingPage() {
  const [customer, setCustomer] = useState(null);
  const [menu, setMenu] = useState([]);
  const [cart, setCart] = useState({});
  const [note, setNote] = useState("");
  const [paymentConfirmation, setPaymentConfirmation] = useState("");
  const [outletId, setOutletId] = useState(initialOutletId);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api(`/api/customers/${tableCustomerUuid}`).then(setCustomer).catch((e) => setMessage(e.message));
    api(`/api/menu?outlet=${encodeURIComponent(outletId)}`).then(setMenu).catch((e) => setMessage(e.message));
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
        customerUuid: tableCustomerUuid,
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
        body: JSON.stringify(payload),
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
        <p>{customer ? `${customer.name}` : "Loading customer..."}</p>
        <div className="subtext">UUID: {tableCustomerUuid}</div>
        <div className="subtext">Outlet: {outletId}</div>
      </section>

      <section className="card">
        <h2>Menu</h2>
        <div className="menu-grid">
          {menu.map((item) => (
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
                  <span>{qty}</span>
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
          <strong>{formatRp(total)}</strong>
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
      setOrders(data);
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function loadRecap() {
    try {
      const data = await api(
        `/api/recap/summary?range=${encodeURIComponent(recapRange)}&date=${encodeURIComponent(anchorDate)}&outlet=${encodeURIComponent(outletId)}`
      );
      setRecap(data);
    } catch (error) {
      setMessage(error.message);
    }
  }

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
        body: JSON.stringify({ status, outlet_id: outletId }),
      });
      await loadOrders();
    } catch (error) {
      setMessage(error.message);
    }
  }

  return (
    <main className="container">
      <section className="hero">
        <h1>Cashier Dashboard</h1>
        <p>Incoming orders + synced POS recap</p>
        <label className="label">Outlet</label>
        <input
          className="input"
          value={outletId}
          onChange={(e) => setOutletId(e.target.value || "default")}
          placeholder="default"
        />
        <label className="label">Anchor Date</label>
        <input
          className="input"
          type="date"
          value={anchorDate}
          onChange={(e) => setAnchorDate(e.target.value || todayIsoDate())}
        />
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
      </section>

      <section className="card">
        <h2>Recap Summary</h2>
        {recap ? (
          <>
            <div className="subtext">Range: {recap.range} | Anchor: {recap.anchorDate}</div>
            <div className="metrics-grid">
              <div className="metric-card">
                <div className="subtext">Transactions</div>
                <strong>{recap.transaksiCount}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Gross Total</div>
                <strong>{formatRp(recap.grossTotal)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Discount</div>
                <strong>{formatRp(recap.totalDiscount)}</strong>
              </div>
              <div className="metric-card">
                <div className="subtext">Average Ticket</div>
                <strong>{formatRp(recap.averageTicket)}</strong>
              </div>
            </div>

            <h3>Payment Breakdown</h3>
            {recap.paymentBreakdown.length === 0 ? (
              <p>No payment data.</p>
            ) : (
              <ul className="list">
                {recap.paymentBreakdown.map((row) => (
                  <li className="list-row" key={row.methodId}>
                    <span>{row.methodName} ({row.transactionCount})</span>
                    <strong>{formatRp(row.total)}</strong>
                  </li>
                ))}
              </ul>
            )}

            <div className="dual-grid">
              <div>
                <h3>Top Movers</h3>
                {recap.topItems.length === 0 ? (
                  <p>No top items.</p>
                ) : (
                  <ul className="list compact">
                    {recap.topItems.map((item) => (
                      <li className="list-row" key={`${item.itemId || "item"}-${item.itemName}`}>
                        <span>{item.itemName} x{item.qtySold}</span>
                        <strong>{formatRp(item.revenue)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <h3>Slow Movers</h3>
                {recap.slowItems.length === 0 ? (
                  <p>No slow items.</p>
                ) : (
                  <ul className="list compact">
                    {recap.slowItems.map((item) => (
                      <li className="list-row" key={`${item.itemId || "item"}-${item.itemName}`}>
                        <span>{item.itemName} x{item.qtySold}</span>
                        <strong>{formatRp(item.revenue)}</strong>
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
        <h2>Orders</h2>
        {orders.length === 0 ? <p>No orders yet.</p> : (
          <ul className="list">
            {orders.map((order) => (
              <li key={order.id} className="order-card">
                <div className="list-row">
                  <div>
                    <strong>{order.customerName}</strong>
                    <div className="subtext">{order.customerUuid}</div>
                  </div>
                  <span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>
                </div>
                <div className="subtext">Order ID: {order.id}</div>
                {order.note ? <div className="subtext">Note: {order.note}</div> : null}
                <div className="subtext">
                  Payment Confirmation: {paymentConfirmationLabel(order.paymentConfirmation)}
                </div>
                <ul className="list compact">
                  {order.items.map((item) => (
                    <li key={item.id} className="list-row">
                      <span>{item.itemName} x{item.qty}</span>
                      <span>{formatRp(item.lineTotal)}</span>
                    </li>
                  ))}
                </ul>
                <div className="checkout-row">
                  <strong>Total</strong>
                  <strong>{formatRp(order.total)}</strong>
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

function App() {
  if (isDashboardPage) return <DashboardPage />;
  if (isTablePage) return <TableOrderingPage />;
  return <HomePage />;
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
