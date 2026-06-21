# Tota & Mena Elite — Spring Boot Backend Specification (WebSocket Edition)
### For AI-Assisted Development (Cursor / Windsurf / Copilot)

> **Audience:** Vibe-coding AI. Treat every section as a hard requirement.  
> **Stack:** Java 17+, Spring Boot 3.x, Spring Security 6, PostgreSQL, Redis, JWT (JJWT 0.12.x), STOMP over SockJS  
> **Game integrity rule:** The backend **owns** all game outcomes. The frontend is display-only.  
> **Transport rule:** All real-time game state (phase changes, timer ticks, results) is pushed over WebSocket. REST is only for auth, bet placement, wallet, and user profile.

---

## 0. Non-Negotiable Security Rules (Read First)

| Rule | Detail |
|------|--------|
| **Server-side RNG** | Winning rows are generated on the server, never the client. The frontend `Math.random()` in `script.js` must be stripped entirely. |
| **JWT everywhere** | Every REST endpoint AND the WebSocket handshake require a valid JWT. |
| **WS auth on CONNECT, not just handshake** | The STOMP `CONNECT` frame must carry `Authorization: Bearer <token>` in its headers. Validate JWT in `ChannelInterceptor` before allowing any subscription. |
| **No user can touch another user's data** | Every query touching `user_id`, `bet_id`, or `round_id` is cross-checked against the JWT subject. Return `403` on mismatch — never `404`. |
| **Rate limiting** | All REST endpoints are rate-limited. WS message sends are rate-limited per user in the `ChannelInterceptor`. |
| **OTP is 6 digits, hardcoded `123456` for now** | Replace with real SMS provider later. Verification window: 5 minutes. Max 3 attempts per phone per 10 minutes. |
| **All money is integers (paise)** | Never use `float` or `double` for balance or bets. Store as `BIGINT`. Display layer divides by 100. |
| **Idempotency on bets** | Each `place_bet` message carries a client-generated `idempotency_key` (UUID). Duplicates within 60 seconds return the original response without double-charging. |
| **Never push winning rows early** | The server NEVER includes `winningRowTota` or `winningRowMena` in any broadcast until the round is `FINISHED`. |

---

## 1. Project Setup

### 1.1 `pom.xml` Dependencies

```xml
<!-- Core -->
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-data-redis
spring-boot-starter-validation

<!-- WebSocket -->
spring-boot-starter-websocket

<!-- DB -->
postgresql (runtime)
flyway-core

<!-- JWT -->
io.jsonwebtoken:jjwt-api:0.12.3
io.jsonwebtoken:jjwt-impl:0.12.3
io.jsonwebtoken:jjwt-jackson:0.12.3

<!-- Rate Limiting -->
bucket4j-spring-boot-starter

<!-- Utils -->
lombok
mapstruct
```

### 1.2 `application.yml` Structure

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379

app:
  jwt:
    secret: ${JWT_SECRET}           # min 64 random chars, env var
    access-expiry-ms: 900000        # 15 minutes
    refresh-expiry-ms: 604800000    # 7 days
  otp:
    value: "123456"                 # TODO: replace with SMS provider
    ttl-minutes: 5
    max-attempts: 3
  game:
    betting-phase-seconds: 15
    multiplier: 2.00                # BigDecimal, not double
  websocket:
    allowed-origins: ${WS_ALLOWED_ORIGIN}   # e.g. https://yourdomain.com
