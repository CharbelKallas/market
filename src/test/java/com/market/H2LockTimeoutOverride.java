package com.market;

import com.zaxxer.hikari.SQLExceptionOverride;

import java.sql.SQLException;

/**
 * H2 reports a lock timeout as SQLTimeoutException (state HYT00), which Hikari treats as a dead connection and
 * closes. PostgreSQL reports it as an ordinary error (55P03), so to make tests behave like production this keeps the
 * connection for H2's lock timeout.
 */
public class H2LockTimeoutOverride implements SQLExceptionOverride {
    @java.lang.Override
    public SQLExceptionOverride.Override adjudicate(SQLException e) {
        return "HYT00".equals(e.getSQLState())
                ? SQLExceptionOverride.Override.DO_NOT_EVICT
                : SQLExceptionOverride.Override.CONTINUE_EVICT;
    }
}
