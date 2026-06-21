// ── STATE ─────────────────────────────────────────────────────────────────────
let coins = 500;
let gamePhase = 'idle';   // idle | pick-bird | pick-amount | pick-number | revealing | done
let boardP = [];       // 5 entries: 'P' or 'M'
let boardM = [];
let selNum = null;
let selBird = null;
let betAmount = 50;

const ROWS = 5;

/* ── BOARD GENERATION ──────────────────────────────────────────────────────────
   This generates the random winning rows for the game.
   Line-by-line explanation:
   1. Randomly selects a winning row for the "Popat" bird between 0 and 4.
   2. Randomly selects a DIFFERENT winning row for the "Mena" bird.
   3. Fills the remaining 3 non-winning rows with a randomized shuffle of birds.
   4. Loops through the 5 rows and assigns the correct combination to boardP and boardM.
   ───────────────────────────────────────────────────────────────────────────── */
function generateBoard() {
  const ppRow = Math.floor(Math.random() * ROWS);
  let mmRow;
  do { mmRow = Math.floor(Math.random() * ROWS); } while (mmRow === ppRow);

  // Remaining 3 rows are mixed
  boardP = []; boardM = [];
  let extras = ['P', 'M', 'P', 'M', Math.random() < 0.5 ? 'P' : 'M'].slice(0, ROWS - 2);
  // shuffle extras
  for (let i = extras.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [extras[i], extras[j]] = [extras[j], extras[i]];
  }

  let eIdx = 0;
  for (let i = 0; i < ROWS; i++) {
    if (i === ppRow) { boardP.push('P'); boardM.push('P'); }
    else if (i === mmRow) { boardP.push('M'); boardM.push('M'); }
    else {
      const v = extras[eIdx++];
      boardP.push(v);
      boardM.push(v === 'P' ? 'M' : 'P');
    }
  }
}

// ── UI HELPERS ────────────────────────────────────────────────────────────────
function setStatus(msg, cls = '') {
  const el = document.getElementById('status-msg');
  el.textContent = msg;
  el.className = cls;
}

function updateCoins() {
  document.getElementById('coins-val').textContent = coins;
}

function flashCoins(type) {
  const el = document.getElementById('coins-val');
  el.className = '';
  void el.offsetWidth;
  el.className = type === 'win' ? 'wf' : 'lf';
  setTimeout(() => el.className = '', 2000);
}

function setProgress(step) {
  ['pd1', 'pd2', 'pd3'].forEach((id, i) => {
    const el = document.getElementById(id);
    el.className = 'pdot';
    if (i + 1 < step) el.classList.add('done');
    if (i + 1 === step) el.classList.add('on');
  });
}

// ── CARD HELPERS ──────────────────────────────────────────────────────────────
function getCard(row, col) {
  return document.getElementById(`card-${row}-${col}`);
}

function makeReelTrack() {
  const seq = ['🦅', '🦉', '🦅', '🦉', '🦅', '🦉'];
  const full = [...seq, ...seq];
  const track = document.createElement('div');
  track.className = 'spin-reel';
  full.forEach(sym => {
    const s = document.createElement('div');
    s.className = `spin-sym ${sym === '🦅' ? 'sp' : 'sm'}`;
    s.textContent = sym;
    track.appendChild(s);
  });
  return track;
}

/* ── BUILD TABLE ───────────────────────────────────────────────────────────────
   This dynamically generates all the HTML for the board depending on the game state.
   Line-by-line explanation:
   1. Clears out any existing HTML in the table body.
   2. Loops 5 times (for the 5 rows).
   3. Creates the row and cell <div>s.
   4. Builds the 3D flipping card structure with a 'card-front' and 'card-back'.
   5. If the game is in 'hidden' state, adds the click event listeners to pick the row.
   6. If the game is in 'spinning' state, injects the infinite spinning reel strip inside the cards.
   ────────────────────────────────────────────────────────────────────────────── */
