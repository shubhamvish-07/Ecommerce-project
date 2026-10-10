(() => {
"use strict";

/* ===== 1. catalogue ===== */
const SHAPE = {Shirts:"g-shirt",Kurtas:"g-kurta",Trousers:"g-trouser",Dresses:"g-dress",Accessories:"g-scarf"};
const ONE = ["One size"];
const APPAREL_SIZES = ["XS", "S", "M", "L", "XL", "XXL"];
const P = [];
const FREE_SHIP = 2500;
const API_BASE = "http://localhost:8080";
const COLOUR_SWATCHES = {
  black:"#242424", white:"#F2F1EC", red:"#9E3B2E", blue:"#26346B",
  indigo:"#2A3A6B", green:"#6E7B5E", beige:"#D9C7A7", cream:"#E4E0D5",
  brown:"#76543C", yellow:"#D9B94A", pink:"#D9A5AE", grey:"#8A8F9E",
  gray:"#8A8F9E", navy:"#1A2340"
};

/* ===== 2. helpers ===== */
const $ = s => document.querySelector(s);
const money = new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:0});
const rupees = n => money.format(n);
const escapeHTML = value => String(value ?? "").replace(/[&<>"']/g, character => ({
  "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;"
})[character]);
const imageUrlFor = value => {
  if (!value) return "";
  try {
    const url = new URL(value, API_BASE);
    return ["http:", "https:"].includes(url.protocol) ? url.href : "";
  } catch {
    return "";
  }
};
const swatchFor = colour => COLOUR_SWATCHES[String(colour || "").trim().toLowerCase()] || "#6E7B5E";
// pick a legible line colour for any fabric swatch
const inkFor = hex => {
  const n = parseInt(hex.slice(1),16);
  const L = (0.299*(n>>16) + 0.587*((n>>8)&255) + 0.114*(n&255))/255;
  return L > 0.6 ? "rgba(20,26,46,.55)" : "rgba(255,255,255,.7)";
};
const byId = id => P.find(p => String(p.id) === String(id));

/* ===== 3. state ===== */
const state = {cat:"All", q:"", sort:"feat", colour:{}};
let cart = [];
try { cart = JSON.parse(localStorage.getItem("mw.cart") || "[]") || []; } catch (e) { cart = []; }
const save = () => { try { localStorage.setItem("mw.cart", JSON.stringify(cart)); } catch (e) {} };
let savedAddress = null;
localStorage.removeItem("mw.address");
let profileLoaded = false;

/* ===== 4. filter chips ===== */
function renderChips(){
  const cats = ["All", ...new Set(P.map(p => p.cat))];
  $("#chips").innerHTML = cats.map(c =>
    `<button class="chip" data-cat="${escapeHTML(c)}" aria-pressed="${c === "All"}">${escapeHTML(c)}</button>`).join("");
}

/* ===== 5. grid ===== */
const grid = $("#grid");

function visible(){
  const q = state.q.trim().toLowerCase();
  let list = P.filter(p =>
    (state.cat === "All" || p.cat === state.cat) &&
    (!q || p.name.toLowerCase().includes(q) || p.cat.toLowerCase().includes(q)));
  const s = state.sort;
  if (s === "lo") list = list.slice().sort((a,b) => a.price - b.price);
  else if (s === "hi") list = list.slice().sort((a,b) => b.price - a.price);
  else if (s === "new") list = list.slice().sort((a,b) =>
    (Date.parse(b.createdAt) || Number(b.id) || 0) - (Date.parse(a.createdAt) || Number(a.id) || 0));
  return list;
}

