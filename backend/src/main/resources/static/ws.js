const token = localStorage.getItem('accessToken');

// Make stompClient accessible globally so script.js can use it
window.stompClient = new window.StompJs.Client({
  webSocketFactory: () => new SockJS(`/ws?token=${localStorage.getItem('accessToken')}`),

  connectHeaders: {
    Authorization: `Bearer ${localStorage.getItem('accessToken')}`
  },

  onConnect: () => {
    console.log('STOMP Connected');
    
    // Subscribe to broadcast topics
    window.stompClient.subscribe('/topic/game/tick', (frame) => {
        if (typeof window.onTick === 'function') window.onTick(frame);
    });
    window.stompClient.subscribe('/topic/game/phase', (frame) => {
        if (typeof window.onPhaseChange === 'function') window.onPhaseChange(frame);
    });
    window.stompClient.subscribe('/topic/game/result', (frame) => {
        if (typeof window.onResult === 'function') window.onResult(frame);
    });

    // Subscribe to private user queues
    window.stompClient.subscribe('/user/queue/bet-ack', (frame) => {
        if (typeof window.onBetAck === 'function') window.onBetAck(frame);
    });
    window.stompClient.subscribe('/user/queue/errors', (frame) => {
        if (typeof window.onWsError === 'function') window.onWsError(frame);
    });
    window.stompClient.subscribe('/user/queue/balance', (frame) => {
        if (typeof window.onBalanceUpdate === 'function') window.onBalanceUpdate(frame);
    });

    // Sync state on connect/reconnect
    if (typeof window.syncStateOnReconnect === 'function') {
        window.syncStateOnReconnect();
    }
  },

  onStompError: async (frame) => {
    console.error('STOMP error', frame);
    const msg = (frame.headers.message || '').toLowerCase();
    if (msg.includes('expired') || msg.includes('invalid') || msg.includes('unauthorized') || msg.includes('revoked')) {
        console.log("Token invalid or expired, refreshing...");
        await refreshAccessToken();
        // Update headers and reconnect if token was successfully refreshed
        if (localStorage.getItem('accessToken')) {
            window.stompClient.connectHeaders = {
                Authorization: `Bearer ${localStorage.getItem('accessToken')}`
            };
            window.stompClient.webSocketFactory = () => new SockJS(`/ws?token=${localStorage.getItem('accessToken')}`);
            window.stompClient.activate();
        }
    }
  }
});

// Configure automatic reconnect
window.stompClient.reconnectDelay = 5000;

function wsForceSignOut() {
    if (window.forceSignOut) {
        window.forceSignOut();
    } else {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        const isSubdir = window.location.pathname.includes('/pages/');
        window.location.href = isSubdir ? 'signin.html' : 'pages/signin.html';
    }
}

async function refreshAccessToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
        wsForceSignOut();
        return;
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
        } else {
            wsForceSignOut();
        }
    } catch (e) {
        console.error("Failed to refresh token", e);
        wsForceSignOut();
    }
}

// Start connection if token exists
if (localStorage.getItem('accessToken')) {
    window.stompClient.activate();
}
