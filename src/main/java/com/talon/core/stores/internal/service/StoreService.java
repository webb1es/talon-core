package com.talon.core.stores.internal.service;

import com.talon.core.stores.internal.dto.CreateStoreRequest;
import com.talon.core.stores.internal.entity.Store;
import com.talon.core.stores.internal.repository.StoreRepository;
import com.talon.core.stores.internal.dto.StoreResponse;
import com.talon.core.stores.internal.dto.UpdateStoreRequest;
import com.talon.core.stores.StorePort;
import com.talon.core.stores.StoreMemberPort;
import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.shared.BadRequestException;
import com.talon.core.shared.ConflictException;
import com.talon.core.shared.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService implements StorePort {

    private static final Set<String> ADMIN_ROLES = Set.of("admin", "super_admin");
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.15");
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_TIMEZONE = "Africa/Harare";

    private final StoreRepository storeRepository;
    private final StoreMemberPort storeMemberPort;

    @Transactional(readOnly = true)
    public List<StoreResponse> list(CurrentUserProfile currentUser) {
        List<Store> stores;
        if (isAdmin(currentUser)) {
            stores = storeRepository.findAll();
        } else {
            if (currentUser.storeIds().isEmpty()) {
                return List.of();
            }
            stores = storeRepository.findAllById(currentUser.storeIds()).stream()
                .filter(Store::isActive)
                .toList();
        }
        return stores.stream()
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID id, CurrentUserProfile currentUser) {
        Store store = visibleStore(id, currentUser);
        return toResponse(store);
    }

    @Transactional
    public StoreResponse create(CreateStoreRequest request, CurrentUserProfile currentUser) {
        requireAdmin(currentUser);
        Store store = new Store();
        store.setName(request.name().trim());
        store.setAddress(blankToNull(request.address()));
        store.setCurrencyCode(normalizeCurrency(request.currencyCode()));
        store.setTaxRate(request.taxRate() == null ? DEFAULT_TAX_RATE : request.taxRate());
        store.setTimezone(blankToDefault(request.timezone(), DEFAULT_TIMEZONE));
        store.setReceiptHeader(blankToNull(request.receiptHeader()));
        store.setReceiptFooter(blankToNull(request.receiptFooter()));
        store.setActive(true);
        return toResponse(storeRepository.save(store));
    }

    @Transactional
    public StoreResponse update(UUID id, UpdateStoreRequest request, CurrentUserProfile currentUser) {
        requireAdmin(currentUser);
        Store store = storeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Store not found"));
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Store name is required");
            }
            store.setName(request.name().trim());
        }
        if (request.address() != null) {
            store.setAddress(blankToNull(request.address()));
        }
        if (request.currencyCode() != null) {
            store.setCurrencyCode(normalizeCurrency(request.currencyCode()));
        }
        if (request.taxRate() != null) {
            store.setTaxRate(request.taxRate());
        }
        if (request.timezone() != null) {
            store.setTimezone(blankToDefault(request.timezone(), DEFAULT_TIMEZONE));
        }
        if (request.receiptHeader() != null) {
            store.setReceiptHeader(blankToNull(request.receiptHeader()));
        }
        if (request.receiptFooter() != null) {
            store.setReceiptFooter(blankToNull(request.receiptFooter()));
        }
        if (request.active() != null) {
            store.setActive(request.active());
        }
        return toResponse(storeRepository.save(store));
    }

    @Transactional
    public void delete(UUID id, CurrentUserProfile currentUser) {
        requireAdmin(currentUser);
        Store store = storeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Store not found"));
        if (storeMemberPort.hasMembers(id)) {
            throw new ConflictException("Store has assigned team members; deactivate it instead");
        }
        storeRepository.delete(store);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return storeRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoreSummary> findById(UUID id) {
        return storeRepository.findById(id).map(store -> new StoreSummary(store.getId(), store.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreSummary> findAllById(Collection<UUID> ids) {
        return storeRepository.findAllById(ids).stream()
            .map(store -> new StoreSummary(store.getId(), store.getName()))
            .toList();
    }

    private Store visibleStore(UUID id, CurrentUserProfile currentUser) {
        Store store = storeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Store not found"));
        if (isAdmin(currentUser)) {
            return store;
        }
        if (!currentUser.storeIds().contains(id) || !store.isActive()) {
            throw new NotFoundException("Store not found");
        }
        return store;
    }

    private static void requireAdmin(CurrentUserProfile currentUser) {
        if (!isAdmin(currentUser)) {
            throw new AccessDeniedException("Not allowed to manage stores");
        }
    }

    private static boolean isAdmin(CurrentUserProfile currentUser) {
        return ADMIN_ROLES.contains(currentUser.role());
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currencyCode.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(
            store.getId(),
            store.getName(),
            store.getAddress(),
            store.getCurrencyCode(),
            store.getTaxRate(),
            store.getTimezone(),
            store.getReceiptHeader(),
            store.getReceiptFooter(),
            store.isActive(),
            store.getCreatedAt(),
            store.getUpdatedAt()
        );
    }
}
