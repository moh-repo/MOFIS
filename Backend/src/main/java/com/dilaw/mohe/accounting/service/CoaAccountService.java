package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.model.CoaAccount;
import com.dilaw.mohe.accounting.model.CoaLevel;
import com.dilaw.mohe.accounting.repository.CoaAccountRepository;
import com.dilaw.mohe.accounting.repository.CoaLevelRepository;
import com.dilaw.mohe.repository.spec.CountryColumnSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maintains the chart of accounts (الدليل المحاسبي).
 *
 * <p>The hierarchy is fixed by the Executive Regulation of the Financial Law —
 * أبواب، بنود، أنواع، مجموعات، حسابات — but the Regulation also defines the level
 * names, the code widths and every code value, so none of those are decided
 * here. This class assembles accounts from what the caller supplies and computes
 * {@code fullCode}; it does not know what a valid chapter number looks like.
 *
 * <p>Validation is duplicated between here and the database on purpose. The
 * checks in this class exist to return a usable message; the ones in
 * {@code TR_COA_ACCOUNT_VALIDATE} exist because a chart is edited by more than
 * one route — this service, the manual import, and whatever SSMS session an
 * administrator opens. The database is the one that has to be right.
 */
@Service
public class CoaAccountService {

    @Autowired private CoaAccountRepository accountRepository;
    @Autowired private CoaLevelRepository levelRepository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private AttacheCountryValidator attacheCountries;

