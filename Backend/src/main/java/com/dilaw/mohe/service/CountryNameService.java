package com.dilaw.mohe.service;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.dto.LocalizedOptionDto;
import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.repository.StudyCountryInRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The study-country id → name list, cached.
 *
 * <p>Before this service, sixteen services each ran their own
 * {@code studyCountryInRepository.findAll()} per request to build the same
 * id → name map — including {@code CountryScopeService} on the authorization
 * path. The list is a handful of rows that changes a few times a year, so it is
 * loaded once and cached ({@link CacheConfig#COUNTRY_NAMES}, 10&nbsp;minutes);
 * country CRUD in {@code StudyCountryInService} evicts, so the TTL only bounds
 * staleness for a direct SSMS edit.
 *
 * <p>The loader is a separate bean for the same reason as
 * {@code CodeListLoader}: {@code @Cacheable} on a method the service calls
 * internally would be a self-invocation and bypass the cache proxy.
 */
@Service
public class CountryNameService {

    /** Immutable snapshot row — deliberately not the JPA entity, so cached copies are safe to share. */
    public record CountryRef(Integer id, String nameAr, String nameEn, boolean active) {

        /** The name in the requested language, falling back to the other, then the id. */
        public String name(boolean arabic) {
            String primary = arabic ? nameAr : nameEn;
            String fallback = arabic ? nameEn : nameAr;
            if (primary != null && !primary.isBlank()) return primary;
            if (fallback != null && !fallback.isBlank()) return fallback;
            return String.valueOf(id);
        }
    }

    /** Cache boundary. Separate bean so the service's own calls go through the proxy. */
    @Component
    public static class Loader {
        private final StudyCountryInRepository repository;

        public Loader(StudyCountryInRepository repository) {
            this.repository = repository;
        }

        @Cacheable(CacheConfig.COUNTRY_NAMES)
        public List<CountryRef> all() {
            return repository.findAll().stream()
                    .map(c -> new CountryRef(c.getId(), c.getStudyCountryInName(),
                            c.getStudyCountryInNameEn(), !Boolean.FALSE.equals(c.getIsActive())))
                    .toList();
        }
    }

    private final Loader loader;

    public CountryNameService(Loader loader) {
        this.loader = loader;
    }

    /** Every country, active or not, in table order. */
    public List<CountryRef> all() {
        return loader.all();
    }

    public List<Integer> allIds() {
        return all().stream().map(CountryRef::id).toList();
    }

    /** id → Arabic name with English fallback — the shape most call sites built by hand. */
    public Map<Integer, String> nameMap() {
        return nameMap(true);
    }

    /** id → name in the requested language, with fallback to the other. */
    public Map<Integer, String> nameMap(boolean arabic) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (CountryRef c : all()) {
            map.put(c.id(), c.name(arabic));
        }
        return map;
    }

    public String name(Integer id, boolean arabic) {
        if (id == null) return null;
        return all().stream()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .map(c -> c.name(arabic))
                .orElse(String.valueOf(id));
    }

    /**
     * Filter options for a view that carries the country only as its Arabic name (the
     * Financial Data Form views). The value stays that name, because the rows and the
     * PDF are filtered by it; the label is the name in the reader's language, and both
     * names travel so the page can re-label on a language switch. Live QA R5-29: the
     * English page listed the Arabic names. A name the country table does not know is
     * shown as it is. Sorted by label.
     */
    public List<LocalizedOptionDto> optionsByArabicName(Collection<String> arabicNames, boolean arabic) {
        Map<String, CountryRef> byArabicName = new HashMap<>();
        for (CountryRef c : all()) {
            if (c.nameAr() != null) {
                byArabicName.putIfAbsent(c.nameAr().trim(), c);
            }
        }
        return arabicNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .map(n -> {
                    CountryRef ref = byArabicName.get(n.trim());
                    String english = ref != null ? ref.name(false) : n;
                    return new LocalizedOptionDto(n, arabic ? n : english, english, n);
                })
                .sorted(Comparator.comparing(LocalizedOptionDto::getLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
