'use strict';

/* ══════════════════════════════════════════════════════════
   STATE
   ══════════════════════════════════════════════════════════ */
const STATE = {
  balancePaise: 0,
  bets: { tota: { row: null, amount: 0 }, mena: { row: null, amount: 0 } },
  panelBetAmounts: { tota: 0, mena: 0 },
  roundTimer: 0,
  phase: 'BETTING',
  multipliers: { tota: 2.00, mena: 1.50 },
  currentRoundId: null,
  winningRows: { tota: null, mena: null },
  selectedRows: { tota: null, mena: null },
  betStatus: { tota: null, mena: null }
};

let betDebounceTimer = { tota: null, mena: null };

let currentHistoryFilter = 'all';

/* ══════════════════════════════════════════════════════════
   DOM REFERENCES
   ══════════════════════════════════════════════════════════ */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];

const DOM = {};

function init() {
  const menuBtn = $('.menu-btn');
  if (menuBtn) {
    menuBtn.addEventListener('click', () => {
      document.body.classList.toggle('sidebar-open');
    });
  }
  document.body.addEventListener('click', (e) => {
    if (document.body.classList.contains('sidebar-open') && !e.target.closest('.sidebar-left') && !e.target.closest('.menu-btn')) {
      document.body.classList.remove('sidebar-open');
    }
  });
  DOM.manualAmtTota = $('#amt-tota');
  DOM.betBtnTota = $('#btn-tota');
  DOM.chipsTota = $$('#chips-tota .chip');
  DOM.manualAmtMena = $('#amt-mena');
  DOM.betBtnMena = $('#btn-mena');
  DOM.chipsMena = $$('#chips-mena .chip');
  DOM.cells = $$('.board__cell');
  DOM.multDisplay = $('#multDisplay');
  DOM.possWinDisplay = $('#possWinDisplay');
  DOM.timerDisplay = $('#timerDisplay');
  DOM.winOverlay = $('#winOverlay');
  DOM.winAmtDisplay = $('#winAmtDisplay');
  DOM.winCloseBtn = $('#winCloseBtn');

  setupPanel('tota', DOM.chipsTota, DOM.manualAmtTota, DOM.betBtnTota);
  setupPanel('mena', DOM.chipsMena, DOM.manualAmtMena, DOM.betBtnMena);

  ['tota', 'mena'].forEach(bird => {
    const track = document.getElementById(`reel-${bird}`);
    if (!track) return;
    const baseCells = Array.from(track.querySelectorAll('.board__cell'));
    for (let i = 0; i < 3; i++) {
      baseCells.forEach(cell => {
        const clone = cell.cloneNode(true);
        track.appendChild(clone);
      });
    }
  });

  DOM.allCells = $$('.board__cell');
  const reelTota = document.getElementById('reel-tota');
  const reelMena = document.getElementById('reel-mena');

  DOM.bettingCells = {
    tota: reelTota ? Array.from(reelTota.children).slice(0, 5) : [],
    mena: reelMena ? Array.from(reelMena.children).slice(0, 5) : []
  };

  DOM.bettingCells.tota.forEach(c => c.addEventListener('click', () => selectRow('tota', +c.dataset.row)));
  DOM.bettingCells.mena.forEach(c => c.addEventListener('click', () => selectRow('mena', +c.dataset.row)));

  if (DOM.winCloseBtn) DOM.winCloseBtn.addEventListener('click', closeWin);
  const winBg = $('.win-overlay__bg');
  if (winBg) winBg.addEventListener('click', closeWin);

  const isSubdirPage = window.location.pathname.includes('/pages/');
  const imgPrefix = isSubdirPage ? '../' : '';
  [`${imgPrefix}assets/tota.webp`, `${imgPrefix}assets/mena.webp`, `${imgPrefix}assets/emp_design.webp`].forEach(src => {
    const img = new Image();
    img.src = src;
  });

  DOM.allCells.forEach(cell => resetCellVisual(cell));

  updatePossibleWin();

  const token = localStorage.getItem('accessToken');
  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const userId = payload.sub.substring(0, 8).toUpperCase();
      const phone = payload.phone;

      const userNameElems = document.querySelectorAll('.user-name');
      userNameElems.forEach(el => {
        el.style.lineHeight = '1.2';
        el.style.textAlign = 'left';
        el.innerHTML = `${phone}<br><span style="font-size:10px;opacity:0.7">ID: ${userId}</span>`;
      });
    } catch (e) {
      console.error("Failed to parse token for user info", e);
    }
  }

  // Handled by window.syncStateOnReconnect in ws.js

  // WhatsApp Deposit/Withdrawal Integration
  const whatsappUrl = "#";
  document.querySelectorAll('.add-funds-btn, .wc-btn--deposit, .wc-btn--withdraw').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      window.open(whatsappUrl, '_blank');
    });
  });

  document.querySelectorAll('.qt-chip').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const amount = btn.textContent.replace(/[^0-9]/g, '');
      if (amount) {
        window.open(`${whatsappUrl}?text=I%20want%20to%20deposit%20${amount}`, '_blank');
      } else {
        window.open(whatsappUrl, '_blank');
      }
    });
  });

  // Removed fetchGlobalStats() and fetchUserStats() here, handled by loadSharedPageData in DOMContentLoaded
}

