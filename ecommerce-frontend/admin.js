"use strict";

const API_BASE = "http://localhost:8080";
const accessMessage = document.querySelector("#accessMessage");
const workspace = document.querySelector("#adminWorkspace");
const form = document.querySelector("#productForm");
const formHeading = document.querySelector("#addProductHeading");
const formMessage = document.querySelector("#formMessage");
const listMessage = document.querySelector("#listMessage");
const productList = document.querySelector("#productList");
const productCount = document.querySelector("#productCount");
const submitButton = document.querySelector("#submitProduct");
const cancelEditButton = document.querySelector("#cancelEdit");
const refreshButton = document.querySelector("#refreshProducts");
const ordersMessage = document.querySelector("#ordersMessage");
const adminOrdersList = document.querySelector("#adminOrdersList");
const refreshOrdersButton = document.querySelector("#refreshOrders");
const orderStatusFilter = document.querySelector("#orderStatusFilter");
let editingProductId = null;

function showMessage(element, message, type = "") {
    element.textContent = message;
    element.className = `form-message ${type}`.trim();
}

async function readResponse(response) {
    const text = await response.text();
    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return { message: text };
    }
}

function responseMessage(data, fallback) {
    if (!data) {
        return fallback;
    }

    const details = data.errors && typeof data.errors === "object"
        ? Object.entries(data.errors).map(([field, message]) => `${field}: ${message}`)
        : [];
    const message = data.message || data.detail || data.title;

    return [message, ...details].filter(Boolean).join(". ") || fallback;
}

function showAdminSignInLink(element, data, action = "admin request") {
    showMessage(
        element,
        `${data?.message || "The order service rejected this admin session."} The ${action} was not confirmed; your session is still saved.`
    );
    const link = document.createElement("a");
    link.href = "auth.html";
    link.textContent = " Sign in again";
    link.addEventListener("click", () => {
        sessionStorage.setItem("mw.returnTo", "/admin.html");
    });
    element.appendChild(link);
}

async function adminFetch(url, options = {}) {
    const token = localStorage.getItem("accessToken");
    if (!token) {
        return new Response(
            JSON.stringify({message: "Authentication is required"}),
            {status: 401, headers: {"Content-Type": "application/json"}}
        );
    }

    const headers = new Headers(options.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return fetch(url, {...options, headers, cache: "no-store"});
}

function imageUrlFor(url) {
    if (!url) {
        return "";
    }

    try {
        const resolved = new URL(url, API_BASE);
        return ["http:", "https:"].includes(resolved.protocol) ? resolved.href : "";
    } catch {
        return "";
    }
}

function renderProducts(products) {
    productCount.textContent = `${products.length} active ${products.length === 1 ? "product" : "products"}`;
    productList.replaceChildren();

    if (!products.length) {
        const emptyMessage = document.createElement("p");
        emptyMessage.className = "field-hint";
        emptyMessage.textContent = "No active products yet. Add your first product above.";
        productList.appendChild(emptyMessage);
        return;
    }

    products.forEach(product => {
        const row = document.createElement("article");
        row.className = "product-row";

        const imageUrl = imageUrlFor(product.imageUrl);
        if (imageUrl) {
            const image = document.createElement("img");
            image.className = "product-thumb";
            image.src = imageUrl;
            image.alt = "";
            row.appendChild(image);
        } else {
            const placeholder = document.createElement("div");
            placeholder.className = "product-thumb product-placeholder";
            placeholder.setAttribute("aria-hidden", "true");
            placeholder.textContent = "No image";
            row.appendChild(placeholder);
        }

        const details = document.createElement("div");
        details.className = "product-details";
        const name = document.createElement("strong");
        name.textContent = String(product.name ?? "");
        const description = document.createElement("span");
        description.textContent = `${String(product.category ?? "")}${product.color ? ` · ${String(product.color)}` : ""}`;
        details.append(name, description);

        const inventory = document.createElement("div");
        inventory.className = "product-inventory";
        inventory.appendChild(document.createTextNode(
            new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(product.price))
        ));
        const stock = document.createElement("span");
        stock.textContent = `Stock: ${String(product.stock ?? "")}`;
        inventory.appendChild(stock);

        const actions = document.createElement("div");
        actions.className = "product-actions";
        for (const [action, label] of [["edit", "Edit"], ["delete", "Delete"]]) {
            const button = document.createElement("button");
            button.className = "secondary-btn";
            button.type = "button";
            button.dataset.productAction = action;
            button.dataset.productId = String(product.id);
            button.textContent = label;
            actions.appendChild(button);
        }

        row.append(details, inventory, actions);
        productList.appendChild(row);
    });
}

