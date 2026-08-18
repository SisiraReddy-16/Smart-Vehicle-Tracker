// GaragePulse - Profile page behaviour
async function doLogout() {
  if (!confirm('Log out of GaragePulse?')) return;
  await apiPost('/logout', {});
  window.location.href = '../login/index.html';
}

document.getElementById('logout-btn').addEventListener('click', doLogout);
document.getElementById('logout-row').addEventListener('click', doLogout);

async function loadProfile() {
  const profile = await apiFetch('/profile');
  if (!profile) return; // apiFetch already redirected to login on 401, or showed a network error

  if (!profile.success && profile.success !== undefined) {
    document.getElementById('profile-username').textContent = 'Could not load profile';
    document.getElementById('profile-mobile').textContent = profile.message || '';
    return;
  }

  document.getElementById('profile-username').textContent = profile.username || 'GaragePulse User';
  document.getElementById('profile-mobile').textContent = profile.mobile || '-';
  document.getElementById('profile-vehicle-count').textContent = profile.vehicleCount ?? '0';
}

loadProfile();
