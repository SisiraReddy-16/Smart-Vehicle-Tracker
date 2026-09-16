const API_HOST = window.location.hostname || 'localhost';
const API_BASE = `https://${API_HOST}/api`;

async function apiFetch(path, options = {}) {
  let res;
  try {
    res = await fetch(API_BASE + path, {
      credentials: 'include', // sends the JSESSIONID cookie so the servlet knows who's logged in
      ...options
    });
  } catch (networkErr) {
    // Server process isn't running, wrong host/port, CORS blocked, etc.
    showConnectionBanner('Can\u2019t reach the GaragePulse server. Make sure the backend is running, then try again.');
    return null;
  }

  if (res.status === 401) {
    // Session missing/expired - bounce back to login.
    window.location.href = '../login/index.html';
    return null;
  }

  let body;
  try {
    body = await res.json();
  } catch (parseErr) {
    showConnectionBanner('The server returned an unexpected response. Please try again.');
    return null;
  }

  if (res.status === 503) {
    // Our own DBConnection throws a clear "database unreachable" message for this.
    showConnectionBanner(body && body.message ? body.message : 'The database is temporarily unreachable.');
    return null;
  }

  hideConnectionBanner();
  return body;
}

/** Convenience for POSTing simple form fields (application/x-www-form-urlencoded). */
function apiPost(path, fields) {
  return apiFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields).toString()
  });
}

/** Convenience for DELETEing a record, e.g. apiDelete('/vehicles', {id: 12}). */
function apiDelete(path, params) {
  const query = new URLSearchParams(params).toString();
  return apiFetch(path + (path.includes('?') ? '&' : '?') + query, { method: 'DELETE' });
}

function formatDate(isoDate) {
  if (!isoDate) return '-';
  const d = new Date(isoDate + 'T00:00:00');
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatMoney(amount) {
  return '\u20b9' + Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 0 });
}

/* ---------------------------------------------------------------------
 * Lightweight, dependency-free connectivity banner. Injected once per
 * page so every screen gets clear "can't reach the server/DB" feedback
 * instead of silently hanging on "Loading..." forever.
 * ------------------------------------------------------------------- */
let bannerEl = null;

function ensureBannerStyles() {
  if (document.getElementById('conn-banner-style')) return;
  const style = document.createElement('style');
  style.id = 'conn-banner-style';
  style.textContent = `
    #conn-banner{position:fixed; top:0; left:0; right:0; z-index:999; background:#b90b1e; color:#fff;
      font-family:'Plus Jakarta Sans', sans-serif; font-weight:700; font-size:13px; text-align:center;
      padding:10px 16px; box-shadow:0 2px 0 rgba(0,0,0,.15);}
    #conn-banner button{background:transparent; border:none; color:#fff; font-weight:800; margin-left:12px;
      cursor:pointer; text-decoration:underline;}
  `;
  document.head.appendChild(style);
}

function showConnectionBanner(message) {
  ensureBannerStyles();
  if (!bannerEl) {
    bannerEl = document.createElement('div');
    bannerEl.id = 'conn-banner';
    document.body.prepend(bannerEl);
  }
  bannerEl.innerHTML = '';
  const text = document.createElement('span');
  text.textContent = message;
  const retry = document.createElement('button');
  retry.type = 'button';
  retry.textContent = 'Retry';
  retry.addEventListener('click', () => window.location.reload());
  bannerEl.appendChild(text);
  bannerEl.appendChild(retry);
}

function hideConnectionBanner() {
  if (bannerEl) {
    bannerEl.remove();
    bannerEl = null;
  }
}
