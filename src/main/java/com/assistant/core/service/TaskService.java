package com.assistant.core.service;

import com.assistant.core.dto.PageResponseDTO;
import com.assistant.core.dto.TaskRequestDTO;
import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.model.Task;
import com.assistant.core.repository.TaskRepository;
import com.assistant.core.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";

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
        return taskRepository.findByUserIdAndStatus(userId, STATUS_PENDING).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public PageResponseDTO<TaskResponseDTO> listPendingTasks(Long userId, int page, int size) {
        int offset = page * size;
        List<Task> tasks = taskRepository.findByUserIdAndStatus(userId, STATUS_PENDING, size, offset);
        long total = taskRepository.countByUserIdAndStatus(userId, STATUS_PENDING);
        List<TaskResponseDTO> content = tasks.stream().map(this::toResponseDTO).collect(Collectors.toList());
        return new PageResponseDTO<>(content, total, page, size);
    }

    @Transactional
    public TaskResponseDTO markDone(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (!task.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Task not found");
        }
        task.setStatus(STATUS_DONE);
        task = taskRepository.save(task);
        log.info("Task marked done: id={}, userId={}", taskId, userId);
        return toResponseDTO(task);
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
