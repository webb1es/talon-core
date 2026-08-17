package com.talon.core.stores.internal.init;

import com.talon.core.stores.internal.entity.Store;
import com.talon.core.stores.internal.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ensures a baseline set of stores exists on every boot. Each name is
 * created only if no store with that name is already present, so re-runs
 * and renamed/deleted seed stores don't get recreated or duplicated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoreSeeder implements ApplicationRunner {

    private static final List<String> SEED_STORE_NAMES = List.of("Main Store", "Branch 2", "Branch 3");

    private final StoreRepository storeRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (String name : SEED_STORE_NAMES) {
            if (storeRepository.findByName(name).isPresent()) {
                continue;
            }
            Store store = new Store();
            store.setName(name);
            store.setActive(true);
            storeRepository.save(store);
            log.info("Seeded store '{}'", name);
        }
    }
}