async function loadSharedPageData() {
  // 1. Load balance and user info from /api/v1/user/me and /api/v1/wallet
  const [userRes, walletRes] = await Promise.all([
    apiFetch('/api/v1/user/me'),
    apiFetch('/api/v1/wallet')
  ]);

  if (userRes && userRes.ok) {
    const user = await userRes.json();
    // Replace ALL elements with class "user-name" across every page
    document.querySelectorAll('.user-name').forEach(el => {
      const userId = user.id.substring(0, 8).toUpperCase();
      el.style.lineHeight = '1.2';
      el.style.textAlign = 'left';
      el.innerHTML = `${user.phone}<br><span style="font-size:10px;opacity:0.7">ID: ${userId}</span>`;
    });
    // Replace profile page avatar initials: element with class "prof-avatar"
    const profAvatar = document.querySelector('.prof-avatar');
    if (profAvatar) {
      profAvatar.textContent = user.phone.slice(-4); // last 4 digits as avatar
    }
    // Replace profile page name: element with class "prof-name"
    const profName = document.querySelector('.prof-name');
    if (profName) profName.textContent = user.displayName || user.phone;
    // Replace profile page ID: element with class "prof-id"
    const profId = document.querySelector('.prof-id');
    if (profId) profId.textContent = 'ID: ' + user.id.substring(0, 8).toUpperCase();
    // Replace profile page phone field
    const phoneInput = document.querySelector('input[type="text"]');
    if (phoneInput && window.location.pathname.includes('profile')) {
      phoneInput.value = user.phone;
    }
    // KYC badge: show "Verified" only if user.kycStatus === 'VERIFIED'
    const kycBadge = document.querySelector('.kyc-badge');
    if (kycBadge) {
      kycBadge.style.display = user.kycStatus === 'VERIFIED' ? 'flex' : 'none';
    }
  }

  if (walletRes && walletRes.ok) {
    const wallet = await walletRes.json();
    // Replace ALL elements with id "coins-val" across every page
    document.querySelectorAll('#coins-val').forEach(el => {
      el.textContent = Number(wallet.balancePaise / 100)
        .toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    });
    // Replace wallet page balance card: element with class "wc-bal"
    const wcBal = document.querySelector('.wc-bal');
    if (wcBal) {
      wcBal.textContent = '₹' + Number(wallet.balancePaise / 100)
        .toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    const wcDep = document.querySelector('.wc-dep');
    if (wcDep) {
      wcDep.textContent = '₹' + Number((wallet.depositBalancePaise || 0) / 100)
        .toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    const wcWin = document.querySelector('.wc-win');
    if (wcWin) {
      wcWin.textContent = '₹' + Number((wallet.winningBalancePaise || 0) / 100)
        .toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    // Update STATE.balancePaise if STATE exists (game page)
    if (typeof STATE !== 'undefined') STATE.balancePaise = wallet.balancePaise;
  }

  // 2. Load global stats — GET /api/v1/stats/global
  fetchGlobalStats();
}



async function fetchGlobalStats() {
  const cacheKey = 'cache_/api/v1/stats/global';
  const cached = sessionStorage.getItem(cacheKey);

  const renderData = (data) => {
    // LIVE STATS — update ALL .ls-item blocks on ANY page
    document.querySelectorAll('.ls-item').forEach(item => {
      const label = item.querySelector('.ls-label')?.textContent.trim();
      const valElem = item.querySelector('.ls-val');
      if (!valElem) return;
      if (label === 'PLAYERS ONLINE')
        valElem.textContent = Number(data.playersOnline).toLocaleString('en-IN');
      if (label === 'TOTAL BETS TODAY')
        valElem.textContent = '₹' + Number(data.totalBetsTodayPaise / 100)
          .toLocaleString('en-IN', { maximumFractionDigits: 0 });
      if (label === 'BIGGEST WIN TODAY')
        valElem.textContent = '₹' + Number(data.biggestWinTodayPaise / 100)
          .toLocaleString('en-IN', { maximumFractionDigits: 0 });
    });

    // RECENT RESULTS — update ALL .rr-list blocks on ANY page
    if (data.recentResults && data.recentResults.length > 0) {
      const isSubdir = window.location.pathname.includes('/pages/');
      const prefix = isSubdir ? '../' : '';
      document.querySelectorAll('.rr-list').forEach(rrList => {
        rrList.innerHTML = data.recentResults.map(r => `
          <div class="rr-row">
            <span class="rr-id">#${r.roundId.substring(0, 8).toUpperCase()}</span>
            <div class="rr-bird" style="display: flex; gap: 8px; align-items: center; justify-content: flex-end; width: 100%;">
              <div style="display:flex; align-items:center; gap:4px;"><img src="${prefix}assets/tota.webp" style="width:20px;height:20px;"> T${r.winningRowTota}</div>
              <div style="display:flex; align-items:center; gap:4px;"><img src="${prefix}assets/mena.webp" style="width:20px;height:20px;"> M${r.winningRowMena}</div>
            </div>
          </div>
        `).join('');
      });
    }

    // CURRENT ROUND on index.html top-stats bar
    updateCurrentRoundIdUI(data.currentRoundId);
  };

  if (cached) {
    try { renderData(JSON.parse(cached)); } catch(e){}
  }

  try {
    const res = await apiFetch('/api/v1/stats/global');
    if (!res || !res.ok) return;
    const data = await res.json();
    sessionStorage.setItem(cacheKey, JSON.stringify(data));
    renderData(data);
  } catch (e) {
    console.error("Failed to load shared page data", e);
  }
}

function updateCurrentRoundIdUI(roundId) {
  if (!roundId) return;
  const shortId = '#' + roundId.substring(0, 8).toUpperCase();
  document.querySelectorAll('.stat-card').forEach(card => {
    if (card.querySelector('.stat-title')?.textContent.trim() === 'CURRENT ROUND') {
      card.querySelector('.stat-val').textContent = shortId;
    }
  });
}

async function fetchUserStats() {
  const cacheKey = 'cache_/api/v1/stats/user';
  const cached = sessionStorage.getItem(cacheKey);

  const renderData = (data) => {
      document.querySelectorAll('.sd-card').forEach(card => {
        const label = card.querySelector('.sd-label')?.textContent.trim();
        const valElem = card.querySelector('.sd-val');
        if (!valElem) return;
        if (label === 'GAMES PLAYED')
          valElem.textContent = Number(data.gamesPlayed).toLocaleString('en-IN');
        if (label === 'WIN RATE')
          valElem.textContent = Number(data.winRate).toFixed(1) + '%';
        if (label === 'BEST MULT')
          valElem.textContent = Number(data.bestMultiplier).toFixed(1) + 'x';
        // transactions.html stats
        if (label === 'DEPOSITED (MONTH)')
          valElem.textContent = '₹' + Number(data.depositedThisMonthPaise / 100)
            .toLocaleString('en-IN', { maximumFractionDigits: 0 });
        if (label === 'WITHDRAWN (MONTH)')
          valElem.textContent = '₹' + Number(data.withdrawnThisMonthPaise / 100)
            .toLocaleString('en-IN', { maximumFractionDigits: 0 });
      });

      // home.html: total winnings quick stat
      document.querySelectorAll('.qs-item, .home-summary-card').forEach(item => {
        const labelElem = item.querySelector('.qs-label, .summary-label');
        if (!labelElem) return;
        const label = labelElem.textContent.trim().toUpperCase();
        const valElem = item.querySelector('.qs-val, .summary-value');
        if (!valElem) return;
        if (label === 'TOTAL WINNINGS')
          valElem.textContent = '₹' + Number((data.totalWinningsPaise || 0) / 100)
            .toLocaleString('en-IN', { maximumFractionDigits: 0 });
        if (label === 'CURRENT TIER')
          valElem.textContent = data.vipTier || 'STANDARD';
      });
      // profile.html: user specific data
      const upName = document.querySelector('.up-name');
      if (upName) upName.textContent = data.gamesPlayed + ' Games Played';
      const profPhone = document.getElementById('prof-phone');
      if (profPhone) profPhone.value = data.phone || '';
  };

  if (cached) {
    try { renderData(JSON.parse(cached)); } catch(e){}
  }

  try {
    const isResultsPage = window.location.pathname.includes('results');
    const isTransactionsPage = window.location.pathname.includes('transactions');
    
    const reqs = [apiFetch('/api/v1/stats/user')];
    if (!isResultsPage) {
        reqs.push(apiFetch('/api/v1/game/history?page=0&size=20'));
    } else {
        reqs.push(Promise.resolve(null));
    }
    
    if (isTransactionsPage) {
        reqs.push(apiFetch('/api/v1/wallet/transactions?page=0&size=20'));
    } else {
        reqs.push(Promise.resolve(null));
    }

    const [res, historyRes, txRes] = await Promise.all(reqs);

    if (res && res.ok) {
      const data = await res.json();
      sessionStorage.setItem(cacheKey, JSON.stringify(data));
      renderData(data);

      // History and transaction loading logic
      if (isResultsPage) {
        if (typeof loadHistoryTable === 'function') loadHistoryTable();
      }
      if (isTransactionsPage) {
        if (typeof loadTransactionsTable === 'function') loadTransactionsTable();
      }

      const logoutBtn = document.querySelector('.logout-btn');
      const isSubdir = window.location.pathname.includes('/pages/');
      if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
          window.location.href = isSubdir ? '../pages/signin.html' : 'pages/signin.html';
        });
      }

      const deleteAccountBtn = document.getElementById('delete-account-btn');
      if (deleteAccountBtn) {
        deleteAccountBtn.addEventListener('click', async () => {
          if (confirm("Are you sure you want to delete your account? This action cannot be undone.")) {
            try {
              const resDel = await apiFetch('/api/v1/user/me', { method: 'DELETE' });
              if (resDel.ok) {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                window.location.href = isSubdir ? '../pages/signin.html' : 'pages/signin.html';
              } else {
                alert("Failed to delete account. Please try again.");
              }
            } catch (e) {
              alert("Error deleting account: " + e.message);
            }
          }
        });
      }
      // home.html: hero banner welcome name
      const heroBanner = document.querySelector('.hero-banner h3');
      if (heroBanner) heroBanner.textContent = `Welcome Back!`;
    }

    // Game history — GET /api/v1/game/history?page=0&size=20
    if (historyRes && historyRes.ok) {
      const historyData = await historyRes.json();
      const historyTable = document.querySelector('.history-table');
      if (historyTable && !isResultsPage && historyData.content && historyData.content.length > 0) {
        const headerHtml = `<div class="ht-header"><span>Type</span><span>Amount</span><span>Date</span></div>`;

        historyTable.innerHTML = headerHtml + historyData.content.map(b => {
          const roundShort = '#' + b.roundId.substring(0, 8).toUpperCase();
          const birdClass = b.bird === 'tota' ? 'ht-val--green' : 'ht-val--brown';
          const won = b.status === 'WON';
          const amtDisplay = won
            ? '+₹' + Number(b.payoutPaise / 100).toLocaleString('en-IN')
            : '-₹' + Number(b.amountPaise / 100).toLocaleString('en-IN');
          const color = won ? 'var(--green)' : 'var(--red)';
          return `
            <div class="ht-row">
              <span class="ht-val">${roundShort}</span>
              <span class="ht-val ${birdClass}">${b.bird.toUpperCase()}</span>
              <span class="ht-val" style="color:${color}">${amtDisplay}</span>
            </div>`;
        }).join('');
      }
    }

    // Transactions page — GET /api/v1/wallet/transactions?page=0&size=20
    if (txRes && txRes.ok) {
        const txData = await txRes.json();
        const txTable = document.querySelector('.history-table');
        if (txTable && txData.content && txData.content.length > 0) {
          txTable.innerHTML = `<div class="ht-header"><span>Type</span><span>Amount</span><span>Date</span></div>`
            + txData.content.map(tx => {
              const isCredit = tx.type === 'BET_WON' || tx.type === 'DEPOSIT' || tx.type === 'REFUND';
              const label = {
                BET_PLACED: 'Bet Placed', BET_WON: 'Bet Won', BET_LOST: 'Bet Lost',
                DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal', REFUND: 'Refund'
              }[tx.type] || tx.type;
              const sign = isCredit ? '+' : '-';
              const cls = isCredit ? 'ht-val--green' : 'ht-val--brown';
              const date = new Date(tx.createdAt).toLocaleDateString('en-IN',
                { day: '2-digit', month: 'short', year: 'numeric' });
              return `
                <div class="ht-row">
                  <span class="ht-val">${label}</span>
                  <span class="ht-val ${cls}">${sign}₹${Number(Math.abs(tx.amountPaise) / 100)
                  .toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
                  <span class="ht-val" style="color:#999;font-size:10px;">${date}</span>
                </div>`;
            }).join('');
        }
      }
  } catch (e) {
    console.error('Error fetching user stats', e);
  }
}

