package com.dilaw.mohe.ums.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Whitelist-based Pageable with sort for UMS list APIs (avoids invalid JPA sort paths). */
public final class UmsPageableUtil {

    private static final int MAX_PAGE_SIZE = 200;

    private UmsPageableUtil() {
    }

    public static Pageable of(int page, int size, String sortBy, String sortDir,
            Set<String> allowedProperties, String defaultProperty) {
        int p = Math.max(0, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String prop = (sortBy != null && allowedProperties.contains(sortBy)) ? sortBy : defaultProperty;
        Sort sort = (sortDir != null && sortDir.equalsIgnoreCase("desc"))
                ? Sort.by(prop).descending()
                : Sort.by(prop).ascending();
        return PageRequest.of(p, s, sort);
    }
}
