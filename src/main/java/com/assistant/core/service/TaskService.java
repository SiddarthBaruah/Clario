package com.assistant.core.service;

import com.assistant.core.dto.PageResponseDTO;
import com.assistant.core.dto.TaskRequestDTO;
import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.model.Task;
import com.assistant.core.repository.TaskRepository;
import com.assistant.core.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_DONE = "DONE";
    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_DONE);

    private static final int SEARCH_MAX_RESULTS = 10;

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskResponseDTO createTask(Long userId, TaskRequestDTO request) {
        String title = InputSanitizer.sanitizeTitle(request.getTitle());
        String description = InputSanitizer.sanitizeLongText(request.getDescription());
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(title);
        task.setDescription(description);
        task.setDueTime(request.getDueTime());
        task.setReminderTime(request.getReminderTime());
        task.setStatus(STATUS_PENDING);
        task = taskRepository.save(task);
        log.info("Task created: id={}, userId={}, title={}", task.getId(), userId, title);
        return toResponseDTO(task);
    }

    public List<TaskResponseDTO> listPendingTasks(Long userId) {
        return taskRepository.findByUserIdAndStatusAndDeletedFalse(userId, STATUS_PENDING).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public PageResponseDTO<TaskResponseDTO> listPendingTasks(Long userId, int page, int size) {
        List<Task> tasks = taskRepository.findByUserIdAndStatusAndDeletedFalse(userId, STATUS_PENDING, PageRequest.of(page, size));
        long total = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, STATUS_PENDING);
        List<TaskResponseDTO> content = tasks.stream().map(this::toResponseDTO).collect(Collectors.toList());
        return new PageResponseDTO<>(content, total, page, size);
    }

    /** Returns active tasks (PENDING and IN_PROGRESS) for listing in assistant. */
    public List<TaskResponseDTO> listActiveTasks(Long userId) {
        List<Task> tasks = taskRepository.findByUserIdAndStatusInAndDeletedFalse(
                userId, List.of(STATUS_PENDING, STATUS_IN_PROGRESS), PageRequest.of(0, 500));
        return tasks.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    /** Search by natural-language query over title and description; returns top matches for the user. */
    public List<TaskResponseDTO> searchTasksByQuery(Long userId, String query, int maxResults) {
        String q = StringUtils.hasText(query) ? query.trim() : "";
        if (q.isEmpty()) {
            return List.of();
        }
        if (maxResults <= 0) maxResults = SEARCH_MAX_RESULTS;
        List<Task> tasks = taskRepository.findByUserIdAndTitleOrDescriptionContaining(
                userId, q, PageRequest.of(0, maxResults));
        return tasks.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional
    public TaskResponseDTO updateStatus(Long userId, Long taskId, String status) {
        if (status == null || !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (!task.getUserId().equals(userId) || task.isDeleted()) {
            throw new IllegalArgumentException("Task not found");
        }
        task.setStatus(status);
        task = taskRepository.save(task);
        log.info("Task status updated: id={}, userId={}, status={}", taskId, userId, status);
        return toResponseDTO(task);
    }

    @Transactional
    public TaskResponseDTO markDone(Long userId, Long taskId) {
        return updateStatus(userId, taskId, STATUS_DONE);
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (!task.getUserId().equals(userId) || task.isDeleted()) {
            throw new IllegalArgumentException("Task not found");
        }
        task.setDeleted(true);
        taskRepository.save(task);
        log.info("Task soft-deleted: id={}, userId={}", taskId, userId);
    }

    private TaskResponseDTO toResponseDTO(Task t) {
        TaskResponseDTO dto = new TaskResponseDTO();
        dto.setId(t.getId());
        dto.setUserId(t.getUserId());
        dto.setTitle(t.getTitle());
        dto.setDescription(t.getDescription());
        dto.setDueTime(t.getDueTime());
        dto.setReminderTime(t.getReminderTime());
        dto.setStatus(t.getStatus());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }
}
