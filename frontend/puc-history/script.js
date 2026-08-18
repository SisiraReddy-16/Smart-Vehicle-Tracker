// GaragePulse - PUC History behaviour
const vehicleSelect = document.getElementById('vehicle-select');
const certList = document.getElementById('cert-list');
const emptyState = document.getElementById('empty-state');
const modal = document.getElementById('add-puc-modal');
const form = document.getElementById('add-puc-form');
const errorBox = document.getElementById('form-error');
const submitBtn = document.getElementById('submit-btn');

let currentVehicleId = null;

document.getElementById('logout-btn').addEventListener('click', async function () {
  if (!confirm('Log out of GaragePulse?')) return;
  await apiPost('/logout', {});
  window.location.href = '../login/index.html';
});

document.getElementById('add-puc-btn').addEventListener('click', function () {
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
  loadRecords();
});

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  errorBox.classList.add('hidden');

  const testDate = document.getElementById('puc-test-date').value;
  const validUntil = document.getElementById('puc-valid-until').value;
  const certNumber = document.getElementById('puc-cert-number').value.trim();
  const result = document.getElementById('puc-result').value;
  const cost = document.getElementById('puc-cost').value;

  if (!testDate || !validUntil) {
    errorBox.textContent = 'Test date and valid-until date are required.';
    errorBox.classList.remove('hidden');
    return;
  }

  submitBtn.disabled = true;
  const apiResult = await apiPost('/puc?vehicleId=' + currentVehicleId,
    { testDate, validUntil, certNumber, result, cost });
  submitBtn.disabled = false;

  if (apiResult && apiResult.success) {
    modal.classList.add('hidden');
    form.reset();
    loadRecords();
  } else {
    errorBox.textContent = apiResult ? apiResult.message : 'Could not save record.';
    errorBox.classList.remove('hidden');
  }
});

function renderCertCard(r) {
  const pass = r.result === 'Pass';
  return `
    <div class="cert-card ${pass ? '' : 'faded'}" data-record-id="${r.id}">
      <div class="cert-top">
        <div><p class="label">Date of Test</p><p class="value ${pass ? '' : 'strike'}">${formatDate(r.testDate)}</p></div>
        <div style="display:flex; align-items:center; gap:8px;">
          <div class="status ${pass ? 'pass' : 'fail'}"><span class="material-symbols-outlined">${pass ? 'check_circle' : 'warning'}</span> ${r.result}</div>
          <button type="button" class="remove-btn" title="Remove record" data-remove-puc="${r.id}">
            <span class="material-symbols-outlined">delete</span>
          </button>
        </div>
      </div>
      <div class="cert-grid">
        <div><p class="label small">Cert Number</p><p class="mono">${r.certNumber || '-'}</p></div>
        <div><p class="label small">Valid Until</p><p class="value">${formatDate(r.validUntil)}</p></div>
      </div>
    </div>`;
}

async function removePucRecord(recordId, cardEl) {
  if (!confirm('Remove this PUC record?')) return;

  const result = await apiDelete('/puc', { id: recordId });
  if (result && result.success) {
    cardEl.remove();
    loadRecords(); // refresh so "Next PUC Due" reflects the remaining latest record
  } else if (result) {
    alert(result.message || 'Could not remove PUC record.');
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

  loadRecords();
}

async function loadRecords() {
  certList.innerHTML = '';
  emptyState.classList.add('hidden');
  if (!currentVehicleId) return;

  const records = await apiFetch('/puc?vehicleId=' + currentVehicleId);
  if (!records) return;

  if (records.length === 0) {
    emptyState.classList.remove('hidden');
    document.getElementById('next-puc-date').textContent = '-';
    document.getElementById('puc-status-text').textContent = 'No data';
    return;
  }

  // Backend orders by test_date DESC -> index 0 is the latest record.
  const latest = records[0];
  document.getElementById('next-puc-date').textContent = formatDate(latest.validUntil);
  const overdue = new Date(latest.validUntil) < new Date();
  document.getElementById('puc-status-text').textContent = overdue ? 'Overdue - Renew Now' : 'Valid';

  certList.innerHTML = records.map(renderCertCard).join('');

  certList.querySelectorAll('[data-remove-puc]').forEach(btn => {
    btn.addEventListener('click', () => {
      const card = btn.closest('.cert-card');
      removePucRecord(btn.dataset.removePuc, card);
    });
  });
}

loadVehiclesIntoSelector();