```

---

## 2. Database Schema (Flyway Migrations)

### `V1__create_users.sql`

```sql
CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    phone         VARCHAR(15) NOT NULL UNIQUE,
    display_name  VARCHAR(50),
    kyc_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE wallets (
    id              UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID   NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance_paise   BIGINT NOT NULL DEFAULT 0 CHECK (balance_paise >= 0),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### `V2__create_auth_tables.sql`

```sql
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE otp_attempts (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    phone         VARCHAR(15) NOT NULL,
    attempt_count INT         NOT NULL DEFAULT 0,
    window_start  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### `V3__create_game_tables.sql`

```sql
CREATE TABLE rounds (
    id                UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    status            VARCHAR(20) NOT NULL DEFAULT 'BETTING',
    winning_row_tota  SMALLINT,
    winning_row_mena  SMALLINT,
    betting_starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    spinning_at       TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE bets (
    id               UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id         UUID     NOT NULL REFERENCES rounds(id),
    user_id          UUID     NOT NULL REFERENCES users(id),
    bird             VARCHAR(4)  NOT NULL CHECK (bird IN ('tota','mena')),
    selected_row     SMALLINT NOT NULL CHECK (selected_row BETWEEN 1 AND 5),
    amount_paise     BIGINT   NOT NULL CHECK (amount_paise > 0),
    idempotency_key  UUID     NOT NULL,
    payout_paise     BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (round_id, user_id, bird),
    UNIQUE (idempotency_key)
);

CREATE TABLE transactions (
    id                  UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID     NOT NULL REFERENCES users(id),
    type                VARCHAR(20) NOT NULL,
    amount_paise        BIGINT   NOT NULL,
    balance_after_paise BIGINT   NOT NULL,
    reference_id        UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bets_round_user   ON bets(round_id, user_id);
CREATE INDEX idx_bets_user         ON bets(user_id);
CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_rounds_status     ON rounds(status);
```

---

## 3. Package Structure

```
com.totemena.elite
├── config/
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java          ← NEW
│   ├── WebSocketSecurityConfig.java  ← NEW
│   ├── JwtConfig.java
│   └── RateLimitConfig.java
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── OtpService.java
│   ├── JwtService.java
│   ├── JwtAuthFilter.java            ← for REST
│   ├── JwtHandshakeInterceptor.java  ← NEW: WS HTTP handshake
│   ├── JwtChannelInterceptor.java    ← NEW: STOMP CONNECT frame
│   └── dto/
│       ├── SendOtpRequest.java
│       ├── VerifyOtpRequest.java
│       └── TokenResponse.java
├── user/
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   └── User.java
├── wallet/
│   ├── WalletController.java
│   ├── WalletService.java
│   ├── WalletRepository.java
│   ├── TransactionRepository.java
│   └── dto/
├── game/
│   ├── GameController.java           ← REST: bet placement only
│   ├── GameWsController.java         ← NEW: STOMP @MessageMapping
│   ├── GameService.java
│   ├── GameBroadcastService.java     ← NEW: pushes events to topics
│   ├── RoundRepository.java
│   ├── BetRepository.java
│   ├── GameScheduler.java
│   └── dto/
│       ├── PlaceBetRequest.java
│       ├── RoundTickEvent.java        ← NEW
│       ├── PhaseChangeEvent.java      ← NEW
│       ├── RoundResultEvent.java      ← NEW
│       ├── BetAckEvent.java           ← NEW
│       └── BetResultResponse.java
└── common/
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   └── (AppException, InsufficientBalanceException, etc.)
    └── dto/ApiResponse.java
```

---

## 4. WebSocket Configuration

### 4.1 `WebSocketConfig.java`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker for topics
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix for user-specific queues
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("${app.websocket.allowed-origins}")
            .withSockJS();   // SockJS fallback for environments that block WS
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8 * 1024);      // 8KB max message
        registration.setSendTimeLimit(15 * 1000);         // 15s send timeout
        registration.setSendBufferSizeLimit(512 * 1024);  // 512KB buffer
    }
}
```

### 4.2 `WebSocketSecurityConfig.java`

```java
@Configuration
public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages
            .nullDestMatcher().authenticated()           // CONNECT frame
            .simpSubscribeDestMatchers("/topic/**").authenticated()
            .simpSubscribeDestMatchers("/user/queue/**").authenticated()
            .simpDestMatchers("/app/**").authenticated()
            .anyMessage().denyAll();
    }

    @Override
    protected boolean sameOriginDisabled() {
        return true;  // We handle CORS ourselves
    }
}
```

### 4.3 `JwtHandshakeInterceptor.java`

This runs during the HTTP upgrade request (before the WS connection is established).

```java
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Extract token from query param: /ws?token=<JWT>
        // (Browser WebSocket API cannot set custom headers, so token goes in query string)
        String token = extractTokenFromQuery(request.getURI().getQuery());
        if (token == null || !jwtService.isValid(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;  // Reject the handshake
        }
        // Stash the user_id in WS session attributes for later use
        attributes.put("userId", jwtService.extractUserId(token));
        attributes.put("token", token);
        return true;
    }
}
```

> **Security note:** The token in query params is visible in server access logs.  
> Mitigate by: (1) using short-lived tokens (15 min), (2) ensuring HTTPS so it's not sniffable on the wire, (3) logging only hashed/truncated tokens.

### 4.4 `JwtChannelInterceptor.java`

This runs on every STOMP frame — the second security layer.

```java
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
            .getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Token must also be in STOMP CONNECT headers: "Authorization: Bearer <token>"
            String token = extractBearerToken(accessor.getFirstNativeHeader("Authorization"));
            if (token == null || !jwtService.isValid(token)) {
                throw new MessagingException("Invalid or expired JWT");
            }
            // Verify user is active
            UUID userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new MessagingException("User not found"));
            if (!user.isActive()) {
                throw new MessagingException("Account disabled");
            }
            // Set the Principal on the session so Spring knows who this is
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            accessor.setUser(auth);
        }

        // Rate limit non-CONNECT frames
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            enforceWsRateLimit(accessor.getUser());
        }

        return message;
    }
}
```

Register the interceptor in `WebSocketConfig`:
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(jwtChannelInterceptor);
}
```

---

## 5. Authentication (REST — unchanged from v1)

### 5.1 Endpoints

```
POST /api/v1/auth/send-otp
POST /api/v1/auth/verify-otp
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

### 5.2 `POST /api/v1/auth/send-otp`

**Request:** `{ "phone": "9876543210" }`

**Logic:**
1. Sanitize: strip spaces, validate 10-digit Indian number.
2. Check Redis `otp:attempts:{phone}`. If count >= 3 in 10-min window → `429`.
3. Increment Redis counter with 10-min TTL.
4. Store `otp:pending:{phone}` → `"123456"` with 5-min TTL.
5. Return `200 { "message": "OTP sent" }` — same response regardless of whether phone exists.

### 5.3 `POST /api/v1/auth/verify-otp`

**Request:** `{ "phone": "9876543210", "otp": "123456" }`

**Logic:**
1. Fetch `otp:pending:{phone}` from Redis. Missing → `401`.
2. Compare. Wrong → `401`. Delete Redis key immediately after any attempt (one-shot).
3. If correct:
   - UPSERT user by phone. New users also get a `wallets` row with `balance_paise = 0`.
   - Generate access token (JWT, 15 min) + refresh token (opaque UUID, 7 days).
   - Hash refresh token (SHA-256), store in `refresh_tokens`.
4. Return:
```json
{
  "accessToken": "<JWT>",
  "refreshToken": "<opaque-uuid>",
  "expiresIn": 900
}
```

### 5.4 JWT Structure

```json
{
  "sub": "<user_id UUID>",
  "phone": "+919876543210",
  "iat": 1718000000,
  "exp": 1718000900,
  "jti": "<random UUID>"
}
```

- Algorithm: `HS256`. Secret from `${JWT_SECRET}` only. Throw at startup if < 64 chars.
- `jti` used for Redis-based revocation on logout.

### 5.5 `POST /api/v1/auth/refresh`

1. SHA-256 hash submitted token.
2. Look up in `refresh_tokens` (not revoked, not expired).
3. Revoke old token, issue new access + refresh (token rotation).

### 5.6 `POST /api/v1/auth/logout`

1. Add JWT `jti` to Redis revocation set with TTL = remaining token lifetime.
2. Revoke refresh token in DB.
3. Return `200`.

---

## 6. Security Configuration (`SecurityConfig.java`)

```java
http
  .csrf(csrf -> csrf.disable())
  .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
  .authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/ws/**").permitAll()      // WS handshake auth handled separately
    .requestMatchers("/actuator/health").permitAll()
    .anyRequest().authenticated()
  )
  .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
  .exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, res, e) -> {
        res.setStatus(401);
        res.getWriter().write("{\"error\":\"Unauthorized\"}");
    })
    .accessDeniedHandler((req, res, e) -> {
        res.setStatus(403);
        res.getWriter().write("{\"error\":\"Forbidden\"}");
    })
  );
```

**CORS:**
```java
config.setAllowedOrigins(List.of("${app.frontend.origin}"));
config.setAllowedMethods(List.of("GET","POST","PUT","DELETE"));
config.setAllowedHeaders(List.of("Authorization","Content-Type"));
config.setAllowCredentials(true);
```

---

## 7. Rate Limiting

| Target | Limit |
|--------|-------|
| `POST /auth/send-otp` | 5 req / 10 min per IP |
| `POST /auth/verify-otp` | 5 req / 10 min per IP |
| `POST /auth/refresh` | 10 req / min per IP |
| `POST /game/bet` (REST) | 20 req / min per user |
| WS SEND frames (`/app/**`) | 30 frames / min per user (in `JwtChannelInterceptor`) |
| All other REST endpoints | 120 req / min per user |

WS rate limit uses Bucket4j with Redis. Key: `ws:ratelimit:{userId}`.

---

## 8. WebSocket Topics & Message Contracts

This is the core of the real-time design. All messages are JSON.

### 8.1 Topic Map

| Topic | Who subscribes | Direction | Purpose |
|-------|---------------|-----------|---------|
| `/topic/game/tick` | All connected clients | Server → All | Timer countdown, 1/sec |
| `/topic/game/phase` | All connected clients | Server → All | Phase change announcement |
| `/topic/game/result` | All connected clients | Server → All | Round result (winning rows) |
| `/user/queue/bet-ack` | Authenticated user only | Server → User | Bet placement confirmation |
| `/user/queue/errors` | Authenticated user only | Server → User | Error messages |
| `/user/queue/balance` | Authenticated user only | Server → User | Balance update after payout |

> `/topic/*` = broadcast to all. `/user/queue/*` = private to one user. Spring routes these automatically based on the Principal set by `JwtChannelInterceptor`.

### 8.2 Message Schemas

#### `RoundTickEvent` — broadcast every second on `/topic/game/tick`
```json
{
  "type": "TICK",
  "roundId": "uuid",
  "phase": "BETTING",
  "secondsRemaining": 11
}
```

#### `PhaseChangeEvent` — broadcast on `/topic/game/phase`
```json
{
  "type": "PHASE_CHANGE",
  "roundId": "uuid",
  "newPhase": "SPINNING",
  "timestamp": "2025-01-01T12:00:00Z"
}
```
Sent when round transitions `BETTING → SPINNING` and `SPINNING → FINISHED`.

#### `RoundResultEvent` — broadcast on `/topic/game/result` (only when FINISHED)
```json
{
  "type": "ROUND_RESULT",
  "roundId": "uuid",
  "winningRowTota": 3,
  "winningRowMena": 1
}
```
⚠ **This is the ONLY message that ever contains winning rows. It is ONLY sent after `finishRound()` completes.**

#### `BetAckEvent` — sent privately on `/user/queue/bet-ack`
```json
{
  "type": "BET_ACK",
  "betId": "uuid",
  "bird": "tota",
  "selectedRow": 3,
  "amountPaise": 10000,
  "balanceAfterPaise": 115000,
  "idempotencyKey": "uuid"
}
```

#### `BalanceUpdateEvent` — sent privately on `/user/queue/balance` after payout
```json
{
  "type": "BALANCE_UPDATE",
  "newBalancePaise": 245000,
  "reason": "BET_WON",
  "amountPaise": 20000,
  "roundId": "uuid"
}
```

#### `ErrorEvent` — sent privately on `/user/queue/errors`
```json
{
  "type": "ERROR",
  "code": "BETTING_CLOSED",
  "message": "Betting is closed for this round"
}
```

---

## 9. Game Engine

### 9.1 Round Lifecycle

```
BETTING (15 sec) → SPINNING (5 sec) → FINISHED → [new BETTING]
```

`GameScheduler` drives this. Active round tracked in Redis: `game:current_round_id`.

### 9.2 `GameScheduler.java`

```java
@Scheduled(fixedRate = 1000)
public void tick() {
    String roundId = redis.get("game:current_round_id");
    if (roundId == null) {
        startNewRound();
        return;
    }

    String phase = redis.get("game:round:" + roundId + ":phase");
    long elapsedSeconds = getElapsedSeconds(roundId);

    // Always broadcast a tick
    gameBroadcastService.broadcastTick(roundId, phase, computeSecondsRemaining(phase, elapsedSeconds));

    if ("BETTING".equals(phase) && elapsedSeconds >= 15) {
        transitionToSpinning(roundId);
    } else if ("SPINNING".equals(phase) && elapsedSeconds >= 5) {
        finishRound(roundId);
    }
}
```

### 9.3 `startNewRound()`

```
1. INSERT INTO rounds (status='BETTING') → new round_id
2. redis.set("game:current_round_id", roundId)
3. redis.set("game:round:{id}:phase", "BETTING", TTL=120)
4. redis.set("game:round:{id}:started_at", NOW_EPOCH_MS, TTL=120)
5. gameBroadcastService.broadcastPhaseChange(roundId, "BETTING")
```

### 9.4 `transitionToSpinning(roundId)`

```
1. winTota = SecureRandom.nextInt(5) + 1
   winMena = SecureRandom.nextInt(5) + 1

2. UPDATE rounds SET
     status='SPINNING',
     winning_row_tota=winTota,
     winning_row_mena=winMena,
     spinning_at=NOW()
   WHERE id=roundId AND status='BETTING'
   → 0 rows updated → race condition guard, log and return.

3. redis.set("game:round:{id}:phase", "SPINNING")
4. redis.set("game:round:{id}:started_at", NOW_EPOCH_MS)

5. gameBroadcastService.broadcastPhaseChange(roundId, "SPINNING")

⚠ Winning rows are stored in DB but NOT broadcast here.
```

### 9.5 `finishRound(roundId)` — @Transactional

```
1. Load round from DB (must be SPINNING).
2. Load all bets for this round.
3. For each bet:
   a. won = (bet.selectedRow == (bird==tota ? round.winningRowTota : round.winningRowMena))
   b. payoutPaise = won ? bet.amountPaise * 2 : 0
   c. UPDATE bets SET status=(won?'WON':'LOST'), payout_paise=payoutPaise

4. For each WINNING bet:
   a. SELECT FOR UPDATE wallet WHERE user_id = bet.userId
   b. UPDATE wallets SET balance_paise = balance_paise + payoutPaise
   c. INSERT INTO transactions (type='BET_WON', ...)
   d. gameBroadcastService.sendBalanceUpdate(bet.userId, newBalance, payoutPaise, roundId)

5. UPDATE rounds SET status='FINISHED', finished_at=NOW()
6. redis.del("game:current_round_id")

7. gameBroadcastService.broadcastPhaseChange(roundId, "FINISHED")
8. gameBroadcastService.broadcastResult(roundId, winTota, winMena)
   ← ONLY here do winning rows go to the client

9. Schedule startNewRound() in 3.5 seconds (matches frontend animation duration)
```

### 9.6 `GameBroadcastService.java`

```java
@Service
@RequiredArgsConstructor
public class GameBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    // Broadcast to ALL connected clients
    public void broadcastTick(String roundId, String phase, int secondsRemaining) {
        messagingTemplate.convertAndSend("/topic/game/tick",
            new RoundTickEvent(roundId, phase, secondsRemaining));
    }

    public void broadcastPhaseChange(String roundId, String newPhase) {
        messagingTemplate.convertAndSend("/topic/game/phase",
            new PhaseChangeEvent(roundId, newPhase));
    }

    public void broadcastResult(String roundId, int winTota, int winMena) {
        messagingTemplate.convertAndSend("/topic/game/result",
            new RoundResultEvent(roundId, winTota, winMena));
    }

    // Send to ONE specific user (private)
    public void sendBetAck(UUID userId, BetAckEvent event) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(), "/queue/bet-ack", event);
    }

    public void sendBalanceUpdate(UUID userId, long newBalance, long amount, String roundId) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(), "/queue/balance",
            new BalanceUpdateEvent(newBalance, "BET_WON", amount, roundId));
    }

    public void sendError(UUID userId, String code, String message) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(), "/queue/errors",
            new ErrorEvent(code, message));
    }
}
```

---

## 10. REST API (Bet Placement + Wallet + User)

All REST endpoints require `Authorization: Bearer <token>`.

### 10.1 `POST /api/v1/game/bet`

Bet placement remains REST (not WS) because it's a transactional write that needs HTTP response codes for clear error handling.

**Request:**
```json
{
  "bird": "tota",
  "selectedRow": 3,
  "amountPaise": 10000,
  "idempotencyKey": "uuid-v4-client-generated"
}
```

**Validation:**
- `bird`: `"tota"` or `"mena"` only
- `selectedRow`: 1–5 inclusive
- `amountPaise`: > 0, multiple of 100, max ₹100,000 (10,000,000 paise)
- `idempotencyKey`: valid UUID v4

**Logic:**
```
1. Idempotency check: SELECT bets WHERE idempotency_key = ?
   Found and created_at > NOW() - 60s → return cached 200 response.

2. Load current round from Redis. phase != 'BETTING' → 409 ("Betting is closed").

3. If user already has a bet on this bird this round → UPDATE logic (adjust diff).
   Else → INSERT logic.

4. INSERT logic:
   a. SELECT FOR UPDATE wallet WHERE user_id = ? (from JWT)
   b. balance < amountPaise → 422 ("Insufficient balance")
   c. UPDATE wallet balance (deduct)
   d. INSERT bets
   e. INSERT transactions (BET_PLACED)

5. UPDATE logic:
   a. diff = newAmount - existingAmount
   b. Deduct or refund diff accordingly
   c. UPDATE bets row

6. Everything in @Transactional.
7. After commit: call gameBroadcastService.sendBetAck(userId, ackEvent)
   ← User gets the confirmation over WS on /user/queue/bet-ack
8. REST response still returns 200 synchronously:
```

**REST Response (sync):**
```json
{
  "betId": "uuid",
  "bird": "tota",
  "selectedRow": 3,
  "amountPaise": 10000,
  "balanceAfterPaise": 115000
}
```
The frontend can use either the REST response or the WS `BetAckEvent` — both carry the same data.

### 10.2 `GET /api/v1/game/round/{roundId}/result`

Available only when `status = FINISHED`. Used by clients who reconnected and missed the WS broadcast.

**Response:**
```json
{
  "roundId": "uuid",
  "winningRowTota": 3,
  "winningRowMena": 1,
  "myResults": [
    { "bird": "tota", "selectedRow": 3, "status": "WON", "payoutPaise": 20000 },
    { "bird": "mena", "selectedRow": 4, "status": "LOST", "payoutPaise": 0 }
  ],
  "currentBalancePaise": 245000
}
```

### 10.3 `GET /api/v1/game/history`

`?page=0&size=20` — paginated past rounds for the authenticated user.

### 10.4 `GET /api/v1/wallet`

```json
{ "balancePaise": 125000, "displayBalance": "₹1,250.00" }
```

### 10.5 `GET /api/v1/wallet/transactions`

`?page=0&size=20&type=BET_WON`

### 10.6 `GET /api/v1/user/me`

```json
{
  "id": "uuid",
  "phone": "+919876543210",
  "displayName": "Player 1",
  "kycStatus": "PENDING",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

### 10.7 `PUT /api/v1/user/me`

Update `displayName` only. Phone is immutable.

---

## 11. Global Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    → 422 { "error": "Insufficient balance", "code": "INSUFFICIENT_BALANCE" }

    @ExceptionHandler(BettingClosedException.class)
    → 409 { "error": "Betting is closed for this round", "code": "BETTING_CLOSED" }

    @ExceptionHandler(AccessDeniedException.class)
    → 403 { "error": "Forbidden" }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    → 400 { "error": "Validation failed", "fields": { ... } }

    @ExceptionHandler(Exception.class)
    → 500 { "error": "Internal server error" }
    // Log full stack trace internally. Never expose it in response.
}
```

**Rule:** Never expose stack traces, SQL errors, or internal paths in any response.

---

## 12. Frontend Integration Changes

### 12.1 Auth Flow (unchanged)

- On app load, check `accessToken` in `localStorage`.
- If missing or expired → redirect to `pages/auth.html`.
- `auth.html` calls `POST /api/v1/auth/send-otp` and `POST /api/v1/auth/verify-otp`.
- On success, store `accessToken` and `refreshToken` in `localStorage`.

### 12.2 WebSocket Connection (replace all polling)

Create `ws.js`:

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const token = localStorage.getItem('accessToken');

const stompClient = new Client({
  // Token in query string for WS handshake (browsers can't set WS headers)
  webSocketFactory: () => new SockJS(`/ws?token=${token}`),

  connectHeaders: {
    // Token also in STOMP CONNECT frame headers
    Authorization: `Bearer ${token}`
  },

  onConnect: () => {
    // Subscribe to broadcast topics
    stompClient.subscribe('/topic/game/tick',   onTick);
    stompClient.subscribe('/topic/game/phase',  onPhaseChange);
    stompClient.subscribe('/topic/game/result', onResult);

    // Subscribe to private user queues
    stompClient.subscribe('/user/queue/bet-ack',  onBetAck);
    stompClient.subscribe('/user/queue/errors',   onWsError);
    stompClient.subscribe('/user/queue/balance',  onBalanceUpdate);
  },

  onDisconnect: () => {
    // Reconnect after 2s with exponential backoff
    scheduleReconnect();
  },

  onStompError: (frame) => {
    if (frame.headers.message?.includes('expired')) {
      refreshTokenAndReconnect();
    }
  }
});

stompClient.activate();
```

### 12.3 Event Handlers in `script.js`

```javascript
// ❌ REMOVE: setInterval polling
// ❌ REMOVE: STATE.winningRows.tota = Math.floor(Math.random() * 5) + 1
// ❌ REMOVE: STATE.winningRows.mena = Math.floor(Math.random() * 5) + 1
// ❌ REMOVE: All local balance mutations (STATE.balance +=/-=)

// ✅ REPLACE WITH:

function onTick(frame) {
  const data = JSON.parse(frame.body);
  // data = { roundId, phase, secondsRemaining }
  STATE.currentRoundId = data.roundId;
  STATE.phase = data.phase;
  // Update timer display — server is the source of truth
  updateTimerDisplay(data.secondsRemaining);
}

function onPhaseChange(frame) {
  const data = JSON.parse(frame.body);
  if (data.newPhase === 'SPINNING') {
    lockBettingUI();
    startSpinAnimation();   // purely visual — no RNG here
  }
  if (data.newPhase === 'FINISHED') {
    // Winning rows arrive separately in onResult, ~same time
  }
  if (data.newPhase === 'BETTING') {
    resetBoard();
    startNewBettingUI();
  }
}

function onResult(frame) {
  const data = JSON.parse(frame.body);
  // data = { roundId, winningRowTota, winningRowMena }
  STATE.winningRows.tota = data.winningRowTota;
  STATE.winningRows.mena = data.winningRowMena;
  stopSpinAndReveal();    // trigger visual reveal using server-provided rows
}

function onBetAck(frame) {
  const data = JSON.parse(frame.body);
  STATE.balance = data.balanceAfterPaise;
  updateBalanceDisplay();
  showToast(`Bet confirmed: ₹${data.amountPaise / 100} on ${data.bird.toUpperCase()} Row ${data.selectedRow}`);
}

function onBalanceUpdate(frame) {
  const data = JSON.parse(frame.body);
  STATE.balance = data.newBalancePaise;
  updateBalanceDisplay();
  if (data.reason === 'BET_WON') showBigWinModal(data.amountPaise);
}

function onWsError(frame) {
  const data = JSON.parse(frame.body);
  showToast(data.message);
}
```

### 12.4 Placing a Bet (REST, unchanged mechanism)

```javascript
async function placeBet(bird) {
  const response = await fetch('/api/v1/game/bet', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
    },
    body: JSON.stringify({
      bird,
      selectedRow: STATE.selectedRows[bird],
      amountPaise: STATE.panelBetAmounts[bird],
      idempotencyKey: crypto.randomUUID()
    })
  });
  // REST 4xx/5xx errors handled here
  // Success also triggers onBetAck via WS
}
```

### 12.5 Reconnection Strategy

```javascript
// On WS disconnect, the client must:
// 1. Reconnect with exponential backoff (2s, 4s, 8s, max 30s)
// 2. On reconnect, call GET /api/v1/game/current (REST) ONCE to re-sync state
//    in case a TICK or PHASE_CHANGE was missed during disconnect
// 3. If accessToken expired during disconnect, call POST /api/v1/auth/refresh first