function updateBetButtonStates() {
  ['tota', 'mena'].forEach(bird => {
    const btn = document.getElementById(`btn-${bird}`);
    const label = btn ? btn.querySelector('.bet-btn__label') : null;
    if (!label) return;

    const status = STATE.betStatus[bird];
    const hasBet = STATE.bets[bird].amount > 0;

    if (status === 'WON') {
      label.textContent = `WON ✓ +₹${fmtCur(STATE.bets[bird].amount * STATE.multipliers[bird])}`;
      btn.style.background = 'linear-gradient(135deg, #16a34a, #15803d)';
      btn.style.borderColor = '#16a34a';
      btn.disabled = true;
    } else if (status === 'LOST') {
      label.textContent = `LOST ✗ -₹${fmtCur(STATE.bets[bird].amount)}`;
      btn.style.background = 'linear-gradient(135deg, #dc2626, #b91c1c)';
      btn.style.borderColor = '#dc2626';
      btn.disabled = true;
    } else if (hasBet && STATE.phase === 'BETTING') {
      label.textContent = `CANCEL BET`;
      btn.style.background = 'linear-gradient(135deg, #dc2626, #991b1b)';
      btn.style.borderColor = '#dc2626';
      btn.disabled = false;
    } else if (status === 'PLACING') {
      label.textContent = `PLACING...`;
      btn.style.background = '#eab308';
      btn.style.borderColor = '#ca8a04';
      btn.disabled = true;
    } else if (hasBet && STATE.phase !== 'BETTING') {
      label.textContent = `BET PLACED ✓ ₹${fmtCur(STATE.bets[bird].amount)}`;
      btn.style.background = 'linear-gradient(135deg, #c9a04c, #a07830)';
      btn.style.borderColor = '#c9a04c';
      btn.disabled = true;
    } else {
      // Default state — restore original per-bird style
      label.textContent = bird === 'tota' ? 'BET ON TOTA' : 'BET ON MENA';
      btn.style.background = '';
      btn.style.borderColor = '';
      btn.disabled = false;
    }
  });
}


function resetCellVisual(cell) {
  const bird = cell.querySelector('.bird');
  const lbl = cell.querySelector('.bird-lbl');
  if (bird) {
    bird.src = `assets/${cell.dataset.bird}.webp`;
    bird.style.opacity = '0';
    bird.style.transform = 'translate(-50%, -50%) scale(0.4)';
  }
  if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
  cell.removeAttribute('data-active');
  cell.removeAttribute('data-win');
  cell.classList.remove('cell--spinning', 'cell--winner');
}

function setupPanel(bird, chips, input, btn) {
  chips.forEach(chip => {
    chip.addEventListener('click', () => {
      if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
      if (STATE.bets[bird] && STATE.bets[bird].amount > 0) return showToast('Cancel your current bet first to change the amount!');
      
      STATE.panelBetAmounts[bird] = +chip.dataset.amt;
      chips.forEach(c => c.classList.remove('chip--selected'));
      chip.classList.add('chip--selected');
      if (input) input.value = '';
      renderBoard();
      updatePossibleWin();
    });
  });
  if (input) {
    input.addEventListener('input', () => {
      if (STATE.bets[bird] && STATE.bets[bird].amount > 0) {
        input.value = STATE.panelBetAmounts[bird] || '';
        return showToast('Cancel your current bet first to change the amount!');
      }
      STATE.panelBetAmounts[bird] = +input.value || 0;
      chips.forEach(c => c.classList.remove('chip--selected'));
      renderBoard();
      updatePossibleWin();
    });
  }
  if (btn) {
    btn.addEventListener('click', () => {
      if (STATE.bets[bird] && STATE.bets[bird].amount > 0 && STATE.phase === 'BETTING') {
        cancelBet(bird);
      } else {
        placeBet(bird);
      }
    });
  }
}

function selectRow(bird, row) {
  if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
  STATE.selectedRows[bird] = row;
  
  // IMMEDIATELY render board to show selection, regardless of whether a bet exists
  renderBoard();

  // Automatically update the bet ONLY if they've already placed one
  if (STATE.bets[bird] && STATE.bets[bird].amount > 0) {
    clearTimeout(betDebounceTimer[bird]);
    betDebounceTimer[bird] = setTimeout(() => {
      if (STATE.selectedRows[bird] === row && STATE.phase === 'BETTING') {
        placeBet(bird);
      }
    }, 300);
  }
}

