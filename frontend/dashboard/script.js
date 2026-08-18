// GaragePulse - Dashboard behaviour
document.querySelectorAll('a[href="#notifications"]').forEach(link => {
  link.addEventListener('click', function (e) {
    e.preventDefault();
    document.getElementById('notifications').scrollIntoView({ behavior: 'smooth' });
  });
});

document.getElementById('logout-btn').addEventListener('click', async function () {
  if (!confirm('Log out of GaragePulse?')) return;
  await apiPost('/logout', {});
  window.location.href = '../login/index.html';
});

// Dismissed notifications are remembered per-browser so a manually
// cleared alert doesn't reappear on refresh. Once the underlying due date
// actually changes (record updated/renewed), the notification's signature
// changes too and it comes back automatically if still relevant - dues
// that get resolved by adding a newer record simply stop being generated
// by the backend and disappear on their own, no dismissal needed.
const DISMISSED_KEY = 'gp_dismissed_notifs';

function getDismissed() {
  try { return JSON.parse(localStorage.getItem(DISMISSED_KEY) || '[]'); }
  catch (e) { return []; }
}

function dismissNotification(signature) {
  const dismissed = getDismissed();
  if (!dismissed.includes(signature)) dismissed.push(signature);
  localStorage.setItem(DISMISSED_KEY, JSON.stringify(dismissed));
}

async function loadDashboard() {
  const vehicles = await apiFetch('/vehicles');
  if (!vehicles) return; // apiFetch already redirected to login / showed a connection banner

  document.getElementById('vehicle-count').textContent = `My Vehicles: ${vehicles.length}`;

  const cost = await apiFetch('/cost');
  if (cost) {
    document.getElementById('upcoming-cost').textContent = formatMoney(cost.upcomingEstimate) + ' due soon';
  }

  const notifications = await apiFetch('/notifications');
  const list = document.getElementById('notif-list');
  list.innerHTML = '';

  if (!notifications) return; // connection banner already shown

  const dismissed = getDismissed();
  const visible = notifications.filter(n => !dismissed.includes(n.vehicle + '|' + n.category + '|' + n.dueDate));

  if (visible.length === 0) {
    list.innerHTML = '<p class="notif-empty">You\'re all caught up - nothing due in the next 30 days.</p>';
    return;
  }

  const severityIcon = { critical: 'error', warning: 'warning', info: 'info' };
  const severityClass = { critical: 'notif-critical', warning: 'notif-warn', info: 'notif-info' };

  visible.forEach(n => {
    const signature = n.vehicle + '|' + n.category + '|' + n.dueDate;
    const div = document.createElement('div');
    div.className = 'notif ' + severityClass[n.severity];
    div.innerHTML = `<span class="material-symbols-outlined">${severityIcon[n.severity]}</span>` +
      `<p>${n.message}</p>` +
      `<span class="material-symbols-outlined notif-dismiss" title="Dismiss">close</span>`;
    div.querySelector('.notif-dismiss').addEventListener('click', () => {
      dismissNotification(signature);
      div.remove();
      if (!list.children.length) {
        list.innerHTML = '<p class="notif-empty">You\'re all caught up - nothing due in the next 30 days.</p>';
      }
    });
    list.appendChild(div);
  });
}

loadDashboard();
