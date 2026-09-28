package com.dilaw.mohe.systemsettings.service;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.systemsettings.model.SysParameter;
import com.dilaw.mohe.systemsettings.repository.SysParameterRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Typed access to {@code MOHECORE.SYS_PARAMETER}.
 *
 * <p>A parameter the database does not hold is a deployment error, not a
 * case for a Java default: {@link #intValue} and friends throw, naming the
 * key, so a missing row is found on the first request rather than papered
 * over with the constant this table replaced. The whole table is one cached
 * read ({@code sys-parameters}, sixty seconds; evicted on write).
 */
@Service
public class SysParameterService {

    /** The keys the application reads; seeded by V072. */
    public static final String DASHBOARD_MONTHS_WINDOW = "DASHBOARD.MONTHS_WINDOW";
    public static final String DASHBOARD_YEARS_WINDOW = "DASHBOARD.YEARS_WINDOW";
    public static final String DASHBOARD_TOP_COUNTRIES = "DASHBOARD.TOP_COUNTRIES";
    public static final String DASHBOARD_RECENT_STUDENTS = "DASHBOARD.RECENT_STUDENTS";
    public static final String ALERTS_DOC_EXPIRY_WARN_DAYS = "ALERTS.DOC_EXPIRY_WARN_DAYS";
    public static final String ALERTS_DOC_EXPIRY_CRITICAL_DAYS = "ALERTS.DOC_EXPIRY_CRITICAL_DAYS";
    public static final String ALERTS_DOC_EXPIRY_HIGH_DAYS = "ALERTS.DOC_EXPIRY_HIGH_DAYS";
    public static final String AUDIT_DEFAULT_WINDOW_DAYS = "AUDIT.DEFAULT_WINDOW_DAYS";
    public static final String STUDENTS_MATCH_SIMILARITY_FLOOR = "STUDENTS.MATCH_SIMILARITY_FLOOR";
    /** Seeded by V080: how long a failed sign-in counts towards the lockout ladder. */
    public static final String AUTH_ATTEMPT_WINDOW_MINUTES = "AUTH.ATTEMPT_WINDOW_MINUTES";

    public static final List<String> KNOWN_KEYS = List.of(
            DASHBOARD_MONTHS_WINDOW, DASHBOARD_YEARS_WINDOW, DASHBOARD_TOP_COUNTRIES, DASHBOARD_RECENT_STUDENTS,
            ALERTS_DOC_EXPIRY_WARN_DAYS, ALERTS_DOC_EXPIRY_CRITICAL_DAYS, ALERTS_DOC_EXPIRY_HIGH_DAYS,
            AUDIT_DEFAULT_WINDOW_DAYS, STUDENTS_MATCH_SIMILARITY_FLOOR, AUTH_ATTEMPT_WINDOW_MINUTES);

    private final SysParameterRepository repo;

    public SysParameterService(SysParameterRepository repo) {
        this.repo = repo;
    }

    @Cacheable(cacheNames = CacheConfig.SYS_PARAMETERS, key = "'all'")
    public Map<String, SysParameter> all() {
        return repo.findAll().stream().collect(Collectors.toMap(SysParameter::getKey, Function.identity(),
                (a, b) -> a, java.util.LinkedHashMap::new));
    }

    public List<SysParameter> list() {
        return List.copyOf(all().values());
    }

    public Optional<SysParameter> find(String key) {
        return Optional.ofNullable(all().get(key));
    }

    public String text(String key) {
        return find(key).map(SysParameter::getValue)
                .orElseThrow(() -> new IllegalStateException("SYS_PARAMETER has no row " + key + " — apply V072"));
    }

    public int intValue(String key) {
        String raw = text(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("SYS_PARAMETER " + key + " is not an integer: " + raw);
        }
    }

    public BigDecimal decimalValue(String key) {
        String raw = text(key);
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("SYS_PARAMETER " + key + " is not a number: " + raw);
        }
    }

    public boolean boolValue(String key) {
        String raw = text(key).trim();
        return raw.equals("1") || raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("yes");
    }

    /** Updates the value of an existing parameter; the key, type and names are the migration's. */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SYS_PARAMETERS, allEntries = true)
    public SysParameter update(String key, String value, String updatedBy) {
        SysParameter p = repo.findById(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No parameter " + key));
        String v = value == null ? "" : value.trim();
        switch (p.getValueType()) {
            case "INT" -> {
                try {
                    Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a whole number");
                }
            }
            case "DECIMAL" -> {
                try {
                    new BigDecimal(v);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a number");
                }
            }
            case "BOOL" -> {
                if (!(v.equals("0") || v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be true or false");
                }
            }
            default -> {
                if (v.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " cannot be empty");
                }
            }
        }
        p.setValue(v);
        p.setUpdatedBy(updatedBy);
        p.setUpdatedAt(LocalDateTime.now());
        return repo.save(p);
    }
}
