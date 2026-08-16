package com.talon.core.users.internal.web;

import com.talon.core.users.internal.dto.DefaultStoreRequest;
import com.talon.core.users.internal.dto.MeResponse;
import com.talon.core.users.internal.service.MeService;
import com.talon.core.users.internal.dto.PatchMeRequest;
import com.talon.core.auth.CurrentUser;
import com.talon.core.auth.CurrentUserProfile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping({"/api/me", "/api/profile/session-info"})
    public MeResponse me(@CurrentUser CurrentUserProfile currentUser) {
        return meService.me(currentUser);
    }

    @PatchMapping("/api/me")
    public MeResponse patch(@CurrentUser CurrentUserProfile currentUser, @RequestBody PatchMeRequest request) {
        return meService.patch(currentUser, request);
    }

    @PutMapping({"/api/me/default-store", "/api/profile/store"})
    public void switchDefaultStore(@CurrentUser CurrentUserProfile currentUser,
                                   @Valid @RequestBody DefaultStoreRequest request) {
        meService.switchDefaultStore(currentUser, request.storeId());
    }
}
