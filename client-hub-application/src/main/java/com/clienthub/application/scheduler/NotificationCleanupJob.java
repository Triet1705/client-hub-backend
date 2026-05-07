package com.clienthub.application.scheduler;

import com.clienthub.domain.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class NotificationCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationRepository notificationRepository;

    public NotificationCleanupJob(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Runs every day at 2:00 AM.
     * Deletes notifications that were read more than 3 days ago.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void deleteOldReadNotifications() {
        Instant cutoffDate = Instant.now().minus(3, ChronoUnit.DAYS);
        logger.info("Starting cleanup of read notifications older than: {}", cutoffDate);

        int deletedCount = notificationRepository.deleteOldReadNotifications(cutoffDate);

        logger.info("Successfully deleted {} old read notifications.", deletedCount);
    }
}
