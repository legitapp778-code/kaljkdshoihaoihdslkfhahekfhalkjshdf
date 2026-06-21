package com.totemena.elite.support;

import com.totemena.elite.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketRepository ticketRepository;

    @GetMapping("/tickets")
    public ResponseEntity<List<SupportTicket>> getMyTickets(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ticketRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> createTicket(@AuthenticationPrincipal User user,
            @RequestBody Map<String, String> request) {
        SupportTicket ticket = SupportTicket.builder()
                .userId(user.getId())
                .subject(request.getOrDefault("subject", "General Inquiry"))
                .status("Open")
                .build();
        return ResponseEntity.ok(ticketRepository.save(ticket));
    }
}
