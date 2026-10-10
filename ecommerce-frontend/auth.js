"use strict";

const loginForm = document.querySelector("#loginForm");
const registerForm = document.querySelector("#registerForm");

const formTitle = document.querySelector("#formTitle");
const formDescription = document.querySelector("#formDescription");

const switchText = document.querySelector("#switchText");
const switchLink = document.querySelector("#switchLink");

function showLoginForm() {

registerForm.classList.add("hidden");
loginForm.classList.remove("hidden");

formTitle.textContent = "Welcome back.";

formDescription.textContent =
    "Sign in to continue shopping and view your orders.";

switchText.textContent =
    "Don't have an account?";

switchLink.textContent = "Sign up";
switchLink.href = "#register";

clearMessages();

}

function showRegisterForm() {

loginForm.classList.add("hidden");
registerForm.classList.remove("hidden");

formTitle.textContent = "Create your account.";

formDescription.textContent =
    "Join us to save your details and keep track of your orders.";

switchText.textContent =
    "Already have an account?";

switchLink.textContent = "Login";
switchLink.href = "#login";

clearMessages();

}

if (window.location.hash === "#register") {
showRegisterForm();
} else {
showLoginForm();
}

switchLink.addEventListener("click", event => {

event.preventDefault();

if (loginForm.classList.contains("hidden")) {
    showLoginForm();
} else {
    showRegisterForm();
}

});

document.querySelectorAll(".show-password").forEach(button => {

button.addEventListener("click", () => {

    const targetId = button.dataset.target;
    const input = document.querySelector(`#${targetId}`);

    if (input.type === "password") {

        input.type = "text";
        button.textContent = "Hide";

    } else {

        input.type = "password";
        button.textContent = "Show";
    }

});

});

loginForm.addEventListener("submit", async event => {

event.preventDefault();

clearMessages();

const email =
    document.querySelector("#loginEmail").value.trim();

const password =
    document.querySelector("#loginPassword").value;

let valid = true;

if (!email) {

    showError(
        "loginEmailError",
        "Email is required"
    );

    valid = false;
}

if (!password) {

    showError(
        "loginPasswordError",
        "Password is required"
    );

    valid = false;
}

if (!valid) {
    return;
}

try {

    const response = await fetch(
        "http://localhost:8080/auth/login",
        {
            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify({
                email: email,
                password: password
            })
        }
    );

    const data = await response.json();

    if (!response.ok) {

        showMessage(
            "loginMessage",
            response.status === 429
                ? "Too many attempts. Please wait before trying again."
                : data.message || "Login failed",
            "error"
        );

        return;
    }

    localStorage.setItem(
        "accessToken",
        data.accessToken
    );

    if (data.role) {
        localStorage.setItem("userRole", data.role);
    } else {
        localStorage.removeItem("userRole");
    }

    showMessage(
        "loginMessage",
        "Login successful",
        "success"
    );

    setTimeout(() => {
        const returnTo = sessionStorage.getItem("mw.returnTo");
        sessionStorage.removeItem("mw.returnTo");
        window.location.href = returnTo && returnTo.startsWith("/") && !returnTo.startsWith("//")
            ? returnTo
            : "index.html";
    }, 700);

} catch (error) {

    showMessage(
        "loginMessage",
        "Unable to connect to server",
        "error"
    );
}

});

registerForm.addEventListener("submit", async event => {

event.preventDefault();

clearMessages();

const name =
    document.querySelector("#registerName").value.trim();

const email =
    document.querySelector("#registerEmail").value.trim();

const password =
    document.querySelector("#registerPassword").value;

let valid = true;

if (!name) {

    showError(
        "registerNameError",
        "Name is required"
    );

    valid = false;
}

if (!email) {

    showError(
        "registerEmailError",
        "Email is required"
    );

    valid = false;
}

if (!password) {

    showError(
        "registerPasswordError",
        "Password is required"
    );

    valid = false;

} else if (password.length < 12) {

    showError(
        "registerPasswordError",
        "Password must be at least 12 characters"
    );

    valid = false;
}

if (!valid) {
    return;
}

try {

    const response = await fetch(
        "http://localhost:8080/auth/register",
        {
            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify({
                name: name,
                email: email,
                password: password
            })
        }
    );

    const data = await response.json();

    if (!response.ok) {

        showMessage(
            "registerMessage",
            response.status === 429
                ? "Too many registration attempts. Please try again later."
                : data.message || "Registration failed",
            "error"
        );

        return;
    }

    document.querySelector("#loginEmail").value = email;

    document.querySelector("#registerPassword").value = "";

    showLoginForm();

    showMessage(
        "loginMessage",
        "Registration successful. Please sign in.",
        "success"
    );

} catch (error) {

    showMessage(
        "registerMessage",
        "Unable to connect to server",
        "error"
    );
}

});

function showError(id, message) {

document.querySelector(`#${id}`).textContent = message;

}

function showMessage(id, message, type) {

const element =
    document.querySelector(`#${id}`);

element.textContent = message;

element.className =
    `form-message ${type}`;

}

function clearMessages() {

document
    .querySelectorAll(".field-error")
    .forEach(element => {
        element.textContent = "";
    });

document
    .querySelectorAll(".form-message")
    .forEach(element => {
        element.textContent = "";
        element.className = "form-message";
    });

}