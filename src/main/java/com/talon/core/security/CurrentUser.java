package com.talon.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller-method parameter annotation injecting the resolved
 * CurrentUserProfile for the current session.
 *
 * Usage: {@code @GetMapping("/x") public X handle(@CurrentUser CurrentUserProfile currentUser) }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