    private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "fullCode");

    /*
     * ── Country scope ─────────────────────────────────────────────────────────
     *
     * The chart is scoped with countryScopeOrShared, not countryScope: on this
     * table a null country is the الدليل المحاسبي الموحد — the unified chart every
     * attaché books against — and hiding it would leave a country officer with an
     * empty chart and nothing to classify against. A non-null country is one
     * attaché's own addition to that chart.
     *
     * That is the opposite reading from WAFAR_SPENDING, where a null means the
     * attaché was never recorded. The difference is safe only because the write
     * side is closed: createShared below refuses to let anyone but a ministry user
     * produce a null-country account, so an officer cannot manufacture a row that
     * every other attaché will then see.
     */
    private Specification<CoaAccount> scoped() {
        return Specification.where(
                CountryColumnSpec.countryScopeOrShared(countryScopeService.getScope(), "studyCountryInId"));
    }

    private static Specification<CoaAccount> inYear(Integer fiscalYearId) {
        return (root, query, cb) -> cb.equal(root.get("fiscalYearId"), fiscalYearId);
    }

    public List<CoaAccount> forYear(Integer fiscalYearId) {
        return accountRepository.findAll(scoped().and(inYear(fiscalYearId)), BY_CODE);
    }

    public List<CoaAccount> forSegment(Integer segmentId, Integer fiscalYearId) {
        return accountRepository.findAll(scoped().and(inYear(fiscalYearId))
                .and((root, query, cb) -> cb.equal(root.get("segmentId"), segmentId)), BY_CODE);
    }

    public List<CoaAccount> postableAccounts(Integer fiscalYearId) {
        return accountRepository.findAll(scoped().and(inYear(fiscalYearId))
                .and((root, query, cb) -> cb.and(
                        cb.isTrue(root.get("postable")),
                        cb.isTrue(root.get("active")))), BY_CODE);
    }

    /**
     * One account by id — the single gate for {@code create}'s parent lookup,
     * {@code update} and {@code deactivate} as well as the detail endpoint.
     *
     * <p>Scoping it here means an officer cannot hang a child off an account they
     * cannot see, or rename one, without a separate check in each method.
     *
     * <p>Out of scope reads as not found rather than forbidden: a 403 would
     * confirm the account exists, and account ids are sequential.
     */
    public CoaAccount require(Integer id) {
        return accountRepository.findOne(
                        scoped().and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    /**
     * Adds an account beneath {@code parentAccountId}, or at the root when that
     * is null.
     *
     * <p>{@code fullCode} is computed rather than accepted from the caller: it is
     * derived data, and letting a client supply it is how a chart ends up with a
     * code that does not match its own parent.
     */
    @Transactional
    public CoaAccount create(CoaAccount draft) {
        requireText(draft.getCode(), "code");
        requireText(draft.getNameAr(), "Arabic name");

        CoaAccount parent = Optional.ofNullable(draft.getParentAccountId())
                .map(this::require)
                .orElse(null);

        if (parent == null) {
            draft.setLevelNo((short) 1);
        } else {
            if (Boolean.TRUE.equals(parent.getPostable())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Account " + parent.getFullCode() + " is postable, so it cannot be given children. "
                                + "Clear its postable flag first.");
            }
            draft.setLevelNo((short) (parent.getLevelNo() + 1));
            draft.setSegmentId(parent.getSegmentId());
            draft.setFiscalYearId(parent.getFiscalYearId());
        }

        draft.setFullCode((parent == null ? "" : parent.getFullCode()) + draft.getCode());
        draft.setNormalBalance(normalBalanceFor(draft.getAccountNature()));
        draft.setStudyCountryInId(resolveCountryForWrite(draft.getStudyCountryInId()));
        attacheCountries.requireActive(draft.getStudyCountryInId());
        validateCodeWidth(draft);

        accountRepository.findBySegmentIdAndFiscalYearIdAndFullCode(
                        draft.getSegmentId(), draft.getFiscalYearId(), draft.getFullCode())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Account code " + draft.getFullCode() + " already exists in this segment and year.");
                });

        return accountRepository.save(draft);
    }

    /**
     * Renames an account, and changes its flags.
     *
     * <p>{@code code} is only changeable while the account has no children:
     * changing it otherwise would leave every descendant's {@code fullCode}
     * pointing at a path that no longer exists, without touching those rows.
     */
    @Transactional
    public CoaAccount update(Integer id, CoaAccount changes) {
        CoaAccount existing = require(id);

        boolean codeChanged = changes.getCode() != null && !changes.getCode().equals(existing.getCode());
        boolean hasChildren = accountRepository.existsByParentAccountId(id);

        if (codeChanged && hasChildren) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account " + existing.getFullCode() + " has children, so its code cannot change. "
                            + "It would orphan every descendant's full code.");
        }
        if (Boolean.TRUE.equals(changes.getPostable()) && hasChildren) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a leaf may be postable, and " + existing.getFullCode() + " has children. "
                            + "A parent that receives postings makes its own subtotal wrong.");
        }

        if (codeChanged) {
            CoaAccount parent = Optional.ofNullable(existing.getParentAccountId())
                    .map(this::require).orElse(null);
            existing.setCode(changes.getCode());
            existing.setFullCode((parent == null ? "" : parent.getFullCode()) + changes.getCode());
            validateCodeWidth(existing);
        }
        if (changes.getNameAr() != null)   existing.setNameAr(changes.getNameAr());
        if (changes.getNameEn() != null)   existing.setNameEn(changes.getNameEn());
        if (changes.getPostable() != null) existing.setPostable(changes.getPostable());
        if (changes.getBudgetary() != null) existing.setBudgetary(changes.getBudgetary());
        if (changes.getAccountNature() != null) {
            existing.setAccountNature(changes.getAccountNature());
            existing.setNormalBalance(normalBalanceFor(changes.getAccountNature()));
        }
        // studyCountryInId is deliberately not editable. Moving an account between
        // attachés — or promoting one into the shared chart — changes who may post
        // to it and who can see everything already posted, which is not a rename.
        existing.setUpdatedAt(Instant.now());
        return accountRepository.save(existing);
    }

    /**
     * Deactivates an account. There is no delete.
     *
     * <p>An account that has been posted to has to survive for the audit trail
     * even after it stops being used, and at this layer we cannot tell whether it
     * has been. Deactivating is always the safe answer.
     */
    @Transactional
    public CoaAccount deactivate(Integer id) {
        CoaAccount account = require(id);
        account.setActive(false);
        account.setUpdatedAt(Instant.now());
        return accountRepository.save(account);
    }

    /**
     * Which attaché a new account belongs to, and whether it may be shared at all.
     *
     * <p>A null means the unified chart, visible to every attaché — so only a
     * ministry user may create one. An officer's account is stamped with their own
     * attaché whether or not they asked for it, and an explicit country is checked
     * against their scope.
     *
     * <p>This is what makes {@code countryScopeOrShared} safe to read with. Without
     * it, anyone could add an account with no country and have it appear in every
     * other attaché's chart.
     */
    private Integer resolveCountryForWrite(Integer requested) {
        CountryScope scope = countryScopeService.getScope();

        if (scope.isMinistryUser()) {
            // Ministry may create either: null for the unified chart, or a specific
            // attaché's account on its behalf.
            return requested;
        }
        if (requested == null) {
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.size() == 1) {
                return allowed.get(0);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attaché this account belongs to is required (studyCountryInId). "
                    + "Only the ministry may add to the unified chart.");
        }
        countryScopeService.validateCountryAccess(requested);
        return requested;
    }

    /**
     * Debit-normal: assets and expenses. Credit-normal: liabilities, equity,
     * revenue. Getting this wrong inverts the account in every report, so it is
     * derived rather than accepted from the caller.
     */
    public static String normalBalanceFor(String accountNature) {
        if (accountNature == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account must have a nature.");
        }
        return switch (accountNature) {
            case "ASSET", "EXPENSE" -> "D";
            case "LIABILITY", "EQUITY", "REVENUE" -> "C";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown account nature: " + accountNature);
        };
    }

    /**
     * Checks the code against the width its level declares — when one has been
     * declared.
     *
     * <p>{@code codeLength} is null until the Regulation's widths are recorded,
     * and null means "do not validate". That is deliberate: a guessed width would
     * reject the real manual on import.
     */
    private void validateCodeWidth(CoaAccount account) {
        levelRepository.findAll().stream()
                .filter(l -> l.getSegmentId().equals(account.getSegmentId()))
                .filter(l -> l.getLevelNo().equals(account.getLevelNo()))
                .findFirst()
                .map(CoaLevel::getCodeLength)
                .ifPresent(width -> {
                    if (width != null && account.getCode().length() != width) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Code '" + account.getCode() + "' is " + account.getCode().length()
                                        + " characters; level " + account.getLevelNo() + " requires " + width + ".");
                    }
                });
    }

    /** Counts by level, for the maintenance screen's summary. */
    public Map<Short, Long> countByLevel(Integer fiscalYearId) {
        return forYear(fiscalYearId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CoaAccount::getLevelNo, java.util.stream.Collectors.counting()));
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account needs a " + what + ".");
        }
    }
}
