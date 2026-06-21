package com.totemena.elite.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Transactional
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal User authUser) {
        User user = userRepository.findById(authUser.getId()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "phone", user.getPhone(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "kycStatus", user.getKycStatus(),
                "createdAt", user.getCreatedAt()
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@AuthenticationPrincipal User user, @RequestBody Map<String, String> request) {
        String newName = request.get("displayName");
        if (newName != null && !newName.isBlank()) {
            user.setDisplayName(newName);
            userRepository.save(user);
        }
        return ResponseEntity.ok(Map.of("message", "Profile updated"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMe(@AuthenticationPrincipal User user) {
        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
    }
}
