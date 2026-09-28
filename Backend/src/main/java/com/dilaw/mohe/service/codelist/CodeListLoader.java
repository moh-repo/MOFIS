package com.dilaw.mohe.service.codelist;

import com.dilaw.mohe.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The cached SELECT behind {@link CodeListService}. A separate bean on purpose:
 * Spring's {@code @Cacheable} works through the proxy, so a service calling its
 * own cached method bypasses the cache — every {@code find} would have hit the
 * database. All reads and the eviction live here.
 */
@Service
public class CodeListLoader {

    private final JdbcTemplate jdbc;

    public CodeListLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All rows of a list, active and soft-deleted, in table order. */
    @Cacheable(cacheNames = CacheConfig.CODE_LISTS, key = "#name")
    public List<CodeRow> rows(String name) {
        if (!CodeListRegistry.exists(name)) {
            throw new IllegalArgumentException("Unknown code list: " + name);
        }
        return jdbc.query(CodeListRegistry.selectFor(name), (rs, i) -> new CodeRow(
                rs.getObject(1) == null ? null : rs.getInt(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getObject(5) == null ? 0 : rs.getInt(5),
                rs.getString(6),
                rs.getString(7),
                rs.getBoolean(8),
                rs.getBoolean(9),
                rs.getString(10)));
    }

    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void evictAll() {
        // eviction is the annotation's work
    }
}
