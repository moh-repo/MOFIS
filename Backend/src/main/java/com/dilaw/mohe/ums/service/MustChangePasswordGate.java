package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a signed-in account must replace its password before doing anything else
 * (V121, decision DEC-04: a password an administrator set is temporary).
 *
 * <p>Asked on every API request by {@code MustChangePasswordInterceptor}, so the
 * answer is cached per account for a minute. {@code UmsUserService} evicts the entry
 * when it sets or clears the flag, so on one instance the change takes effect at
 * once; on several, within the minute.
 */
@Component
public class MustChangePasswordGate {

    static final long TTL_MS = 60_000L;

    private record Entry(boolean mustChange, long readAt) {
    }

    private final UmsUserRepository users;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public MustChangePasswordGate(UmsUserRepository users) {
        this.users = users;
    }

    public boolean mustChange(Long userId) {
        if (userId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Entry e = cache.get(userId);
        if (e != null && now - e.readAt() < TTL_MS) {
            return e.mustChange();
        }
        boolean must = users.findMustChangePassword(userId).orElse(false);
        cache.put(userId, new Entry(must, now));
        return must;
    }

    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }
}
