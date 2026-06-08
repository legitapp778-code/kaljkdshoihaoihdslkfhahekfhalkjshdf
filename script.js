
'use strict';

/* ══════════════════════════════════════════════════════════
   STATE
   ══════════════════════════════════════════════════════════ */
const STATE = {
  balance: 1250000,
  bets: { tota: { row: null, amount: 0 }, mena: { row: null, amount: 0 } },
  panelBetAmounts: { tota: 0, mena: 0 },
  roundTimer: 40,
  phase: 'BETTING',
  multiplier: 2.00,
  winningRows: { tota: null, mena: null }
};



/* ══════════════════════════════════════════════════════════
   DOM REFERENCES
   ══════════════════════════════════════════════════════════ */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];

const DOM = {};

/* ══════════════════════════════════════════════════════════
   INIT
   ══════════════════════════════════════════════════════════ */
function init() {
  // Mobile Menu Toggle
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

  DOM.cells.forEach(c => c.addEventListener('click', () => placeBet(c.dataset.bird, +c.dataset.row)));
  if (DOM.winCloseBtn) DOM.winCloseBtn.addEventListener('click', closeWin);
  const winBg = $('.win-overlay__bg');
  if (winBg) winBg.addEventListener('click', closeWin);

  // Preload base images to avoid flickering
  ['assets/tota.png', 'assets/mena.png', 'assets/emp_design.png'].forEach(src => {
    const img = new Image();
    img.src = src;
  });

  // Set initial state — bird hidden
  DOM.cells.forEach(cell => resetCellVisual(cell));

  startTimer();
  updatePossibleWin();
}

/* ══════════════════════════════════════════════════════════
   CELL VISUAL RESET — bird hidden
   ══════════════════════════════════════════════════════════ */
function resetCellVisual(cell) {
  const bird = cell.querySelector('.bird');
  const lbl = cell.querySelector('.bird-lbl');
  const check = cell.querySelector('.cell-check');

  // Bird: invisible initially
  if (bird) {
    bird.src = `assets/${cell.dataset.bird}.png`;
    bird.style.opacity = '0';
    bird.style.transform = 'translate(-50%, -50%) scale(0.4)';
  }

  // Labels hidden
  if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
  if (check) { check.style.opacity = '0'; check.style.display = 'none'; }

  // Clear data attributes
  cell.removeAttribute('data-active');
  cell.removeAttribute('data-win');
  cell.classList.remove('cell--spinning', 'cell--winner');
}

/* ══════════════════════════════════════════════════════════
   BETTING PANEL
   ══════════════════════════════════════════════════════════ */
function setupPanel(bird, chips, input, btn) {
  chips.forEach(chip => {
    chip.addEventListener('click', () => {
      if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
      STATE.panelBetAmounts[bird] = +chip.dataset.amt;
      chips.forEach(c => c.classList.remove('chip--selected'));
      chip.classList.add('chip--selected');
      if (input) input.value = '';
      updatePossibleWin();
    });
  });
  if (input) {
    input.addEventListener('input', () => {
      if (STATE.phase !== 'BETTING') { input.value = ''; return showToast('Betting is locked!'); }
      STATE.panelBetAmounts[bird] = +input.value || 0;
      chips.forEach(c => c.classList.remove('chip--selected'));
      updatePossibleWin();
    });
  }
  if (btn) {
    btn.addEventListener('click', () => {
      if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
      const row = STATE.bets[bird].row;
      if (!row) return showToast(`Select a card in ${bird.toUpperCase()} column first!`);
      placeBet(bird, row);
    });
  }
}

function placeBet(bird, row) {
  if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
  const amt = STATE.panelBetAmounts[bird];
  if (amt <= 0) return showToast('Select or enter a bet amount!');
  const diff = amt - STATE.bets[bird].amount;
  if (diff > STATE.balance) return showToast('Insufficient balance!');
  STATE.balance -= diff;
  STATE.bets[bird] = { row, amount: amt };
  showToast(`₹${amt} on ${bird.toUpperCase()} Row ${row}`);
  renderBoard();
  updatePossibleWin();
}

/* ══════════════════════════════════════════════════════════
   BOARD RENDERING (betting phase only)
   ══════════════════════════════════════════════════════════ */
