const registerForm = document.getElementById('register-form');
const registerMessage = document.getElementById('register-message');
const submitButton = document.querySelector('.submit-btn');

document.querySelectorAll('.toggle-password').forEach((toggle) => {
    toggle.addEventListener('click', () => togglePasswordVisibility(toggle));
});

registerForm.addEventListener('submit', async (event) => {
    event.preventDefault();

    registerMessage.textContent = '';

    const username = document.getElementById('username').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirm-password').value;

    if (password !== confirmPassword) {
        registerMessage.textContent = 'Passwords do not match.';
        return;
    }

    submitButton.disabled = true;

    try {
        const response = await fetch('/api/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: username,
                email: email,
                password: password,
                confirmPassword: confirmPassword
            })
        });

        const result = await response.json();

        if (!response.ok || !result.registered) {
            registerMessage.textContent = result.message || 'Could not create the account.';
            return;
        }

        sessionStorage.setItem('userId', result.userId);
        window.location.href = result.isAdmin ? '/dashboard' : '/incident';
    } catch (error) {
        registerMessage.textContent = 'Unable to reach the server.';
    } finally {
        submitButton.disabled = false;
    }
});

function togglePasswordVisibility(toggle) {
    const input = document.getElementById(toggle.dataset.target);
    if (input.type === 'password') {
        input.type = 'text';
        toggle.textContent = 'Hide';
    } else {
        input.type = 'password';
        toggle.textContent = 'Show';
    }
}