async function loadProducts() {
    refreshButton.disabled = true;
    showMessage(listMessage, "Loading products…");

    try {
        const response = await fetch(`${API_BASE}/admin/products`, {
            headers: { Authorization: `Bearer ${localStorage.getItem("accessToken")}` }
        });
        const data = await readResponse(response);

        if (!response.ok) {
            showMessage(listMessage, responseMessage(data, "Unable to load products"), "error");
            return false;
        }

        renderProducts(Array.isArray(data) ? data : []);
        showMessage(listMessage, "");
        return true;
    } catch {
        showMessage(listMessage, "Unable to connect to the product service.", "error");
        return false;
    } finally {
        refreshButton.disabled = false;
    }
}

function statusLabel(status) {
    return ({
        PLACED: "Placed",
        SHIPPED: "Shipped",
        DELIVERED: "Delivered",
        CANCELLED: "Cancelled"
    })[status] || String(status ?? "");
}

function orderDate(value) {
    if (typeof value !== "string") {
        return "";
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? "" : new Date(timestamp).toLocaleString();
}

function money(value) {
    const amount = Number(value);
    return Number.isFinite(amount)
        ? new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount)
        : "";
}

function renderAdminOrder(order) {
    const details = document.createElement("details");
    details.className = "admin-order";
    const summary = document.createElement("summary");
    summary.className = "admin-order-summary";

    const fields = [
        ["Order", `#${order.id}`],
        ["Date", orderDate(order.createdAt)],
        ["Customer", String(order.customerEmail ?? "")],
        ["Status", statusLabel(order.status)],
        ["Total", money(order.totalAmount)]
    ];
    fields.forEach(([label, value]) => {
        const field = document.createElement("div");
        const fieldLabel = document.createElement("span");
        fieldLabel.textContent = label;
        const fieldValue = document.createElement("strong");
        fieldValue.textContent = value;
        field.append(fieldLabel, fieldValue);
        summary.appendChild(field);
    });
    details.appendChild(summary);

    const items = document.createElement("ul");
    items.className = "order-items";
    (Array.isArray(order.items) ? order.items : []).forEach(item => {
        const row = document.createElement("li");
        const description = document.createElement("span");
        description.textContent = `${String(item.productName ?? "Product")} · ${String(item.size ?? "One size")} · ${Number(item.quantity)} × ${money(item.unitPrice)}`;
        const total = document.createElement("strong");
        total.textContent = money(item.lineTotal);
        row.append(description, total);
        items.appendChild(row);
    });
    details.appendChild(items);

    const delivery = document.createElement("p");
    delivery.className = "order-delivery";
    delivery.textContent = [
        `Payment: ${order.paymentMethod === "COD" ? "Cash on Delivery" : String(order.paymentMethod ?? "")}`,
        order.recipientName,
        order.recipientPhone,
        order.deliveryAddress,
        [order.deliveryCity, order.deliveryState, order.deliveryPostalCode].filter(Boolean).join(", ")
    ].filter(Boolean).join(" · ");
    details.appendChild(delivery);

    const actions = document.createElement("div");
    actions.className = "order-actions";
    const nextStatuses = order.status === "PLACED"
        ? [["SHIPPED", "Mark shipped"], ["CANCELLED", "Cancel order"]]
        : order.status === "SHIPPED"
            ? [["DELIVERED", "Mark delivered"]]
            : [];
    nextStatuses.forEach(([status, label]) => {
        const button = document.createElement("button");
        button.className = "secondary-btn";
        button.type = "button";
        button.dataset.orderId = String(order.id);
        button.dataset.nextStatus = status;
        button.textContent = label;
        actions.appendChild(button);
    });
    if (nextStatuses.length) {
        details.appendChild(actions);
    }
    return details;
}

async function loadOrders() {
    refreshOrdersButton.disabled = true;
    adminOrdersList.replaceChildren();
    showMessage(ordersMessage, "Loading orders…");
    const filter = orderStatusFilter.value;
    const query = filter ? `?status=${encodeURIComponent(filter)}` : "";

    try {
        const response = await adminFetch(`${API_BASE}/admin/orders${query}`, {
            headers: { Authorization: `Bearer ${localStorage.getItem("accessToken")}` }
        });
        const data = await readResponse(response);
        if (!response.ok) {
            if (response.status === 401) {
                showAdminSignInLink(ordersMessage, data, "order list request");
                return false;
            }
            showMessage(ordersMessage, responseMessage(data, "Unable to load orders."), "error");
            return false;
        }

        if (!Array.isArray(data) || data.length === 0) {
            showMessage(ordersMessage, "No orders found.");
            return true;
        }
        adminOrdersList.replaceChildren(...data.map(renderAdminOrder));
        showMessage(ordersMessage, "");
        return true;
    } catch {
        showMessage(ordersMessage, "Unable to connect to the order service.", "error");
        return false;
    } finally {
        refreshOrdersButton.disabled = false;
    }
}

function beginProductEdit(product) {
    editingProductId = product.id;
    form.elements.name.value = product.name ?? "";
    form.elements.description.value = product.description ?? "";
    form.elements.price.value = product.price ?? "";
    form.elements.category.value = product.category ?? "";
    form.elements.color.value = product.color ?? "";
    form.elements.stock.value = product.stock ?? "";
    form.elements.image.value = "";
    formHeading.textContent = "Edit product";
    submitButton.textContent = "Save changes";
    cancelEditButton.hidden = false;
    showMessage(formMessage, "Editing product. Leave the image empty to keep the current image.");
    form.scrollIntoView({ behavior: "smooth", block: "start" });
}

