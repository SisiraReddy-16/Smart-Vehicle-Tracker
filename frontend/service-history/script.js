// GaragePulse - Service History behaviour
const vehicleSelect = document.getElementById('vehicle-select');
const timeline = document.getElementById('timeline');
const emptyState = document.getElementById('empty-state');
const nextDueEl = document.getElementById('next-due-date');
const modal = document.getElementById('add-service-modal');
const form = document.getElementById('add-service-form');
const errorBox = document.getElementById('form-error');
const submitBtn = document.getElementById('submit-btn');

let currentVehicleId = null;

document.getElementById('logout-btn').addEventListener('click', async function () {
  if (!confirm('Log out of GaragePulse?')) return;
  await apiPost('/logout', {});
  window.location.href = '../login/index.html';
});

document.getElementById('add-service-btn').addEventListener('click', function () {
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
  loadServices();
});

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  errorBox.classList.add('hidden');

  const serviceDate = document.getElementById('service-date').value;
  const cost = document.getElementById('service-cost').value;
  const serviceType = document.getElementById('service-type').value.trim();
  const shopName = document.getElementById('service-shop').value.trim();
  const nextDueDate = document.getElementById('service-next-due').value;

  if (!serviceDate || !cost || !serviceType) {
    errorBox.textContent = 'Service date, cost and type are required.';
    errorBox.classList.remove('hidden');
    return;
  }

  submitBtn.disabled = true;
  const result = await apiPost('/services?vehicleId=' + currentVehicleId,
    { serviceDate, cost, serviceType, shopName, nextDueDate });
  submitBtn.disabled = false;

  if (result && result.success) {
    modal.classList.add('hidden');
    form.reset();
    loadServices();
  } else {
    errorBox.textContent = result ? result.message : 'Could not save service record.';
    errorBox.classList.remove('hidden');
  }
});

function renderServiceCard(s) {
  return `
    <div class="tl-item" data-record-id="${s.id}">
      <div class="tl-node">
        <div class="tl-icon bg-tertiary"><span class="material-symbols-outlined">build</span></div>
        <span class="tl-date">${formatDate(s.serviceDate)}</span>
      </div>
      <div class="tl-card">
        <div class="tl-card-head">
          <div>
            <h3>${s.serviceType}</h3>
            <p class="shop"><span class="material-symbols-outlined">storefront</span> ${s.shopName || 'Not specified'}</p>
          </div>
          <div class="tl-card-actions">
            <span class="price">${formatMoney(s.cost)}</span>
            <button type="button" class="remove-btn" title="Remove record" data-remove-service="${s.id}">
              <span class="material-symbols-outlined">delete</span>
            </button>
          </div>
        </div>
      </div>
    </div>`;
}

async function removeServiceRecord(recordId, itemEl) {
  if (!confirm('Remove this service record?')) return;

  const result = await apiDelete('/services', { id: recordId });
  if (result && result.success) {
    itemEl.remove();
    loadServices(); // refresh so "Next Service Due" reflects the remaining latest record
  } else if (result) {
    alert(result.message || 'Could not remove service record.');
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

  loadServices();
}

async function loadServices() {
  timeline.innerHTML = '';
  emptyState.classList.add('hidden');
  if (!currentVehicleId) return;

  const services = await apiFetch('/services?vehicleId=' + currentVehicleId);
  if (!services) return;

  if (services.length === 0) {
    emptyState.classList.remove('hidden');
    nextDueEl.textContent = '-';
    return;
  }

  timeline.innerHTML = services.map(renderServiceCard).join('') +
    '<div class="tl-end"><span class="tl-dot"></span></div>';

  // Most recent record (index 0, since backend orders DESC) carries the next due date.
  nextDueEl.textContent = services[0].nextDueDate && services[0].nextDueDate !== 'null'
    ? formatDate(services[0].nextDueDate) : 'Not scheduled';
}

loadVehiclesIntoSelector();