function syncStateOnReconnect() {
  fetch('/api/v1/game/current', { headers: authHeaders() })
    .then(r => r.json())
    .then(data => {
      STATE.phase = data.phase;
      STATE.currentRoundId = data.roundId;
      STATE.balance = data.balance;
      // Re-render UI from server state
      renderFromState(data);
    });
}
```

> Keep `GET /api/v1/game/current` as a REST endpoint solely for this reconnect use case. It is NOT called on a timer.

---

## 13. Deployment Checklist

| Item | Requirement |
|------|-------------|
| `JWT_SECRET` | Min 64 random chars, env var only, never in code or git |
| `DB_PASS` | Env var only |
| `WS_ALLOWED_ORIGIN` | Set to exact frontend domain, never `*` |
| HTTPS / WSS | Mandatory. WS over plain `ws://` is banned. Use `wss://` in production. |
| PostgreSQL | `balance_paise >= 0` DB constraint (already in schema) |
| Redis | OTP, JWT revocation, rate limiting, round state, WS rate limit buckets |
| Logs | Never log OTPs, tokens, or balances. Log `user_id`, `round_id`, action type only. |
| SQL Injection | JPA named queries / `@Query` with bound params only. Zero string-concat queries. |
| `.gitignore` | `application-local.yml`, `.env`, `*.jks` |
| WS token in query string | Log only first 8 chars of token in access logs. Never full token. |

---

## 14. Summary: What the Frontend Can Trust

| Data | Source |
|------|--------|
| Timer / seconds remaining | WS `/topic/game/tick` push |
| Phase changes | WS `/topic/game/phase` push |
| Winning rows | WS `/topic/game/result` push (only after FINISHED) |
| Bet confirmation | WS `/user/queue/bet-ack` or REST POST `/game/bet` response |
| Balance after payout | WS `/user/queue/balance` push |
| Full state on reconnect | REST `GET /api/v1/game/current` (once, on reconnect only) |

**The client is purely a renderer. It displays what the server pushes. It never computes game outcomes.**
