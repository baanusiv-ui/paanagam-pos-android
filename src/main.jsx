import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const initialMenu = [
  { id: 'filter-coffee', name: 'Filter Coffee', tamil: 'ஃபில்டர் காபி', price: 20, category: 'Coffee', active: true },
  { id: 'strong-coffee', name: 'Strong Coffee', tamil: 'ஸ்ட்ராங் காபி', price: 25, category: 'Coffee', active: true },
  { id: 'tea', name: 'Tea', tamil: 'தேநீர்', price: 15, category: 'Tea', active: true },
  { id: 'lemon-grass-tea', name: 'Lemongrass Tea', tamil: 'எலுமிச்சைப் புல் தேநீர்', price: 20, category: 'Tea', active: true },
  { id: 'black-coffee', name: 'Black Coffee', tamil: 'பிளாக் காபி', price: 15, category: 'Coffee', active: true },
  { id: 'masala-sundal', name: 'Masala Sundal', tamil: 'மசாலா சுண்டல்', price: 25, category: 'Snacks', active: true },
  { id: 'bun-muska', name: 'Bun Muska', tamil: 'பன் மஸ்கா', price: 30, category: 'Snacks', active: true },
  { id: 'chicken-fry', name: 'Chicken Fry', tamil: 'சிக்கன் ஃப்ரை', price: 40, category: 'Food', active: true }
];
const KEY = 'paanagam.pos.v1';
const DEFAULT_PIN = '1234';
const money = n => '₹' + Number(n || 0).toFixed(2).replace(/\.00$/, '');
function readStore() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || '{}');
    return { menu: Array.isArray(saved.menu) ? saved.menu : initialMenu, sales: Array.isArray(saved.sales) ? saved.sales : [], pin: saved.pin || DEFAULT_PIN, loyalty: saved.loyalty || {} };
  } catch {
    return { menu: initialMenu, sales: [], pin: DEFAULT_PIN, loyalty: {} };
  }
}
function App() {
  const [store, setStore] = useState(readStore);
  const [cart, setCart] = useState([]);
  const [page, setPage] = useState('POS');
  const [category, setCategory] = useState('All');
  const [payment, setPayment] = useState('Cash');
  const [adminOpen, setAdminOpen] = useState(false);
  const [pinEntry, setPinEntry] = useState('');
  const [newPin, setNewPin] = useState('');
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState(null);
  const [restoreText, setRestoreText] = useState('');
  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(store)); } catch { setMessage('Storage is full. Export a backup and clear old sales.'); }
  }, [store]);
  const categories = useMemo(() => ['All', ...new Set(store.menu.filter(m => m.active).map(m => m.category))], [store.menu]);
  const visibleMenu = store.menu.filter(m => m.active && (category === 'All' || m.category === category));
  const total = cart.reduce((sum, item) => sum + item.price * item.qty, 0);
  const today = new Date().toLocaleDateString();
  const todaySales = store.sales.filter(s => new Date(s.createdAt).toLocaleDateString() === today);
  const todayTotal = todaySales.reduce((s, x) => s + x.total, 0);

  function flash(text) { setMessage(text); window.setTimeout(() => setMessage(''), 2800); }
  function addToCart(product) {
    setCart(curr => {
      const found = curr.find(x => x.id === product.id);
      return found ? curr.map(x => x.id === product.id ? { ...x, qty: x.qty + 1 } : x) : [...curr, { id: product.id, name: product.name, price: Number(product.price), qty: 1 }];
    });
  }
  function qtyChange(id, delta) {
    setCart(curr => curr.map(x => x.id === id ? { ...x, qty: x.qty + delta } : x).filter(x => x.qty > 0));
  }
  function checkout() {
    if (!cart.length) return flash('Add an item before checkout.');
    const sale = { id: 'PG-' + Date.now().toString().slice(-8), createdAt: new Date().toISOString(), items: cart.map(x => ({ ...x })), total, payment };
    setStore(curr => ({ ...curr, sales: [sale, ...curr.sales] }));
    setCart([]);
    setPage('POS');
    flash('Sale saved on this device: ' + sale.id);
  }
  function exportBackup() {
    const blob = new Blob([JSON.stringify({ ...store, exportedAt: new Date().toISOString(), schema: 1 }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = 'paanagam-backup-' + new Date().toISOString().slice(0, 10) + '.json'; a.click();
    URL.revokeObjectURL(url);
    flash('Backup export started.');
  }
  function restoreBackup() {
    try {
      const parsed = JSON.parse(restoreText);
      if (!Array.isArray(parsed.menu) || !Array.isArray(parsed.sales)) throw new Error('Backup must contain menu and sales arrays.');
      setStore({ menu: parsed.menu, sales: parsed.sales, pin: parsed.pin || store.pin, loyalty: parsed.loyalty || {} });
      setRestoreText(''); flash('Backup restored to this device.');
    } catch (e) { flash('Restore failed: ' + e.message); }
  }
  function unlockAdmin() {
    if (pinEntry === store.pin) { setAdminOpen(true); setPinEntry(''); setPage('Admin'); }
    else flash('Incorrect admin PIN.');
  }
  function saveProduct(product) {
    const clean = { ...product, price: Math.max(0, Number(product.price) || 0), active: true };
    setStore(curr => ({ ...curr, menu: curr.menu.some(m => m.id === clean.id) ? curr.menu.map(m => m.id === clean.id ? clean : m) : [...curr.menu, clean] }));
    setEditing(null); flash('Menu item saved.');
  }
  function changePin() {
    if (!/^\d{4,8}$/.test(newPin)) return flash('PIN must be 4–8 digits.');
    setStore(curr => ({ ...curr, pin: newPin })); setNewPin(''); flash('Admin PIN updated.');
  }

  return <div className="app-shell">
    <header className="topbar">
      <div className="brand-mark">பா</div>
      <div className="brand-copy"><strong>PAANAGAM</strong><small>காபியும் கதையும் · POS</small></div>
      <div className="offline"><span /> Offline</div>
    </header>
    <div className="summary-strip">
      <div><span>Today's sales</span><strong>{money(todayTotal)}</strong></div>
      <div><span>Orders</span><strong>{todaySales.length}</strong></div>
      <div><span>Current bill</span><strong>{money(total)}</strong></div>
    </div>
    <nav className="tabs">
      <button className={page === 'POS' ? 'selected' : ''} onClick={() => setPage('POS')}>◫ <span>POS</span></button>
      <button className={page === 'Sales' ? 'selected' : ''} onClick={() => setPage('Sales')}>▤ <span>Sales</span></button>
      <button className={page === 'Admin' ? 'selected' : ''} onClick={() => setPage('Admin')}>⚙ <span>Admin</span></button>
    </nav>

    {page === 'POS' && <main className="layout">
      <section className="menu-section">
        <div className="section-heading"><div><h1>Menu</h1><p>Tap a product to add to the bill</p></div><span className="count-pill">{visibleMenu.length} items</span></div>
        <div className="category-row">{categories.map(c => <button key={c} className={category === c ? 'chip active' : 'chip'} onClick={() => setCategory(c)}>{c}</button>)}</div>
        <div className="product-grid">{visibleMenu.map(product => <button className="product-card" key={product.id} onClick={() => addToCart(product)}>
          <span className="product-icon">{product.category === 'Coffee' ? '☕' : product.category === 'Tea' ? '🍵' : product.category === 'Food' ? '🍗' : '🥯'}</span>
          <strong>{product.name}</strong><span className="tamil">{product.tamil || ''}</span><b>{money(product.price)}</b>
        </button>)}</div>
      </section>
      <aside className="bill-panel">
        <div className="bill-title"><div><h2>Current bill</h2><p>{cart.reduce((s, x) => s + x.qty, 0)} items</p></div><button className="text-button" onClick={() => setCart([])} disabled={!cart.length}>Clear</button></div>
        <div className="cart-list">{cart.length === 0 ? <div className="empty-cart"><div>🧾</div><strong>Ready for the next order</strong><span>Select menu items to start a bill.</span></div> : cart.map(item => <div className="cart-item" key={item.id}>
          <div className="cart-name"><strong>{item.name}</strong><span>{money(item.price)} each</span></div>
          <div className="qty-control"><button onClick={() => qtyChange(item.id, -1)}>−</button><span>{item.qty}</span><button onClick={() => qtyChange(item.id, 1)}>+</button></div>
          <b className="line-total">{money(item.price * item.qty)}</b>
        </div>)}</div>
        <div className="bill-footer">
          <div className="subtotal"><span>Total</span><strong>{money(total)}</strong></div>
          <div className="pay-choice"><button className={payment === 'Cash' ? 'pay-active' : ''} onClick={() => setPayment('Cash')}>💵 Cash</button><button className={payment === 'UPI' ? 'pay-active' : ''} onClick={() => setPayment('UPI')}>▦ UPI</button></div>
          <button className="checkout" onClick={checkout} disabled={!cart.length}>Complete sale · {money(total)}</button>
          <p className="local-note">Sales are saved on this device.</p>
        </div>
      </aside>
    </main>}

    {page === 'Sales' && <main className="page-content">
      <div className="section-heading"><div><h1>Sales history</h1><p>Stored locally on this device</p></div><button className="outline-button" onClick={exportBackup}>Export backup</button></div>
      <div className="sales-summary"><div><span>Today's collected</span><strong>{money(todayTotal)}</strong></div><div><span>Today's bills</span><strong>{todaySales.length}</strong></div></div>
      {store.sales.length === 0 ? <div className="empty-state"><div>📒</div><h3>No sales recorded yet</h3><p>Completed POS bills appear here.</p></div> : <div className="sales-list">{store.sales.map(sale => <article className="sale-row" key={sale.id}><div><strong>{sale.id}</strong><span>{new Date(sale.createdAt).toLocaleString()}</span><small>{sale.items.map(i => i.name + ' ×' + i.qty).join(', ')}</small></div><div className="sale-right"><strong>{money(sale.total)}</strong><span className="payment-label">{sale.payment}</span></div></article>)}</div>}
    </main>}

    {page === 'Admin' && <main className="page-content">
      {!adminOpen ? <div className="admin-lock"><div className="lock-icon">🔐</div><h1>Admin access</h1><p>Enter the admin PIN to manage the menu and backups.</p><form onSubmit={e => { e.preventDefault(); unlockAdmin(); }}><input aria-label="Admin PIN" type="password" inputMode="numeric" maxLength="8" value={pinEntry} onChange={e => setPinEntry(e.target.value.replace(/\D/g, ''))} placeholder="Enter PIN" /><button className="checkout">Unlock admin</button></form><small>Starter default PIN: 1234. Change it after first login.</small></div> :
      <div className="admin-content"><div className="section-heading"><div><h1>Admin settings</h1><p>Menu, security and device backup</p></div><button className="text-button" onClick={() => { setAdminOpen(false); setPage('POS'); }}>Lock</button></div>
        <section className="admin-card"><div className="admin-card-heading"><div><h2>Menu management</h2><p>Add products or change pricing.</p></div><button className="solid-small" onClick={() => setEditing({ id: 'item-' + Date.now(), name: '', tamil: '', price: 20, category: 'Coffee', active: true })}>+ Add</button></div>
          {store.menu.map(p => <div className="menu-admin-row" key={p.id}><div><strong>{p.name}</strong><span>{p.category} · {money(p.price)}</span></div><button className="outline-button small" onClick={() => setEditing({ ...p })}>Edit</button><button className="text-button danger" onClick={() => setStore(curr => ({ ...curr, menu: curr.menu.map(m => m.id === p.id ? { ...m, active: !m.active } : m) }))}>{p.active ? 'Hide' : 'Show'}</button></div>)}
        </section>
        <section className="admin-card"><h2>Change admin PIN</h2><p>Use 4–8 digits. Keep this PIN private.</p><div className="inline-form"><input type="password" inputMode="numeric" maxLength="8" placeholder="New PIN" value={newPin} onChange={e => setNewPin(e.target.value.replace(/\D/g, ''))}/><button className="solid-small" onClick={changePin}>Save PIN</button></div></section>
        <section className="admin-card"><h2>Backup and restore</h2><p>Export a JSON backup regularly. Restore replaces the local menu and sales with the backup contents.</p><div className="button-pair"><button className="outline-button" onClick={exportBackup}>Download backup</button><button className="outline-button" onClick={() => document.getElementById('backup-file').click()}>Choose backup file</button><input id="backup-file" className="hidden" type="file" accept="application/json,.json" onChange={e => { const file = e.target.files?.[0]; if (file) { const reader = new FileReader(); reader.onload = () => setRestoreText(String(reader.result || '')); reader.readAsText(file); }}} /></div>
          <textarea value={restoreText} onChange={e => setRestoreText(e.target.value)} placeholder="Or paste backup JSON here..." rows="4" /><button className="checkout" onClick={restoreBackup} disabled={!restoreText.trim()}>Restore backup</button></section>
      </div>}
    </main>}

    {editing && <div className="modal-backdrop" role="presentation"><form className="edit-modal" onSubmit={e => { e.preventDefault(); saveProduct(editing); }}><div className="modal-head"><h2>{store.menu.some(x => x.id === editing.id) ? 'Edit menu item' : 'Add menu item'}</h2><button type="button" className="text-button" onClick={() => setEditing(null)}>✕</button></div>
      <label>Product name<input required autoFocus value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })}/></label>
      <label>Tamil name (optional)<input value={editing.tamil || ''} onChange={e => setEditing({ ...editing, tamil: e.target.value })}/></label>
      <label>Price (₹)<input required type="number" min="0" step="0.5" value={editing.price} onChange={e => setEditing({ ...editing, price: e.target.value })}/></label>
      <label>Category<select value={editing.category} onChange={e => setEditing({ ...editing, category: e.target.value })}>{['Coffee','Tea','Snacks','Food','Other'].map(x => <option key={x}>{x}</option>)}</select></label>
      <button className="checkout">Save item</button>
    </form></div>}
    {message && <div className="toast" role="status">{message}</div>}
    <footer className="app-footer">PAANAGAM · This device only · Back up your data regularly</footer>
  </div>;
}
createRoot(document.getElementById('root')).render(<App />);
