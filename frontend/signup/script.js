const form = document.getElementById('signup-form');
const errorBox = document.getElementById('form-error');
const submitBtn = document.getElementById('submit-btn');

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  errorBox.classList.add('hidden');

  const username = document.getElementById('username').value.trim();
  const mobile = document.getElementById('mobile').value.trim();
  const password = document.getElementById('password').value;
  const confirm = document.getElementById('confirm_password').value;

  if (!username || !mobile || !password || !confirm) {
    showError('Please fill in every field.');
    return;
  }
  if (password !== confirm) {
    showError("Passwords don't match. Please double check and try again.");
    return;
  }

  submitBtn.disabled = true;
  const result = await apiPost('/signup', { username, mobile, password });
  submitBtn.disabled = false;

  if (result && result.success) {
    window.location.href = '../dashboard/index.html';
  } else if (result) {
    showError(result.message || 'Signup failed. Please try again.');
  }
});

function showError(message) {
  errorBox.textContent = message;
  errorBox.classList.remove('hidden');
}