function resetProductForm() {
    editingProductId = null;
    form.reset();
    formHeading.textContent = "Add a product";
    submitButton.textContent = "Add product";
    cancelEditButton.hidden = true;
    showMessage(formMessage, "");
}

async function initializeAdminPage() {
    const token = localStorage.getItem("accessToken");
    const role = String(localStorage.getItem("userRole") || "").toUpperCase();

    if (!token || role !== "ADMIN") {
        showMessage(
            accessMessage,
            token
                ? "This page is for administrator accounts only."
                : "Sign in with an administrator account to manage products.",
            "error"
        );
        return;
    }

    const loaded = await loadProducts();
    if (!loaded) {
        showMessage(accessMessage, "Administrator access could not be verified.", "error");
        return;
    }

    workspace.hidden = false;
    showMessage(accessMessage, "Administrator access verified.", "success");
    await loadOrders();
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    showMessage(formMessage, "");
    submitButton.disabled = true;

    const updating = editingProductId !== null;
    const endpoint = updating
        ? `${API_BASE}/admin/products/${encodeURIComponent(editingProductId)}`
        : `${API_BASE}/admin/products`;

    try {
        const response = await fetch(endpoint, {
            method: updating ? "PUT" : "POST",
            headers: { Authorization: `Bearer ${localStorage.getItem("accessToken")}` },
            body: new FormData(form)
        });
        const data = await readResponse(response);

        if (!response.ok) {
            showMessage(formMessage, responseMessage(data, updating ? "Product update failed." : "Product creation failed."), "error");
            return;
        }

        resetProductForm();
        await loadProducts();
        showMessage(formMessage, updating ? "Product updated successfully." : "Product created successfully.", "success");
    } catch {
        showMessage(formMessage, "Unable to connect to the product service.", "error");
    } finally {
        submitButton.disabled = false;
    }
});

productList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-product-action]");
    if (!button) {
        return;
    }
    const productId = button.dataset.productId;
    if (button.dataset.productAction === "edit") {
        button.disabled = true;
        try {
            const response = await fetch(`${API_BASE}/products/${encodeURIComponent(productId)}`);
            const data = await readResponse(response);
            if (!response.ok) {
                showMessage(listMessage, responseMessage(data, "Unable to load product details."), "error");
                return;
            }
            beginProductEdit(data);
        } catch {
            showMessage(listMessage, "Unable to connect to the product service.", "error");
        } finally {
            button.disabled = false;
        }
        return;
    }

    if (button.dataset.productAction === "delete" &&
        window.confirm("Delete this product from the active catalogue?")) {
        button.disabled = true;
        try {
            const response = await fetch(`${API_BASE}/admin/products/${encodeURIComponent(productId)}`, {
                method: "DELETE",
                headers: { Authorization: `Bearer ${localStorage.getItem("accessToken")}` }
            });
            const data = await readResponse(response);
            if (!response.ok) {
                showMessage(listMessage, responseMessage(data, "Product deletion failed."), "error");
                return;
            }
            if (await loadProducts()) {
                showMessage(listMessage, "Product deleted from the active catalogue.", "success");
            }
        } catch {
            showMessage(listMessage, "Unable to connect to the product service.", "error");
        } finally {
            button.disabled = false;
        }
    }
});

adminOrdersList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-next-status]");
    if (!button) {
        return;
    }
    const { orderId, nextStatus } = button.dataset;
    button.disabled = true;
    try {
        const response = await adminFetch(`${API_BASE}/admin/orders/${encodeURIComponent(orderId)}/status`, {
            method: "PATCH",
            headers: {
                Authorization: `Bearer ${localStorage.getItem("accessToken")}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ status: nextStatus })
        });
        const data = await readResponse(response);
        if (!response.ok) {
            if (response.status === 401) {
                showAdminSignInLink(ordersMessage, data, "status update");
                return;
            }
            showMessage(ordersMessage, responseMessage(data, "Unable to update order status."), "error");
            return;
        }
        const currentOrder = button.closest(".admin-order");
        const updatedOrder = renderAdminOrder(data);
        updatedOrder.open = true;
        currentOrder?.replaceWith(updatedOrder);
        showMessage(ordersMessage, `Order #${data.id} updated to ${statusLabel(data.status)}.`, "success");
    } catch {
        showMessage(ordersMessage, "Unable to connect to the order service.", "error");
    } finally {
        button.disabled = false;
    }
});

refreshButton.addEventListener("click", loadProducts);
refreshOrdersButton.addEventListener("click", loadOrders);
orderStatusFilter.addEventListener("change", loadOrders);
cancelEditButton.addEventListener("click", resetProductForm);
initializeAdminPage();