function cardHTML(p){
  const c = state.colour[p.id] || p.colors[0];
  const badge = p.tag ? `<span class="badge${p.tag === "Sale" ? " sale" : ""}">${p.tag}</span>` : "";
  const dots = p.colors.map(h =>
    `<button class="dot" style="background:${h}" data-colour="${h}" aria-pressed="${h === c}" aria-label="Colourway ${h}"></button>`).join("");
  const sizes = p.sizes.map((s, i) =>
    `<button class="size" type="button" data-size="${escapeHTML(s)}" aria-pressed="${p.sizes.length === 1 && i === 0}">${escapeHTML(s)}</button>`).join("");
  const photoUrl = imageUrlFor(p.imageUrl);
  const photo = photoUrl
    ? `<img class="product-photo" src="${escapeHTML(photoUrl)}" alt="${escapeHTML(p.name)}" loading="lazy">`
    : "";
  const shape = SHAPE[p.cat]
    ? `<svg viewBox="0 0 100 120" aria-label="${escapeHTML(p.name)}" role="img"><use href="#${SHAPE[p.cat]}"/></svg>`
    : "";
  return `<article class="card" data-id="${escapeHTML(p.id)}">
    <div class="frame${photoUrl ? "" : " product-placeholder"}" style="--swatch:${c};--stroke:${inkFor(c)}">
      ${shape}${photo}
      ${badge}
      <div class="act${p.sizes.length === 1 ? " pick" : ""}" data-selected-size="${p.sizes.length === 1 ? escapeHTML(p.sizes[0]) : ""}">
        <button class="act-add" type="button" data-act="pick"${p.stock < 1 ? " disabled" : ""}>${p.stock > 0 ? "Choose size & quantity" : "Out of stock"}</button>
        <div class="picker">
          <div class="sizes" aria-label="Choose a size">${sizes}</div>
          <div class="pick-controls">
            <label>Qty <input class="product-qty" type="number" min="1" max="${Math.max(1, remainingStock(p))}" value="1" aria-label="Quantity"></label>
            <button class="act-confirm" type="button" data-act="add"${remainingStock(p) < 1 || p.sizes.length > 1 ? " disabled" : ""}>Add</button>
          </div>
        </div>
      </div>
    </div>
    <div class="meta">
      <h3 class="title">${escapeHTML(p.name)}</h3>
      <p class="price">${rupees(p.price)}${p.was ? `<span class="was">${rupees(p.was)}</span>` : ""}</p>
      <p class="stock-count">${p.stock > 0 ? `${p.stock} left in stock` : "Out of stock"}</p>
      ${p.colourName ? `<p class="product-colour">${escapeHTML(p.colourName)}</p>` : ""}
      <div class="dots">${dots}</div>
    </div>
  </article>`;
}

function render(){
  const list = visible();
  $("#count").textContent = list.length
    ? `${list.length} ${list.length === 1 ? "piece" : "pieces"}${state.cat !== "All" ? " in " + state.cat.toLowerCase() : ""}`
    : "";
  grid.innerHTML = list.length
    ? list.map(cardHTML).join("")
    : `<div class="empty"><b>Nothing on this loom yet</b>No pieces match that search. Try a different word or clear the filters.<br><button id="clr">Clear filters</button></div>`;
  updateProductPickers();
}

/* one delegated listener for the whole grid */
grid.addEventListener("click", e => {
  const card = e.target.closest(".card");
  if (e.target.id === "clr"){ state.q = ""; state.cat = "All"; $("#q").value = ""; syncChips(); render(); return; }
  if (!card) return;
  const p = byId(card.dataset.id);
  if (!p) return;

  const dot = e.target.closest(".dot");
  if (dot){
    state.colour[p.id] = dot.dataset.colour;
    const frame = card.querySelector(".frame");
    frame.style.setProperty("--swatch", dot.dataset.colour);
    frame.style.setProperty("--stroke", inkFor(dot.dataset.colour));
    card.querySelectorAll(".dot").forEach(d => d.setAttribute("aria-pressed", d === dot));
    return;
  }
  if (e.target.closest("[data-act='pick']")){
    if (p.stock < 1) {
      toast("This piece is currently out of stock.");
      return;
    }
    const available = remainingStock(p);
    if (available < 1) {
      toast("You already have all available stock of this piece in your bag.");
      return;
    }
    const quantityInput = card.querySelector(".product-qty");
    quantityInput.max = String(available);
    quantityInput.value = "1";
    card.querySelector(".act").classList.add("pick");
    return;
  }
  const size = e.target.closest(".size");
  if (size){
    const act = card.querySelector(".act");
    act.dataset.selectedSize = size.dataset.size;
    card.querySelectorAll(".size").forEach(button => button.setAttribute("aria-pressed", button === size));
    card.querySelector("[data-act='add']").disabled = false;
    return;
  }
  if (e.target.closest("[data-act='add']")) {
    const quantityInput = card.querySelector(".product-qty");
    const quantity = Number(quantityInput.value);
    const selectedSize = card.querySelector(".act").dataset.selectedSize;
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > Number(quantityInput.max)) {
      toast(`Choose a quantity from 1 to ${quantityInput.max}.`);
      quantityInput.focus();
      return;
    }
    if (!selectedSize) {
      toast("Choose a size first.");
      return;
    }
    add(p, selectedSize, state.colour[p.id] || p.colors[0], quantity);
    card.querySelector(".act").classList.remove("pick");
  }
});
grid.addEventListener("mouseleave", e => {
  const card = e.target.closest?.(".card");
  if (card === e.target) card.querySelector(".act")?.classList.remove("pick");
}, true);

