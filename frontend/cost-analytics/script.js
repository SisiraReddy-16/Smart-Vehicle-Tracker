// GaragePulse - Cost Analytics behaviour
async function loadCostSummary() {
  const cost = await apiFetch('/cost');
  if (!cost) return; // apiFetch redirects to login on 401

  document.getElementById('service-total').textContent = formatMoney(cost.serviceTotal);
  document.getElementById('insurance-total').textContent = formatMoney(cost.insuranceTotal);
  document.getElementById('puc-total').textContent = formatMoney(cost.pucTotal);
  document.getElementById('grand-total').textContent = formatMoney(cost.grandTotal);

  const upcoming = Number(cost.upcomingEstimate);
  document.getElementById('upcoming-note').textContent = upcoming > 0
    ? `Estimated ${formatMoney(upcoming)} coming due in the next 30 days.`
    : 'Nothing due in the next 30 days. 🎉';
}

loadCostSummary();