function buildTable(state) {
  const body = document.getElementById('tbl-body');
  body.innerHTML = '';

  for (let i = 0; i < ROWS; i++) {
    const row = document.createElement('div');
    row.className = 'grow';
    if (state === 'hidden') row.classList.add('sel-able');
    if (selNum !== null && i === selNum) row.classList.add('sel-row');

    const rnum = document.createElement('div');
    rnum.className = 'rnum';
    rnum.textContent = i + 1;
    row.appendChild(rnum);

    ['p', 'm'].forEach(col => {
      const cell = document.createElement('div');
      cell.className = 'card-cell';

      const card = document.createElement('div');
      card.className = 'card';
      card.id = `card-${i}-${col}`;

      const back = document.createElement('div');
      back.className = 'card-back';
      if (state === 'spinning') {
        card.classList.add('spinning');
        back.appendChild(makeReelTrack());
      } else {
        const q = document.createElement('span');
        q.className = 'q';
        q.textContent = '?';
        back.appendChild(q);
      }
      card.appendChild(back);

      const front = document.createElement('div');
      front.className = 'card-face';
      const val = col === 'p' ? boardP[i] : boardM[i];

      const shouldReveal =
        state === 'both' ||
        (state === 'revealing-p' && col === 'p') ||
        (state === 'revealing-m' && col === 'm');

      if (shouldReveal && val !== undefined) {
        front.classList.add(val === 'P' ? 'fp' : 'fm');
        front.textContent = val === 'P' ? '🦅' : '🦉';

        const isWinCell =
          selNum === i &&
          ((selBird === 'P' && val === 'P' && col === 'p') ||
            (selBird === 'M' && val === 'M' && col === 'm'));
        if (state === 'both' && isWinCell) front.classList.add('is-win');

        card.appendChild(front);
        requestAnimationFrame(() => requestAnimationFrame(() => {
          card.classList.add('flipped');
        }));
      } else {
        front.textContent = '?';
        card.appendChild(front);
      }

      cell.appendChild(card);
      row.appendChild(cell);
    });

    if (state === 'hidden') {
      row.addEventListener('click', () => pickNumber(i));
    }

    body.appendChild(row);
  }
}

/* ── PHASE PANEL RENDERER ──────────────────────────────────────────────────────
   This dynamically renders the UI control panel at the bottom based on the gamePhase.
   Line-by-line explanation:
   1. Clears out the control panel HTML.
   2. Checks if we are in phase 'pick-bird', 'pick-amount', or 'pick-number'.
   3. Dynamically generates the <button>s for picking birds, amounts, or spinning.
   4. Attaches click listeners to move the user to the next state when clicked.
   5. Renders a 'Back' button to let the user undo their selection.
   ────────────────────────────────────────────────────────────────────────────── */
