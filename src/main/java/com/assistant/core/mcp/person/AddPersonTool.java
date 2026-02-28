package com.assistant.core.mcp.person;

import com.assistant.core.dto.AddPersonRequestDTO;
import com.assistant.core.dto.PersonResponseDTO;
import com.assistant.core.mcp.Tool;
import com.assistant.core.service.PeopleService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool: add_person. Delegates to PeopleService only—no raw SQL.
 */
@Component
public class AddPersonTool implements Tool {

    private final PeopleService peopleService;

    public AddPersonTool(PeopleService peopleService) {
        this.peopleService = peopleService;
    }

    @Override
    public String name() {
        return "add_person";
    }

    @Override
    public String description() {
        return "Use when the user wants to save a contact, add a person, remember someone, or store details about a person (e.g. 'add contact for my dentist', 'remember John's birthday is in March', 'save Sarah - she's my accountant'). "
                + "Parameters: userId (required), name (required), notes (optional), importantDates (optional, JSON string for dates like birthdays).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        AddPersonRequestDTO request = new AddPersonRequestDTO();
        request.setName(requireString(arguments, "name"));
        request.setNotes(getString(arguments, "notes"));
        request.setImportantDates(getString(arguments, "importantDates"));
        PersonResponseDTO result = peopleService.addPerson(userId, request);
        return toMap(result);
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
    }

    private static String requireString(Map<String, Object> args, String key) {
        String s = getString(args, key);
        if (s == null || s.isBlank()) throw new IllegalArgumentException(key + " is required");
        return s;
    }

    private static String getString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? v.toString() : null;
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
