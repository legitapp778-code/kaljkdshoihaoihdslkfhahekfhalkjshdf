
'use strict';

/* ══════════════════════════════════════════════════════════
   STATE
   ══════════════════════════════════════════════════════════ */
const STATE = {
  balance: 1250000,
  bets: { tota: { row: null, amount: 0 }, mena: { row: null, amount: 0 } },
  panelBetAmounts: { tota: 0, mena: 0 },
  roundTimer: 30,
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

  // Clone cells for reel effect
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

  // Re-select all cells after cloning, but only attach click to the original 5
  DOM.allCells = $$('.board__cell');
  DOM.bettingCells = {
    tota: Array.from(document.getElementById('reel-tota').children).slice(0, 5),
    mena: Array.from(document.getElementById('reel-mena').children).slice(0, 5)
  };

  DOM.bettingCells.tota.forEach(c => c.addEventListener('click', () => placeBet('tota', +c.dataset.row)));
  DOM.bettingCells.mena.forEach(c => c.addEventListener('click', () => placeBet('mena', +c.dataset.row)));

  if (DOM.winCloseBtn) DOM.winCloseBtn.addEventListener('click', closeWin);
  const winBg = $('.win-overlay__bg');
  if (winBg) winBg.addEventListener('click', closeWin);

  // Preload base images to avoid flickering
  ['assets/tota.png', 'assets/mena.png', 'assets/emp_design.png'].forEach(src => {
    const img = new Image();
    img.src = src;
  });

  // Set initial state — bird hidden
  DOM.allCells.forEach(cell => resetCellVisual(cell));

  startTimer();
  updatePossibleWin();
}

/* ══════════════════════════════════════════════════════════
   CELL VISUAL RESET — bird hidden
   ══════════════════════════════════════════════════════════ */
function resetCellVisual(cell) {
  const bird = cell.querySelector('.bird');
  const lbl = cell.querySelector('.bird-lbl');

  // Bird: invisible initially
  if (bird) {
    bird.src = `assets/${cell.dataset.bird}.png`;
    bird.style.opacity = '0';
    bird.style.transform = 'translate(-50%, -50%) scale(0.4)';
  }

  // Labels hidden
  if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }

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

  DOM.bettingCells[bird].forEach(c => c.removeAttribute('data-active'));
  const activeCell = DOM.bettingCells[bird].find(c => +c.dataset.row === row);
  if (activeCell) activeCell.setAttribute('data-active', 'true');

  renderBoard();
  updatePossibleWin();
}

/* ══════════════════════════════════════════════════════════
   BOARD RENDERING (betting phase only)
   ══════════════════════════════════════════════════════════ */
function renderBoard() {
  ['tota', 'mena'].forEach(bird => {
    DOM.bettingCells[bird].forEach(cell => {
      const row = +cell.dataset.row;
      const cellBird = cell.dataset.bird;
      const hasBet = STATE.bets[cellBird].row === row && STATE.bets[cellBird].amount > 0;

      const betBadge = cell.querySelector('.cell-bet');
      if (betBadge) {
        betBadge.textContent = hasBet ? `₹${STATE.bets[cellBird].amount}` : '';
        betBadge.style.display = hasBet ? 'block' : 'none';
      }

      if (STATE.phase === 'BETTING') {
        const lbl = cell.querySelector('.bird-lbl');

        if (hasBet) {
          if (lbl) { lbl.style.opacity = '1'; lbl.style.display = 'block'; }
        } else {
          if (!cell.classList.contains('cell--winner')) {
            if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
          }
        }
      }
    });
  });
}

/* ══════════════════════════════════════════════════════════
   TIMER & GAME LOOP
   ══════════════════════════════════════════════════════════ */
let timerInterval = null;
let spinInterval = null;

