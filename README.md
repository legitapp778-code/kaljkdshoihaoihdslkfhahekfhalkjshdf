# Tota & Mena Elite (Fakespins) — Project Context & Documentation

This document serves as a comprehensive context guide for any AI developer or engineer working on this repository. It explains the project architecture, design theme, core mechanics, and codebase structure.

---

## 1. Project Overview
**Tota & Mena Elite** is a premium, mobile-first, casino-style board betting game. The interface features a 5-row, 2-column grid representing two betting columns: **Tota** (green parrot, Column 1) and **Mena** (brown myna, Column 2). 

Players place bets on which row (1-5) the birds will land on at the end of a 60-second round.

---

## 2. Core Gameplay Mechanics
The game runs on a continuous **60-second round cycle** divided into three distinct phases:

1. **Betting Phase (60s to 20s)**:
   - Users can select a bet amount using predefined chip buttons (100, 200, 300, 500) or enter a custom amount using manual inputs.
   - Users click directly on cards in **Column 1 (Tota)** or **Column 2 (Mena)** to place a bet on that specific row.
   - Users can place bets on both Column 1 and Column 2 simultaneously (independent dual betting).
   - Placed bets are visualized with a gold badge directly on the card showing the bet amount (e.g., `₹200`).

2. **Spinning Phase (20s to 0s)**:
   - When the timer counts down to 20s, betting locks. Input fields and chip choices are disabled.
   - A vertical reel-spinning animation runs on the cards, shuffling Tota and Mena assets every 90ms with motion blur to simulate high-fidelity casino slot reels.

3. **Result Phase (0s)**:
   - At 0s, the spinning stops. 
   - A random winning row (1 to 5) is picked for Tota, and a random winning row (1 to 5) is picked for Mena.
   - The board resets so that **only the two winning cells are revealed** (using the nonempty card layout), while all other cells revert to the empty mandala design.
   - If the player's placed bets match the winning rows, they win **2.00x** the bet amount.
   - Result records are pushed to the **Spin History log** (up to 10 entries).
   - After displaying results for 5 seconds, the board clears, bets reset, and the timer restarts at 60s.

---

## 3. Technology Stack & File Structure
This is a lightweight front-end web application built using:
- **HTML5**: Structured semantic layout.
- **Vanilla CSS3**: Design system, animations, responsive media queries.
- **Vanilla Javascript (ES6)**: Game state loop, timing phase triggers, modal popups, wallet operations, and history logging.

### File Mapping
- [index.html](file:///c:/Users/dpart/Desktop/fakespins/index.html): Defines the game container layout, board rows, bet choice panels, stats footer bar, and the auxiliary overlays (Rules, History, Wallet, Win Modal).
- [style.css](file:///c:/Users/dpart/Desktop/fakespins/style.css): Premium design system implementation, modal styling, card overlay covers, and keyframe reel-spinning animations.
- [script.js](file:///c:/Users/dpart/Desktop/fakespins/script.js): Game loop timer, click handlers, asset swapping, mock wallet deposits/withdrawals, and history rendering.
- **`assets/`** directory:
  - `logo.png`: Premium header logo.
  - `emp_design.png`: Card background detailing gold borders, scroll corners, and a central empty mandala.
  - `tota.png` & `mena.png`: High-resolution graphics of the birds.
  - `1.png` to `5.png`: Stylized row index card numbers.

---

## 4. Visual Design System (Royal Casino Theme)
The UI incorporates a premium royal casino theme leveraging the following color palette:
- **Primary Gold**: `#c9a04c` (border outlines, text accents)
- **Cream Cards**: `#FCF6EA` / `#fdf8ed` (card covers, panels, and dropdown elements)
- **Tota Green**: `#2e7d32` (Tota bird theme, deposit color, and winning values)
- **Mena Brown**: `#7b3f00` / `#5d4037` (Mena bird theme and withdrawal buttons)
- **Dark Text**: `#2a1800` (high contrast, readability)
- **White Backgrounds**: `#FFFFFF` (clean stats bar and overlay containers)
- **Textured Body**: `#FAF8F5` (subtle premium off-white body backdrop)

---

## 5. UI Features & Modals
- **Wallet Modal**: Offers mock deposits and withdrawals that update the player balance (`STATE.balance` and navigation balance display).
- **History Modal**: Lists round statistics, indicating if the player won, lost, or had no active bet.
- **Rules Modal**: Provides a step-by-step tutorial on gameplay.
- **Responsiveness**: Elements are bound by a max-width container (`480px`) centered on desktop, utilizing media queries to scale images and gaps for smaller mobile aspect ratios.
