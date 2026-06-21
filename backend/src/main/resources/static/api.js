const BASE = '';

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  };
}

async function apiFetch(path, options = {}) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { ...authHeaders(), ...(options.headers || {}) }
  });
  if (res.status === 401) {
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
        return fetch(BASE + path, {
          ...options,
          headers: { ...authHeaders(), ...(options.headers || {}) }
        });
      }
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/pages/signin.html';
    return null;
  }
  return res;
}

window.apiFetch = apiFetch;
window.API_BASE = BASE;
