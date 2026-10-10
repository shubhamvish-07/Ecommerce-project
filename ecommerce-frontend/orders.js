"use strict";

const API_BASE = "http://localhost:8080";
const ordersMessage = document.querySelector("#ordersMessage");
const signInPrompt = document.querySelector("#signInPrompt");
const retryButton = document.querySelector("#retryOrders");
const ordersList = document.querySelector("#ordersList");

function setMessage(message, type = "") {
    ordersMessage.textContent = message;
    ordersMessage.className = `form-message ${type}`.trim();
}

function showSignInPrompt() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("userRole");
    sessionStorage.setItem("mw.returnTo", `${location.pathname}${location.search}${location.hash}`);
    ordersList.replaceChildren();
    retryButton.hidden = true;
    ordersMessage.textContent = "";
    signInPrompt.hidden = false;
}

async function readErrorMessage(response) {
    let data;
    try {
        data = await response.json();
    } catch {
        return `Unable to load orders (HTTP ${response.status}).`;
    }

    const details = data?.errors && typeof data.errors === "object"
        ? Object.entries(data.errors).map(([field, message]) => `${field}: ${message}`)
        : [];
    return [data?.message, ...details].filter(Boolean).join(". ")
        || `Unable to load orders (HTTP ${response.status}).`;
}

function formatOrderDate(value) {
    if (typeof value !== "string") {
        return "";
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? "" : new Date(timestamp).toLocaleString();
}

function formatMoney(value) {
    const amount = Number(value);
    return Number.isFinite(amount)
        ? new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount)
        : "";
}

function orderStatusLabel(status) {
    return ({
        PLACED: "Placed",
        SHIPPED: "Shipped",
        DELIVERED: "Delivered",
        CANCELLED: "Cancelled"
    })[status] || String(status ?? "");
}

function renderOrder(order) {
    const details = document.createElement("details");
    details.className = "order-entry";

    const summary = document.createElement("summary");
    summary.className = "order-summary";

    const number = document.createElement("div");
    const numberLabel = document.createElement("span");
    numberLabel.textContent = "Order";
    const numberValue = document.createElement("strong");
    numberValue.textContent = `#${order.id}`;
    number.append(numberLabel, numberValue);

    const dateValue = formatOrderDate(order.createdAt);
    const date = document.createElement("div");
    const dateLabel = document.createElement("span");
    dateLabel.textContent = "Date";
    date.appendChild(dateLabel);
    if (dateValue) {
        const dateText = document.createElement("strong");
        dateText.textContent = dateValue;
        date.appendChild(dateText);
    }

    const status = document.createElement("div");
    const statusLabel = document.createElement("span");
    statusLabel.textContent = "Status";
    const statusValue = document.createElement("strong");
    statusValue.textContent = orderStatusLabel(order.status);
    status.append(statusLabel, statusValue);

    const total = document.createElement("div");
    const totalLabel = document.createElement("span");
    totalLabel.textContent = "Total";
    const totalValue = document.createElement("strong");
    totalValue.textContent = formatMoney(order.totalAmount);
    total.append(totalLabel, totalValue);

    summary.append(number, date, status, total);
    details.appendChild(summary);

    const items = document.createElement("ul");
    items.className = "order-items";
    (Array.isArray(order.items) ? order.items : []).forEach(item => {
        const row = document.createElement("li");
        const description = document.createElement("span");
        description.textContent = `${String(item.productName ?? "Product")} · ${String(item.size ?? "One size")} · ${Number(item.quantity)} × ${formatMoney(item.unitPrice)}`;
        const lineTotal = document.createElement("strong");
        lineTotal.textContent = formatMoney(item.lineTotal);
        row.append(description, lineTotal);
        items.appendChild(row);
    });
    details.appendChild(items);

    const delivery = document.createElement("p");
    delivery.className = "order-delivery";
    delivery.textContent = [
        `Payment: ${order.paymentMethod === "COD" ? "Cash on Delivery" : String(order.paymentMethod ?? "")}`,
        order.recipientName,
        order.deliveryAddress,
        [order.deliveryCity, order.deliveryState, order.deliveryPostalCode].filter(Boolean).join(", ")
    ].filter(Boolean).join(" · ");
    details.appendChild(delivery);

    return details;
}

async function loadOrders() {
    signInPrompt.hidden = true;
    retryButton.hidden = true;
    ordersList.replaceChildren();

    const token = localStorage.getItem("accessToken");
    if (!token) {
        showSignInPrompt();
        return;
    }

    setMessage("Loading your orders…");
    try {
        const response = await fetch(`${API_BASE}/user/orders`, {
            headers: { Authorization: `Bearer ${token}` }
        });
        if (response.status === 401) {
            showSignInPrompt();
            return;
        }
        if (!response.ok) {
            setMessage(await readErrorMessage(response), "error");
            retryButton.hidden = false;
            return;
        }

        const orders = await response.json();
        if (!Array.isArray(orders)) {
            setMessage("The order service returned an invalid response.", "error");
            retryButton.hidden = false;
            return;
        }
        if (!orders.length) {
            setMessage("You have no orders yet.");
            return;
        }

        ordersList.replaceChildren(...orders.map(renderOrder));
        setMessage("");
    } catch (error) {
        console.error("Unable to load orders", error);
        setMessage("Unable to connect to the order service.", "error");
        retryButton.hidden = false;
    }
}

retryButton.addEventListener("click", loadOrders);
loadOrders();