function renderPhasePanel() {
  const content = document.getElementById('phase-content');
  content.innerHTML = '';

  if (gamePhase === 'idle' || gamePhase === 'pick-bird') {
    // Step 1: Pick bird
    setStatus('Step 1 · Pick your bird', 'step');
    setProgress(1);

    const birdWrap = document.createElement('div');
    birdWrap.id = 'bird-btns';

    const pBtn = document.createElement('button');
    pBtn.className = 'bird-btn pb' + (selBird === 'P' ? ' on' : '');
    pBtn.innerHTML = '🦅 Popat <span class="mtag">2×</span>';

    const mBtn = document.createElement('button');
    mBtn.className = 'bird-btn mb' + (selBird === 'M' ? ' on' : '');
    mBtn.innerHTML = '🦜 Mena <span class="mtag">1.5×</span>';

    pBtn.addEventListener('click', () => {
      selBird = 'P';
      gamePhase = 'pick-amount';
      renderPhasePanel();
    });
    mBtn.addEventListener('click', () => {
      selBird = 'M';
      gamePhase = 'pick-amount';
      renderPhasePanel();
    });

    birdWrap.appendChild(pBtn);
    birdWrap.appendChild(mBtn);
    content.appendChild(birdWrap);

  } else if (gamePhase === 'pick-amount') {
    // Step 2: Pick amount
    setStatus('Step 2 · Choose your bet amount', 'step');
    setProgress(2);

    const amtWrap = document.createElement('div');
    amtWrap.id = 'amt-btns';

    [25, 50, 100, 200].forEach(amt => {
      const btn = document.createElement('button');
      btn.className = 'amt-btn' + (betAmount === amt ? ' on' : '');
      btn.textContent = `${amt} 🪙`;
      btn.addEventListener('click', () => {
        if (coins < amt) { setStatus('Not enough coins!'); return; }
        betAmount = amt;
        gamePhase = 'pick-number';
        buildTable('hidden');
        renderPhasePanel();
      });
      amtWrap.appendChild(btn);
    });

    content.appendChild(amtWrap);

    // Back button
    const back = document.createElement('button');
    back.className = 'back-btn';
    back.textContent = '← Back';
    back.addEventListener('click', () => {
      gamePhase = 'pick-bird';
      selBird = null;
      buildTable('idle');
      renderPhasePanel();
    });
    content.appendChild(back);

  } else if (gamePhase === 'pick-number') {
    // Step 3: Pick row
    setStatus(`Step 3 · ${selBird === 'P' ? '🦅 Popat' : '🦜 Mena'} · ${betAmount}🪙 · Click a row`, 'step');
    setProgress(3);

    if (selNum !== null) {
      // Show spin button
      const spinBtn = document.createElement('button');
      spinBtn.id = 'spin-btn-inline';
      spinBtn.innerHTML = `🎰 SPIN · Row ${selNum + 1}`;
      spinBtn.addEventListener('click', doSpin);
      content.appendChild(spinBtn);
    } else {
      const hint = document.createElement('div');
      hint.style.cssText = 'color:rgba(255,255,255,.4);font-size:.85rem;letter-spacing:2px;text-align:center;padding:8px 0;';
      hint.textContent = '👆 Click a row above';
      content.appendChild(hint);
    }

    // Back button
    const back = document.createElement('button');
    back.className = 'back-btn';
    back.textContent = '← Back';
    back.addEventListener('click', () => {
      gamePhase = 'pick-amount';
      selNum = null;
      buildTable('idle');
      renderPhasePanel();
    });
    content.appendChild(back);
  }
}

/* ── PICK NUMBER ───────────────────────────────────────────────────────────────
   This locks in the user's selected row visually on the board.
   Line-by-line explanation:
   1. Checks if the user is in the correct phase to be clicking rows.
   2. Saves the clicked index to the selNum variable.
   3. Toggles the 'sel-row' CSS class on all rows to highlight only the clicked one.
   4. Re-renders the bottom panel to show the 'SPIN' button.
   ────────────────────────────────────────────────────────────────────────────── */
function pickNumber(idx) {
  if (gamePhase !== 'pick-number') return;
  selNum = idx;

  document.querySelectorAll('.grow').forEach((r, i) => {
    r.classList.toggle('sel-row', i === idx);
  });

  renderPhasePanel();
}

/* ── SPIN ──────────────────────────────────────────────────────────────────────
   This triggers the main game loop to start spinning.
   Line-by-line explanation:
   1. Validates if the user has enough coins, then subtracts the betAmount.
   2. Calls generateBoard() to determine the random results of this spin.
   3. Changes phase to 'spinning' and clears the bottom UI panel.
   4. Re-builds the entire table using buildTable('spinning') to inject the animated reels.
   5. Calls stopReelsStaggered() to handle the landing animation sequence.
   ────────────────────────────────────────────────────────────────────────────── */
function doSpin() {
  if (coins < betAmount) { setStatus('Not enough coins!'); return; }
  coins -= betAmount;
  updateCoins();
  generateBoard();

  gamePhase = 'spinning';
  document.getElementById('phase-content').innerHTML = '';
  setStatus('🎰 Spinning…', 'step');

  buildTable('spinning');

  stopReelsStaggered(() => {
    gamePhase = 'revealing';
    setTimeout(() => {
      buildTable('both');
      setStatus('Cards revealing…', 'step');
    }, 120);
    setTimeout(evaluate, 1200);
  });
}

