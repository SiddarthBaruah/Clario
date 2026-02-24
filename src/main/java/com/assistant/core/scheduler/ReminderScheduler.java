package com.assistant.core.scheduler;

import com.assistant.core.model.Task;
import com.assistant.core.model.ReminderLog;
import com.assistant.core.model.User;
import com.assistant.core.repository.ReminderLogRepository;
import com.assistant.core.repository.TaskRepository;
import com.assistant.core.repository.UserRepository;
import com.assistant.core.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Runs every minute: finds tasks due for reminder, ensures not already in reminder_log,
 * formats message, simulates WhatsApp send, inserts reminder_log for idempotency.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault());

    private final TaskRepository taskRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;

    public ReminderScheduler(TaskRepository taskRepository,
                             ReminderLogRepository reminderLogRepository,
                             UserRepository userRepository,
                             WhatsAppService whatsAppService) {
        this.taskRepository = taskRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.userRepository = userRepository;
        this.whatsAppService = whatsAppService;
    }

    @Scheduled(fixedDelay = 60000)
    public void sendDueReminders() {
        Instant now = Instant.now();
        List<Task> dueTasks = taskRepository.findUpcomingReminders(now);
        if (dueTasks.isEmpty()) {
            return;
        }
        log.debug("Reminder run: {} task(s) with reminder_time <= now", dueTasks.size());
        for (Task task : dueTasks) {
            if (reminderLogRepository.existsByTaskId(task.getId())) {
                log.trace("Task {} already has reminder_log, skipping", task.getId());
                continue;
            }
            User user = userRepository.findById(task.getUserId())
                    .orElse(null);
            if (user == null) {
                log.warn("User not found for task {} (user_id={}), skipping reminder", task.getId(), task.getUserId());
                continue;
            }
            String message = formatReminderMessage(task);
            whatsAppService.sendReminder(user.getPhoneNumber(), message);
            ReminderLog reminderLog = new ReminderLog();
            reminderLog.setTaskId(task.getId());
            reminderLog.setSentAt(now);
            reminderLog.setStatus("SENT");
            reminderLogRepository.insert(reminderLog);
            log.info("Reminder sent for task id={}, title='{}'", task.getId(), task.getTitle());
        }
    }

    private String formatReminderMessage(Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reminder: ").append(task.getTitle());
        if (task.getDueTime() != null) {
            sb.append(" (due ").append(TIME_FORMAT.format(task.getDueTime())).append(")");
        }
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            sb.append("\n").append(task.getDescription());
        }
        return sb.toString();
    }
}
