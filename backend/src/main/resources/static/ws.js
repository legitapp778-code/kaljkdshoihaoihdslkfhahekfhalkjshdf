// ── Detect correct WebSocket URL ──
// On Railway (HTTPS), must use wss:// not ws://
// Since frontend and backend are same origin, derive from window.location
function getWsUrl() {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  const token = localStorage.getItem('accessToken');
  return `${proto}//${host}/ws?token=${encodeURIComponent(token)}`;
}

window.stompClient = new window.StompJs.Client({
  // Native WebSocket — no SockJS
  brokerURL: getWsUrl(),

  connectHeaders: {
    Authorization: `Bearer ${localStorage.getItem('accessToken')}`
  },

  // Railway kills idle connections — heartbeat keeps it alive
  heartbeatIncoming: 10000,  // expect server heartbeat every 10s
  heartbeatOutgoing: 10000,  // send heartbeat every 10s

  // Fast reconnect — don't freeze the game for 5 seconds
  reconnectDelay: 2000,

  onConnect: () => {
    console.log('[WS] Connected via native WebSocket');

    window.stompClient.subscribe('/topic/game/tick', (frame) => {
      if (typeof window.onTick === 'function') window.onTick(frame);
    });
    window.stompClient.subscribe('/topic/game/phase', (frame) => {
      if (typeof window.onPhaseChange === 'function') window.onPhaseChange(frame);
    });
    window.stompClient.subscribe('/topic/game/result', (frame) => {
      if (typeof window.onResult === 'function') window.onResult(frame);
    });
    window.stompClient.subscribe('/user/queue/bet-ack', (frame) => {
      if (typeof window.onBetAck === 'function') window.onBetAck(frame);
    });
    window.stompClient.subscribe('/user/queue/errors', (frame) => {
      if (typeof window.onWsError === 'function') window.onWsError(frame);
    });
    window.stompClient.subscribe('/user/queue/balance', (frame) => {
      if (typeof window.onBalanceUpdate === 'function') window.onBalanceUpdate(frame);
    });

    if (typeof window.syncStateOnReconnect === 'function') {
      window.syncStateOnReconnect();
    }
  },

  onDisconnect: () => {
    console.log('[WS] Disconnected — will auto-reconnect in 2s');
  },

  onStompError: async (frame) => {
    const msg = frame.headers?.message || '';
    console.error('[WS] STOMP error:', msg);

    if (msg.includes('TOKEN_EXPIRED') || msg.includes('expired')) {
      console.log('[WS] Token expired, refreshing...');
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        // Update brokerURL with new token and reconnect
        window.stompClient.brokerURL = getWsUrl();
        window.stompClient.connectHeaders = {
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`
        };
        window.stompClient.activate();
      }
    }
  },

  onWebSocketError: (event) => {
    console.error('[WS] WebSocket error:', event);
  }
});

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    window.location.href = '/pages/signin.html';
    return false;
  }
  try {
    const res = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    if (res.ok) {
      const data = await res.json();
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
      return true;
    }
  } catch (e) {
    console.error('[WS] Refresh failed:', e);
  }
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  window.location.href = '/pages/signin.html';
  return false;
}

// Activate only if logged in
if (localStorage.getItem('accessToken')) {
  window.stompClient.activate();
}
