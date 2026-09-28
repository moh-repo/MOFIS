package com.dilaw.mohe.config.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The database backstop for rule N-04 (security plan N-04.d, V095).
 *
 * <p>Every Wafar table carries a block predicate that refuses an insert, update
 * or delete unless {@code SESSION_CONTEXT('TenantWafarWrite')} is 1 on the
 * connection. {@link TenantContextDataSource} sets it from
 * {@link TenantContext.Scope#wafarWrite()}: 1 for a country account and for
 * system work, 0 for every ministry-level account. So a ministry connection
 * cannot write those rows even where a future code path forgets
 * {@code WafarPolicy}.
 *
 * <p>Two paths write Wafar rows <em>on behalf of</em> an attaché from a ministry
 * connection, and both are already controls in their own right: the super
 * admin approving a spend under decision D-13(b), and the approval workflow
 * executing a request the attaché raised and the chain signed. Those call
 * {@link #elevateCurrentTransaction} — a native call on the transaction's own
 * connection, because the ThreadLocal scope was stamped when the connection was
 * borrowed and changing it now would change nothing. The key is cleared with
 * the rest when the connection returns to the pool. Every call is logged and
 * the callers audit it.
 */
@Component
public class WafarWriteKey {

    private static final Logger log = LoggerFactory.getLogger(WafarWriteKey.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Turns the key on for the rest of the current transaction.
     *
     * @param reason why this write from a ministry connection is legitimate; goes to the log
     * @throws IllegalStateException outside a transaction — there is no connection to elevate
     */
    public void elevateCurrentTransaction(String reason) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The Wafar write key can only be raised inside a transaction (" + reason + ")");
        }
        // tenant-bypass: the key is raised for one transaction on the reasons named
        // in the class comment; the caller audits the use.
        entityManager.createNativeQuery(
                        "EXEC sp_set_session_context @key = N'" + TenantContextDataSource.KEY_WAFAR_WRITE
                                + "', @value = N'1', @read_only = 0")
                .executeUpdate();
        log.info("Wafar write key raised for this transaction: {}", reason);
    }
}
