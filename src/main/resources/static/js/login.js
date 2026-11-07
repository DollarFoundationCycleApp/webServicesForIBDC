const loginForm = document.getElementById('login-form');
const loginMessage = document.getElementById('login-message');

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();

    loginMessage.textContent = '';

    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                usernameOrEmail: email,
                password: password
            })
        });

        const result = await response.json();

        if (!response.ok || !result.authenticated) {
            loginMessage.textContent = result.message || 'Invalid login.';
            return;
        }

        sessionStorage.setItem('userId', result.userId);
        window.location.href = result.isAdmin ? '/dashboard' : '/incident';
    } catch (error) {
        loginMessage.textContent = 'Unable to reach the server.';
    }
});

function togglePasswordVisibility() {
    const passwordInput = document.getElementById('password');
    if (passwordInput.type === 'password') {
        passwordInput.type = 'text';
    } else {
        passwordInput.type = 'password';
    }
}