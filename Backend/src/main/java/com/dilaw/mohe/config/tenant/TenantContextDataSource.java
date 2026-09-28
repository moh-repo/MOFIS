package com.dilaw.mohe.config.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Stamps the current {@link TenantContext} onto every connection handed out, and
 * clears it when the connection goes back to the pool.
 *
 * <p>Row-Level Security reads {@code SESSION_CONTEXT}, which lives on the
 * <em>session</em>, i.e. the physical connection. HikariCP reuses those
 * connections across requests, so the two halves of this class are equally
 * important:
 *
 * <ul>
 *   <li><b>Set on borrow</b> — otherwise the predicate sees no scope and every
 *       query returns nothing.</li>
 *   <li><b>Clear on close</b> — otherwise the next request to borrow this
 *       connection inherits the previous user's countries, which is a
 *       cross-tenant read and exactly the class of bug this whole change exists
 *       to prevent.</li>
 * </ul>
 *
 * <p>{@code sp_set_session_context} is called with {@code @read_only = 0} so the
 * value can be overwritten and cleared on subsequent borrows.
 *
 * <p>Note that Row-Level Security applies to {@code sysadmin} and {@code db_owner}
 * as well — verified against this server — so there is no privileged connection
 * that quietly bypasses the filter. That makes the clearing half non-negotiable
 * but it also means the protection is real rather than advisory.
 */
public class TenantContextDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantContextDataSource.class);

    private static final String KEY_ALL = "TenantAll";
    private static final String KEY_IDS = "TenantCountryIds";
    /** N-04.d: 1 only on connections that may write Wafar rows (country accounts, system work). */
    public static final String KEY_WAFAR_WRITE = "TenantWafarWrite";

    public TenantContextDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection connection) throws SQLException {
        applyScope(connection, TenantContext.current());
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new ClearOnCloseHandler(connection));
    }

    /** Writes the scope into SESSION_CONTEXT for this connection. */
    private static void applyScope(Connection connection, TenantContext.Scope scope) throws SQLException {
        setSessionContext(connection, KEY_ALL, scope.all() ? "1" : "0");
        setSessionContext(connection, KEY_IDS, scope.idsAsCsv());
        setSessionContext(connection, KEY_WAFAR_WRITE, scope.wafarWrite() ? "1" : "0");
    }

    private static void setSessionContext(Connection connection, String key, String value) throws SQLException {
        try (var stmt = connection.prepareCall("EXEC sp_set_session_context @key = ?, @value = ?, @read_only = 0")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.execute();
        }
    }

    /**
     * Clears the tenant keys before the connection returns to the pool.
     *
     * <p>A failure to clear is logged but not thrown: the connection is being
     * closed, and swallowing the close would leak it from the pool. The next
     * borrow overwrites both keys unconditionally, so a missed clear is corrected
     * rather than inherited — the clear is a second line of defence against a
     * connection that is borrowed by something which bypasses this wrapper.
     */
    private record ClearOnCloseHandler(Connection target) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                clearQuietly();
                target.close();
                return null;
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Class<?> type && type.isInstance(target)) {
                return target;
            }
            if ("isWrapperFor".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Class<?> type) {
                return type.isInstance(target) || target.isWrapperFor(type);
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }

        private void clearQuietly() {
            if (isClosed()) {
                return;
            }
            try {
                setSessionContext(target, KEY_ALL, "0");
                setSessionContext(target, KEY_IDS, "");
                setSessionContext(target, KEY_WAFAR_WRITE, "0");
            } catch (SQLException ex) {
                log.warn("Could not clear tenant SESSION_CONTEXT before returning the connection "
                        + "to the pool; the next borrow will overwrite it. Cause: {}", ex.getMessage());
            }
        }

        private boolean isClosed() {
            try {
                return target.isClosed();
            } catch (SQLException ex) {
                return true;
            }
        }
    }
}
