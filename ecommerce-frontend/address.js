"use strict";

const ADDRESS_API = "http://localhost:8080/user/profile";
const addressForm = document.querySelector("#deliveryAddressForm");
const addressNotice = document.querySelector("#addressNotice");
const saveAddressButton = document.querySelector("#saveAddress");
const addressFields = ["name", "phone", "addressLine", "city", "state", "postalCode"];
const returnTo = new URLSearchParams(location.search).get("return") === "profile"
    ? "profile"
    : "checkout";

document.querySelector("#addressBack").href = returnTo === "profile"
    ? "profile.html"
    : "index.html";
document.querySelector("#addressBack").addEventListener("click", () => {
    if (returnTo === "checkout") {
        sessionStorage.removeItem("mw.resumeCheckout");
    }
});

function signInForAddress() {
    sessionStorage.setItem(
        "mw.returnTo",
        `${location.pathname}?return=${returnTo}`
    );
    location.href = "auth.html";
}

function setNotice(message, type = "") {
    addressNotice.textContent = message;
    addressNotice.className = `address-notice ${type}`.trim();
    addressNotice.hidden = !message;
}

function setFieldError(field, message) {
    const input = addressForm.elements.namedItem(field);
    const error = document.querySelector(`[data-error-for="${field}"]`);
    input.setAttribute("aria-invalid", message ? "true" : "false");
    error.textContent = message || "";
}

function fillForm(profile) {
    const values = {
        name: profile.recipientName || profile.name || "",
        phone: profile.recipientPhone || "",
        addressLine: profile.deliveryAddress || "",
        city: profile.deliveryCity || "",
        state: profile.deliveryState || "",
        postalCode: profile.deliveryPostalCode || ""
    };
    Object.entries(values).forEach(([field, value]) => {
        addressForm.elements.namedItem(field).value = value;
    });
}

async function readError(response) {
    let data;
    try {
        data = await response.json();
    } catch {
        return {message: `Unable to save address (HTTP ${response.status}).`};
    }
    return data && typeof data === "object"
        ? data
        : {message: `Unable to save address (HTTP ${response.status}).`};
}

async function loadAddress() {
    const token = localStorage.getItem("accessToken");
    if (!token) {
        signInForAddress();
        return;
    }

    try {
        const response = await fetch(ADDRESS_API, {
            headers: {Authorization: `Bearer ${token}`}
        });
        if (response.status === 401) {
            signInForAddress();
            return;
        }
        if (!response.ok) {
            setNotice(`Unable to load your saved address (HTTP ${response.status}).`, "error");
            return;
        }
        fillForm(await response.json());
        addressForm.elements.namedItem("name").focus({preventScroll: true});
    } catch (error) {
        console.error("Unable to load delivery address", error);
        setNotice("Unable to connect to your profile. Please try again.", "error");
    }
}

addressForm.addEventListener("input", event => {
    const field = event.target.name;
    if (addressFields.includes(field)) {
        setFieldError(field, "");
    }
});

addressForm.addEventListener("submit", async event => {
    event.preventDefault();
    let valid = true;
    addressFields.forEach(field => {
        const input = addressForm.elements.namedItem(field);
        const value = input.value.trim();
        const message = !value
            ? "Please fill out this field."
            : !input.checkValidity()
                ? field === "phone"
                    ? "Enter a valid 10-digit Indian mobile number."
                    : field === "postalCode"
                        ? "Enter a valid 6-digit PIN code."
                        : "Please check this value."
                : "";
        setFieldError(field, message);
        if (message) valid = false;
    });
    if (!valid) {
        addressForm.querySelector('[aria-invalid="true"]').focus();
        return;
    }

    const token = localStorage.getItem("accessToken");
    if (!token) {
        signInForAddress();
        return;
    }

    const fields = new FormData(addressForm);
    const address = Object.fromEntries(
        addressFields.map(field => [field, String(fields.get(field) || "").trim()])
    );
    saveAddressButton.disabled = true;
    saveAddressButton.querySelector("span").textContent = "Saving address…";
    setNotice("");
    try {
        const response = await fetch(ADDRESS_API, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${token}`
            },
            body: JSON.stringify(address)
        });
        if (response.status === 401) {
            signInForAddress();
            return;
        }
        if (!response.ok) {
            const data = await readError(response);
            const errors = data.errors && typeof data.errors === "object" ? data.errors : {};
            Object.entries(errors).forEach(([field, message]) => {
                const key = addressFields.includes(field)
                    ? field
                    : field.startsWith("deliveryAddress.")
                        ? field.slice("deliveryAddress.".length)
                        : "";
                if (key) setFieldError(key, String(message));
            });
            setNotice(data.message || "Please check your address details.", "error");
            return;
        }

        await response.json();
        setNotice("Address saved. Taking you back to checkout…", "success");
        if (returnTo === "checkout") {
            sessionStorage.setItem("mw.resumeCheckout", "true");
            location.href = "index.html";
        } else {
            location.href = "profile.html";
        }
    } catch (error) {
        console.error("Unable to save delivery address", error);
        setNotice("Unable to save your address. Please try again.", "error");
    } finally {
        saveAddressButton.disabled = false;
        saveAddressButton.querySelector("span").textContent = "Save address and continue";
    }
});

loadAddress();