async function placeBet(bird) {
  if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
  const row = STATE.selectedRows[bird];
  if (!row) return showToast(`Select a card in ${bird.toUpperCase()} column first!`);

  const amt = STATE.panelBetAmounts[bird];
  if (amt <= 0) return showToast('Select or enter a bet amount!');

  const diffPaise = (amt - STATE.bets[bird].amount) * 100;
  if (diffPaise > STATE.balancePaise) return showToast('Insufficient balance!');

  // Optimistic UI update
  const originalState = { row: STATE.bets[bird].row, amount: STATE.bets[bird].amount };
  STATE.bets[bird] = { row, amount: amt };
  STATE.betStatus[bird] = 'PLACING';
  updateBetButtonStates();
  renderBoard();

  const token = localStorage.getItem('accessToken');
  try {
    const res = await fetch('/api/v1/game/bet', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        bird: bird,
        selectedRow: row,
        amountPaise: amt * 100,
        idempotencyKey: (crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
          const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
          return v.toString(16);
        }))
      })
    });
    if (!res.ok) {
      const data = await res.json();
      throw new Error(data.error || 'Failed to place bet');
    }
    const data = await res.json();
    STATE.balancePaise = data.balanceAfterPaise;
    STATE.betStatus[bird] = null; // revert to default placed status
    updateBalanceDisplay('down');
    updateBetButtonStates();
    updatePossibleWin();
    showToast(`✅ Bet placed on ${bird === 'tota' ? 'T' : 'M'}${row} · ₹${fmtCur(amt)} on ${bird === 'tota' ? 'Tota' : 'Mena'}`);
  } catch (error) {
    // Rollback
    STATE.bets[bird] = originalState;
    STATE.betStatus[bird] = null;
    updateBetButtonStates();
    renderBoard();
    updatePossibleWin();
    showToast(error.message || 'Network error while placing bet');
  }
}

