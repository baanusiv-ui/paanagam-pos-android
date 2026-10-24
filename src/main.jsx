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
const INITIAL_RECIPES = {
  'filter-coffee': [
    { ingredient: 'Prepared decoction', qty: 25, unit: 'ml', note: 'Per cup SOP' },
    { ingredient: 'Milk', qty: 75, unit: 'ml', note: 'Per cup SOP' },
    { ingredient: 'Sugar', qty: 5, unit: 'g', note: 'Per cup SOP' }
  ]
};

const SOP_LIBRARY = [
  {id:'filter-coffee',category:'Coffee',name:'Filter Coffee',serving:'1 cup',status:'Confirmed SOP',ingredients:'Prepared coffee decoction: 25 ml\nMilk: 75 ml\nSugar: 5 g',method:'Measure 25 ml prepared decoction, 75 ml milk and 5 g sugar. Combine and serve hot. Coffee decoction batch SOP: 250 g coffee powder + 1,100 ml water yields approximately 1,000 ml decoction; about 100 ml remains in the grounds.',notes:'Confirmed portion from Paanagam service notes. Decoction yield is approximate; record actual yield for production costing.'},
  {id:'ginger-tea',category:'Tea',name:'Ginger Tea',serving:'100 ml',status:'Source SOP',ingredients:'Tea powder: 3 g\nWater: 50 ml\nSugar: 4 g\nMilk: 50 ml\nGinger: 5 g',method:'Prepare tea base with specified ingredients. Add milk and sweetener, heat to serving condition without prolonged boiling, strain if required, and serve immediately.',notes:'Source: Paanagam Tea & Herbal Tea Kitchen SOP. Cost reference in source: ₹8.64 before GST; menu price ₹15.'},
  {id:'cardamom-tea',category:'Tea',name:'Cardamom Tea',serving:'100 ml',status:'Source SOP — verify water quantity',ingredients:'Tea powder: 3 g\nWater: 5 ml\nSugar: 4 g\nMilk: 60 ml\nCardamom: 2 g',method:'Prepare tea base. Add milk and sweetener, heat to serving condition without prolonged boiling, strain if required, and serve immediately.',notes:'Source document specifies 5 ml water. Unusual quantity retained exactly as written; verify before production.'},
  {id:'masala-tea',category:'Tea',name:'Masala Tea',serving:'100 ml',status:'Source SOP — verify quantities',ingredients:'Tea powder: 10 g\nMasala tea powder: 3 g\nWater: 5 ml\nSugar: 4 g\nMilk: 60 ml',method:'Prepare tea base. Add milk and sweetener, heat to serving condition without prolonged boiling, strain if required, and serve immediately.',notes:'Source quantities retained as written; verify tea powder and water quantities before production.'},
  {id:'dum-tea',category:'Tea',name:'Dum Tea',serving:'100 ml',status:'Source SOP — verify water quantity',ingredients:'Tea powder: 3 g\nWater: 6 ml\nSugar: 4 g\nMilk: 60 ml',method:'Prepare tea base, add milk and sweetener, heat to serving condition without prolonged boiling, strain if required, and serve immediately.',notes:'Source document specifies 6 ml water. Verify before production.'},
  {id:'black-tea',category:'Tea',name:'Black Tea',serving:'100 ml',status:'Source SOP — verify water quantity',ingredients:'Tea powder: 3 g\nWater: 10 ml\nSugar: 4 g',method:'Boil or steep tea powder with the specified water quantity, strain, add sugar and serve immediately.',notes:'Source quantities retained as written; verify water quantity before production.'},
  {id:'green-tea-sugar',category:'Herbal Tea',name:'Green Tea with Sugar',serving:'100 ml',status:'Source SOP',ingredients:'Green tea bag: 1 bag / 2 g\nWater: 100 ml\nSugar: 4 g',method:'Heat water to serving temperature. Place tea bag in cup or pot, add hot water and steep to the required strength. Add sugar, stir and serve immediately.',notes:'Source: Paanagam Tea & Herbal Tea Kitchen SOP.'},
  {id:'green-tea-honey',category:'Herbal Tea',name:'Green Tea with Honey',serving:'100 ml',status:'Source SOP — tea bag quantity marked approximate',ingredients:'Green tea bag: 1 bag / 1 g*\nWater: 100 ml\nHoney: 1 g',method:'Heat water to serving temperature. Steep tea bag to the required strength. Add honey, stir and serve immediately.',notes:'Asterisk/uncertainty retained from source; confirm tea bag weight.'},
  {id:'lemon-tea-sugar',category:'Tea',name:'Lemon Tea with Sugar',serving:'100 ml',status:'Source SOP — lemon quantity approximate',ingredients:'Tea powder: 3 g\nLemon: 0.004 unit (as sheet)\nWater: 100 ml\nSugar: 4 g',method:'Prepare tea base with hot water. Add lemon and sweetener after brewing, stir and serve immediately. Do not boil lemon juice.',notes:'Source uses 0.004 lemon unit; standardize actual juice quantity during recipe trial.'},
  {id:'lemon-tea-honey',category:'Tea',name:'Lemon Tea with Honey',serving:'100 ml',status:'Source SOP — lemon quantity approximate',ingredients:'Tea powder: 3 g\nLemon: 0.004 unit (as sheet)\nWater: 100 ml\nHoney: 1 g',method:'Prepare tea base with hot water. Add lemon and honey after brewing, stir and serve immediately. Do not boil lemon juice.',notes:'Source uses 0.004 lemon unit; standardize actual juice quantity during recipe trial.'},
  {id:'chamomile',category:'Herbal Tea',name:'Chamomile Herbal Tea',serving:'100 ml',status:'Source SOP — verify ingredients',ingredients:'Tea powder: 3 g*\nWater: 100 ml\nSugar: 4 g*',method:'Prepare as an herbal infusion using hot water and steep to the required strength. Sweeten only if approved for the final recipe.',notes:'Source marks tea powder and sugar with asterisks. Verify ingredient identity/quantities before production.'},
  {id:'sulaimani',category:'Tea',name:'Sulaimani Tea',serving:'Approx. 2 × 200 ml trial batch; final yield must be measured',status:'Trial — not yet approved for costing',ingredients:'Water: 500 ml\nFresh ginger: 1 inch piece (≈10 g)\nCinnamon: 2 inch piece (≈4 g)\nClove: 2 (≈0.4 g)\nStar anise: 1 (≈1.5 g)\nCardamom: 2 (≈0.4 g)\nBlack pepper: 5 (≈0.5 g)\nCoriander seeds: ¼ tsp (≈0.5 g)\nTulsi leaves: 5 (≈1 g)\nMint leaves: 5 (≈1 g)\nTea powder: 1 tsp (≈2 g)\nSugar: 1 tsp (≈4 g)\nLemon juice: add last; quantity not standardized',method:'1. Sanitize work area and equipment. 2. Lightly crush ginger and whole spices. 3. Add 500 ml water, ginger, cinnamon, cloves, star anise, cardamom, pepper and coriander. 4. Boil, then simmer 5–7 minutes. 5. Add tulsi and mint; simmer 1 minute. 6. Add tea powder and brew 2–3 minutes without over-boiling. 7. Add sugar and dissolve. 8. Strain. 9. Add lemon juice off heat; never boil lemon juice. 10. Check taste, aroma, colour and actual yield; serve hot.',notes:'Video-derived trial recipe. Source does not establish final yield or lemon quantity. Do not lock cost or per-100-ml recipe until taste test and measured yield are approved.'},
  {id:'chocolate-shake',category:'Milkshake',name:'Chocolate Shake',serving:'1 glass',status:'Source SOP — scoop weight to be locked',ingredients:'Chilled milk: 300 ml\nChocolate ice cream: 2 scoops\nCocoa powder: 1 tbsp (≈5 g)\nChocolate syrup: 2 tbsp (≈40 g)\nWhipped cream: topping\nChocolate shavings: garnish',method:'Blend milk, chocolate ice cream, cocoa and chocolate syrup until smooth. Pour into serving glass. Top with whipped cream and chocolate shavings. Serve immediately.',notes:'Weigh and lock ice-cream scoop size for exact costing. Dairy allergen.'},
  {id:'vanilla-shake',category:'Milkshake',name:'Vanilla Shake',serving:'1 glass',status:'Source SOP — scoop weight to be locked',ingredients:'Chilled milk: 300 ml\nVanilla ice cream: 2 scoops\nVanilla extract: 1 tsp (≈5 ml)\nSugar: 1 tbsp (≈12.5 g), optional\nWhipped cream: topping\nSprinkles: garnish',method:'Blend milk, vanilla ice cream, vanilla extract and optional sugar until smooth. Pour into glass. Top with whipped cream and sprinkles. Adjust sugar if ice cream is already sweet. Serve immediately.',notes:'Weigh and lock ice-cream scoop size. Dairy allergen.'},
  {id:'strawberry-shake',category:'Milkshake',name:'Strawberry Shake',serving:'1 glass',status:'Source SOP — scoop weight to be locked',ingredients:'Chilled milk: 300 ml\nStrawberry ice cream: 2 scoops\nFresh strawberries: 5–6, chopped\nSugar: 1 tbsp (≈12.5 g), optional\nWhipped cream: topping\nStrawberry slices: garnish',method:'Wash, sanitize, de-stem and chop strawberries. Blend milk, strawberry ice cream, strawberries and optional sugar. Pour into glass and top with whipped cream and strawberry slices. Serve immediately.',notes:'Weigh and lock scoop size. Dairy allergen.'},
  {id:'oreo-shake',category:'Milkshake',name:'Oreo Shake',serving:'1 glass',status:'Source SOP — scoop weight to be locked',ingredients:'Chilled milk: 300 ml\nVanilla ice cream: 2 scoops\nOreo biscuits: 4–5\nChocolate syrup: 1 tbsp (≈20 g)\nWhipped cream: topping\nCrushed Oreo: garnish',method:'Break biscuits into pieces. Blend milk, vanilla ice cream, Oreo and chocolate syrup until smooth with fine cookie texture. Pour into glass, top with whipped cream and crushed Oreo. Serve immediately.',notes:'Dairy; Oreo may contain wheat, soy and other allergens depending on supplier.'},
  {id:'peanut-shake',category:'Milkshake',name:'Peanut Butter Shake',serving:'1 glass',status:'Source SOP — scoop weight to be locked',ingredients:'Chilled milk: 300 ml\nVanilla ice cream: 2 scoops\nPeanut butter: 2 tsp (≈32 g as source states)\nHoney: 1 tbsp (≈21 g), optional\nCrushed peanuts: 1 tbsp (≈8 g)\nWhipped cream: topping',method:'Blend milk, vanilla ice cream, peanut butter and optional honey until smooth. Pour into glass and top with whipped cream and crushed peanuts. Serve immediately.',notes:'Peanut allergen and dairy. The source tsp-to-gram conversion should be validated by weighing the actual product before costing.'}
];