/* ===== 6. filters ===== */
function syncChips(){
  document.querySelectorAll(".chip").forEach(c => c.setAttribute("aria-pressed", c.dataset.cat === state.cat));
}
$("#chips").addEventListener("click", e => {
  const c = e.target.closest(".chip"); if (!c) return;
  state.cat = c.dataset.cat; syncChips(); render();
});
$("#sort").addEventListener("change", e => { state.sort = e.target.value; render(); });

let t;
$("#q").addEventListener("input", e => {          // debounced: no re-render per keystroke
  clearTimeout(t);
  t = setTimeout(() => { state.q = e.target.value; render(); }, 120);
});

/* ===== 7. bag ===== */
function remainingStock(p) {
  const inBag = cart.reduce((sum, line) => sum + (line.id === p.id ? Number(line.qty) || 0 : 0), 0);
  return Math.max(0, p.stock - inBag);
}
function updateProductPickers() {
  grid.querySelectorAll(".card").forEach(card => {
    const product = byId(card.dataset.id);
    if (!product) return;
    const available = remainingStock(product);
    const act = card.querySelector(".act");
    const quantityInput = card.querySelector(".product-qty");
    quantityInput.max = String(Math.max(1, available));
    card.querySelector("[data-act='pick']").disabled = available < 1;
    card.querySelector("[data-act='add']").disabled = available < 1 || !act.dataset.selectedSize;
  });
}
function add(p, size, colour, quantity){
  const available = remainingStock(p);
  if (quantity > available) {
    toast(`Only ${available} more ${available === 1 ? "unit is" : "units are"} available.`);
    return;
  }
  const line = cart.find(l => l.id === p.id && l.size === size && l.colour === colour);
  if (line) line.qty += quantity;
  else cart.push({id:p.id, size, colour, qty:quantity});
  save(); paintBag(); toast(`${p.name} · ${size} × ${quantity} added`);
}
function change(i, d){
  const p = byId(cart[i].id);
  if (d > 0 && p && remainingStock(p) < 1) {
    toast(`Only ${p.stock} ${p.stock === 1 ? "unit is" : "units are"} in stock.`);
    return;
  }
  cart[i].qty += d;
  if (cart[i].qty < 1) cart.splice(i,1);
  save(); paintBag();
}
const subtotal = () => cart.reduce((sum, line) => {
  const product = byId(line.id);
  return product ? sum + product.price * line.qty : sum;
}, 0);

