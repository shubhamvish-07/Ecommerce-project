"use strict";

const PROFILE_API = "http://localhost:8080/user/profile";
const profileMessage = document.querySelector("#profileMessage");
const profileContent = document.querySelector("#profileContent");

function setProfileMessage(message, type = "") {
    profileMessage.textContent = message;
    profileMessage.className = `form-message ${type}`.trim();
}

function signInForProfile() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("userRole");
    sessionStorage.setItem("mw.returnTo", `${location.pathname}${location.search}${location.hash}`);
    location.href = "auth.html";
}

function renderProfile(profile) {
    document.querySelector("#profileName").textContent = profile.name || "";
    document.querySelector("#profileEmail").textContent = profile.email || "";
    const addressFields = [
        profile.recipientName,
        profile.recipientPhone,
        profile.deliveryAddress,
        profile.deliveryCity,
        profile.deliveryState,
        profile.deliveryPostalCode
    ];
    const hasAddress = addressFields.every(value => typeof value === "string" && value.trim());
    document.querySelector("#addressSummary").hidden = !hasAddress;
    document.querySelector("#noAddress").hidden = hasAddress;
    if (hasAddress) {
        document.querySelector("#addressRecipient").textContent = profile.recipientName;
        document.querySelector("#addressPhone").textContent = profile.recipientPhone;
        document.querySelector("#addressLines").textContent =
            `${profile.deliveryAddress}, ${profile.deliveryCity}, ${profile.deliveryState} ${profile.deliveryPostalCode}`;
    }
    profileContent.hidden = false;
    setProfileMessage("");
}

async function loadProfile() {
    const token = localStorage.getItem("accessToken");
    if (!token) {
        signInForProfile();
        return;
    }
    try {
        const response = await fetch(PROFILE_API, {
            headers: {Authorization: `Bearer ${token}`}
        });
        if (response.status === 401) {
            signInForProfile();
            return;
        }
        if (!response.ok) {
            setProfileMessage(`Unable to load profile (HTTP ${response.status}).`, "error");
            return;
        }
        renderProfile(await response.json());
    } catch (error) {
        console.error("Unable to load profile", error);
        setProfileMessage("Unable to connect to your profile.", "error");
    }
}

loadProfile();