const INITIAL_INVENTORY = [
  { id: 'coffee-powder', name: 'Coffee powder', unit: 'g', onHand: 0, lots: [] },
  { id: 'water', name: 'Water', unit: 'ml', onHand: 0, lots: [] },
  { id: 'decoction', name: 'Prepared decoction', unit: 'ml', onHand: 0, lots: [] },
  { id: 'milk', name: 'Milk', unit: 'ml', onHand: 0, lots: [] },
  { id: 'sugar', name: 'Sugar', unit: 'g', onHand: 0, lots: [] }
];
const INITIAL_SECONDARY_RECIPES = [{ id: 'decoction-bom', name: 'Coffee Decoction', outputInventoryId: 'decoction', yieldQty: 1000, inputs: [{ inventoryId: 'coffee-powder', qty: 250 }, { inventoryId: 'water', qty: 1100 }] }];
const KEY = 'paanagam.pos.v1';
const DEFAULT_PIN = '1234';
const CLOUD_CONFIG_KEY = 'paanagam.cloud.v1';
const readCloudConfig = () => { try { return JSON.parse(localStorage.getItem(CLOUD_CONFIG_KEY) || '{"endpoint":"","token":"","autoSync":true}'); } catch { return { endpoint: '', token: '', autoSync: true }; } };
const money = n => '₹' + Number(n || 0).toFixed(2).replace(/\.00$/, '');
function getDeviceId() { try { let id = localStorage.getItem('paanagam.device.id'); if (!id) { id = 'PG-DEVICE-' + (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random().toString(36).slice(2)); localStorage.setItem('paanagam.device.id', id); } return id; } catch { return 'PG-DEVICE-LOCAL'; } }
function readStore() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || '{}');
    const inventory = Array.isArray(saved.inventory)
      ? saved.inventory.map(item => ({ ...item, onHand: Math.max(0, Number(item.onHand) || 0), lots: Array.isArray(item.lots) ? item.lots : [] }))
      : INITIAL_INVENTORY.map(item => ({ ...item, lots: [] }));
    return {
      menu: Array.isArray(saved.menu) ? saved.menu : initialMenu,
      sales: Array.isArray(saved.sales) ? saved.sales : [],
      pin: saved.pin || DEFAULT_PIN,
      loyalty: saved.loyalty || {},
      inventory,
      recipes: saved.recipes && typeof saved.recipes === 'object' ? saved.recipes : INITIAL_RECIPES,
      secondaryRecipes: Array.isArray(saved.secondaryRecipes) && saved.secondaryRecipes.length ? saved.secondaryRecipes : INITIAL_SECONDARY_RECIPES,
      stockMovements: Array.isArray(saved.stockMovements) ? saved.stockMovements : [],
      nextLotId: Number.isInteger(saved.nextLotId) && saved.nextLotId > 0 ? saved.nextLotId : 1
    };
  } catch {
    return {
      menu: initialMenu, sales: [], pin: DEFAULT_PIN, loyalty: {},
      inventory: INITIAL_INVENTORY.map(item => ({ ...item, lots: [] })),
      recipes: INITIAL_RECIPES, secondaryRecipes: INITIAL_SECONDARY_RECIPES, stockMovements: [], nextLotId: 1
    };
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
  const [cloudConfig, setCloudConfig] = useState(readCloudConfig);
  const [cloudStatus, setCloudStatus] = useState('Cloud backup not configured');
  const [cloudBusy, setCloudBusy] = useState(false);
  const [stockForm, setStockForm] = useState({ ingredientId: 'coffee-powder', qty: '', totalCost: '', supplier: '' });
  const [stockOutForm, setStockOutForm] = useState({ ingredientId: 'coffee-powder', qty: '' });
  const [recipeProduct, setRecipeProduct] = useState('filter-coffee');
  const [recipeForm, setRecipeForm] = useState({ ingredient: 'Prepared decoction', qty: '25', unit: 'ml', note: '' });
  const [prepBatches, setPrepBatches] = useState('1');
  const [secondaryRecipeId, setSecondaryRecipeId] = useState('decoction-bom');
  const [secondaryDraft, setSecondaryDraft] = useState({ name: '', outputInventoryId: 'decoction', yieldQty: '1000', inputInventoryId: 'coffee-powder', inputQty: '250', secondInputInventoryId: 'water', secondInputQty: '1100' });
  const [inventoryTab, setInventoryTab] = useState('Stock');
  const [sopSearch, setSopSearch] = useState('');
  const [sopCategory, setSopCategory] = useState('All');
  const [selectedSop, setSelectedSop] = useState('filter-coffee');
  const [loyaltyCustomer, setLoyaltyCustomer] = useState('');
  const [reconcileForm, setReconcileForm] = useState({ ingredientId: 'coffee-powder', physicalQty: '', note: '' });
  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(store)); } catch { setMessage('Storage is full. Export a backup and clear old sales.'); }
  }, [store]);
  useEffect(() => { try { localStorage.setItem(CLOUD_CONFIG_KEY, JSON.stringify(cloudConfig)); } catch {} }, [cloudConfig]);
  useEffect(() => {
    if (!cloudConfig.autoSync || !cloudConfig.endpoint.trim() || !cloudConfig.token.trim()) { setCloudStatus(cloudConfig.endpoint.trim() ? 'Cloud sync needs endpoint and token' : 'Local-only · cloud endpoint not configured'); return; }
    if (!navigator.onLine) { setCloudStatus('Offline · changes saved locally; cloud sync waiting'); return; }
    setCloudStatus('Local data saved · cloud sync queued');
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(cloudConfig.endpoint.trim(), { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + cloudConfig.token.trim() }, body: JSON.stringify({ app: 'paanagam-pos-android', schema: 1, deviceId: getDeviceId(), updatedAt: new Date().toISOString(), payload: store }) });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        setCloudStatus('Cloud backup synced · ' + new Date().toLocaleTimeString());
      } catch (error) { setCloudStatus('Cloud sync failed (' + error.message + ') · local data is safe'); }
    }, 1800);
    return () => window.clearTimeout(timer);
  }, [store, cloudConfig]);
  useEffect(() => {
    const online = () => setCloudStatus('Online · pending cloud sync');
    const offline = () => setCloudStatus('Offline · changes remain saved locally');
    window.addEventListener('online', online); window.addEventListener('offline', offline);
    return () => { window.removeEventListener('online', online); window.removeEventListener('offline', offline); };
  }, []);
  const categories = useMemo(() => ['All', ...new Set(store.menu.filter(m => m.active).map(m => m.category))], [store.menu]);
  const visibleMenu = store.menu.filter(m => m.active && (category === 'All' || m.category === category));
  const total = cart.reduce((sum, item) => sum + item.price * item.qty, 0);
  const today = new Date().toLocaleDateString();
  const todaySales = store.sales.filter(s => new Date(s.createdAt).toLocaleDateString() === today);
  const todayTotal = todaySales.reduce((s, x) => s + x.total, 0);
  const todayCash = todaySales.filter(s => s.payment === 'Cash').reduce((sum, s) => sum + Number(s.total || 0), 0);
  const todayUpi = todaySales.filter(s => s.payment === 'UPI').reduce((sum, s) => sum + Number(s.total || 0), 0);
  const todayProductTotals = todaySales.reduce((acc, sale) => {
    for (const item of (sale.items || [])) {
      const key = item.name || 'Unnamed item';
      if (!acc[key]) acc[key] = { qty: 0, amount: 0 };
      acc[key].qty += Number(item.qty) || 0;
      acc[key].amount += (Number(item.qty) || 0) * (Number(item.price) || 0);
    }
    return acc;
  }, {});
  const todayProductRows = Object.entries(todayProductTotals).sort((a, b) => b[1].qty - a[1].qty);

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
    const hasFreeCoffee = cart.some(item => item.id === 'loyalty-free-coffee' && item.qty > 0);
    if (hasFreeCoffee && (!loyaltyCustomer.trim() || Number(store.loyalty?.[loyaltyCustomer.trim()]?.rewards || 0) < cart.filter(item => item.id === 'loyalty-free-coffee').reduce((n,item) => n + item.qty, 0))) return flash('Enter the same customer ID and confirm available free coffee rewards before checkout.');

    // Validate every master recipe before allowing a sale; do not silently skip unknown ingredients.
    const demand = new Map();
    for (const sold of cart) {
      const recipeId = sold.id === 'loyalty-free-coffee' ? 'filter-coffee' : sold.id;
      const recipe = store.recipes?.[recipeId] || [];
      if (!recipe.length) return flash('Master recipe missing for ' + sold.name + '. Configure it under Stock → Recipes before selling.');
      for (const line of recipe) {
        const qtyPerItem = Number(line.qty);
        const item = store.inventory.find(x => x.name.toLowerCase() === String(line.ingredient).toLowerCase());
        if (!item) return flash('Recipe ingredient "' + String(line.ingredient) + '" is missing from stock. Fix the master recipe before checkout.');
        if (!Number.isFinite(qtyPerItem) || qtyPerItem <= 0) return flash('Invalid recipe quantity for ' + item.name + '. Fix the master recipe before checkout.');
        if (String(line.unit) !== String(item.unit)) return flash('Recipe unit mismatch for ' + item.name + ': recipe uses ' + line.unit + ', stock uses ' + item.unit + '.');
        const qty = qtyPerItem * sold.qty;
        demand.set(item.id, (demand.get(item.id) || 0) + qty);
      }
    }
    for (const [itemId, required] of demand) {
      const item = store.inventory.find(x => x.id === itemId);
      const lotAvailable = (item.lots || []).reduce((sum, lot) => sum + Math.max(0, Number(lot.remaining) || 0), 0);
      const available = Math.min(Math.max(0, Number(item.onHand) || 0), lotAvailable);
      if (required > available + 0.00001) {
        return flash('Cannot complete sale: ' + item.name + ' needs ' + required + ' ' + item.unit + ', FIFO lots have ' + available + '. Receive/reconcile stock first.');
      }
    }

    const customerKey = loyaltyCustomer.trim();
    const earnedStamps = customerKey ? cart.reduce((sum, item) => sum + (store.menu.find(m => m.id === item.id)?.category === 'Coffee' && item.price > 0 ? item.qty : 0), 0) : 0;
    const sale = { id: 'PG-' + Date.now().toString().slice(-8), createdAt: new Date().toISOString(), items: cart.map(x => ({ ...x })), total, payment, customerKey: customerKey || undefined, loyaltyStampsEarned: earnedStamps };
    setStore(curr => {
      const inventory = curr.inventory.map(item => ({ ...item, lots: (item.lots || []).map(lot => ({ ...lot })) }));
      const movements = [];
      for (const sold of sale.items) {
        const recipeId = sold.id === 'loyalty-free-coffee' ? 'filter-coffee' : sold.id;
        for (const line of (curr.recipes?.[recipeId] || [])) {
          const requested = Number(line.qty) * sold.qty;
          const target = inventory.find(x => x.name.toLowerCase() === String(line.ingredient).toLowerCase());
          if (!target || !Number.isFinite(requested) || requested <= 0) continue;
          const result = consumeFromLots(target, requested);
          movements.push({ id: 'MV-' + Date.now() + '-' + movements.length, at: new Date().toISOString(), type: 'SALE_CONSUMPTION', saleId: sale.id, product: sold.name, ingredient: target.name, qty: result.fulfilled, requestedQty: requested, unit: target.unit, lotUses: result.lotUses, cost: result.cost, insufficient: result.remaining > 0, missingQty: result.remaining, note: result.remaining > 0 ? 'STOCK SHORTAGE: sale recorded; receive/reconcile stock and review missing quantity. FIFO costing is partial.' : 'Consumed oldest purchase lots first; unit prices are not averaged.' });
        }
      }
      const loyalty = { ...(curr.loyalty || {}) };
      if (customerKey) {
        const prior = loyalty[customerKey] || { stamps: 0, rewards: 0 };
        const combined = Math.max(0, Number(prior.stamps) || 0) + earnedStamps;
        const redeemed = sale.items.filter(item => item.id === 'loyalty-free-coffee').reduce((n,item) => n + item.qty, 0);
        loyalty[customerKey] = { stamps: combined % 10, rewards: Math.max(0, Number(prior.rewards) || 0) + Math.floor(combined / 10) - redeemed };
      }
      return { ...curr, loyalty, sales: [sale, ...curr.sales], inventory, stockMovements: [...movements.reverse(), ...(curr.stockMovements || [])] };
    });
    setCart([]);
    setPage('POS');
    if (customerKey) setLoyaltyCustomer('');
    flash('Sale saved on this device: ' + sale.id + (earnedStamps ? ' · ' + earnedStamps + ' coffee stamp(s) added.' : ''));
  }
  async function syncCloudNow() {
    if (!cloudConfig.endpoint.trim() || !cloudConfig.token.trim()) return flash('Set the secured cloud endpoint and bearer token first.');
    if (!navigator.onLine) return flash('Device is offline. Data remains saved locally and will sync when online.');
    setCloudBusy(true); setCloudStatus('Syncing local data to cloud…');
    try {
      const response = await fetch(cloudConfig.endpoint.trim(), { method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + cloudConfig.token.trim() }, body: JSON.stringify({ app: 'paanagam-pos-android', schema: 1, deviceId: getDeviceId(), updatedAt: new Date().toISOString(), payload: store }) });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      setCloudStatus('Cloud backup synced · ' + new Date().toLocaleString()); flash('Cloud backup completed.');
    } catch (error) { setCloudStatus('Cloud sync failed (' + error.message + ') · local data is safe'); flash('Cloud backup failed. Check endpoint/token; local data is safe.'); }
    finally { setCloudBusy(false); }
  }
  async function restoreCloudNow() {
    if (!cloudConfig.endpoint.trim() || !cloudConfig.token.trim()) return flash('Set the secured cloud endpoint and bearer token first.');
    if (!navigator.onLine) return flash('Cloud restore requires internet.');
    if (!window.confirm('Restore cloud data to this device? Current local data will be replaced. Export a local backup first.')) return;
    setCloudBusy(true);
    try {
      const url = new URL(cloudConfig.endpoint.trim()); url.searchParams.set('deviceId', getDeviceId());
      const response = await fetch(url.toString(), { headers: { 'Authorization': 'Bearer ' + cloudConfig.token.trim() } });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const data = await response.json(); const parsed = data.payload || data;
      if (!Array.isArray(parsed.menu) || !Array.isArray(parsed.sales) || !Array.isArray(parsed.inventory)) throw new Error('Cloud response must include menu, sales and inventory payload.');
      setStore({ ...readStore(), ...parsed }); setCloudStatus('Cloud data restored · ' + new Date().toLocaleString()); flash('Cloud backup restored to this device.');
    } catch (error) { setCloudStatus('Cloud restore failed (' + error.message + ')'); flash('Cloud restore failed. Check the endpoint response format.'); }
    finally { setCloudBusy(false); }
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
      if (parsed.inventory != null && !Array.isArray(parsed.inventory)) throw new Error('Backup inventory must be an array.');
      if (parsed.stockMovements != null && !Array.isArray(parsed.stockMovements)) throw new Error('Backup stock movements must be an array.');
      setStore({
        menu: parsed.menu, sales: parsed.sales, pin: parsed.pin || store.pin, loyalty: parsed.loyalty || {},
        inventory: Array.isArray(parsed.inventory) ? parsed.inventory : store.inventory,
        recipes: parsed.recipes && typeof parsed.recipes === 'object' ? parsed.recipes : store.recipes,
        secondaryRecipes: Array.isArray(parsed.secondaryRecipes) ? parsed.secondaryRecipes : store.secondaryRecipes,
        stockMovements: Array.isArray(parsed.stockMovements) ? parsed.stockMovements : store.stockMovements,
        nextLotId: Number(parsed.nextLotId) || store.nextLotId
      });
      setRestoreText(''); flash('Backup restored to this device.');
    } catch (e) { flash('Restore failed: ' + e.message); }
  }
  function unlockAdmin() {
    if (pinEntry === store.pin) { setAdminOpen(true); setPinEntry(''); setPage('Admin'); }
    else flash('Incorrect admin PIN.');
  }
  function receiveStock() {
    const qty = Number(stockForm.qty);
    const totalCost = Number(stockForm.totalCost);
    const target = store.inventory.find(x => x.id === stockForm.ingredientId);
    if (!target || !Number.isFinite(qty) || qty <= 0 || !Number.isFinite(totalCost) || totalCost < 0) return flash('Enter a valid quantity and total purchase cost.');
    const lot = { id: 'LOT-' + (store.nextLotId || 1), receivedAt: new Date().toISOString(), qty, remaining: qty, unitCost: totalCost / qty, totalCost, supplier: stockForm.supplier.trim() || 'Not specified' };
    setStore(curr => ({
      ...curr,
      inventory: curr.inventory.map(x => x.id === target.id ? { ...x, onHand: Number((x.onHand + qty).toFixed(4)), lots: [...(x.lots || []), lot] } : x),
      stockMovements: [{ id: 'MV-' + Date.now(), at: new Date().toISOString(), type: 'PURCHASE', ingredient: target.name, qty, unit: target.unit, unitCost: lot.unitCost, totalCost, lotId: lot.id, supplier: lot.supplier }, ...(curr.stockMovements || [])],
      nextLotId: (curr.nextLotId || 1) + 1
    }));
    setStockForm(x => ({ ...x, qty: '', totalCost: '', supplier: '' }));
    flash('Stock receipt saved as a separate price lot.');
  }
  function consumeFromLots(item, requested) {
    let remaining = requested;
    const lotUses = [];
    // Enforce FIFO by receipt timestamp even if a restored backup has lots out of order.
    const fifoLots = [...(item.lots || [])].sort((a, b) => {
      const ta = Date.parse(a.receivedAt || '') || 0;
      const tb = Date.parse(b.receivedAt || '') || 0;
      return ta - tb;
    });
    for (const lot of fifoLots) {
      const available = Math.max(0, Number(lot.remaining) || 0);
      const take = Math.min(available, remaining);
      if (take > 0) {
        lot.remaining = Number((available - take).toFixed(4));
        remaining = Number((remaining - take).toFixed(4));
        lotUses.push({ lotId: lot.id, qty: take, unitCost: Number(lot.unitCost) || 0, cost: take * (Number(lot.unitCost) || 0) });
      }
      if (remaining <= 0) break;
    }
    const fulfilled = Number((requested - remaining).toFixed(4));
    item.onHand = Math.max(0, Number((item.onHand - fulfilled).toFixed(4)));
    return { fulfilled, remaining, lotUses, cost: lotUses.reduce((sum, x) => sum + x.cost, 0) };
  }
  function recordStockOut() {
    const qty = Number(stockOutForm.qty);
    const target = store.inventory.find(x => x.id === stockOutForm.ingredientId);
    if (!target || !Number.isFinite(qty) || qty <= 0) return flash('Enter a valid stock-out quantity.');
    const lotAvailable = (target.lots || []).reduce((sum, lot) => sum + Math.max(0, Number(lot.remaining) || 0), 0);
    const available = Math.min(Math.max(0, Number(target.onHand) || 0), lotAvailable);
    if (qty > available + 0.00001) return flash('Stock-out exceeds available FIFO lot balance (' + available + ' ' + target.unit + '). Reconcile stock first.');
    setStore(curr => {
      const inventory = curr.inventory.map(x => ({ ...x, lots: (x.lots || []).map(l => ({ ...l })) }));
      const item = inventory.find(x => x.id === target.id);
      const result = consumeFromLots(item, qty);
      return { ...curr, inventory, stockMovements: [{ id: 'MV-' + Date.now(), at: new Date().toISOString(), type: 'MANUAL_STOCK_OUT', ingredient: item.name, qty: result.fulfilled, unit: item.unit, lotUses: result.lotUses, cost: result.cost, note: 'Manual stock-out recorded FIFO.' }, ...(curr.stockMovements || [])] };
    });
    setStockOutForm(x => ({ ...x, qty: '' }));
    flash('Stock-out recorded.');
  }
  function recordReconciliation() {
    const physicalQty = Number(reconcileForm.physicalQty);
    const target = store.inventory.find(x => x.id === reconcileForm.ingredientId);
    if (!target || reconcileForm.physicalQty === '' || !Number.isFinite(physicalQty) || physicalQty < 0) return flash('Enter a valid physical count (zero or higher).');
    const recordedQty = Math.max(0, Number(target.onHand) || 0);
    const variance = Number((physicalQty - recordedQty).toFixed(4));
    if (Math.abs(variance) < 0.00001) return flash('Physical count matches recorded stock. No adjustment needed.');
    const at = new Date().toISOString();
    setStore(curr => {
      const inventory = curr.inventory.map(x => ({ ...x, lots: (x.lots || []).map(l => ({ ...l })) }));
      const item = inventory.find(x => x.id === target.id);
      let lotUses = [];
      let adjustmentLotId;
      if (variance < 0) {
        const result = consumeFromLots(item, Math.abs(variance));
        lotUses = result.lotUses;
      } else {
        // Physical surplus has no verified purchase price; keep it separate and do not average costs.
        adjustmentLotId = 'ADJ-' + Date.now();
        item.lots = [...(item.lots || []), { id: adjustmentLotId, receivedAt: at, qty: variance, remaining: variance, unitCost: 0, totalCost: 0, supplier: 'Physical reconciliation — cost unknown', reconciliationAdjustment: true }];
        item.onHand = Number((recordedQty + variance).toFixed(4));
      }
      item.onHand = Number(physicalQty.toFixed(4));
      const movement = { id: 'MV-' + Date.now(), at, type: 'RECONCILIATION', ingredient: item.name, unit: item.unit, recordedQty, physicalQty, variance, qty: Math.abs(variance), lotUses, adjustmentLotId, note: reconcileForm.note.trim() || (variance > 0 ? 'Physical surplus; cost unknown and not averaged into purchase lots.' : 'Physical shortage removed from oldest FIFO lots.') };
      return { ...curr, inventory, stockMovements: [movement, ...(curr.stockMovements || [])] };
    });
    setReconcileForm(x => ({ ...x, physicalQty: '', note: '' }));
    flash('Physical stock reconciliation saved with variance history.');
  }
  function saveSecondaryRecipe() {
    const name = secondaryDraft.name.trim();
    const yieldQty = Number(secondaryDraft.yieldQty);
    const inputQty = Number(secondaryDraft.inputQty);
    const secondInputQty = Number(secondaryDraft.secondInputQty);
    const output = store.inventory.find(x => x.id === secondaryDraft.outputInventoryId);
    const first = store.inventory.find(x => x.id === secondaryDraft.inputInventoryId);
    const second = secondaryDraft.secondInputInventoryId ? store.inventory.find(x => x.id === secondaryDraft.secondInputInventoryId) : null;
    if (!name || !output || !first || !Number.isFinite(yieldQty) || yieldQty <= 0 || !Number.isFinite(inputQty) || inputQty <= 0) return flash('Enter a recipe name, output yield and valid first input.');
    if (first.id === output.id || (second && second.id === output.id)) return flash('Recipe output cannot also be one of its own inputs.');
    if (second && (!Number.isFinite(secondInputQty) || secondInputQty <= 0)) return flash('Check the second input quantity.');
    const inputs = [{ inventoryId: first.id, qty: inputQty }];
    if (second && secondInputQty > 0) inputs.push({ inventoryId: second.id, qty: secondInputQty });
    const recipe = { id: 'bom-' + Date.now(), name, outputInventoryId: output.id, yieldQty, inputs };
    setStore(curr => ({ ...curr, secondaryRecipes: [...(curr.secondaryRecipes || []), recipe] }));
    setSecondaryRecipeId(recipe.id);
    setSecondaryDraft(x => ({ ...x, name: '' }));
    flash('Secondary recipe/BOM saved. It is ready for batch production.');
  }
  function prepareDecoction() {
    const recipe = (store.secondaryRecipes || INITIAL_SECONDARY_RECIPES).find(x => x.id === secondaryRecipeId);
    const batches = Number(prepBatches);
    if (!recipe) return flash('Select a secondary recipe first.');
    if (!Number.isInteger(batches) || batches < 1 || batches > 20) return flash('Choose between 1 and 20 batches.');
    const output = store.inventory.find(x => x.id === recipe.outputInventoryId);
    if (!output || !Array.isArray(recipe.inputs) || !recipe.inputs.length) return flash('BOM output or input is missing. Check the recipe.');
    for (const input of recipe.inputs) {
      const item = store.inventory.find(x => x.id === input.inventoryId);
      const required = Number(input.qty) * batches;
      const lotAvailable = item ? (item.lots || []).reduce((sum, lot) => sum + Math.max(0, Number(lot.remaining) || 0), 0) : 0;
      if (!item || !Number.isFinite(required) || required <= 0 || item.id === output.id || item.onHand + 0.00001 < required || lotAvailable + 0.00001 < required) return flash('Insufficient FIFO stock for ' + (item?.name || 'BOM input') + '. No batch was recorded.');
    }
    setStore(curr => {
      const inventory = curr.inventory.map(x => ({ ...x, lots: (x.lots || []).map(l => ({ ...l })) }));
      const outputItem = inventory.find(x => x.id === recipe.outputInventoryId);
      const when = new Date().toISOString();
      const movements = [];
      let totalCost = 0;
      for (const input of recipe.inputs) {
        const item = inventory.find(x => x.id === input.inventoryId);
        const required = Number(input.qty) * batches;
        const result = consumeFromLots(item, required);
        if (result.remaining > 0.00001) return curr;
        totalCost += result.cost;
        movements.push({ id: 'MV-' + Date.now() + '-' + movements.length, at: when, type: 'BOM_INPUT', recipe: recipe.name, ingredient: item.name, qty: result.fulfilled, unit: item.unit, lotUses: result.lotUses, cost: result.cost, batchCount: batches, note: 'Consumed FIFO input lots for secondary recipe production.' });
      }
      const outputQty = Number(recipe.yieldQty) * batches;
      const lot = { id: 'LOT-' + (curr.nextLotId || 1), receivedAt: when, qty: outputQty, remaining: outputQty, unitCost: totalCost / outputQty, totalCost, supplier: 'Produced: ' + recipe.name, batchCount: batches, secondaryRecipeId: recipe.id, inputCosts: totalCost };
      outputItem.onHand = Number((outputItem.onHand + outputQty).toFixed(4));
      outputItem.lots = [...(outputItem.lots || []), lot];
      movements.push({ id: 'MV-' + Date.now() + '-output', at: when, type: 'BOM_OUTPUT', recipe: recipe.name, ingredient: outputItem.name, qty: outputQty, unit: outputItem.unit, lotId: lot.id, unitCost: lot.unitCost, totalCost, batchCount: batches, note: 'Secondary recipe yield added as a separate costed output lot.' });
      return { ...curr, inventory, stockMovements: [...movements, ...(curr.stockMovements || [])], nextLotId: (curr.nextLotId || 1) + 1 };
    });
    flash('Production recorded: ' + recipe.name + '. Output is costed from FIFO input lots.');
  }
    function saveRecipeLine() {
    const qty = Number(recipeForm.qty);
    if (!recipeForm.ingredient.trim() || !Number.isFinite(qty) || qty <= 0) return flash('Enter an ingredient and a quantity above zero.');
    const stockItem = store.inventory.find(x => x.name.toLowerCase() === recipeForm.ingredient.trim().toLowerCase());
    if (!stockItem) return flash('Ingredient is missing from the inventory ledger.');
    if (stockItem.unit !== recipeForm.unit) return flash('Recipe unit must match stock unit: ' + stockItem.unit + '.');
    const line = { ingredient: stockItem.name, qty, unit: recipeForm.unit, note: recipeForm.note.trim() };
    setStore(curr => ({ ...curr, recipes: { ...(curr.recipes || INITIAL_RECIPES), [recipeProduct]: [...(curr.recipes?.[recipeProduct] || []), line] } }));
    setRecipeForm(x => ({ ...x, qty: '', note: '' }));
    flash('Recipe line added. Future sales will use it.');
  }
  function deleteRecipeLine(productId, index) {
    setStore(curr => ({ ...curr, recipes: { ...(curr.recipes || INITIAL_RECIPES), [productId]: (curr.recipes?.[productId] || []).filter((_, i) => i !== index) } }));
    flash('Recipe line removed.');
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
      <button className={page === 'Closing' ? 'selected' : ''} onClick={() => setPage('Closing')}>▣ <span>Day Close</span></button>
      <button className={page === 'Inventory' ? 'selected' : ''} onClick={() => setPage('Inventory')}>▦ <span>Stock</span></button>
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
        <div className="loyalty-panel">
          <label>Customer mobile or ID (optional)<input inputMode="tel" value={loyaltyCustomer} onChange={e => setLoyaltyCustomer(e.target.value)} placeholder="Enter customer ID for coffee rewards" /></label>
          {loyaltyCustomer.trim() && <div className="loyalty-status">
            <span>{Math.max(0, Number(store.loyalty?.[loyaltyCustomer.trim()]?.stamps) || 0)}/10 coffee stamps</span>
            <strong>{Math.max(0, Number(store.loyalty?.[loyaltyCustomer.trim()]?.rewards) || 0)} free coffee reward(s)</strong>
            {Array.from({length:10}, (_,i) => <i key={i} className={i < (Math.max(0, Number(store.loyalty?.[loyaltyCustomer.trim()]?.stamps) || 0)) ? 'stamp filled' : 'stamp'} />)}
            {Number(store.loyalty?.[loyaltyCustomer.trim()]?.rewards) > 0 && <button className="outline-button small" onClick={() => {
              const key = loyaltyCustomer.trim();
              setCart(curr => { const found=curr.find(x=>x.id==='loyalty-free-coffee'); return found ? curr.map(x=>x.id==='loyalty-free-coffee'?{...x,qty:x.qty+1}:x) : [...curr,{id:'loyalty-free-coffee',name:'Loyalty Free Coffee',price:0,qty:1}]; });
              flash('Free coffee added to the bill.');
            }}>Redeem 1 free coffee</button>}
          </div>}
          <small>Buy 10 paid coffee drinks to earn 1 free coffee. Customer ID is stored on this device only.</small>
        </div>
        <div className="bill-footer">
          <div className="subtotal"><span>Total</span><strong>{money(total)}</strong></div>
          <div className="pay-choice"><button className={payment === 'Cash' ? 'pay-active' : ''} onClick={() => setPayment('Cash')}>💵 Cash</button><button className={payment === 'UPI' ? 'pay-active' : ''} onClick={() => setPayment('UPI')}>▦ UPI</button></div>
          <button className="checkout" onClick={checkout} disabled={!cart.length}>Complete sale · {money(total)}</button>
          <p className="local-note">Sales are saved on this device.</p>
        </div>
      </aside>
    </main>}

    {page === 'Closing' && <main className="page-content">
      <div className="section-heading"><div><h1>Day Closing Dashboard</h1><p>{new Date().toLocaleDateString()} · Completed bills saved on this device</p></div><button className="outline-button" onClick={exportBackup}>Export backup</button></div>
      <div className="sales-summary">
        <div><span>Total collected</span><strong>{money(todayTotal)}</strong></div>
        <div><span>Cash</span><strong>{money(todayCash)}</strong></div>
        <div><span>UPI</span><strong>{money(todayUpi)}</strong></div>
        <div><span>Completed bills</span><strong>{todaySales.length}</strong></div>
      </div>
      <section className="admin-card">
        <div className="admin-card-heading"><div><h2>Product-wise sales</h2><p>Quantity sold and sales amount for today</p></div><button className="outline-button small" onClick={() => {
          const rows = [['Date', new Date().toLocaleDateString()], ['Total collected', todayTotal], ['Cash', todayCash], ['UPI', todayUpi], ['Bills', todaySales.length], [], ['Product', 'Quantity', 'Sales amount'], ...todayProductRows.map(([name, data]) => [name, data.qty, data.amount])];
          const csv = rows.map(row => row.map(value => '"' + String(value ?? '').replace(/"/g, '""') + '"').join(',')).join('\\r\\n');
          const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a'); a.href = url; a.download = 'paanagam-daily-close-' + new Date().toISOString().slice(0, 10) + '.csv'; a.click();
          URL.revokeObjectURL(url);
          flash('Daily closing CSV export started.');
        }}>Export CSV</button></div>
        {todayProductRows.length === 0 ? <p className="local-note">No completed bills for today yet. Complete a sale from POS to populate this report.</p> : <div className="sales-list">{todayProductRows.map(([name, data]) => <article className="sale-row" key={name}><div><strong>{name}</strong><span>{data.qty} sold today</span></div><div className="sale-right"><strong>{money(data.amount)}</strong></div></article>)}</div>}
        <p className="local-note">Cash/UPI totals include completed sales only. This dashboard is local to this device; it does not sync to the cloud.</p>
      </section>
    </main>}

    {page === 'Sales' && <main className="page-content">
      <div className="section-heading"><div><h1>Sales history</h1><p>Stored locally on this device</p></div><button className="outline-button" onClick={exportBackup}>Export backup</button></div>
      <div className="sales-summary"><div><span>Today's collected</span><strong>{money(todayTotal)}</strong></div><div><span>Today's bills</span><strong>{todaySales.length}</strong></div></div>
      <section className="admin-card">
        <div className="admin-card-heading"><div><h2>Daily closing report</h2><p>{new Date().toLocaleDateString()} · Based on saved bills on this device</p></div><button className="outline-button small" onClick={() => {
          const rows = [['Date', new Date().toLocaleDateString()], ['Total collected', todayTotal], ['Cash', todayCash], ['UPI', todayUpi], ['Bills', todaySales.length], [], ['Product', 'Quantity', 'Sales amount'], ...todayProductRows.map(([name, data]) => [name, data.qty, data.amount])];
          const csv = rows.map(row => row.map(value => '"' + String(value ?? '').replace(/"/g, '""') + '"').join(',')).join('\\r\\n');
          const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a'); a.href = url; a.download = 'paanagam-daily-close-' + new Date().toISOString().slice(0, 10) + '.csv'; a.click();
          URL.revokeObjectURL(url);
          flash('Daily closing CSV export started.');
        }}>Export CSV</button></div>
        <div className="sales-summary">
          <div><span>Cash</span><strong>{money(todayCash)}</strong></div>
          <div><span>UPI</span><strong>{money(todayUpi)}</strong></div>
          <div><span>Total</span><strong>{money(todayTotal)}</strong></div>
        </div>
        {todayProductRows.length === 0 ? <p className="local-note">No completed bills for today yet.</p> : <div className="sales-list">{todayProductRows.map(([name, data]) => <article className="sale-row" key={name}><div><strong>{name}</strong><span>{data.qty} sold today</span></div><div className="sale-right"><strong>{money(data.amount)}</strong></div></article>)}</div>}
        <p className="local-note">Closing figures include completed sales only. This report is stored locally and does not sync to the cloud.</p>
      </section>
      {store.sales.length === 0 ? <div className="empty-state"><div>📒</div><h3>No sales recorded yet</h3><p>Completed POS bills appear here.</p></div> : <div className="sales-list">{store.sales.map(sale => <article className="sale-row" key={sale.id}><div><strong>{sale.id}</strong><span>{new Date(sale.createdAt).toLocaleString()}</span><small>{sale.items.map(i => i.name + ' ×' + i.qty).join(', ')}</small>{sale.customerKey && <small>Loyalty ID: {sale.customerKey} · stamps earned: {sale.loyaltyStampsEarned || 0}</small>}</div><div className="sale-right"><strong>{money(sale.total)}</strong><span className="payment-label">{sale.payment}</span></div></article>)}</div>}
    </main>}

    {page === 'Inventory' && <main className="page-content">
      <div className="section-heading"><div><h1>Stock & Kitchen</h1><p>Separate purchase lots · FIFO costing · local offline ledger</p></div><button className="outline-button" onClick={exportBackup}>Export backup</button></div>
      <div className="category-row">
        {['Stock','Purchases','Decoction','Recipes','SOP Library','Stock out','Movements'].map(tab => <button key={tab} className={inventoryTab === tab ? 'chip active' : 'chip'} onClick={() => setInventoryTab(tab)}>{tab}</button>)}
      </div>
      {inventoryTab === 'Stock' && <section className="admin-card"><h2>Current ingredient stock</h2><p>Recorded quantity is shown alongside the sum of remaining FIFO lots. Investigate any difference before live trading.</p>
        <div className="sales-list">{store.inventory.map(item => <article className="sale-row" key={item.id}><div><strong>{item.name}</strong><span>Recorded: {Number(item.onHand || 0).toFixed(3).replace(/\.?0+$/,'')} {item.unit}</span><small>FIFO lots: {(item.lots || []).reduce((s,l) => s + Math.max(0,Number(l.remaining)||0),0).toFixed(3).replace(/\.?0+$/,'')} {item.unit} · {(item.lots || []).filter(l => Number(l.remaining)>0).length} active lot(s)</small></div></article>)}</div>
      </section>}
      {inventoryTab === 'Purchases' && <section className="admin-card"><h2>Receive purchase stock</h2><p>Every receipt becomes a separate price lot. Old and new prices are never averaged.</p>
        <label>Ingredient<select value={stockForm.ingredientId} onChange={e => setStockForm(x => ({...x,ingredientId:e.target.value}))}>{store.inventory.map(item => <option key={item.id} value={item.id}>{item.name} ({item.unit})</option>)}</select></label>
        <label>Quantity received<input type="number" min="0.0001" step="0.0001" value={stockForm.qty} onChange={e => setStockForm(x => ({...x,qty:e.target.value}))} placeholder="Quantity in selected unit"/></label>
        <label>Total purchase cost (₹)<input type="number" min="0" step="0.01" value={stockForm.totalCost} onChange={e => setStockForm(x => ({...x,totalCost:e.target.value}))} placeholder="Total cost for this lot"/></label>
        <label>Supplier / source (optional)<input value={stockForm.supplier} onChange={e => setStockForm(x => ({...x,supplier:e.target.value}))} placeholder="Supplier name"/></label>
        <button className="checkout" onClick={receiveStock}>Save purchase lot</button>
        <h3>Recent purchase lots</h3><div className="sales-list">{store.inventory.flatMap(item => (item.lots||[]).map(lot => ({...lot,ingredient:item.name,unit:item.unit}))).filter(lot => !lot.batchCount && !lot.reconciliationAdjustment).sort((a,b)=>String(b.receivedAt).localeCompare(String(a.receivedAt))).slice(0,30).map(lot => <article className="sale-row" key={lot.id}><div><strong>{lot.ingredient} · {lot.id}</strong><span>{new Date(lot.receivedAt).toLocaleString()} · Remaining {lot.remaining} {lot.unit}</span><small>Received {lot.qty} · Unit cost {money(lot.unitCost)} per {lot.unit} · Supplier: {lot.supplier || 'Not specified'}</small></div></article>)}</div>
      </section>}
      {inventoryTab === 'Decoction' && <section className="admin-card"><h2>Secondary Recipe / BOM Production</h2><p>Define a secondary recipe (inputs → prepared stock output), then produce batches. Each output becomes a separate FIFO cost lot. Existing coffee SOP is retained.</p>
        <label>Select secondary recipe<select value={secondaryRecipeId} onChange={e => setSecondaryRecipeId(e.target.value)}>{(store.secondaryRecipes || INITIAL_SECONDARY_RECIPES).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
        {(() => { const r=(store.secondaryRecipes || INITIAL_SECONDARY_RECIPES).find(x=>x.id===secondaryRecipeId); const out=r && store.inventory.find(x=>x.id===r.outputInventoryId); return r ? <div className="admin-card"><strong>{r.name} BOM</strong><p>Output: {out?.name || 'Missing output'} · Yield: {r.yieldQty} {out?.unit || ''} per batch</p>{r.inputs.map((input,i)=>{const item=store.inventory.find(x=>x.id===input.inventoryId);return <p key={i}>Input {i+1}: {item?.name || 'Missing stock item'} — {input.qty} {item?.unit || ''} per batch</p>})}</div> : null; })()}
        <label>Number of batches (1–20)<input type="number" min="1" max="20" step="1" value={prepBatches} onChange={e => setPrepBatches(e.target.value)}/></label>
        <button className="checkout" onClick={prepareDecoction}>Record production batch</button>
        <p className="local-note">Production is blocked when an input has insufficient recorded stock or FIFO lots. No input costs are averaged; the produced output lot receives the actual consumed FIFO cost.</p>
        <h3>Add a secondary recipe / BOM</h3>
        <label>Recipe name<input value={secondaryDraft.name} onChange={e=>setSecondaryDraft(x=>({...x,name:e.target.value}))} placeholder="e.g. Masala tea premix"/></label>
        <label>Output stock item<select value={secondaryDraft.outputInventoryId} onChange={e=>setSecondaryDraft(x=>({...x,outputInventoryId:e.target.value}))}>{store.inventory.map(item=><option key={item.id} value={item.id}>{item.name} ({item.unit})</option>)}</select></label>
        <label>Yield quantity per batch<input type="number" min="0.0001" step="0.0001" value={secondaryDraft.yieldQty} onChange={e=>setSecondaryDraft(x=>({...x,yieldQty:e.target.value}))}/></label>
        <label>Input 1<select value={secondaryDraft.inputInventoryId} onChange={e=>setSecondaryDraft(x=>({...x,inputInventoryId:e.target.value}))}>{store.inventory.map(item=><option key={item.id} value={item.id}>{item.name} ({item.unit})</option>)}</select></label>
        <label>Input 1 quantity per batch<input type="number" min="0.0001" step="0.0001" value={secondaryDraft.inputQty} onChange={e=>setSecondaryDraft(x=>({...x,inputQty:e.target.value}))}/></label>
        <label>Input 2 (optional)<select value={secondaryDraft.secondInputInventoryId} onChange={e=>setSecondaryDraft(x=>({...x,secondInputInventoryId:e.target.value}))}><option value="">None</option>{store.inventory.map(item=><option key={item.id} value={item.id}>{item.name} ({item.unit})</option>)}</select></label>
        <label>Input 2 quantity per batch<input type="number" min="0" step="0.0001" value={secondaryDraft.secondInputQty} onChange={e=>setSecondaryDraft(x=>({...x,secondInputQty:e.target.value}))}/></label>
        <button className="checkout" onClick={saveSecondaryRecipe}>Save secondary recipe BOM</button>
      </section>}
      {inventoryTab === 'SOP Library' && <section className="admin-card"><h2>SOP & copy-ready recipe library</h2><p>Imported from the available Paanagam tea, Sulaimani tea, milkshake SOP documents and confirmed filter-coffee service notes. Source quantities and uncertainties are preserved; trial/verify labels are shown where needed. This is not a claim that every live Apper database record has been imported.</p>
        <label>Search recipes<input value={sopSearch} onChange={e=>setSopSearch(e.target.value)} placeholder="Search name, ingredient or method…"/></label>
        <label>Category<select value={sopCategory} onChange={e=>setSopCategory(e.target.value)}>{['All',...Array.from(new Set(SOP_LIBRARY.map(s=>s.category)))].map(x=><option key={x}>{x}</option>)}</select></label>
        <div className="menu-admin-list">{SOP_LIBRARY.filter(s=>(sopCategory==='All'||s.category===sopCategory)&&(!sopSearch||[s.name,s.category,s.ingredients,s.method,s.notes].join(' ').toLowerCase().includes(sopSearch.toLowerCase()))).map(s=><button key={s.id} className={'menu-admin-row '+(selectedSop===s.id?'selected':'')} onClick={()=>setSelectedSop(s.id)} style={{textAlign:'left',width:'100%'}}><div><strong>{s.name}</strong><span>{s.category} · {s.serving}</span><span>{s.status}</span></div><span>View</span></button>)}</div>
        {(()=>{const s=SOP_LIBRARY.find(x=>x.id===selectedSop)||SOP_LIBRARY[0];const copyText='PAANAGAM SOP — '+s.name+'\nServing: '+s.serving+'\nStatus: '+s.status+'\n\nINGREDIENTS\n'+s.ingredients+'\n\nMETHOD\n'+s.method+'\n\nNOTES\n'+s.notes;return <div className="admin-card"><h3>{s.name}</h3><p><strong>{s.status}</strong> · {s.serving}</p><label>Ingredients<textarea readOnly rows="6" value={s.ingredients}/></label><label>Preparation method<textarea readOnly rows="7" value={s.method}/></label><label>Source notes / checks<textarea readOnly rows="4" value={s.notes}/></label><div className="button-pair"><button className="outline-button" onClick={()=>{navigator.clipboard?.writeText(copyText).then(()=>flash('SOP copied to clipboard.')).catch(()=>flash('Copy unavailable; select and copy the text manually.'));}}>Copy full SOP</button><button className="outline-button" onClick={()=>{const blob=new Blob([copyText],{type:'text/plain;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=s.id+'-sop.txt';a.click();URL.revokeObjectURL(url);}}>Export SOP .txt</button></div></div>})()}
        <p className="local-note">Common milkshake hygiene: sanitize blender and tools, keep milk/ice cream chilled, measure portions, blend only to required texture, serve immediately, and clean between flavours. Peanut and dairy cross-contact must be controlled.</p>
      </section>}
      {inventoryTab === 'Recipes' && <section className="admin-card"><h2>Recipe management</h2><p>Recipe units must match the ingredient stock units exactly.</p>
        <label>Menu product<select value={recipeProduct} onChange={e => setRecipeProduct(e.target.value)}>{store.menu.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
        <div className="sales-list">{(store.recipes?.[recipeProduct]||[]).map((line,i)=><article className="sale-row" key={recipeProduct+'-'+i}><div><strong>{line.ingredient}</strong><span>{line.qty} {line.unit} per menu item</span><small>{line.note||''}</small></div><button className="text-button danger" onClick={()=>deleteRecipeLine(recipeProduct,i)}>Remove</button></article>)}</div>
        <label>Ingredient<select value={recipeForm.ingredient} onChange={e=>{const v=e.target.value;const item=store.inventory.find(x=>x.name===v);setRecipeForm(x=>({...x,ingredient:v,unit:item?.unit||x.unit}));}}>{store.inventory.map(item=><option key={item.id} value={item.name}>{item.name} ({item.unit})</option>)}</select></label>
        <label>Quantity per menu item<input type="number" min="0.0001" step="0.0001" value={recipeForm.qty} onChange={e=>setRecipeForm(x=>({...x,qty:e.target.value}))}/></label>
        <label>Unit<select value={recipeForm.unit} onChange={e=>setRecipeForm(x=>({...x,unit:e.target.value}))}>{['g','kg','ml','L','unit'].map(u=><option key={u}>{u}</option>)}</select></label>
        <label>Note (optional)<input value={recipeForm.note} onChange={e=>setRecipeForm(x=>({...x,note:e.target.value}))} placeholder="SOP or portion note"/></label>
        <button className="checkout" onClick={saveRecipeLine}>Add recipe line</button>
        <p className="local-note">Only Filter Coffee has the starter SOP configured by default: 25 ml decoction, 75 ml milk and 5 g sugar per cup. Configure and verify every other product before live use.</p>
      </section>}
      {inventoryTab === 'Stock out' && <section className="admin-card"><h2>Manual stock out</h2><p>Use for recorded wastage or other stock removal. This consumes oldest FIFO lots first.</p>
        <label>Ingredient<select value={stockOutForm.ingredientId} onChange={e=>setStockOutForm(x=>({...x,ingredientId:e.target.value}))}>{store.inventory.map(item=><option key={item.id} value={item.id}>{item.name} ({item.unit})</option>)}</select></label>
        <label>Quantity to remove<input type="number" min="0.0001" step="0.0001" value={stockOutForm.qty} onChange={e=>setStockOutForm(x=>({...x,qty:e.target.value}))}/></label>
        <button className="checkout" onClick={recordStockOut}>Record stock out</button>
      </section>}
      {inventoryTab === 'Movements' && <section className="admin-card"><h2>Stock movement history</h2><p>Receipts, decoction batches, sale consumption, manual stock out and physical reconciliation.</p>
        {store.stockMovements.length===0 ? <p className="local-note">No stock movements recorded yet.</p> : <div className="sales-list">{store.stockMovements.map(m=><article className="sale-row" key={m.id}><div><strong>{m.type} · {m.ingredient}</strong><span>{new Date(m.at).toLocaleString()} · {m.qty} {m.unit||''}</span><small>{m.lotId ? 'Lot '+m.lotId+' · ' : ''}{m.totalCost != null ? 'Total cost '+money(m.totalCost)+' · ' : ''}{m.note||''}</small></div></article>)}</div>}
      </section>}
    </main>}

    {page === 'Admin' && <main className="page-content">
      {!adminOpen ? <div className="admin-lock"><div className="lock-icon">🔐</div><h1>Admin access</h1><p>Enter the admin PIN to manage the menu and backups.</p><form onSubmit={e => { e.preventDefault(); unlockAdmin(); }}><input aria-label="Admin PIN" type="password" inputMode="numeric" maxLength="8" value={pinEntry} onChange={e => setPinEntry(e.target.value.replace(/\D/g, ''))} placeholder="Enter PIN" /><button className="checkout">Unlock admin</button></form><small>Starter default PIN: 1234. Change it after first login.</small></div> :
      <div className="admin-content"><div className="section-heading"><div><h1>Admin settings</h1><p>Menu, security and device backup</p></div><button className="text-button" onClick={() => { setAdminOpen(false); setPage('POS'); }}>Lock</button></div>
        <section className="admin-card"><div className="admin-card-heading"><div><h2>Menu management</h2><p>Add products or change pricing.</p></div><button className="solid-small" onClick={() => setEditing({ id: 'item-' + Date.now(), name: '', tamil: '', price: 20, category: 'Coffee', active: true })}>+ Add</button></div>
          {store.menu.map(p => <div className="menu-admin-row" key={p.id}><div><strong>{p.name}</strong><span>{p.category} · {money(p.price)}</span></div><button className="outline-button small" onClick={() => setEditing({ ...p })}>Edit</button><button className="text-button danger" onClick={() => setStore(curr => ({ ...curr, menu: curr.menu.map(m => m.id === p.id ? { ...m, active: !m.active } : m) }))}>{p.active ? 'Hide' : 'Show'}</button></div>)}
        </section>
        <section className="admin-card">
          <div className="admin-card-heading"><div><h2>Physical stock reconciliation</h2><p>Count stock and record the variance. FIFO purchase prices are never averaged.</p></div></div>
          <label>Ingredient
            <select value={reconcileForm.ingredientId} onChange={e => setReconcileForm(x => ({ ...x, ingredientId: e.target.value, physicalQty: '' }))}>
              {store.inventory.map(item => <option key={item.id} value={item.id}>{item.name} ({item.unit})</option>)}
            </select>
          </label>
          {(() => { const item = store.inventory.find(x => x.id === reconcileForm.ingredientId); return item ? <p className="local-note">Recorded on-hand: <strong>{Number(item.onHand || 0).toFixed(4).replace(/\\.?0+$/, '')} {item.unit}</strong> · FIFO lot balance: <strong>{(item.lots || []).reduce((s,l) => s + Math.max(0, Number(l.remaining) || 0), 0).toFixed(4).replace(/\\.?0+$/, '')} {item.unit}</strong></p> : null; })()}
          <label>Actual physical quantity<input type="number" min="0" step="0.0001" required value={reconcileForm.physicalQty} onChange={e => setReconcileForm(x => ({ ...x, physicalQty: e.target.value }))} placeholder="Enter count in selected unit"/></label>
          <label>Reason / note (optional)<input value={reconcileForm.note} onChange={e => setReconcileForm(x => ({ ...x, note: e.target.value }))} placeholder="e.g. weekly physical count"/></label>
          <button className="checkout" onClick={recordReconciliation} disabled={reconcileForm.physicalQty === ''}>Save physical count</button>
          <p className="local-note">A shortage consumes oldest FIFO lots first. A surplus is recorded as a separate zero-cost adjustment lot marked cost unknown; it is not treated as a purchase.</p>
        </section>
        <section className="admin-card"><h2>Change admin PIN</h2><p>Use 4–8 digits. Keep this PIN private.</p><div className="inline-form"><input type="password" inputMode="numeric" maxLength="8" placeholder="New PIN" value={newPin} onChange={e => setNewPin(e.target.value.replace(/\D/g, ''))}/><button className="solid-small" onClick={changePin}>Save PIN</button></div></section>
        <section className="admin-card"><h2>Live cloud backup & local storage</h2><p>Sales, menu, stock lots, recipes, loyalty and movement history are saved locally on this Android device first. Configure a secured HTTPS backup endpoint to automatically sync after changes when internet is available. No cloud credentials are pre-filled.</p><div className="sales-summary"><div><span>Local storage</span><strong>Active</strong></div><div><span>Cloud status</span><strong style={{fontSize:'14px'}}>{cloudStatus}</strong></div></div><label>Secured HTTPS cloud endpoint<input type="url" value={cloudConfig.endpoint} onChange={e=>setCloudConfig(x=>({...x,endpoint:e.target.value}))} placeholder="https://your-project.supabase.co/functions/v1/paanagam-backup" /></label><label>Bearer token / function secret<input type="password" autoComplete="off" value={cloudConfig.token} onChange={e=>setCloudConfig(x=>({...x,token:e.target.value}))} placeholder="Paste the secret for your secured backup endpoint" /></label><label style={{display:'flex',alignItems:'center',gap:'9px',margin:'12px 0'}}><input style={{width:'auto'}} type="checkbox" checked={!!cloudConfig.autoSync} onChange={e=>setCloudConfig(x=>({...x,autoSync:e.target.checked}))}/> Auto-sync changes when online</label><div className="button-pair"><button className="outline-button" disabled={cloudBusy} onClick={syncCloudNow}>{cloudBusy?'Working…':'Back up to cloud now'}</button><button className="outline-button" disabled={cloudBusy} onClick={restoreCloudNow}>Restore from cloud</button></div><p className="local-note">For security, use a private server/Edge Function that validates this bearer token and stores one backup per device. Do not use a public table or expose a service-role key in the app. Automatic cloud backup activates only after a valid endpoint and token are configured.</p></section>
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
