// GaragePulse - My Garage behaviour
const modal = document.getElementById('add-vehicle-modal');
const cardsContainer = document.getElementById('vehicle-cards');
const form = document.getElementById('add-vehicle-form');
const errorBox = document.getElementById('form-error');
const submitBtn = document.getElementById('submit-btn');
let selectedType = 'Car';

function openModal() { modal.classList.remove('hidden'); }
function closeModal() { modal.classList.add('hidden'); errorBox.classList.add('hidden'); form.reset(); }

document.getElementById('open-modal-desktop').addEventListener('click', openModal);
document.getElementById('open-modal-tile').addEventListener('click', openModal);
document.getElementById('open-modal-mobile').addEventListener('click', function (e) {
  e.preventDefault();
  openModal();
});
document.querySelectorAll('[data-close]').forEach(el => el.addEventListener('click', closeModal));

if (window.location.hash === '#add-vehicle-modal') {
  openModal();
}

document.querySelectorAll('.type-btn').forEach(btn => {
  btn.addEventListener('click', function () {
    document.querySelectorAll('.type-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    selectedType = btn.dataset.type;
  });
});

document.getElementById('logout-btn').addEventListener('click', async function () {
  if (!confirm('Log out of GaragePulse?')) return;
  await apiPost('/logout', {});
  window.location.href = '../login/index.html';
});

form.addEventListener('submit', async function (e) {
  e.preventDefault();
  errorBox.classList.add('hidden');

  const plate = document.getElementById('vehicle-plate').value.trim();
  const brand = document.getElementById('vehicle-brand').value.trim();
  const model = document.getElementById('vehicle-model').value.trim();
  const year = document.getElementById('vehicle-year').value.trim();

  if (!plate || !brand || !model) {
    showError('Plate, brand and model are required.');
    return;
  }

  submitBtn.disabled = true;
  const result = await apiPost('/vehicles', { plate, brand, model, type: selectedType, year });
  submitBtn.disabled = false;

  if (result && result.success) {
    closeModal();
    loadVehicles();
  } else {
    showError(result ? result.message : 'Could not add vehicle.');
  }
});

function showError(message) {
  errorBox.textContent = message;
  errorBox.classList.remove('hidden');
}

function vehicleIcon(type) {
  return type === 'Bike' ? 'two_wheeler' : 'directions_car';
}

function renderVehicleCard(v) {
  const iconClass = v.vehicleType === 'Bike' ? 'v-icon secondary' : 'v-icon';
  return `
    <div class="vehicle-card" data-vehicle-id="${v.id}">
      <div class="v-top">
        <span class="status-pill ok"><span class="dot"></span>${v.vehicleType}</span>
        <button type="button" class="remove-btn" title="Remove vehicle" data-remove-vehicle="${v.id}">
          <span class="material-symbols-outlined">delete</span>
        </button>
      </div>
      <div class="v-body">
        <div class="${iconClass}"><span class="material-symbols-outlined">${vehicleIcon(v.vehicleType)}</span></div>
        <div>
          <h3>${v.brand} ${v.model}</h3>
          <p class="plate">${v.licensePlate}</p>
        </div>
      </div>
      <div class="v-stats-basic">
        <span>Reg. Year: ${v.regYear || '-'}</span>
      </div>
      <div class="view-btn-row">
        <a href="../service-history/index.html?vehicleId=${v.id}" class="view-btn">Service</a>
        <a href="../insurance-history/index.html?vehicleId=${v.id}" class="view-btn">Insurance</a>
        <a href="../puc-history/index.html?vehicleId=${v.id}" class="view-btn">PUC</a>
      </div>
    </div>`;
}

async function removeVehicle(vehicleId, cardEl) {
  if (!confirm('Remove this vehicle? Its service, insurance and PUC history will be removed too. This cannot be undone.')) return;

  const result = await apiDelete('/vehicles', { id: vehicleId });
  if (result && result.success) {
    cardEl.remove();
    if (!cardsContainer.querySelector('.vehicle-card')) {
      cardsContainer.innerHTML = '<p class="loading-text">No vehicles yet - add your first ride!</p>';
    }
  } else if (result) {
    alert(result.message || 'Could not remove vehicle.');
  }
}

async function loadVehicles() {
  const vehicles = await apiFetch('/vehicles');
  if (!vehicles) return;

  if (vehicles.length === 0) {
    cardsContainer.innerHTML = '<p class="loading-text">No vehicles yet - add your first ride!</p>';
    return;
  }
  cardsContainer.innerHTML = vehicles.map(renderVehicleCard).join('');

  cardsContainer.querySelectorAll('[data-remove-vehicle]').forEach(btn => {
    btn.addEventListener('click', () => {
      const card = btn.closest('.vehicle-card');
      removeVehicle(btn.dataset.removeVehicle, card);
    });
  });
}

loadVehicles();
