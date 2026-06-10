
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
  winningRows: { tota: null, mena: null },
  selectedRows: { tota: null, mena: null }
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

  DOM.bettingCells.tota.forEach(c => c.addEventListener('click', () => selectRow('tota', +c.dataset.row)));
  DOM.bettingCells.mena.forEach(c => c.addEventListener('click', () => selectRow('mena', +c.dataset.row)));

  if (DOM.winCloseBtn) DOM.winCloseBtn.addEventListener('click', closeWin);
  const winBg = $('.win-overlay__bg');
  if (winBg) winBg.addEventListener('click', closeWin);

  // Preload base images to avoid flickering
  ['assets/tota.webp', 'assets/mena.webp', 'assets/emp_design.webp'].forEach(src => {
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
    bird.src = `assets/${cell.dataset.bird}.webp`;
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
      renderBoard();
      updatePossibleWin();
    });
  });
  if (input) {
    input.addEventListener('input', () => {
      STATE.panelBetAmounts[bird] = +input.value || 0;
      chips.forEach(c => c.classList.remove('chip--selected'));
      renderBoard();
      updatePossibleWin();
    });
  }
  if (btn) {
    btn.addEventListener('click', () => {
      placeBet(bird);
    });
  }
}

function selectRow(bird, row) {
  if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
  STATE.selectedRows[bird] = row;

  // If a bet has already been placed, automatically move the bet to the new selection
  if (STATE.bets[bird].amount > 0) {
    placeBet(bird);
  } else {
    renderBoard();
  }
}

function placeBet(bird) {
  if (STATE.phase !== 'BETTING') return showToast('Betting is locked!');
  const row = STATE.selectedRows[bird];
  if (!row) return showToast(`Select a card in ${bird.toUpperCase()} column first!`);
  
  const amt = STATE.panelBetAmounts[bird];
  if (amt <= 0) return showToast('Select or enter a bet amount!');
  
  const diff = amt - STATE.bets[bird].amount;
  if (diff > STATE.balance) return showToast('Insufficient balance!');
  
  STATE.balance -= diff;
  updateBalanceDisplay();
  STATE.bets[bird] = { row, amount: amt };
  showToast(`₹${amt} on ${bird.toUpperCase()} Row ${row}`);

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
        const cellBg = cell.querySelector('.cell-bg');

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

  // Sync clones so the spinning reels exactly match the current betting state
  ['tota', 'mena'].forEach(bird => {
    const track = document.getElementById(`reel-${bird}`);
    
    // Remove old clones (children from index 5 onwards)
    while (track.children.length > 5) {
      track.removeChild(track.lastChild);
    }
    
    // Clone the top 5 cells to accurately reflect the bet state during the spin
    const baseCells = Array.from(track.children);
    for (let i = 0; i < 3; i++) {
      baseCells.forEach(cell => {
        const clone = cell.cloneNode(true);
        track.appendChild(clone);
      });
    }
  });

  // Update DOM.allCells so resetRound cleans up the new clones as well
  DOM.allCells = document.querySelectorAll('.board__cell');

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
  let finishedCount = 0;

  ['tota', 'mena'].forEach((bird, index) => {
    const track = document.getElementById(`reel-${bird}`);
    const colHeight = track.parentElement.clientHeight;
    // Scroll exactly 2 sets down to land seamlessly on an identical layout
    const distance = (colHeight + 8) * 2;

    if (track._spinAnim) track._spinAnim.cancel();

    // Smooth deceleration
    const stopAnim = track.animate([
      { transform: `translateY(0px)` },
      { transform: `translateY(-${distance}px)` }
    ], {
      duration: 1200 + (index * 400), // First stops at 1.2s, second at 1.6s
      easing: 'cubic-bezier(0.1, 0.9, 0.2, 1)',
      fill: 'forwards'
    });

    stopAnim.onfinish = () => {
      stopAnim.cancel(); // Remove the fill:forwards override so inline style works
      track.style.transform = 'translateY(0px)'; // Reset to true 0 immediately
      
      const winningRow = STATE.winningRows[bird];
      // Only manipulate original top 5 cells
      const cells = Array.from(track.querySelectorAll('.board__cell')).slice(0, 5);
      
      cells.forEach(cell => {
        const rowNum = parseInt(cell.dataset.row);
        const birdImg = cell.querySelector('.bird');
        if (!birdImg) return;
        
        if (rowNum === winningRow) {
          revealWinningBird(cell);
        } else {
          // Keep non-winning cells empty
          birdImg.style.opacity = '0';
        }
      });

      finishedCount++;
      if (finishedCount === 2) {
        resolvePayouts();
        setTimeout(() => {
          resetRound();
        }, 3500); // Wait 3.5s AFTER animation finishes
      }
    };
  });
}

/* ══════════════════════════════════════════════════════════
   BIRD REVEAL — per cell
   ══════════════════════════════════════════════════════════ */
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
    updateBalanceDisplay();
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
  STATE.selectedRows = { tota: null, mena: null };
  if (DOM.manualAmtTota) { DOM.manualAmtTota.disabled = false; DOM.manualAmtTota.value = ''; }
  if (DOM.manualAmtMena) { DOM.manualAmtMena.disabled = false; DOM.manualAmtMena.value = ''; }
  $$('.chip').forEach(c => c.classList.remove('chip--selected'));
  STATE.panelBetAmounts = { tota: 0, mena: 0 };

  DOM.allCells.forEach(cell => {
    cell.removeAttribute('data-active');
    cell.removeAttribute('data-win');
    cell.classList.remove('cell--winner');
    
    const betBadge = cell.querySelector('.cell-bet');
    if (betBadge) { betBadge.style.display = 'none'; betBadge.textContent = ''; }

    const tempElems = cell.querySelectorAll('.card-half, .shatter-flash');
    tempElems.forEach(el => el.remove());

    const birdImg = cell.querySelector('.bird');
    if (birdImg) {
      birdImg.style.opacity = '0';
      birdImg.style.transform = 'translate(-50%, -50%) scale(0.4)';
    }
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

function updateBalanceDisplay() {
  const coinsVal = document.getElementById('coins-val');
  if (coinsVal) {
    coinsVal.textContent = fmtCur(STATE.balance);
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
    preloader.classList.add('is-hidden');
  }
});
