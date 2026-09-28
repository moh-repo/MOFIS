package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.CoaAccount;
import com.dilaw.mohe.accounting.repository.CoaAccountRepository;
import com.dilaw.mohe.accounting.repository.CoaLevelRepository;
import com.dilaw.mohe.accounting.repository.CoaSegmentRepository;
import com.dilaw.mohe.accounting.service.CoaAccountService;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chart of accounts (الدليل المحاسبي): its structure, and its emptiness.
 *
 * <p>Rolled back — it builds real accounts against a real fiscal year.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class ChartOfAccountsTest {

    @Autowired private CoaAccountService accounts;
    @Autowired private CoaAccountRepository accountRepository;
    @Autowired private CoaSegmentRepository segmentRepository;
    @Autowired private CoaLevelRepository levelRepository;
    @Autowired private JdbcTemplate jdbc;

    private Integer economicSegment;
    /** Where the fixtures are built: empty, and no code widths yet (see V064 for why not ECONOMIC). */
    private Integer projectSegment;
    private Integer fiscalYear;

    /**
     * Signed in as a ministry user. {@code CoaAccountService} takes the country
     * scope from the security context, and an absent context is {@code noAccess},
     * so an unauthenticated create is now refused before it can be measured. What
     * these tests are about is the hierarchy rules — code widths, leaf postability,
     * full-code paths. Who may see and create an account is
     * {@code AccountingPagesScopeTest}.
     *
     * <p>The ministry is also the only caller that may create a null-country
     * account, which is what the fixtures below build: the unified chart.
     */
    @AfterEach
    void signOut() {
        AsUser.signOut();
    }

    @BeforeEach
    void fixture() {
        AsUser.signIn(AsUser.SUPERADMIN);
        economicSegment = jdbc.queryForObject(
                "SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'ECONOMIC'", Integer.class);
        projectSegment = jdbc.queryForObject(
                "SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'PROJECT'", Integer.class);
        fiscalYear = jdbc.queryForObject(
                "SELECT FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR WHERE YEAR_NO = 2026", Integer.class);
    }

    /**
     * <b>No invented codes.</b>
     *
     * <p>The Executive Regulation of the Financial Law defines the meanings,
     * natures and names of the budget classification. The hierarchy is law and
     * is built; the code values are a controlled document. V041 seeded none.
     * V064 loads the economic segment from the ministry's own revenue statement
     * form — the manual itself — and nothing else: every other segment must
     * still be empty until the Regulation's lists for it arrive.
     *
     * <p>This test fails if anyone later commits codes into another segment, or
     * commits economic codes that did not come through the V064 generator
     * (they would not carry its exact shape — see ChartOfAccountsRevenueSeedTest).
     */
    @Test
    @DisplayName("no invented codes: only the economic segment is filled, and only by V064")
    void onlyTheManualIsSeeded() {
        Integer elsewhere = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT
                WHERE SEGMENT_ID <> ? AND CREATED_AT < DATEADD(MINUTE, -5, SYSUTCDATETIME())
                """, Integer.class, economicSegment);

        assertThat(elsewhere)
                .as("""
                    Only the economic segment has a manual to import from. If this fails, \
                    someone has committed codes into a segment the Regulation has not \
                    supplied — remove them and import the real list instead.""")
                .isZero();
    }

    @Test
    @DisplayName("the scaffolding is there: six segments, and the five economic levels in order")
    void scaffoldingIsSeeded() {
        assertThat(segmentRepository.findAll())
                .as("segments the account code is composed of")
                .hasSize(6)
                .extracting("segmentCode")
                .contains("ECONOMIC", "ADMIN", "FUNDING_SOURCE");

        assertThat(levelRepository.findAll().stream()
                .filter(l -> l.getSegmentId().equals(economicSegment))
                .sorted(java.util.Comparator.comparing(l -> l.getLevelNo()))
                .map(l -> l.getNameEn())
                .toList())
                .as("the ministry's form names them باب / فصل / بند / نوع; level 5 stays the Law's حساب (V064)")
                .containsExactly("Chapter", "Section", "Item", "Type", "Account");

        assertThat(levelRepository.findAll())
                .as("""
                    A code width is as much the Regulation's to state as a code is. V064 read \
                    the economic widths off the manual itself (1 / 1 / 1 / 2 for levels 1–4); \
                    every other width, including economic level 5, must still ship unset.""")
                .allSatisfy(l -> {
                    if (l.getSegmentId().equals(economicSegment) && l.getLevelNo() <= 4) {
                        assertThat(l.getCodeLength()).isEqualTo((short) (l.getLevelNo() == 4 ? 2 : 1));
                    } else {
                        assertThat(l.getCodeLength()).isNull();
                    }
                });
    }

    @Test
    @DisplayName("full code is the parent path plus the account's own code")
    void fullCodeIsTheParentPath() {
        CoaAccount chapter = accounts.create(account(null, "4", "الباب الرابع", "EXPENSE"));
        assertThat(chapter.getFullCode()).isEqualTo("4");
        assertThat(chapter.getLevelNo()).isEqualTo((short) 1);

        CoaAccount item = accounts.create(account(chapter.getId(), "01", "بند", "EXPENSE"));
        assertThat(item.getFullCode()).isEqualTo("401");
        assertThat(item.getLevelNo())
                .as("a child sits exactly one level below its parent")
                .isEqualTo((short) 2);
    }

    @Test
    @DisplayName("only a leaf may be posted to")
    void onlyLeavesArePostable() {
        CoaAccount parent = accounts.create(account(null, "5", "أب", "EXPENSE"));
        accounts.create(account(parent.getId(), "01", "ابن", "EXPENSE"));

        CoaAccount makePostable = new CoaAccount();
        makePostable.setPostable(true);

        assertThatThrownBy(() -> accounts.update(parent.getId(), makePostable))
                .as("a parent that receives postings makes its own subtotal disagree with its children")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("leaf");
    }

    @Test
    @DisplayName("a postable account cannot be given children")
    void postableAccountsTakeNoChildren() {
        CoaAccount leaf = accounts.create(account(null, "6", "ورقة", "EXPENSE"));
        CoaAccount postable = new CoaAccount();
        postable.setPostable(true);
        accounts.update(leaf.getId(), postable);

        assertThatThrownBy(() -> accounts.create(account(leaf.getId(), "01", "ابن", "EXPENSE")))
                .as("the same rule, approached from the other side")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("postable");
    }

    @Test
    @DisplayName("an account with children cannot be renumbered")
    void codeIsFrozenOnceItHasChildren() {
        CoaAccount parent = accounts.create(account(null, "7", "أب", "EXPENSE"));
        accounts.create(account(parent.getId(), "01", "ابن", "EXPENSE"));

        CoaAccount renumber = new CoaAccount();
        renumber.setCode("9");

        assertThatThrownBy(() -> accounts.update(parent.getId(), renumber))
                .as("changing it would leave every descendant's full code pointing at a path that no longer exists")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("children");
    }

    @Test
    @DisplayName("normal balance is derived from account nature, never accepted from the caller")
    void normalBalanceFollowsNature() {
        assertThat(CoaAccountService.normalBalanceFor("ASSET")).isEqualTo("D");
        assertThat(CoaAccountService.normalBalanceFor("EXPENSE")).isEqualTo("D");
        assertThat(CoaAccountService.normalBalanceFor("LIABILITY")).isEqualTo("C");
        assertThat(CoaAccountService.normalBalanceFor("EQUITY")).isEqualTo("C");
        assertThat(CoaAccountService.normalBalanceFor("REVENUE")).isEqualTo("C");

        // Supplied as credit-normal; stored as debit-normal, because that is what
        // an expense is. Getting this wrong inverts the account in every report.
        CoaAccount draft = account(null, "8", "مصروف", "EXPENSE");
        draft.setNormalBalance("C");
        assertThat(accounts.create(draft).getNormalBalance()).isEqualTo("D");
    }

    @Test
    @DisplayName("a duplicate code in the same segment and year is refused")
    void codesAreUniquePerSegmentAndYear() {
        accounts.create(account(null, "3", "الباب الثالث", "EXPENSE"));

        assertThatThrownBy(() -> accounts.create(account(null, "3", "مكرر", "EXPENSE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("accounts are deactivated, never deleted")
    void accountsAreDeactivatedNotDeleted() {
        CoaAccount account = accounts.create(account(null, "2", "الباب الثاني", "EXPENSE"));

        accounts.deactivate(account.getId());

        assertThat(accountRepository.findById(account.getId()))
                .as("an account that has been posted to must survive for the audit trail")
                .isPresent()
                .get()
                .satisfies(a -> assertThat(a.getActive()).isFalse());
    }

    private CoaAccount account(Integer parentId, String code, String nameAr, String nature) {
        CoaAccount a = new CoaAccount();
        a.setSegmentId(projectSegment);
        a.setFiscalYearId(fiscalYear);
        a.setParentAccountId(parentId);
        a.setCode(code);
        a.setNameAr(nameAr);
        a.setAccountNature(nature);
        return a;
    }
}