function renderBoard() {
  DOM.cells.forEach(cell => {
    const row = +cell.dataset.row;
    const bird = cell.dataset.bird;
    const hasBet = STATE.bets[bird].row === row && STATE.bets[bird].amount > 0;

    const betBadge = cell.querySelector('.cell-bet');
    if (betBadge) {
      betBadge.textContent = hasBet ? `₹${STATE.bets[bird].amount}` : '';
      betBadge.style.display = hasBet ? 'block' : 'none';
    }

    cell.setAttribute('data-active', String(hasBet));

    if (STATE.phase === 'BETTING') {
      const lbl = cell.querySelector('.bird-lbl');
      const check = cell.querySelector('.cell-check');

      if (hasBet) {
        if (lbl) { lbl.style.opacity = '1'; lbl.style.display = 'block'; }
        if (check) { check.style.opacity = '1'; check.style.display = 'flex'; }
      } else {
        if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
        if (check) { check.style.opacity = '0'; check.style.display = 'none'; }
      }
    }
  });
}

/* ══════════════════════════════════════════════════════════
   TIMER & GAME LOOP
   ══════════════════════════════════════════════════════════ */
let timerInterval = null;
let spinInterval = null;

function startTimer() {
  clearInterval(timerInterval);
  STATE.roundTimer = 40;
  STATE.phase = 'BETTING';
  renderTimer();
  timerInterval = setInterval(() => {
    STATE.roundTimer--;
    renderTimer();
    if (STATE.roundTimer === 20) startSpinPhase();
    if (STATE.roundTimer === 5) stopSpinPhaseAndReveal();
    if (STATE.roundTimer <= 0) resetRound();
  }, 1000);
}

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

  // Pick winners NOW
  STATE.winningRows.tota = Math.floor(Math.random() * 5) + 1;
  STATE.winningRows.mena = Math.floor(Math.random() * 5) + 1;

  const allCells = [...DOM.cells];

  // Prepare cells for spinning
  allCells.forEach(cell => {
    cell.removeAttribute('data-active');
    cell.classList.remove('cell--spinning');

    // Hide labels & checkmarks
    const lbl = cell.querySelector('.bird-lbl');
    const check = cell.querySelector('.cell-check');
    if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
    if (check) { check.style.opacity = '0'; check.style.display = 'none'; }

    // Hide all birds initially
    const birdImg = cell.querySelector('.bird');
    if (birdImg) {
      birdImg.style.opacity = '0';
      birdImg.style.filter = 'none';
      birdImg.style.transform = 'translate(-50%, -50%) scale(1)';
    }
  });

  // Roulette chase: one bird jumping through rows
  let tick = 0;
  spinInterval = setInterval(() => {
    const activeTotaRow = (tick % 5) + 1;
    const activeMenaRow = ((tick + 2) % 5) + 1; // offset slightly from Tota

    allCells.forEach(cell => {
      const birdImg = cell.querySelector('.bird');
      const row = +cell.dataset.row;
      const bird = cell.dataset.bird;

      if (birdImg) {
        const isActive = (bird === 'tota' && row === activeTotaRow) ||
          (bird === 'mena' && row === activeMenaRow);

        if (isActive) {
          cell.classList.add('cell--spinning');
          birdImg.src = `assets/${bird}.png`;
          birdImg.style.opacity = '1';
          birdImg.style.transform = 'translate(-50%, -50%) scale(1.15)';
          birdImg.style.filter = 'drop-shadow(0 6px 10px rgba(255, 215, 0, 0.5))';
        } else {
          cell.classList.remove('cell--spinning');
          birdImg.style.opacity = '0';
          birdImg.style.transform = 'translate(-50%, -50%) scale(0.6)';
          birdImg.style.filter = 'drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2))';
        }
      }
    });
    tick++;
  }, 80);
}

