package com.assistant.core.controller;

import com.assistant.core.dto.ApiResponse;
import com.assistant.core.dto.PageResponseDTO;
import com.assistant.core.dto.TaskRequestDTO;
import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.repository.UserRepository;
import com.assistant.core.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    @Value("${app.pagination.default-size:20}")
    private int defaultPageSize;
    @Value("${app.pagination.max-size:100}")
    private int maxPageSize;

    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDTO<TaskResponseDTO>>> getTasks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = resolveUserId(authentication);
        if (page < 0) page = 0;
        if (size <= 0) size = defaultPageSize;
        if (size > maxPageSize) size = maxPageSize;
        PageResponseDTO<TaskResponseDTO> result = taskService.listPendingTasks(userId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponseDTO>> createTask(
            Authentication authentication,
            @Valid @RequestBody TaskRequestDTO request) {
        Long userId = resolveUserId(authentication);
        TaskResponseDTO task = taskService.createTask(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @PutMapping("/{id}/done")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> markTaskDone(
            Authentication authentication,
            @PathVariable Long id) {
        Long userId = resolveUserId(authentication);
        TaskResponseDTO task = taskService.markDone(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> updateTaskStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long userId = resolveUserId(authentication);
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        TaskResponseDTO task = taskService.updateStatus(userId, id, status.trim().toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            Authentication authentication,
            @PathVariable Long id) {
        Long userId = resolveUserId(authentication);
        taskService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private Long resolveUserId(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        if (email == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
