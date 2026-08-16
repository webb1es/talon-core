package com.talon.core.users.internal.web;

import com.talon.core.users.internal.dto.CreateUserRequest;
import com.talon.core.users.internal.dto.UpdateUserRequest;
import com.talon.core.users.internal.dto.UserResponse;
import com.talon.core.users.internal.service.UserService;
import com.talon.core.auth.CurrentUser;
import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.shared.ItemsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ItemsResponse<UserResponse> list(@CurrentUser CurrentUserProfile currentUser) {
        return ItemsResponse.of(userService.list(currentUser));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id, @CurrentUser CurrentUserProfile currentUser) {
        return userService.get(currentUser, id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@CurrentUser CurrentUserProfile currentUser,
                                               @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(currentUser, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @CurrentUser CurrentUserProfile currentUser,
                               @RequestBody UpdateUserRequest request) {
        return userService.update(currentUser, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @CurrentUser CurrentUserProfile currentUser) {
        userService.delete(currentUser, id);
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @CurrentUser CurrentUserProfile currentUser) {
        userService.resetPassword(currentUser, id);
    }
}
