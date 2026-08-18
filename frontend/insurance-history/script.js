// GaragePulse - Insurance History behaviour
const vehicleSelect = document.getElementById('vehicle-select');
const policyList = document.getElementById('policy-list');
const emptyState = document.getElementById('empty-state');
const modal = document.getElementById('add-insurance-modal');
const form = document.getElementById('add-insurance-form');
const errorBox = document.getElementById('form-error');
const submitBtn = document.getElementById('submit-btn');

let currentVehicleId = null;

document.getElementById('logout-btn').addEventListener('click', async function () {
  if (!confirm('Log out of GaragePulse?')) return;
  await apiPost('/logout', {});
  window.location.href = '../login/index.html';
});

document.getElementById('add-insurance-btn').addEventListener('click', function () {
  if (!currentVehicleId) { alert('Add a vehicle in My Garage first.'); return; }
  modal.classList.remove('hidden');
});
document.querySelectorAll('[data-close]').forEach(el => el.addEventListener('click', () => {
  modal.classList.add('hidden');
  errorBox.classList.add('hidden');
  form.reset();
}));

vehicleSelect.addEventListener('change', function () {
  currentVehicleId = vehicleSelect.value;
  loadPolicies();
});

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  errorBox.classList.add('hidden');

  const provider = document.getElementById('ins-provider').value.trim();
  const policyNumber = document.getElementById('ins-policy-number').value.trim();
  const startDate = document.getElementById('ins-start-date').value;
  const endDate = document.getElementById('ins-end-date').value;
  const premium = document.getElementById('ins-premium').value;

  if (!provider || !policyNumber || !startDate || !endDate || !premium) {
    errorBox.textContent = 'All fields are required.';
    errorBox.classList.remove('hidden');
    return;
  }

  submitBtn.disabled = true;
  const result = await apiPost('/insurance?vehicleId=' + currentVehicleId,
    { provider, policyNumber, startDate, endDate, premium });
  submitBtn.disabled = false;

  if (result && result.success) {
    modal.classList.add('hidden');
    form.reset();
    loadPolicies();
  } else {
    errorBox.textContent = result ? result.message : 'Could not save policy.';
    errorBox.classList.remove('hidden');
  }
});

function renderPolicyCard(p) {
  const isActive = p.status === 'Active';
  return `
    <div class="policy-card ${isActive ? '' : 'faded'}" data-record-id="${p.id}">
      <div class="policy-icon"><span class="material-symbols-outlined">${isActive ? 'gpp_good' : 'gpp_maybe'}</span></div>
      <div class="policy-info">
        <div class="policy-info-top">
          <h4>${p.provider}</h4>
          <span class="badge">${p.status}</span>
        </div>
        <p class="dates">${formatDate(p.startDate)} - ${formatDate(p.endDate)}</p>
        <p class="amount">${formatMoney(p.premium)}</p>
      </div>
      <button type="button" class="remove-btn" title="Remove policy" data-remove-policy="${p.id}">
        <span class="material-symbols-outlined">delete</span>
      </button>
    </div>`;
}

async function removePolicy(recordId, cardEl) {
  if (!confirm('Remove this insurance policy?')) return;

  const result = await apiDelete('/insurance', { id: recordId });
  if (result && result.success) {
    cardEl.remove();
    loadPolicies(); // refresh so the "Active Policy" summary reflects what's left
  } else if (result) {
    alert(result.message || 'Could not remove policy.');
  }
}

async function loadVehiclesIntoSelector() {
  const vehicles = await apiFetch('/vehicles');
  if (!vehicles) return;

  if (vehicles.length === 0) {
    vehicleSelect.innerHTML = '<option>No vehicles yet</option>';
    emptyState.classList.remove('hidden');
    emptyState.textContent = 'Add a vehicle in My Garage first.';
    return;
  }

  const params = new URLSearchParams(window.location.search);
  const preselect = params.get('vehicleId');

  vehicleSelect.innerHTML = vehicles.map(v =>
    `<option value="${v.id}">${v.brand} ${v.model} (${v.licensePlate})</option>`
  ).join('');

  currentVehicleId = preselect && vehicles.some(v => String(v.id) === preselect)
    ? preselect : String(vehicles[0].id);
  vehicleSelect.value = currentVehicleId;

  loadPolicies();
}

async function loadPolicies() {
  policyList.innerHTML = '';
  emptyState.classList.add('hidden');
  document.getElementById('active-policy-card').classList.remove('hidden');
  if (!currentVehicleId) return;

  const policies = await apiFetch('/insurance?vehicleId=' + currentVehicleId);
  if (!policies) return;

  if (policies.length === 0) {
    emptyState.classList.remove('hidden');
    document.getElementById('active-policy-card').classList.add('hidden');
    return;
  }

  // Backend already orders by end_date DESC, so index 0 is the most current policy.
  const latest = policies[0];
  document.getElementById('active-policy-number').textContent = latest.policyNumber;
  document.getElementById('active-policy-valid').textContent = 'Valid until ' + formatDate(latest.endDate);
  const statusEl = document.getElementById('active-policy-status');
  statusEl.innerHTML = `<span class="material-symbols-outlined">${latest.status === 'Active' ? 'check_circle' : 'error'}</span> ${latest.status}`;

  policyList.innerHTML = policies.map(renderPolicyCard).join('');

  policyList.querySelectorAll('[data-remove-policy]').forEach(btn => {
    btn.addEventListener('click', () => {
      const card = btn.closest('.policy-card');
      removePolicy(btn.dataset.removePolicy, card);
    });
  });
}

loadVehiclesIntoSelector();
