package com.assistant.core.mcp.person;

import com.assistant.core.dto.PersonResponseDTO;
import com.assistant.core.mcp.Tool;
import com.assistant.core.service.PeopleService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool: retrieve_people. Delegates to PeopleService only—no raw SQL.
 */
@Component
public class RetrievePeopleTool implements Tool {

    private final PeopleService peopleService;

    public RetrievePeopleTool(PeopleService peopleService) {
        this.peopleService = peopleService;
    }

    @Override
    public String name() {
        return "retrieve_people";
    }

    @Override
    public String description() {
        return "Use when the user asks to see their contacts, people they've saved, who they have stored, or to look up someone (e.g. 'who are my contacts?', 'show my people', 'do I have John saved?', 'what's Sarah's number or notes?'). "
                + "Returns all saved people with name, notes, and important dates. Parameters: userId (required).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        List<PersonResponseDTO> people = peopleService.listPeople(userId);
        List<Map<String, Object>> items = people.stream()
                .map(RetrievePeopleTool::toMap)
                .collect(Collectors.toList());
        return Map.of("people", items, "count", items.size());
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
    }

    private static Map<String, Object> toMap(PersonResponseDTO dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("name", dto.getName());
        m.put("notes", dto.getNotes());
        m.put("importantDates", dto.getImportantDates());
        m.put("createdAt", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        return m;
    }
}