async function cancelBet(bird) {
  if (STATE.phase !== 'BETTING') return showToast('Cannot cancel — betting is closed!');
  if (!STATE.bets[bird] || STATE.bets[bird].amount <= 0) return showToast('No active bet to cancel!');

  // Set button to loading state
  const btn = document.getElementById(`btn-${bird}`);
  if (btn) {
    btn.querySelector('.bet-btn__label').textContent = 'CANCELLING...';
    btn.disabled = true;
  }

  const token = localStorage.getItem('accessToken');
  try {
    const res = await fetch(`/api/v1/game/bet/${bird}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      showToast(data.error || data.message || 'Failed to cancel bet');
      updateBetButtonStates(); // restore button
      return;
    }
    const data = await res.json();
    // Update balance immediately from server response
    STATE.balancePaise = data.newBalancePaise;
    updateBalanceDisplay('up');
    showToast(`🚫 Bet on ${bird === 'tota' ? 'T' : 'M'}${STATE.bets[bird].row} cancelled · ₹${fmtCur(STATE.bets[bird].amount)} refunded`);
    // Reset this bird's bet
    STATE.bets[bird] = { row: null, amount: 0 };
    STATE.selectedRows[bird] = null;
    STATE.panelBetAmounts[bird] = 0;
    // Clear chip selection for this bird
    $$(`#chips-${bird} .chip`).forEach(c => c.classList.remove('chip--selected'));
    const input = document.getElementById(`amt-${bird}`);
    if (input) input.value = '';
    renderBoard();
    updatePossibleWin();
    updateBetButtonStates();
  } catch (error) {
    showToast('Network error while cancelling bet');
    updateBetButtonStates(); // restore button
  }
}

function renderBoard() {
  ['tota', 'mena'].forEach(bird => {
    DOM.bettingCells[bird].forEach(cell => {
      const row = +cell.dataset.row;
      const cellBird = cell.dataset.bird;

      const hasBet = STATE.bets[cellBird].row === row && STATE.bets[cellBird].amount > 0;
      const isDraft = STATE.selectedRows[cellBird] === row && !hasBet && STATE.panelBetAmounts[cellBird] > 0;

      const betBadge = cell.querySelector('.cell-bet');
      if (betBadge) {
        if (hasBet) {
          betBadge.textContent = `₹${STATE.bets[cellBird].amount}`;
          betBadge.style.display = 'block';
        } else if (isDraft) {
          betBadge.textContent = `₹${STATE.panelBetAmounts[cellBird]}`;
          betBadge.style.display = 'block';
        } else {
          betBadge.textContent = '';
          betBadge.style.display = 'none';
        }
      }

      if (STATE.phase === 'BETTING') {
        const lbl = cell.querySelector('.bird-lbl');
        if (hasBet || isDraft) {
          cell.setAttribute('data-active', 'true');
          if (lbl) { lbl.style.opacity = '1'; lbl.style.display = 'block'; }
        } else {
          cell.removeAttribute('data-active');
          if (!cell.classList.contains('cell--winner')) {
            if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
          }
        }
      }
    });
  });
}

// WS LISTENERS
window.onTick = function (frame) {
  const data = JSON.parse(frame.body);
  STATE.currentRoundId = data.roundId;
  STATE.phase = data.phase;
  STATE.roundTimer = data.secondsRemaining;
  renderTimer();
  updateCurrentRoundIdUI(data.roundId);
};

window.onPhaseChange = function (frame) {
  const data = JSON.parse(frame.body);
  if (data.newPhase === 'SPINNING') {
    startSpinPhase();
  } else if (data.newPhase === 'FINISHED') {
    // wait for result
  } else if (data.newPhase === 'BETTING') {
    resetRound();
  }
};

window.onResult = function (frame) {
  const data = JSON.parse(frame.body);
  STATE.winningRows.tota = data.winningRowTota;
  STATE.winningRows.mena = data.winningRowMena;
  stopSpinPhaseAndReveal();
};

window.onBetAck = function (frame) {
  const data = JSON.parse(frame.body);
  const prev = STATE.balancePaise;
  STATE.balancePaise = data.balanceAfterPaise;
  updateBalanceDisplay(data.balanceAfterPaise > prev ? 'up' : 'down');
  updateBetButtonStates();
};

window.onBalanceUpdate = function (frame) {
  const data = JSON.parse(frame.body);
  const prev = STATE.balancePaise;
  STATE.balancePaise = data.newBalancePaise;
  updateBalanceDisplay(data.newBalancePaise > prev ? 'up' : 'down');
  // Toasts and overlay are handled in stopSpinPhaseAndReveal
};

window.onWsError = function (frame) {
  const data = JSON.parse(frame.body);
  showToast(data.message);
};

window.syncStateOnReconnect = async function () {
  const token = localStorage.getItem('accessToken');
  if (!token) return;

  try {
    const [walletRes, currentRes] = await Promise.all([
      fetch('/api/v1/wallet', { headers: { 'Authorization': `Bearer ${token}` } }),
      fetch('/api/v1/game/current', { headers: { 'Authorization': `Bearer ${token}` } })
    ]);

    if (walletRes.ok) {
      const data = await walletRes.json();
      STATE.balancePaise = data.balancePaise;
      updateBalanceDisplay();
    }

    if (currentRes.ok) {
      const data = await currentRes.json();
      if (data.status !== 'NO_ACTIVE_ROUND') {
        STATE.currentRoundId = data.roundId;
        STATE.phase = data.phase;

        // Restore active bets
        STATE.bets = { tota: { row: null, amount: 0 }, mena: { row: null, amount: 0 } };
        STATE.selectedRows = { tota: null, mena: null };
        STATE.panelBetAmounts = { tota: 0, mena: 0 };

        if (data.activeBets && data.activeBets.length > 0) {
          data.activeBets.forEach(bet => {
            if (bet.bird === 'tota' || bet.bird === 'mena') {
              const amt = bet.amountPaise / 100;
              STATE.bets[bet.bird] = { row: bet.selectedRow, amount: amt };
              STATE.selectedRows[bet.bird] = bet.selectedRow;
              STATE.panelBetAmounts[bet.bird] = amt;

              // Visually restore chip or input selection
              const chips = Array.from(document.querySelectorAll(`#chips-${bet.bird} .chip`));
              const matchingChip = chips.find(c => +c.dataset.amt === amt);
              if (matchingChip) {
                matchingChip.classList.add('chip--selected');
              } else {
                const input = document.getElementById(`amt-${bet.bird}`);
                if (input) input.value = amt;
              }
            }
          });
        }

        renderBoard();
        updateBetButtonStates();
      }
    }
  } catch (e) {
    console.error("Failed to sync state on reconnect", e);
  }
};

function renderTimer() {
  if (STATE.phase === 'BETTING') {
    DOM.timerDisplay.textContent = `${STATE.roundTimer}s`;
    DOM.timerDisplay.style.color = '';
  } else if (STATE.phase === 'SPINNING') {
    DOM.timerDisplay.textContent = `${STATE.roundTimer}s`;
    DOM.timerDisplay.style.color = '#c9a04c';
  } else {
    DOM.timerDisplay.textContent = 'RESULT!';
    DOM.timerDisplay.style.color = '#2e7d32';
  }
}

function startSpinPhase() {
  STATE.phase = 'SPINNING';
  showToast('No more bets! Spinning...');
  if (DOM.manualAmtTota) DOM.manualAmtTota.disabled = true;
  if (DOM.manualAmtMena) DOM.manualAmtMena.disabled = true;

  ['tota', 'mena'].forEach(bird => {
    const track = document.getElementById(`reel-${bird}`);
    while (track.children.length > 5) {
      track.removeChild(track.lastChild);
    }
    const baseCells = Array.from(track.children);
    for (let i = 0; i < 3; i++) {
      baseCells.forEach(cell => {
        const clone = cell.cloneNode(true);
        track.appendChild(clone);
      });
    }
  });

  DOM.allCells = document.querySelectorAll('.board__cell');

  ['tota', 'mena'].forEach(bird => {
    const track = document.getElementById(`reel-${bird}`);
    const colHeight = track.parentElement.clientHeight;
    const distance = (colHeight + 8) * 3;

    const anim = track.animate([
      { transform: `translateY(0px)` },
      { transform: `translateY(-${distance}px)` }
    ], {
      duration: 800,
      iterations: Infinity,
      easing: 'linear'
    });
    track._spinAnim = anim;
  });
}

function stopSpinPhaseAndReveal() {
  let finishedCount = 0;

  ['tota', 'mena'].forEach((bird, index) => {
    const track = document.getElementById(`reel-${bird}`);
    const colHeight = track.parentElement.clientHeight;
    const distance = (colHeight + 8) * 2;

    if (track._spinAnim) track._spinAnim.cancel();

    const stopAnim = track.animate([
      { transform: `translateY(0px)` },
      { transform: `translateY(-${distance}px)` }
    ], {
      duration: 1200 + (index * 400),
      easing: 'cubic-bezier(0.1, 0.9, 0.2, 1)',
      fill: 'forwards'
    });

    stopAnim.onfinish = () => {
      stopAnim.cancel();
      track.style.transform = 'translateY(0px)';

      const winningRow = STATE.winningRows[bird];
      const cells = Array.from(track.querySelectorAll('.board__cell')).slice(0, 5);

      cells.forEach(cell => {
        const rowNum = parseInt(cell.dataset.row);
        const birdImg = cell.querySelector('.bird');
        if (!birdImg) return;

        if (rowNum === winningRow) {
          revealWinningBird(cell);
        } else {
          birdImg.style.opacity = '0';
        }
      });

      finishedCount++;
      if (finishedCount === 2) {
        STATE.phase = 'RESULT';
        renderTimer();

        // Determine win/loss for each bird the user bet on
        let totalWonPaise = 0;
        let anyBet = false;
        let winDetails = [];

        ['tota', 'mena'].forEach(bird => {
          if (STATE.bets[bird].amount > 0) {
            anyBet = true;
            const userRow = STATE.bets[bird].row;
            const winRow = STATE.winningRows[bird];
            const won = userRow === winRow;
            STATE.betStatus[bird] = won ? 'WON' : 'LOST';

            if (won) {
              const payout = STATE.bets[bird].amount * STATE.multipliers[bird];
              totalWonPaise += payout * 100;
              winDetails.push(`${bird.toUpperCase()} Row ${userRow} ✓ +₹${fmtCur(payout)}`);
            } else {
              winDetails.push(`${bird.toUpperCase()} Row ${userRow} ✗ Lost`);
            }
          }
        });

        updateBetButtonStates();

        // Show overlay if user placed any bet
        if (anyBet) {
          const overlay = document.getElementById('winOverlay');
          const amtDisplay = document.getElementById('winAmtDisplay');
          const rowsDisplay = document.getElementById('winRowsDisplay');

          if (overlay && amtDisplay) {
            if (totalWonPaise > 0) {
              // WIN
              overlay.querySelector('.win-overlay__title').textContent = '🎉 YOU WON!';
              amtDisplay.textContent = `+₹${fmtCur(totalWonPaise / 100)}`;
              amtDisplay.style.color = '#4CAF50';
              const crown = overlay.querySelector('.win-overlay__crown');
              if (crown) crown.textContent = '👑';
              showToast(`🎉 You won ₹${fmtCur(totalWonPaise / 100)}`);
            } else {
              // LOSS
              overlay.querySelector('.win-overlay__title').textContent = 'BETTER LUCK NEXT TIME';
              amtDisplay.textContent = `-₹${fmtCur((STATE.bets.tota.amount + STATE.bets.mena.amount))}`;
              amtDisplay.style.color = '#dc2626';
              const crown = overlay.querySelector('.win-overlay__crown');
              if (crown) crown.textContent = '😔';
              showToast(`😔 You lost ₹${fmtCur(STATE.bets.tota.amount + STATE.bets.mena.amount)}`);
            }
            if (rowsDisplay) {
              rowsDisplay.innerHTML = winDetails.map(d => `<div style="margin-bottom:4px;">${d}</div>`).join('');
            }
            overlay.classList.add('is-visible');
            overlay.setAttribute('aria-hidden', 'false');
            // Auto-close after 5 seconds if user doesn't click
            setTimeout(() => closeWin(), 5000);
          }
        }

        prependRecentResult(STATE.currentRoundId, STATE.winningRows.tota, STATE.winningRows.mena);
      }
    };
  });
}

function revealWinningBird(cell) {
  const bird = cell.dataset.bird;
  const birdImg = cell.querySelector('.bird');
  const lbl = cell.querySelector('.bird-lbl');

  if (!birdImg) return;

  birdImg.src = `assets/${bird}.webp`;
  cell.setAttribute('data-win', 'true');

  birdImg.style.opacity = '';
  birdImg.style.transform = '';
  birdImg.style.filter = '';

  cell.classList.add('cell--winner');

  const leftHalf = document.createElement('div');
  leftHalf.className = 'card-half card-half--left';
  const rightHalf = document.createElement('div');
  rightHalf.className = 'card-half card-half--right';
  const flash = document.createElement('div');
  flash.className = 'shatter-flash';

  cell.appendChild(leftHalf);
  cell.appendChild(rightHalf);
  cell.appendChild(flash);

  if (lbl) {
    lbl.style.opacity = '1';
    lbl.style.display = 'block';
  }
}

function resetRound() {
  STATE.bets = { tota: { row: null, amount: 0 }, mena: { row: null, amount: 0 } };
  STATE.selectedRows = { tota: null, mena: null };
  STATE.betStatus = { tota: null, mena: null };
  updateBetButtonStates();
  if (DOM.manualAmtTota) { DOM.manualAmtTota.disabled = false; DOM.manualAmtTota.value = ''; }
  if (DOM.manualAmtMena) { DOM.manualAmtMena.disabled = false; DOM.manualAmtMena.value = ''; }
  $$('.chip').forEach(c => c.classList.remove('chip--selected'));
  STATE.panelBetAmounts = { tota: 0, mena: 0 };
  
  closeWin();

  DOM.allCells.forEach(cell => {
    const wasWinner = cell.classList.contains('cell--winner');

    cell.removeAttribute('data-active');
    cell.removeAttribute('data-win');
    cell.classList.remove('cell--winner');

    const betBadge = cell.querySelector('.cell-bet');
    if (betBadge) { betBadge.style.display = 'none'; betBadge.textContent = ''; }

    const tempElems = cell.querySelectorAll('.card-half, .shatter-flash');
    tempElems.forEach(el => el.remove());

    const birdImg = cell.querySelector('.bird');
    if (wasWinner && birdImg) {
      // Replay the shatter effect to hide the bird
      const leftHalf = document.createElement('div');
      leftHalf.className = 'card-half card-half--left';
      const rightHalf = document.createElement('div');
      rightHalf.className = 'card-half card-half--right';
      const flash = document.createElement('div');
      flash.className = 'shatter-flash';

      cell.appendChild(leftHalf);
      cell.appendChild(rightHalf);
      cell.appendChild(flash);

      // Hide the bird at the exact moment of the flash
      setTimeout(() => {
        birdImg.style.opacity = '0';
        birdImg.style.transform = 'translate(-50%, -50%) scale(0.4)';
      }, 150);

      // Clean up the DOM after animation finishes
      setTimeout(() => {
        leftHalf.remove();
        rightHalf.remove();
        flash.remove();
      }, 1000);
    } else if (birdImg) {
      birdImg.style.opacity = '0';
      birdImg.style.transform = 'translate(-50%, -50%) scale(0.4)';
    }
  });

  renderBoard();
  updatePossibleWin();
}

function updatePossibleWin() {
  const tAmt = STATE.bets.tota.amount || STATE.panelBetAmounts.tota || 0;
  const mAmt = STATE.bets.mena.amount || STATE.panelBetAmounts.mena || 0;

  const tWin = tAmt * STATE.multipliers.tota;
  const mWin = mAmt * STATE.multipliers.mena;
  const totalWin = tWin + mWin;

  // POSSIBLE WIN display
  if (DOM.possWinDisplay) {
    DOM.possWinDisplay.textContent = totalWin > 0 ? `₹${fmtCur(totalWin)}` : '₹0';
  }

  // WIN MULTIPLIER display
  if (DOM.multDisplay) {
    const hasTota = tAmt > 0;
    const hasMena = mAmt > 0;
    if (hasTota && hasMena) {
      DOM.multDisplay.textContent = '2.00x / 1.50x';
      DOM.multDisplay.style.fontSize = '12px';  // smaller to fit both
    } else if (hasTota) {
      DOM.multDisplay.textContent = '2.00x';
      DOM.multDisplay.style.fontSize = '';
    } else if (hasMena) {
      DOM.multDisplay.textContent = '1.50x';
      DOM.multDisplay.style.fontSize = '';
    } else {
      DOM.multDisplay.textContent = '—';
      DOM.multDisplay.style.fontSize = '';
    }
  }
}

function closeWin() {
  if (DOM.winOverlay) {
    DOM.winOverlay.classList.remove('is-visible');
    DOM.winOverlay.setAttribute('aria-hidden', 'true');
  }
}

function updateBalanceDisplay(flashDir) {
  const coinsVal = document.getElementById('coins-val');
  if (coinsVal) {
    // Balance is in paise from the server
    coinsVal.textContent = fmtCur(STATE.balancePaise / 100);
    // Flash the balance green (refund/win) or red (deduction)
    if (flashDir === 'up' || flashDir === 'down') {
      coinsVal.classList.remove('bal-flash-up', 'bal-flash-down');
      void coinsVal.offsetWidth; // reflow to restart animation
      coinsVal.classList.add(flashDir === 'up' ? 'bal-flash-up' : 'bal-flash-down');
    }
  }
}

let toastTO;
function showToast(msg) {
  let t = document.getElementById('gameToast');
  if (!t) {
    t = document.createElement('div');
    t.id = 'gameToast';
    t.setAttribute('role', 'status');
    Object.assign(t.style, {
      position: 'fixed', bottom: '12vh', left: '50%',
      transform: 'translateX(-50%) translateY(20px)',
      background: 'linear-gradient(135deg,#181000,#0a0800)',
      border: '1px solid #c9a04c', color: '#f5d78e',
      fontFamily: "'Cinzel',serif", fontSize: '12px', fontWeight: '700',
      letterSpacing: '0.08em', padding: '10px 24px', borderRadius: '999px',
      boxShadow: '0 4px 20px rgba(201,160,76,0.25)', zIndex: '9999',
      opacity: '0', transition: 'opacity .3s, transform .3s', pointerEvents: 'none'
    });
    document.body.appendChild(t);
  }
  t.textContent = msg;
  requestAnimationFrame(() => { t.style.opacity = '1'; t.style.transform = 'translateX(-50%) translateY(0)'; });
  clearTimeout(toastTO);
  toastTO = setTimeout(() => { t.style.opacity = '0'; t.style.transform = 'translateX(-50%) translateY(20px)'; }, 2500);
}



function fmtCur(n) { return Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 }); }