/* ── STOP REELS ────────────────────────────────────────────────────────────────
   This creates a dramatic staggered stopping effect for the columns.
   Line-by-line explanation:
   1. Uses an array of timers [800, 1400] to make the second column stop later than the first.
   2. Loops through all 5 rows and removes the 'spinning' CSS class one by one with an 80ms delay to create a clacking/stopping effect.
   3. Injects the '?' back into the cards once they stop.
   4. Triggers the callback function (onAllDone) once the very last card stops spinning.
   ────────────────────────────────────────────────────────────────────────────── */
function stopReelsStaggered(onAllDone) {
  const stopTimes = [800, 1400];

  stopTimes.forEach((t, colIdx) => {
    setTimeout(() => {
      const colKey = colIdx === 0 ? 'p' : 'm';
      for (let r = 0; r < ROWS; r++) {
        const card = getCard(r, colKey);
        if (!card) continue;
        setTimeout(() => {
          card.classList.remove('spinning');
          const back = card.querySelector('.card-back');
          if (!back) return;
          back.innerHTML = '';
          const q = document.createElement('span');
          q.className = 'q';
          q.textContent = '?';
          back.appendChild(q);
          card.classList.add('stopped');
          setTimeout(() => card.classList.remove('stopped'), 450);
        }, r * 80);
      }
      if (colIdx === 1) {
        setTimeout(onAllDone, ROWS * 80 + 450);
      }
    }, t);
  });
}

/* ── EVALUATE ──────────────────────────────────────────────────────────────────
   This resolves the final result and displays winnings.
   Line-by-line explanation:
   1. Checks if the randomly generated board arrays match the user's selected bird and selected row.
   2. Calculates the payout based on the bird's specific multiplier.
   3. Adds the payout to the user's coins and triggers the flashCoins UI animation.
   4. Generates an HTML result box dynamically showing either a WIN or LOSE message.
   5. Updates the progress bar status text.
   6. Generates a 'Play Again' button if the user has enough coins left.
   ────────────────────────────────────────────────────────────────────────────── */
function evaluate() {
  let won = false, mult = 0;
  if (selBird === 'P') { won = boardP[selNum] === 'P' && boardM[selNum] === 'P'; mult = 2; }
  else { won = boardP[selNum] === 'M' && boardM[selNum] === 'M'; mult = 1.5; }

  const payout = won ? Math.floor(betAmount * mult) : 0;
  if (won) coins += payout;
  updateCoins();
  flashCoins(won ? 'win' : 'lose');

  if (typeof burst === 'function') burst(won);

  gamePhase = 'done';

  const content = document.getElementById('phase-content');
  content.innerHTML = '';

  const box = document.createElement('div');
  box.id = 'result-box';
  if (won) {
    box.className = 'win';
    box.innerHTML = `🎉 WIN! +${payout} coins <span style="font-size:.95rem;opacity:.6">(${mult}×)</span>`;
  } else {
    box.className = 'lose';
    box.textContent = `❌ Wrong — lost ${betAmount} coins`;
  }
  content.appendChild(box);

  setStatus(won ? '🏆 You won! Play again?' : '💸 Better luck next time!');

  const hint = document.createElement('div');
  hint.id = 'again-hint';
  hint.textContent = coins >= 25 ? '▸ Press PLAY AGAIN to continue' : 'Not enough coins!';
  content.appendChild(hint);

  if (coins >= 25) {
    const again = document.createElement('button');
    again.className = 'back-btn';
    again.style.marginTop = '8px';
    again.textContent = '🔄 Play Again';
    again.addEventListener('click', () => {
      selNum = null;
      selBird = null;
      betAmount = 50;
      gamePhase = 'pick-bird';
      buildTable('idle');
      renderPhasePanel();
    });
    content.appendChild(again);
  }
}

// ── INIT ──────────────────────────────────────────────────────────────────────
updateCoins();
buildTable('idle');
setProgress(0);
gamePhase = 'pick-bird';
renderPhasePanel();