function startTimer() {
  clearInterval(timerInterval);
  STATE.roundTimer = 22;
  STATE.phase = 'BETTING';
  renderTimer();
  timerInterval = setInterval(() => {
    STATE.roundTimer--;
    renderTimer();

    if (STATE.roundTimer === 7) {
      startSpinPhase();
    }

    if (STATE.roundTimer <= 0) {
      clearInterval(timerInterval);
      stopSpinPhaseAndReveal();
      setTimeout(() => {
        resetRound();
      }, 3500);
    }
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

  const allCells = DOM.allCells;

  // Prepare all cells for spinning (hide birds and labels)
  allCells.forEach(cell => {
    cell.classList.remove('cell--winner');
    cell.removeAttribute('data-win');

    const lbl = cell.querySelector('.bird-lbl');
    const betBadge = cell.querySelector('.cell-bet');
    if (lbl) { lbl.style.opacity = '0'; lbl.style.display = 'none'; }
    if (betBadge) { betBadge.style.display = 'none'; }

    const birdImg = cell.querySelector('.bird');
    if (birdImg) {
      birdImg.style.opacity = '0';
      birdImg.style.filter = 'none';
      birdImg.style.transform = 'translate(-50%, -50%) scale(1)';
    }
  });

  // Start physical reel scroll animation
  ['tota', 'mena'].forEach(bird => {
    const track = document.getElementById(`reel-${bird}`);
    const colHeight = track.parentElement.clientHeight;
    const distance = (colHeight + 8) * 3; // 3 full sets

    const anim = track.animate([
      { transform: `translateY(0px)` },
      { transform: `translateY(-${distance}px)` }
    ], {
      duration: 800, // Very fast spin
      iterations: Infinity,
      easing: 'linear'
    });
    track._spinAnim = anim;
  });
}

function stopSpinPhaseAndReveal() {
  ['tota', 'mena'].forEach(bird => {
    const track = document.getElementById(`reel-${bird}`);
    if (track._spinAnim) track._spinAnim.cancel();
    track.style.transform = 'translateY(0px)'; // Snap to first 5 cells

    const winningRow = STATE.winningRows[bird];
    // Reveal bird only in the first set of 5
    const cell = DOM.bettingCells[bird].find(c => +c.dataset.row === winningRow);
    if (cell) revealWinningBird(cell);
  });

  resolvePayouts();
}

/* ══════════════════════════════════════════════════════════
   BIRD REVEAL — per cell
   ══════════════════════════════════════════════════════════ */
function revealWinningBird(cell) {
  const bird = cell.dataset.bird;
  const birdImg = cell.querySelector('.bird');
  const lbl = cell.querySelector('.bird-lbl');

  if (!birdImg) return;

  birdImg.src = `assets/${bird}.png`;
  cell.setAttribute('data-win', 'true');

  birdImg.style.opacity = '';
  birdImg.style.transform = '';
  birdImg.style.filter = '';

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
  if (win > 0) {
    STATE.balance += win;
    showToast(`You won ₹${fmtCur(win)}!`);
  } else if (hasBets) {
    showToast('Better luck next time!');
  }
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

  DOM.allCells.forEach(cell => {
    cell.removeAttribute('data-active');
    const betBadge = cell.querySelector('.cell-bet');
    if (betBadge) { betBadge.style.display = 'none'; betBadge.textContent = ''; }

    const tempElems = cell.querySelectorAll('.card-half, .shatter-flash');
    tempElems.forEach(el => el.remove());
  });

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
  DOM.possWinDisplay.textContent = (t + m) > 0 ? `${fmtCur((t + m) * STATE.multiplier)}` : '₹0';
  DOM.multDisplay.textContent = `${STATE.multiplier.toFixed(2)}x`;
}

/* ══════════════════════════════════════════════════════════
   WIN MODAL (Removed per user request)
   ══════════════════════════════════════════════════════════ */
function closeWin() {
  if (DOM.winOverlay) {
    DOM.winOverlay.classList.remove('is-visible');
    DOM.winOverlay.setAttribute('aria-hidden', 'true');
  }
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

window.addEventListener('load', () => {
  const preloader = document.getElementById('preloader');
  if (preloader) {
    // Add a slight delay to let the user appreciate the elite loading screen
    setTimeout(() => {
      preloader.classList.add('is-hidden');
    }, 1200);
  }
});