function prependRecentResult(roundId, winTota, winMena) {
  if (!roundId) return;
  const isSubdir = window.location.pathname.includes('/pages/');
  const prefix = isSubdir ? '../' : '';
  const newRow = `
    <div class="rr-row">
      <span class="rr-id">#${roundId.substring(0, 8).toUpperCase()}</span>
      <div class="rr-bird" style="display: flex; gap: 8px; align-items: center; justify-content: flex-end; width: 100%;">
        <div style="display:flex; align-items:center; gap:4px;"><img src="${prefix}assets/tota.webp" style="width:20px;height:20px;"> T${winTota}</div>
        <div style="display:flex; align-items:center; gap:4px;"><img src="${prefix}assets/mena.webp" style="width:20px;height:20px;"> M${winMena}</div>
      </div>
    </div>
  `;

  document.querySelectorAll('.rr-list').forEach(rrList => {
    rrList.insertAdjacentHTML('afterbegin', newRow);
    if (rrList.children.length > 10) {
      rrList.removeChild(rrList.lastElementChild);
    }
  });
}

document.addEventListener('DOMContentLoaded', async () => {
  if (typeof init === 'function') init();

  // Fire off shared data fetching in parallel without awaiting
  loadSharedPageData();

  if (!window.location.pathname.includes('signin.html') && !window.location.pathname.includes('signup.html')) {
    fetchUserStats();
  }

  if (window.location.pathname.includes('results')) {
    const filterAll = document.getElementById('filterAllBtn');
    const filterMy = document.getElementById('filterMyBtn');

    function setActiveFilter(active) {
      [filterAll, filterMy].forEach(btn => {
        if (!btn) return;
        btn.style.background = 'var(--surface-light)';
        btn.style.color = 'var(--text-muted)';
        btn.style.border = '1px solid var(--border)';
      });
      if (active) {
        active.style.background = 'var(--gold)';
        active.style.color = 'var(--bg-dark)';
        active.style.border = 'none';
      }
    }

    if (filterAll) {
      filterAll.addEventListener('click', () => {
        currentHistoryFilter = 'all';
        setActiveFilter(filterAll);
        loadHistoryTable();
      });
    }
    if (filterMy) {
      filterMy.addEventListener('click', () => {
        currentHistoryFilter = 'my';
        setActiveFilter(filterMy);
        loadHistoryTable();
      });
    }

    // Set initial active state
    if (filterAll) setActiveFilter(filterAll);
    loadHistoryTable();
  }

  async function loadHistoryTable() {
    const table = document.getElementById('results-history-table');
    if (!table) return;

    if (currentHistoryFilter === 'all') {
      const cacheKey = 'cache_/api/v1/stats/history';
      const cached = sessionStorage.getItem(cacheKey);
      if (cached) {
        try { renderRecentResultsTable(JSON.parse(cached), table); } catch(e){}
      }
      try {
        const res = await apiFetch('/api/v1/stats/history');
        if (!res.ok) return;
        const data = await res.json();
        sessionStorage.setItem(cacheKey, JSON.stringify(data));
        renderRecentResultsTable(data, table);
      } catch (e) { console.error(e); }

    } else if (currentHistoryFilter === 'my') {
      const cacheKey = 'cache_/api/v1/game/history?page=0&size=20';
      const cached = sessionStorage.getItem(cacheKey);
      if (cached) {
        try { renderMyBetsTable(JSON.parse(cached).content || [], table); } catch(e){}
      }
      try {
        const res = await apiFetch('/api/v1/game/history?page=0&size=20');
        if (!res.ok) return;
        const data = await res.json();
        sessionStorage.setItem(cacheKey, JSON.stringify(data));
        renderMyBetsTable(data.content || [], table);
      } catch (e) { console.error(e); }
    }
  }

  function renderRecentResultsTable(data, table) {
    let html = '<div class="rr-list">';
    if (data.length === 0) {
      html += `<div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 14px;">No recent results found.</div>`;
    }
    data.forEach(r => {
      const shortId = '#' + r.roundId.substring(0, 8).toUpperCase();
      const isSubdir = window.location.pathname.includes('/pages/');
      const prefix = isSubdir ? '../' : '';

      html += `
      <div class="rr-row">
        <span class="rr-id">${shortId}</span>
        <div class="rr-bird" style="display: flex; gap: 8px; align-items: center; justify-content: flex-end; width: 100%;">
          <div style="display:flex; align-items:center; gap:4px;"><img src="${prefix}assets/tota.webp" style="width:20px;height:20px;"> T${r.winningRowTota}</div>
          <div style="display:flex; align-items:center; gap:4px;"><img src="${prefix}assets/mena.webp" style="width:20px;height:20px;"> M${r.winningRowMena}</div>
        </div>
      </div>
    `;
    });
    html += '</div>';
    table.innerHTML = html;
  }

  function renderMyBetsTable(data, table) {
    let html = `
    <div class="ht-header" style="display:grid; grid-template-columns: 1fr 1fr 1fr; padding: 12px 16px; background: rgba(201,160,76,0.1); border-radius: 8px 8px 0 0; font-family: var(--font-sora); font-size: 10px; font-weight: 800; color: var(--gold); letter-spacing: 1px; text-transform: uppercase; text-align: center;">
      <span style="text-align: left;">Round & Time</span>
      <span>Bird</span>
      <span style="text-align: right;">Result</span>
    </div>
  `;

    if (data.length === 0) {
      html += `<div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 14px;">No history found.</div>`;
    }

    data.forEach(b => {
      const shortId = '#' + b.roundId.substring(0, 8).toUpperCase();
      const dateObj = new Date(b.createdAt);
      const timeStr = dateObj.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' + dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      const birdClass = b.bird === 'tota' ? 'ht-val--green' : 'ht-val--brown';
      const won = b.status === 'WON';
      const amtDisplay = won
        ? '+₹' + Number(b.payoutPaise / 100).toLocaleString('en-IN')
        : '-₹' + Number(b.amountPaise / 100).toLocaleString('en-IN');
      const color = won ? 'var(--green)' : 'var(--red)';

      html += `
      <div class="ht-row" style="display:grid; grid-template-columns: 1fr 1fr 1fr; padding: 12px 16px; border-bottom: 1px solid rgba(201,160,76,0.1); align-items:center; text-align: center;">
        <div style="text-align: left; display: flex; flex-direction: column; gap: 4px;">
          <span class="ht-val" style="font-size:12px;">${shortId}</span>
          <span style="font-size:10px; color:var(--text-muted);">${timeStr}</span>
        </div>
        <span class="ht-val ${birdClass}" style="font-weight: 800;">${b.bird.toUpperCase()}</span>
        <span class="ht-val" style="font-size:12px; font-weight: bold; text-align: right; color:${color}">${amtDisplay}</span>
      </div>
    `;
    });

    table.innerHTML = html;
  }



  async function loadTransactionsTable() {
    try {
      const res = await apiFetch('/api/v1/wallet/transactions');
      if (!res || !res.ok) return;
      const page = await res.json();
      const table = document.querySelector('.transactions-table');
      if (!table) return;

      let html = `
      <div class="ht-header" style="display:flex; justify-content:space-between; padding: 12px 16px; background: rgba(201,160,76,0.1); border-radius: 8px 8px 0 0; font-family: var(--font-sora); font-size: 10px; font-weight: 800; color: var(--gold); letter-spacing: 1px; text-transform: uppercase;">
        <span style="flex:1;">Type & Date</span>
        <span style="flex:1; text-align:center;">Amount</span>
        <span style="flex:1; text-align:right;">Balance</span>
      </div>
    `;

      page.content.forEach(tx => {
        const amtClass = tx.amountPaise > 0 ? 'ht-val--green' : 'ht-val--brown';
        const sign = tx.amountPaise > 0 ? '+' : '';
        const date = new Date(tx.createdAt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });

        html += `
        <div class="ht-row" style="align-items: center;">
          <div style="display:flex; flex-direction:column; gap:4px; flex:1;">
            <span class="ht-val" style="font-size:12px; text-transform:capitalize;">${tx.type.replace('_', ' ')}</span>
            <span style="font-size:10px; color:var(--text-light);">${date}</span>
          </div>
          <span class="ht-val ${amtClass}" style="flex:1; text-align:center;">${sign}${fmtCur(tx.amountPaise / 100)}</span>
          <span class="ht-val" style="flex:1; text-align:right;">${fmtCur(tx.balanceAfterPaise / 100)}</span>
        </div>
      `;
      });

      table.innerHTML = html;
    } catch (e) {
      console.error("Failed to load transactions", e);
    }
  }

});

