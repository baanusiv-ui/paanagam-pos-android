import React, { useEffect, useMemo, useState } from 'react';

const defaultMenu = [
  { id: 'coffee', name: 'Filter Coffee', price: 20, category: 'Beverages' },
  { id: 'tea', name: 'Tea', price: 15, category: 'Beverages' },
  { id: 'lemon-tea', name: 'Lemongrass Tea', price: 20, category: 'Beverages' },
  { id: 'black-coffee', name: 'Black Coffee', price: 15, category: 'Beverages' },
  { id: 'bun', name: 'Bun Muska', price: 25, category: 'Snacks' },
  { id: 'sundal', name: 'Masala Sundal', price: 25, category: 'Snacks' },
  { id: 'appam', name: 'Appam', price: 30, category: 'Food' },
  { id: 'chicken', name: 'Fried Chicken', price: 40, category: 'Food' }
];
const STORE_KEY = 'paanagam-pos-starter-v1';
const money = n => '₹' + Number(n || 0).toFixed(2);

function loadSaved() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (raw) return JSON.parse(raw);
  } catch (e) { console.warn('Could not load saved POS data', e); }
  return { menu: defaultMenu, sales: [], adminPin: '1234', loyalty: {} };
}

export default function App() {
  const [saved, setSaved] = useState(loadSaved);
  const [cart, setCart] = useState([]);
  const [payment, setPayment] = useState('Cash');
  const [page, setPage] = useState('POS');
  const [pin, setPin] = useState('');
  const [unlocked, setUnlocked] = useState(false);
  const [notice, setNotice] = useState('');
  const [newName, setNewName] = useState('');
  const [newPrice, setNewPrice] = useState('');
  const [newCategory, setNewCategory] = useState('Beverages');

  useEffect(() => {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(saved)); }
    catch (e) { setNotice('Storage error. Export a backup and check device storage.'); }
  }, [saved]);

  const total = useMemo(() => cart.reduce((sum, item) => sum + item.price * item.qty, 0), [cart]);
  const today = new Date().toLocaleDateString();
  const todaysSales = saved.sales.filter(s => new Date(s.createdAt).toLocaleDateString() === today);
  const todayTotal = todaysSales.reduce((sum, s) => sum + s.total, 0);

  function addToCart(product) {
    setCart(old => {
      const found = old.find(i => i.id === product.id);
      return found ? old.map(i => i.id === product.id ? { ...i, qty: i.qty + 1 } : i) : [...old, { ...product, qty: 1 }];
    });
  }
  function changeQty(id, delta) {
    setCart(old => old.map(i => i.id === id ? { ...i, qty: i.qty + delta } : i).filter(i => i.qty > 0));
  }
  function checkout() {
    if (!cart.length) return;
    const sale = { id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()), createdAt: new Date().toISOString(), items: cart, total, payment };
    setSaved(old => ({ ...old, sales: [sale, ...old.sales] }));
    setCart([]);
    setNotice('Sale saved on this device.');
  }
  function unlockAdmin(e) {
    e.preventDefault();
    if (pin === saved.adminPin) { setUnlocked(true); setPage('Admin'); setPin(''); setNotice('Admin unlocked.'); }
    else setNotice('Incorrect PIN.');
  }
  function addMenuItem(e) {
    e.preventDefault();
    const price = Number(newPrice);
    if (!newName.trim() || !Number.isFinite(price) || price <= 0) { setNotice('Enter a valid item name and price.'); return; }
    setSaved(old => ({ ...old, menu: [...old.menu, { id: 'item-' + Date.now(), name: newName.trim(), price, category: newCategory }] }));
    setNewName(''); setNewPrice(''); setNotice('Menu item saved.');
  }
  function updatePrice(id, nextPrice) {
    const price = Number(nextPrice);
    if (!Number.isFinite(price) || price < 0) return;
    setSaved(old => ({ ...old, menu: old.menu.map(p => p.id === id ? { ...p, price } : p) }));
  }
  function exportBackup() {
    const blob = new Blob([JSON.stringify({ ...saved, exportedAt: new Date().toISOString() }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = 'paanagam-pos-backup.json'; a.click();
    URL.revokeObjectURL(url);
  }
  function importBackup(e) {
    const file = e.target.files?.[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const data = JSON.parse(String(reader.result));
        if (!Array.isArray(data.menu) || !Array.isArray(data.sales)) throw new Error('Backup format not recognized');
        setSaved({ menu: data.menu, sales: data.sales, adminPin: data.adminPin || '1234', loyalty: data.loyalty || {} });
        setNotice('Backup restored.'); setCart([]);
      } catch (err) { setNotice('Could not restore backup: ' + err.message); }
    };
    reader.readAsText(file); e.target.value = '';
  }
  function changePin() {
    const next = window.prompt('Set a new admin PIN (at least 4 digits):');
    if (next === null) return;
    if (!/^\d{4,8}$/.test(next)) { setNotice('PIN must be 4–8 digits.'); return; }
    setSaved(old => ({ ...old, adminPin: next }));
    setNotice('Admin PIN changed on this device.');
  }
  function redeemLoyalty() {
    const phone = window.prompt('Customer reference (optional; do not enter sensitive information):');
    if (!phone) return;
    const count = Number(saved.loyalty[phone] || 0);
    if (count >= 10) {
      setSaved(old => ({ ...old, loyalty: { ...old.loyalty, [phone]: count - 10 } }));
      setNotice('Reward available: apply one free coffee manually to the bill.');
    } else {
      setSaved(old => ({ ...old, loyalty: { ...old.loyalty, [phone]: count + 1 } }));
      setNotice('Loyalty visit recorded: ' + (count + 1) + '/10. This is a starter manual tracker.');
    }
  }

  return <div className="app-shell">
    <header className="topbar">
      <div className="brand-mark">பா</div>
      <div className="brand"><strong>PAANAGAM</strong><span>காபியும் கதையும்</span></div>
      <div className="offline-pill"><span /> Offline POS</div>
    </header>
    <nav className="tabs">
      {['POS', 'Sales', 'Admin'].map(p => <button key={p} className={page === p ? 'tab active' : 'tab'} onClick={() => { if (p === 'Admin' && !unlocked) { setPage('Pin'); return; } setPage(p); }}>{p}</button>)}
    </nav>
    {notice && <div className="notice" role="status"><span>{notice}</span><button onClick={() => setNotice('')} aria-label="Dismiss notice">×</button></div>}

    {page === 'Pin' && <section className="panel pin-panel">
      <h2>Admin login</h2><p>Enter your local admin PIN.</p>
      <form onSubmit={unlockAdmin}><input type="password" inputMode="numeric" value={pin} onChange={e => setPin(e.target.value)} placeholder="Admin PIN" autoComplete="current-password" /><button className="primary" type="submit">Unlock admin</button></form>
      <button className="text-button" onClick={() => setPage('POS')}>Back to POS</button>
    </section>}

    {page === 'POS' && <main className="pos-layout">
      <section className="menu-area">
        <div className="section-heading"><div><h1>New sale</h1><p>Tap an item to add it to the bill.</p></div><span className="date-chip">{new Date().toLocaleDateString('en-IN')}</span></div>
        <div className="product-grid">
          {saved.menu.map(item => <button key={item.id} className="product-card" onClick={() => addToCart(item)}>
            <span className="product-icon">{item.category === 'Beverages' ? '☕' : item.category === 'Food' ? '🍽️' : '🥯'}</span>
            <span className="product-name">{item.name}</span><strong>{money(item.price)}</strong>
          </button>)}
        </div>
      </section>
      <aside className="bill panel">
        <div className="section-heading"><div><h2>Current bill</h2><p>{cart.reduce((n, i) => n + i.qty, 0)} items</p></div><button className="text-button" onClick={() => setCart([])}>Clear</button></div>
        {cart.length === 0 ? <div className="empty-state"><span>🧾</span><p>Bill is empty</p><small>Choose items to start a sale.</small></div> :
          <div className="cart-list">{cart.map(item => <div className="cart-row" key={item.id}>
            <div className="cart-info"><strong>{item.name}</strong><small>{money(item.price)} each</small></div>
            <div className="quantity"><button onClick={() => changeQty(item.id, -1)} aria-label={'Decrease ' + item.name}>−</button><span>{item.qty}</span><button onClick={() => changeQty(item.id, 1)} aria-label={'Increase ' + item.name}>+</button></div>
            <strong className="line-total">{money(item.price * item.qty)}</strong>
          </div>)}</div>}
        <div className="bill-footer">
          <div className="total-row"><span>Total</span><strong>{money(total)}</strong></div>
          <div className="payment-toggle">{['Cash', 'UPI'].map(method => <button key={method} className={payment === method ? 'selected' : ''} onClick={() => setPayment(method)}>{method === 'Cash' ? '💵 ' : '📲 '}{method}</button>)}</div>
          <button className="primary checkout" disabled={!cart.length} onClick={checkout}>Complete sale · {money(total)}</button>
          <small className="hint">Sales are stored locally on this device.</small>
        </div>
      </aside>
    </main>}

    {page === 'Sales' && <main className="content-page">
      <div className="section-heading"><div><h1>Sales history</h1><p>Stored on this device only.</p></div><button className="secondary" onClick={exportBackup}>Export backup</button></div>
      <div className="stats"><div className="stat panel"><small>Today's sales</small><strong>{todaysSales.length}</strong></div><div className="stat panel"><small>Today's total</small><strong>{money(todayTotal)}</strong></div><div className="stat panel"><small>All-time total</small><strong>{money(saved.sales.reduce((n,s) => n + s.total, 0))}</strong></div></div>
      <section className="panel table-panel"><h2>Recent bills</h2>{saved.sales.length === 0 ? <div className="empty-state"><p>No sales recorded yet.</p></div> : <div className="sales-list">{saved.sales.map(s => <article className="sale-row" key={s.id}><div><strong>Bill {String(s.id).slice(-6)}</strong><small>{new Date(s.createdAt).toLocaleString('en-IN')} · {s.payment}</small><small>{s.items.map(i => i.name + ' × ' + i.qty).join(', ')}</small></div><strong>{money(s.total)}</strong></article>)}</div>}</section>
    </main>}

    {page === 'Admin' && unlocked && <main className="content-page">
      <div className="section-heading"><div><h1>Admin settings</h1><p>Menu, backup and device tools.</p></div><button className="secondary" onClick={() => { setUnlocked(false); setPage('POS'); }}>Lock admin</button></div>
      <section className="panel admin-section"><h2>Menu & pricing</h2><div className="admin-menu">{saved.menu.map(item => <label key={item.id} className="admin-menu-row"><span>{item.name}<small>{item.category}</small></span><span className="price-edit"><span>₹</span><input aria-label={item.name + ' price'} type="number" min="0" step="0.5" value={item.price} onChange={e => updatePrice(item.id, e.target.value)} /></span></label>)}</div>
        <form className="add-form" onSubmit={addMenuItem}><input value={newName} onChange={e => setNewName(e.target.value)} placeholder="New item name" /><input type="number" min="0.5" step="0.5" value={newPrice} onChange={e => setNewPrice(e.target.value)} placeholder="Price ₹" /><select value={newCategory} onChange={e => setNewCategory(e.target.value)}><option>Beverages</option><option>Snacks</option><option>Food</option></select><button className="primary" type="submit">Add item</button></form>
      </section>
      <section className="panel admin-section"><h2>Data & security</h2><p className="muted">Default PIN is 1234. Change it before live use.</p><div className="tools-row"><button className="secondary" onClick={changePin}>Change admin PIN</button><button className="secondary" onClick={exportBackup}>Export JSON backup</button><label className="file-button">Restore backup<input type="file" accept="application/json,.json" onChange={importBackup} /></label><button className="secondary" onClick={redeemLoyalty}>Record loyalty visit</button></div></section>
      <section className="panel warning-panel"><strong>Starter limitations</strong><p>This version does not yet deduct recipes or ingredients from inventory, manage purchasing or transfers, calculate food cost, or sync to the existing Apper database. Keep backups and test before real sales.</p></section>
    </main>}
    <footer>PAANAGAM · Local device storage · Export backups regularly</footer>
  </div>;
}
