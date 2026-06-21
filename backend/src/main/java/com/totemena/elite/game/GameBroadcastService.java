package com.totemena.elite.game;

import com.totemena.elite.game.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GameBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public void addSession(String sessionId) {
        if (sessionId != null) activeSessions.add(sessionId);
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) activeSessions.remove(sessionId);
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        addSession(accessor.getSessionId());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        removeSession(accessor.getSessionId());
    }

    public long getPlayersOnline() {
        return activeSessions.size();
    }

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

    public void sendBetAck(UUID userId, BetAckEvent event) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/bet-ack", event);
    }

    public void sendBalanceUpdate(UUID userId, long newBalance, long amount, String roundId, String reason, java.math.BigDecimal multiplier) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/balance",
                new BalanceUpdateEvent(newBalance, reason, amount, roundId, multiplier));
    }

    public void sendError(UUID userId, String code, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/errors",
                new ErrorEvent(code, message));
    }
}