async function loadHistoryTable() {
  try {
    const res = await apiFetch('/api/v1/stats/history');
    if (!res.ok) return;
    const historyData = await res.json();
    renderHistoryTable(historyData);
  } catch (e) {
    console.error("Failed to load history", e);
  }
}

function renderHistoryTable(data) {
  const table = document.getElementById('results-history-table');
  if (!table) return;

  const filteredData = data.filter(r => {
    if (currentHistoryFilter === 'my') {
      return r.totaBetPaise != null || r.menaBetPaise != null;
    }
    return true;
  });

  let html = `
    <div class="ht-header" style="display:grid; grid-template-columns: 1.5fr 2fr 1fr 1fr 1fr; padding: 12px 16px; background: rgba(201,160,76,0.1); border-radius: 8px 8px 0 0; font-family: var(--font-sora); font-size: 10px; font-weight: 800; color: var(--gold); letter-spacing: 1px; text-transform: uppercase; text-align: center;">
      <span style="text-align: left;">Round & Time</span>
      <span>Winning Cells</span>
      <span>Bet (T)</span>
      <span>Bet (M)</span>
      <span style="text-align: right;">Payout</span>
    </div>
  `;

  if (filteredData.length === 0) {
    html += `<div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 14px;">No history found.</div>`;
  }

  filteredData.forEach(r => {
    const dateObj = new Date(r.finishedAt);
    const dateStr = dateObj.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
    const timeStr = dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    // Tota Bet cell
    let totaCell = '-';
    if (r.totaBetPaise != null) {
      totaCell = '₹' + (r.totaBetPaise / 100);
    }

    // Mena Bet cell
    let menaCell = '-';
    if (r.menaBetPaise != null) {
      menaCell = '₹' + (r.menaBetPaise / 100);
    }

    // Total Payout Cell
    let payoutCell = '-';
    let payoutColor = '';
    if (r.totaBetPaise != null || r.menaBetPaise != null) {
      const totalBet = (r.totaBetPaise || 0) + (r.menaBetPaise || 0);
      const totalWin = (r.totaWinPaise || 0) + (r.menaWinPaise || 0);
      if (totalWin > 0) {
        payoutCell = '+₹' + (totalWin / 100);
        payoutColor = 'color: #4CAF50;';
      } else {
        payoutCell = '-₹' + (totalBet / 100);
        payoutColor = 'color: #ff5252;';
      }
    }

    html += `
      <div class="ht-row" style="display:grid; grid-template-columns: 1.5fr 2fr 1fr 1fr 1fr; padding: 12px 16px; border-bottom: 1px solid rgba(201,160,76,0.1); align-items:center; text-align: center;">
        <div style="text-align: left; display: flex; flex-direction: column; gap: 4px;">
          <span class="ht-val" style="font-size:12px;">${shortId}</span>
          <span style="font-size:10px; color:var(--text-muted);">${dateStr}, ${timeStr}</span>
        </div>
        <div style="display:flex; justify-content: center; gap: 8px;">
          <span class="ht-val" style="background: var(--surface-dark); padding: 4px 8px; border-radius: 4px; display:flex; align-items:center; gap:4px;"><img src="../assets/tota.webp" style="width:16px;height:16px;"> T${r.winningRowTota}</span>
          <span class="ht-val" style="background: var(--surface-dark); padding: 4px 8px; border-radius: 4px; display:flex; align-items:center; gap:4px;"><img src="../assets/mena.webp" style="width:16px;height:16px;"> M${r.winningRowMena}</span>
        </div>
        <span class="ht-val" style="font-size:12px;">${totaCell}</span>
        <span class="ht-val" style="font-size:12px;">${menaCell}</span>
        <span class="ht-val" style="font-size:12px; font-weight: bold; text-align: right; ${payoutColor}">${payoutCell}</span>
      </div>
    `;
  });

  table.innerHTML = html;
}

