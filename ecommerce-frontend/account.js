"use strict";

document.addEventListener("DOMContentLoaded", () => {


const token = localStorage.getItem("accessToken");
const role = localStorage.getItem("userRole");

const header = document.querySelector("header");

if (!header) {
    return;
}

const headerContent = header.querySelector(".head-in") || header;
const accountArea = document.createElement("div");

accountArea.className = "account-area";

if (token) {

    const profileLink = document.createElement("a");

    profileLink.href = "profile.html";
    profileLink.className = "account-btn";
    profileLink.textContent = "Profile";

    accountArea.appendChild(profileLink);

    const ordersLink = document.createElement("a");

    ordersLink.href = "orders.html";
    ordersLink.className = "account-btn";
    ordersLink.textContent = "My orders";

    accountArea.appendChild(ordersLink);

    if (role === "ADMIN") {
        const adminLink = document.createElement("a");

        adminLink.href = "admin.html";
        adminLink.className = "account-btn";
        adminLink.textContent = "Manage products";

        accountArea.appendChild(adminLink);
    }

    const logoutButton = document.createElement("button");

    logoutButton.type = "button";
    logoutButton.className = "account-btn";
    logoutButton.textContent = "Logout";

    logoutButton.addEventListener("click", () => {

        localStorage.removeItem("accessToken");
        localStorage.removeItem("userRole");
        localStorage.removeItem("mw.address");

        window.location.href = "auth.html";

    });

    accountArea.appendChild(logoutButton);

} else {

    const loginLink = document.createElement("a");

    loginLink.href = "auth.html";
    loginLink.className = "account-btn";
    loginLink.textContent = "Sign in";

    accountArea.appendChild(loginLink);
}

headerContent.appendChild(accountArea);

});
