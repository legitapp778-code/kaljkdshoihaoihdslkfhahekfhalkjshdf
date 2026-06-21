# Tota & Mena Elite - Technical Documentation & Game Mechanics

This document provides a comprehensive, deep-dive overview of the **Tota & Mena Elite** game architecture, logic, user flow, and underlying codebase.

---

## 1. Project Overview

**Tota & Mena Elite** is a premium, mobile-first, casino-style betting game built entirely with modern Web Technologies (HTML5, Vanilla CSS3, and ES6 JavaScript). 

The game board consists of a 5x2 grid representing two distinct columns:
*   **Column 1 (Tota):** The green parrot.
*   **Column 2 (Mena):** The brown myna.

Players place bets by predicting exactly which row (1 through 5) the Tota and Mena birds will land on when the spinning reels finally stop.

---

## 2. Core Game Loop & Phases

The game operates on a continuous, fully automated loop controlled by `script.js`. It is divided into three distinct phases:

### Phase 1: Betting Phase (15 Seconds)
*   **Timer:** A 15-second countdown clock manages the betting window.
*   **Drafting a Bet:** 
    *   Players select a betting chip (e.g., ₹50, ₹100, ₹500) from the betting panel or type a custom amount.
    *   Clicking a tile on the board immediately updates the tile to a "draft" state. The tile smoothly fades to a premium gold background (`selected.webp`) and displays a preview badge of the selected bet amount.
*   **Placing a Bet:**
    *   The player clicks the **"Place Bet"** button. The game validates their balance.
    *   If sufficient funds exist, the bet is locked in, the balance is deducted, and a success toast notification appears.
*   **Dynamic Auto-Movement:** If a player has already placed a bet and clicks a different row, the game automatically moves the locked-in bet to the new row without requiring them to click "Place Bet" a second time.

### Phase 2: Spinning Phase
*   When the timer hits zero, the game enters the `SPINNING` phase and all betting inputs are locked.
*   **Physical DOM Cloning:** To create a physical casino slot-machine reel effect, the game clones the 5 rows multiple times directly into the DOM container.
*   **Web Animations API:** Instead of relying on rigid CSS keyframes, the game uses JavaScript's `Element.animate()` to physically scroll the column downwards at high speeds infinitely.

### Phase 3: Result Phase (3.5 Seconds)
*   **Deceleration:** The infinite spin animation is cancelled and replaced by a smooth `cubic-bezier` deceleration animation.
*   **Staggered Stopping:** The Tota column stops slightly earlier (1.2s) than the Mena column (1.6s) to create suspense.
*   **The Reveal:** The columns align perfectly. The winning tiles trigger a dramatic glass-shattering animation (`.shatterLeft` and `.shatterRight` in `style.css`), bursting open to reveal the winning bird. The background of the winning tile illuminates to the premium gold (`selected.webp`).
*   **Payouts:** If the user's locked-in row matches the winning row, their bet is multiplied by `2.00x` and added to their balance. A massive "BIG WIN" crown modal overtakes the screen.
*   **Reset:** Exactly 3.5 seconds after the wheel stops, the board wipes clean (removing birds, highlights, and leftover shattered glass), and the 15-second timer immediately restarts.

---

## 3. Technology Stack & File Structure

The project relies strictly on Vanilla web technologies for maximum performance without the overhead of frameworks.

### `index.html` (The Game View)
*   **Structure:** Follows a mobile-first approach wrapped in an `.app` container (`max-width: 480px`). It includes a header (with balance, user profile, and notifications), the central game board grid, the betting panel, and a bottom navigation bar.
*   **Responsive Sidebars:** On larger desktop screens, left and right sidebars (Live Stats, Game History, Navigation) automatically appear to utilize the extra screen real estate.
*   **Inline Preloader:** Contains a native inline script that listens for `window.onload` with an 800ms minimum artificial delay, ensuring the premium loading screen never flashes jarringly and perfectly waits for all image assets to load from the server.

### `script.js` (The Game Engine)
*   The entire brain of the application. It manages the `STATE` object (balances, phases, selected rows).
*   **DOM Caching:** Uses lightweight helper functions (`$`, `$$`) to cache DOM elements on `init()`.
*   Contains detailed line-by-line block comments above every core function (`startTimer`, `startSpinPhase`, `stopSpinPhaseAndReveal`, `resolvePayouts`).

### `style.css` (The Design System & Physics)
*   **Premium Aesthetics:** Uses curated variables for rich colors (`--gold`, `--gold-dark`, `--text-dark`).
*   **Hardware Acceleration:** Heavily utilizes `transform: translateZ(0)` and `will-change: transform` to force the browser to use the GPU for animations, ensuring buttery-smooth 60fps reel spins even on low-end budget smartphones.
*   **Complex Animations:** Uses custom `@keyframes` for the shattered glass physics (`shatterLeft`, `shatterRight`) and the smooth pop-out of the winning birds (`birdEmerge`).
*   **Scroll Locking:** Uses specific layout classes like `.game-scroll` to completely lock the main game screen from accidental stretching/scrolling, while keeping generic sub-pages (like Wallet and Support) fully scrollable via `.main-scroll`.

### Auxiliary Pages (`/pages/`)
*   **`wallet.html`:** A full UI for managing deposits, withdrawals, and saved payment methods.
*   **`transactions.html` & `results.html`:** Detailed tables tracking game history and financial movements.
*   **`settings.html`, `support.html`, `kyc-aml.html`:** Account management and legal compliance views.

### Assets (`/assets/`)
*   **WebP Optimization:** All images are heavily compressed `.webp` files (e.g., `tota.webp`, `mena.webp`, `emp_design.webp`, `selected.webp`). This ensures the app is incredibly lightweight and loads almost instantly over 3G/4G cellular connections.

---

## 4. Notable Engineering & UX Decisions

1.  **Decoupled Betting & Selection:** The architecture heavily separates *selecting a tile* from *committing money*. This provides a friction-free user experience, allowing players to click around the board, visualize their bets with "draft badges", and build confidence before hitting "Place Bet".
2.  **No-Lag Filters:** Expensive CSS properties like `filter: drop-shadow` were explicitly removed from moving elements (like the spinning birds) and replaced with static `box-shadow` to prevent layout thrashing and severe battery drain on mobile devices.
3.  **Dynamic Background Swapping:** Instead of relying purely on complex CSS pseudo-elements for the golden selected tile, the engine directly swaps the actual physical `<img>` source of the tile background to `selected.webp`. This creates a foolproof, 100% stable visual state that survives intense DOM cloning during the spin phase.