function paintBag(){
  updateProductPickers();
  const units = cart.reduce((s,l) => s + l.qty, 0);
  const pip = $("#pip");
  pip.textContent = units;
  pip.classList.toggle("on", units > 0);
  $("#bagBtn").setAttribute("aria-label", `Open bag, ${units} item${units === 1 ? "" : "s"}`);

  const body = $("#bagBody");
  if (!cart.length){
    body.innerHTML = `<div class="bag-empty"><b>Your bag is empty</b><span>Pieces you add will wait for you here.</span><button data-close>Back to the shop</button></div>`;
    return;
  }
  const sum = subtotal();
  const left = FREE_SHIP - sum;
  const lines = cart.map((l,i) => {
    const p = byId(l.id);
    const swatch = /^#[0-9a-f]{6}$/i.test(l.colour || "") ? l.colour : "#ccc";
    if (!p) {
      return `<div class="line">
        <div class="line-img" style="--swatch:${swatch}"></div>
        <div>
          <div class="line-name">Unavailable product</div>
          <div class="line-sub">This product is no longer in the catalogue.</div>
          <div class="line-sub">Quantity ${escapeHTML(l.qty)}</div>
        </div>
        <div class="line-right"><button class="rm" data-rm="${i}">Remove</button></div>
      </div>`;
    }
    return `<div class="line">
      <div class="line-img" style="--swatch:${swatch}"></div>
      <div>
        <div class="line-name">${escapeHTML(p.name)}</div>
        <div class="line-sub">Size ${escapeHTML(l.size)}</div>
        <div class="qty">
          <button data-i="${i}" data-d="-1" aria-label="Reduce quantity">−</button>
          <span>${escapeHTML(l.qty)}</span>
          <button data-i="${i}" data-d="1" aria-label="Increase quantity">+</button>
        </div>
      </div>
      <div class="line-right"><span>${rupees(p.price * l.qty)}</span><button class="rm" data-rm="${i}">Remove</button></div>
    </div>`;
  }).join("");

  body.innerHTML = `
    <div class="ship">
      ${left > 0 ? `Add ${rupees(left)} more for free shipping` : "Free shipping unlocked"}
      <div class="bar"><i style="width:${Math.min(100, sum / FREE_SHIP * 100)}%"></i></div>
    </div>
    <div class="lines">${lines}</div>
    <div class="bag-foot">
      <div class="tot"><span>Subtotal <small>incl. GST</small></span><b>${rupees(sum)}</b></div>
      <button class="checkout" data-checkout>Continue to checkout</button>
    </div>`;
}

$("#bagBody").addEventListener("click", e => {
  const q = e.target.closest("[data-d]");
  if (q) return change(+q.dataset.i, +q.dataset.d);
  const r = e.target.closest("[data-rm]");
  if (r){ cart.splice(+r.dataset.rm,1); save(); paintBag(); return; }
  if (e.target.closest("[data-close]")) return closeBag();
  if (e.target.closest("[data-checkout]")) startCheckout();
  if (e.target.closest("[data-edit-address]")) openAddressPage("checkout");
  if (e.target.closest("[data-back-to-bag]")) paintBag();
  const orderButton = e.target.closest("[data-place-order]");
  if (orderButton) placeOrder(orderButton);
});
/* drawer open/close */
let lastFocus = null;
function openBag(){
  lastFocus = document.activeElement;
  $("#scrim").hidden = false;
  requestAnimationFrame(() => $("#scrim").classList.add("on"));
  $("#bag").classList.add("on");
  $("#bag").setAttribute("aria-hidden","false");
  document.body.style.overflow = "hidden";
  $("#closeBag").focus();
}
function closeBag(){
  $("#scrim").classList.remove("on");
  $("#bag").classList.remove("on");
  $("#bag").setAttribute("aria-hidden","true");
  document.body.style.overflow = "";
  setTimeout(() => { $("#scrim").hidden = true; }, 260);
  lastFocus?.focus();
}
$("#bagBtn").addEventListener("click", openBag);
$("#closeBag").addEventListener("click", closeBag);
$("#scrim").addEventListener("click", closeBag);
document.addEventListener("keydown", e => {
  if (e.key === "Escape" && $("#bag").classList.contains("on")) closeBag();
});
// keep tab focus inside the drawer while it's open
$("#bag").addEventListener("keydown", e => {
  if (e.key !== "Tab") return;
  const f = $("#bag").querySelectorAll("button,[href],input,select");
  if (!f.length) return;
  const first = f[0], last = f[f.length - 1];
  if (e.shiftKey && document.activeElement === first){ e.preventDefault(); last.focus(); }
  else if (!e.shiftKey && document.activeElement === last){ e.preventDefault(); first.focus(); }
});

