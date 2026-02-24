package com.assistant.core.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    @Scheduled(cron = "${app.scheduler.cleanup.cron:0 0 2 * * ?}")
    public void scheduledCleanup() {
        log.debug("Running scheduled cleanup job");
        // Add cleanup logic (e.g. expired tokens, old audit logs)
    }

    @Scheduled(fixedDelayString = "${app.scheduler.health-check.delay:60000}")
    public void healthCheck() {
        log.trace("Health check tick");
    }
}
