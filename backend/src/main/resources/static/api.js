const BASE = '';

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  };
}

function forceSignOut() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  const isSubdir = window.location.pathname.includes('/pages/');
  window.location.href = isSubdir ? 'signin.html' : 'pages/signin.html';
}
window.forceSignOut = forceSignOut;

async function apiFetch(path, options = {}) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { ...authHeaders(), ...(options.headers || {}) }
  });
  if (res.status === 401 || res.status === 403) {
    // Try refresh
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      const r = await fetch(BASE + '/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      });
      if (r.ok) {
        const data = await r.json();
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        // Retry original request
        const retriedRes = await fetch(BASE + path, {
          ...options,
          headers: { ...authHeaders(), ...(options.headers || {}) }
        });
        if (retriedRes.status === 401 || retriedRes.status === 403) {
          forceSignOut();
          return null;
        }
        return retriedRes;
      }
    }
    forceSignOut();
    return null;
  }
  return res;
}

window.apiFetch = apiFetch;
window.API_BASE = BASE;