/* ===== 8. toast ===== */
let tt;
function toast(msg){
  const el = $("#toast");
  el.textContent = msg; el.classList.add("on");
  clearTimeout(tt); tt = setTimeout(() => el.classList.remove("on"), 2400);
}

function orderLinesFromCart(){
  const quantities = new Map();
  const invalidLines = new Set();
  cart.forEach(line => {
    if (typeof line.id !== "string" || !line.id.startsWith("backend-")) return;
    const productId = Number(line.id.slice("backend-".length));
    const quantity = Number(line.qty);
    if (!Number.isSafeInteger(productId) || productId <= 0 ||
        !Number.isSafeInteger(quantity) || quantity < 1 || quantity > 2147483647) return;
    const size = String(line.size || "").trim();
    if (!size) return;
    const key = `${productId}:${size}`;
    if (invalidLines.has(key)) return;
    const mergedQuantity = (quantities.get(key)?.quantity || 0) + quantity;
    if (mergedQuantity <= 2147483647) {
      quantities.set(key, {productId, size, quantity:mergedQuantity});
    } else {
      quantities.delete(key);
      invalidLines.add(key);
    }
  });
  return [...quantities.values()];
}

function addressFromProfile(profile){
  const address = {
    name: profile.recipientName,
    phone: profile.recipientPhone,
    addressLine: profile.deliveryAddress,
    city: profile.deliveryCity,
    state: profile.deliveryState,
    postalCode: profile.deliveryPostalCode
  };
  return Object.values(address).every(value => typeof value === "string" && value.trim())
    ? address
    : null;
}

async function loadProfileAddress(){
  if (profileLoaded) return savedAddress;
  const token = localStorage.getItem("accessToken");
  if (!token) {
    sessionStorage.setItem("mw.resumeCheckout", "true");
    redirectToSignIn();
    return null;
  }
  try {
    const response = await fetch(`${API_BASE}/user/profile`, {
      headers: {Authorization: `Bearer ${token}`}
    });
    if (response.status === 401) {
      sessionStorage.setItem("mw.resumeCheckout", "true");
      redirectToSignIn();
      return null;
    }
    if (!response.ok) {
      toast(await orderErrorMessage(response));
      return null;
    }
    savedAddress = addressFromProfile(await response.json());
    profileLoaded = true;
    return savedAddress;
  } catch (error) {
    console.error("Unable to load delivery address from profile", error);
    toast("Unable to load your profile. Please try again.");
    return null;
  }
}

function openAddressPage(returnTo){
  if (returnTo === "checkout") {
    sessionStorage.setItem("mw.resumeCheckout", "true");
  }
  location.href = `address.html?return=${returnTo}`;
}

async function startCheckout(){
  if (!cart.length) {
    toast("Your bag is empty.");
    return;
  }
  const address = await loadProfileAddress();
  if (!address) {
    openAddressPage("checkout");
    return;
  }
  showCheckoutReview();
}

function showCheckoutReview(){
  if (!savedAddress) {
    openAddressPage("checkout");
    return;
  }
  const fullAddress = [
    savedAddress.addressLine,
    `${savedAddress.city}, ${savedAddress.state} ${savedAddress.postalCode}`
  ].map(escapeHTML).join("<br>");
  $("#bagBody").innerHTML = `
    <section class="checkout-view">
      <button class="checkout-back" type="button" data-back-to-bag>Back to bag</button>
      <h3>Review your order</h3>
      <div class="delivery-summary">
        <div><b>${escapeHTML(savedAddress.name)}</b><span>${escapeHTML(savedAddress.phone)}</span></div>
        <p>${fullAddress}</p>
        <button type="button" data-edit-address>Edit address</button>
      </div>
      <fieldset class="payment-options">
        <legend>Payment method</legend>
        <label><input type="radio" name="paymentMethod" value="COD" checked> Cash on Delivery</label>
        <label class="coming-soon"><input type="radio" disabled> Prepaid <span>Coming soon</span></label>
      </fieldset>
      <button class="checkout" type="button" data-place-order>Place order — Cash on Delivery</button>
    </section>`;
}

