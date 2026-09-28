package com.dilaw.mohe.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asynchronous half of report access logging: persists a row already built on
 * the request thread.
 *
 * <p>Separate from {@link ReportAccessLogService} because {@code @Async} only takes
 * effect through the bean's proxy — a method cannot hand off to an async method on
 * its own class. The split lets the service read the user, address and country
 * scope on the thread that has them and leave only the insert to the pool.
 */
@Service
public class ReportAccessLogWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportAccessLogWriter.class);

    private final ReportAccessLogRepository repository;

    public ReportAccessLogWriter(ReportAccessLogRepository repository) {
        this.repository = repository;
    }

    /** Never throws — a failed audit row must not affect the export it describes. */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(ReportAccessLog entry) {
        try {
            repository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to log report access for {}: {}", entry.getReportCode(), ex.getMessage());
        }
    }
}
