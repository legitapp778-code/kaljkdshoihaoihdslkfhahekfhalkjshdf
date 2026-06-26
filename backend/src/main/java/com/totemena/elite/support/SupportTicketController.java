package com.totemena.elite.support;

import com.totemena.elite.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.totemena.elite.support.dto.CreateTicketRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Validated
public class SupportTicketController {

    private final SupportTicketRepository ticketRepository;

    @GetMapping("/tickets")
    public ResponseEntity<List<SupportTicket>> getMyTickets(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ticketRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> createTicket(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateTicketRequest request) {
        SupportTicket ticket = SupportTicket.builder()
                .userId(user.getId())
                .subject(request.getSubject().trim())
                .status("Open")
                .build();
        return ResponseEntity.ok(ticketRepository.save(ticket));
    }
}