async function orderErrorMessage(response){
  let data;
  try {
    data = await response.json();
  } catch {
    return `Unable to place order (HTTP ${response.status}).`;
  }
  const details = data?.errors && typeof data.errors === "object"
    ? Object.entries(data.errors).map(([field, message]) => `${field}: ${message}`)
    : [];
  return [data?.message, ...details].filter(Boolean).join(". ")
    || `Unable to place order (HTTP ${response.status}).`;
}

function redirectToSignIn(){
  localStorage.removeItem("accessToken");
  localStorage.removeItem("userRole");
  sessionStorage.setItem("mw.returnTo", `${location.pathname}${location.search}${location.hash}`);
  location.href = "auth.html";
}

async function placeOrder(button){
  if (button.disabled) return;
  if (!cart.length) {
    toast("Your bag is empty.");
    return;
  }
  if (!savedAddress) {
    openAddressPage("checkout");
    return;
  }
  const token = localStorage.getItem("accessToken");
  if (!token) {
    sessionStorage.setItem("mw.resumeCheckout", "true");
    redirectToSignIn();
    return;
  }

  const items = orderLinesFromCart();
  if (!items.length) {
    toast("Your bag has no available products to order.");
    return;
  }

  button.disabled = true;
  try {
    const response = await fetch(`${API_BASE}/user/orders`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({items, deliveryAddress: savedAddress})
    });

    if (response.status === 401) {
      sessionStorage.setItem("mw.resumeCheckout", "true");
      redirectToSignIn();
      return;
    }
    if (response.status !== 201) {
      toast(await orderErrorMessage(response));
      return;
    }

    const order = await response.json();
    cart = [];
    save();
    paintBag();
    toast(`Order #${order.id} placed with Cash on Delivery. Total ${rupees(Number(order.totalAmount))}.`);
  } catch (error) {
    console.error("Unable to place order", error);
    toast("Unable to connect to the order service. Your bag is unchanged.");
  } finally {
    button.disabled = false;
  }
}

async function loadBackendProducts(){
  try {
    const response = await fetch(`${API_BASE}/products`);
    if (!response.ok) throw new Error(`Product request failed (${response.status})`);

    const products = await response.json();
    if (!Array.isArray(products)) throw new Error("Product response was not a list");

    products.forEach(product => {
      const price = Number(product.price);
      if (product.id == null || !Number.isFinite(price)) {
        console.error("Skipping invalid product returned by the API", product);
        return;
      }
      const category = String(product.category || "Other");
      const color = String(product.color || "");
      P.push({
        id:`backend-${product.id}`,
        name:String(product.name || "Unnamed product"),
        cat:category,
        price,
        colors:[swatchFor(color)],
        sizes:String(product.category || "").trim().toLowerCase() === "accessories" ? ONE : APPAREL_SIZES,
        stock:Math.max(0, Number(product.stock) || 0),
        imageUrl:product.imageUrl,
        colourName:color,
        createdAt:product.createdAt
      });
    });
  } catch (error) {
    console.error("Unable to load backend products", error);
    toast("The product catalogue is currently unavailable.");
  }

  renderChips();
  render();
  paintBag();
  if (sessionStorage.getItem("mw.resumeCheckout") === "true") {
    sessionStorage.removeItem("mw.resumeCheckout");
    openBag();
    startCheckout();
  }
}

/* ===== 9. theme ===== */
$("#themeBtn").addEventListener("click", () => {
  const dark = matchMedia("(prefers-color-scheme: dark)").matches;
  const now = document.documentElement.dataset.theme || (dark ? "dark" : "light");
  const next = now === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  try { localStorage.setItem("mw.theme", next); } catch (e) {}
});
try {
  const saved = localStorage.getItem("mw.theme");
  if (saved) document.documentElement.dataset.theme = saved;
} catch (e) {}

/* ===== 10. go ===== */
renderChips();
render();
paintBag();
loadBackendProducts();
})();