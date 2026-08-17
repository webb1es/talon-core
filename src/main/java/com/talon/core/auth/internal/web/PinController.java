package com.talon.core.auth.internal.web;

import com.talon.core.auth.CurrentUser;
import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.auth.internal.dto.PinRequest;
import com.talon.core.auth.internal.service.PinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PinController {

    private final PinService pinService;

    public record PinVerifyRequest(String pin) {}

    public record PinVerifyResponse(boolean valid) {}

    @PutMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@CurrentUser CurrentUserProfile currentUser, @Valid @RequestBody PinRequest request) {
        pinService.set(currentUser.id(), request.pin());
    }

    @DeleteMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@CurrentUser CurrentUserProfile currentUser) {
        pinService.clear(currentUser.id());
    }

    @PostMapping("/pin/verify")
    public PinVerifyResponse verify(@CurrentUser CurrentUserProfile currentUser, @RequestBody PinVerifyRequest body) {
        if (body.pin() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN is required");
        }
        return new PinVerifyResponse(pinService.verify(currentUser.id(), body.pin()));
    }
}
