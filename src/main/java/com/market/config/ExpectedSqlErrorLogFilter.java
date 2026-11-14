package com.market.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Hibernate's SqlExceptionHelper logs every SQL error before the app handles it. Two kinds are expected and already
 * answered and logged by the app (409 responses): lock timeouts and unique-constraint violations. This drops just
 * those lines so they don't look like failures; every other SQL error is still logged with its state and message.
 */
public class ExpectedSqlErrorLogFilter extends TurboFilter {

    static final String LOGGER = "org.hibernate.engine.jdbc.spi.SqlExceptionHelper";

    private static final String[] EXPECTED = {
            // SQL states, from the "SQL Error: n, SQLState: x" line
            "SQLState: 55P03", "SQLState: 23505", "SQLState: HYT00",
            // the vendor messages logged right after it (PostgreSQL, H2)
            "canceling statement due to lock timeout", "duplicate key value violates unique constraint",
            "Timeout trying to lock table", "Unique index or primary key violation"
    };

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (format == null || logger == null || !LOGGER.equals(logger.getName()))
            return FilterReply.NEUTRAL;
        for (String expected : EXPECTED)
            if (format.contains(expected))
                return FilterReply.DENY;
        return FilterReply.NEUTRAL;
    }
}
