// GaragePulse - Login screen behaviour
const form = document.getElementById('login-form');
const errorBox = document.getElementById('form-error');
const submitBtn = document.getElementById('submit-btn');

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  errorBox.classList.add('hidden');

  const username = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value;

  if (!username || !password) {
    showError('Please enter both username and password.');
    return;
  }

  submitBtn.disabled = true;
  const result = await apiPost('/login', { username, password });
  submitBtn.disabled = false;

  if (result && result.success) {
    window.location.href = '../dashboard/index.html';
  } else if (result) {
    showError(result.message || 'Login failed. Please try again.');
  }
  // If result is null, apiFetch already showed the connection banner
  // (server down, or unexpected response) - nothing more to do here.
});

function showError(message) {
  errorBox.textContent = message;
  errorBox.classList.remove('hidden');
}
