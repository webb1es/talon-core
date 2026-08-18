package com.talon.core.stores.internal.web;

import com.talon.core.stores.internal.dto.CreateStoreRequest;
import com.talon.core.stores.internal.dto.StoreResponse;
import com.talon.core.stores.internal.service.StoreService;
import com.talon.core.stores.internal.dto.UpdateStoreRequest;
import com.talon.core.auth.CurrentUser;
import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.shared.ItemsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ItemsResponse<StoreResponse> list(@CurrentUser CurrentUserProfile currentUser) {
        return ItemsResponse.of(storeService.list(currentUser));
    }

    @GetMapping("/{id}")
    public StoreResponse get(@PathVariable UUID id, @CurrentUser CurrentUserProfile currentUser) {
        return storeService.get(id, currentUser);
    }

    @PostMapping
    @PreAuthorize("hasRole('manage_stores')")
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody CreateStoreRequest request,
                                                @CurrentUser CurrentUserProfile currentUser) {
        StoreResponse created = storeService.create(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('manage_stores')")
    public StoreResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStoreRequest request,
                                @CurrentUser CurrentUserProfile currentUser) {
        return storeService.update(id, request, currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('manage_stores')")
    public void delete(@PathVariable UUID id, @CurrentUser CurrentUserProfile currentUser) {
        storeService.delete(id, currentUser);
    }
}