async function loadTransactionsTable() {
  try {
    const res = await apiFetch('/api/v1/wallet/transactions');
    if (!res || !res.ok) return;
    const page = await res.json();
    const table = document.querySelector('.transactions-table');
    if (!table) return;

    let html = `
      <div class="ht-header" style="display:flex; justify-content:space-between; padding: 12px 16px; background: rgba(201,160,76,0.1); border-radius: 8px 8px 0 0; font-family: var(--font-sora); font-size: 10px; font-weight: 800; color: var(--gold); letter-spacing: 1px; text-transform: uppercase;">
        <span style="flex:1;">Type & Date</span>
        <span style="flex:1; text-align:center;">Amount</span>
        <span style="flex:1; text-align:right;">Balance</span>
      </div>
    `;

    page.content.forEach(tx => {
      const amtClass = tx.amountPaise > 0 ? 'ht-val--green' : 'ht-val--brown';
      const sign = tx.amountPaise > 0 ? '+' : '';
      const date = new Date(tx.createdAt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });

      html += `
        <div class="ht-row" style="align-items: center;">
          <div style="display:flex; flex-direction:column; gap:4px; flex:1;">
            <span class="ht-val" style="font-size:12px; text-transform:capitalize;">${tx.type.replace('_', ' ')}</span>
            <span style="font-size:10px; color:var(--text-light);">${date}</span>
          </div>
          <span class="ht-val ${amtClass}" style="flex:1; text-align:center;">${sign}${fmtCur(tx.amountPaise / 100)}</span>
          <span class="ht-val" style="flex:1; text-align:right;">${fmtCur(tx.balanceAfterPaise / 100)}</span>
        </div>
      `;
    });

    table.innerHTML = html;
  } catch (e) {
    console.error("Failed to load transactions", e);
  }
}

if (window.location.pathname.includes('wallet.html')) {
  loadTransactionsTable();
}

async function loadSupportTickets() {
  try {
    const res = await apiFetch('/api/v1/support/tickets');
    if (!res || !res.ok) return;
    const tickets = await res.json();
    const list = document.getElementById('support-tickets-list');
    if (!list) return;

    // keep the create button
    const btnHtml = '<button class="load-more-btn" id="create-ticket-btn" style="margin-top: 8px;">Create New Ticket</button>';

    if (tickets.length === 0) {
      list.innerHTML = `<div style="text-align:center; padding:16px; color:var(--text-light); font-size:12px;">No tickets found.</div>` + btnHtml;
    } else {
      let html = '';
      tickets.forEach(t => {
        const date = new Date(t.createdAt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
        const isResolved = t.status.toLowerCase() === 'resolved';
        const statusClass = isResolved ? 'ticket-status--resolved' : 'ticket-status--open';
        html += `
          <div class="ticket-row">
            <div>
              <div style="font-family: var(--font-sora); font-size: 12px; font-weight: 700;">${t.subject}</div>
              <div style="font-size: 10px; color: var(--text-light);">Ticket #${t.id.substring(0, 6).toUpperCase()} • ${date}</div>
            </div>
            <div class="ticket-status ${statusClass}">${t.status}</div>
          </div>
        `;
      });
      list.innerHTML = html + btnHtml;
    }

    const createBtn = document.getElementById('create-ticket-btn');
    if (createBtn) {
      createBtn.addEventListener('click', async () => {
        const subject = prompt("Enter ticket subject:");
        if (subject) {
          const r = await apiFetch('/api/v1/support/tickets', {
            method: 'POST',
            body: JSON.stringify({ subject })
          });
          if (r && r.ok) {
            showToast("Ticket created!");
            loadSupportTickets();
          }
        }
      });
    }

  } catch (e) {
    console.error("Failed to load support tickets", e);
  }
}

if (window.location.pathname.includes('support.html')) {
  loadSupportTickets();
}