function stopSpinPhaseAndReveal() {
  if (spinInterval) clearInterval(spinInterval);
  spinInterval = null;

  const allCells = [...DOM.cells];

  // Stop spin visual and reveal
  allCells.forEach(cell => {
    cell.classList.remove('cell--spinning');
    const birdImg = cell.querySelector('.bird');
    if (birdImg) {
      birdImg.style.filter = 'none';
      birdImg.style.transform = 'translate(-50%, -50%) scale(1)';
    }

    const row = +cell.dataset.row;
    const bird = cell.dataset.bird;
    const isWin = (bird === 'tota' && row === STATE.winningRows.tota) ||
      (bird === 'mena' && row === STATE.winningRows.mena);

    if (isWin) {
      revealWinningBird(cell);
    } else {
      if (birdImg) birdImg.style.opacity = '0';
    }
  });

  resolvePayouts();
}

/* ══════════════════════════════════════════════════════════
   BIRD REVEAL — per cell
function revealWinningBird(cell) {
  const bird = cell.dataset.bird;
  const birdImg = cell.querySelector('.bird');
  const lbl = cell.querySelector('.bird-lbl');

  if (!birdImg) return;

  birdImg.src = `assets/${bird}.png`;
  cell.classList.add('cell--winner');
  cell.setAttribute('data-win', 'true');

  birdImg.style.opacity = '1';
  birdImg.style.transform = 'translate(-50%, -50%) scale(1)';

  if (lbl) {
    lbl.style.opacity = '1';
    lbl.style.display = 'block';
  }
}


/* ══════════════════════════════════════════════════════════
   PAYOUTS
   ══════════════════════════════════════════════════════════ */
function resolvePayouts() {
  STATE.phase = 'RESULT';
  renderTimer();
  let win = 0;
  if (STATE.bets.tota.row === STATE.winningRows.tota) win += STATE.bets.tota.amount * STATE.multiplier;
  if (STATE.bets.mena.row === STATE.winningRows.mena) win += STATE.bets.mena.amount * STATE.multiplier;
  const hasBets = STATE.bets.tota.amount > 0 || STATE.bets.mena.amount > 0;
  if (win > 0) { STATE.balance += win; showWin(win); }
  else if (hasBets) showToast('Better luck next time!');
}

/* ══════════════════════════════════════════════════════════
   RESET ROUND
   ══════════════════════════════════════════════════════════ */
function resetRound() {
  STATE.bets = { tota: { row: null, amount: 0 }, mena: { row: null, amount: 0 } };
  if (DOM.manualAmtTota) { DOM.manualAmtTota.disabled = false; DOM.manualAmtTota.value = ''; }
  if (DOM.manualAmtMena) { DOM.manualAmtMena.disabled = false; DOM.manualAmtMena.value = ''; }
  $$('.chip').forEach(c => c.classList.remove('chip--selected'));
  STATE.panelBetAmounts = { tota: 0, mena: 0 };
  DOM.cells.forEach(cell => resetCellVisual(cell));
  renderBoard();
  updatePossibleWin();
  startTimer();
}

/* ══════════════════════════════════════════════════════════
   STATS
   ══════════════════════════════════════════════════════════ */
function updatePossibleWin() {
  const t = STATE.bets.tota.amount || STATE.panelBetAmounts.tota;
  const m = STATE.bets.mena.amount || STATE.panelBetAmounts.mena;
  DOM.possWinDisplay.textContent = (t + m) > 0 ? `₹${fmtCur((t + m) * STATE.multiplier)}` : '₹0';
  DOM.multDisplay.textContent = `${STATE.multiplier.toFixed(2)}x`;
}

/* ══════════════════════════════════════════════════════════
   WIN MODAL
   ══════════════════════════════════════════════════════════ */
function showWin(amt) {
  DOM.winAmtDisplay.textContent = `+₹${fmtCur(amt)}`;
  DOM.winOverlay.setAttribute('aria-hidden', 'false');
  DOM.winOverlay.classList.add('is-visible');
}
function closeWin() {
  DOM.winOverlay.classList.remove('is-visible');
  DOM.winOverlay.setAttribute('aria-hidden', 'true');
}

/* ══════════════════════════════════════════════════════════
   TOAST
   ══════════════════════════════════════════════════════════ */
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

/* ══════════════════════════════════════════════════════════
   UTILS
   ══════════════════════════════════════════════════════════ */
function fmtCur(n) { return Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 }); }
function shuffleArray(a) { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1));[a[i], a[j]] = [a[j], a[i]]; } return a; }

/* ══════════════════════════════════════════════════════════
   BOOT
   ══════════════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', init);
