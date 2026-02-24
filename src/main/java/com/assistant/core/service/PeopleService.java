package com.assistant.core.service;

import com.assistant.core.dto.AddPersonRequestDTO;
import com.assistant.core.dto.PageResponseDTO;
import com.assistant.core.dto.PersonResponseDTO;
import com.assistant.core.dto.UpdatePersonNotesRequestDTO;
import com.assistant.core.model.People;
import com.assistant.core.repository.PeopleRepository;
import com.assistant.core.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PeopleService {

    private static final Logger log = LoggerFactory.getLogger(PeopleService.class);

    private final PeopleRepository peopleRepository;

    public PeopleService(PeopleRepository peopleRepository) {
        this.peopleRepository = peopleRepository;
    }

    @Transactional
    public PersonResponseDTO addPerson(Long userId, AddPersonRequestDTO request) {
        String name = InputSanitizer.sanitizeName(request.getName());
        String notes = InputSanitizer.sanitizeLongText(request.getNotes());
        String importantDates = InputSanitizer.sanitizeLongText(request.getImportantDates());
        // JSON column does not accept empty string; use null for "no dates"
        if (importantDates != null && importantDates.isBlank()) {
            importantDates = null;
        }
        People people = new People();
        people.setUserId(userId);
        people.setName(name);
        people.setNotes(notes);
        people.setImportantDates(importantDates);
        people = peopleRepository.save(people);
        log.info("Person added: id={}, userId={}, name={}", people.getId(), userId, name);
        return toResponseDTO(people);
    }

    @Transactional
    public PersonResponseDTO updatePersonNotes(Long userId, Long personId, UpdatePersonNotesRequestDTO request) {
        People people = peopleRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Person not found"));
        if (!people.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Person not found");
        }
        String notes = InputSanitizer.sanitizeLongText(request.getNotes());
        people.setNotes(notes != null ? notes : "");
        people = peopleRepository.save(people);
        log.info("Person notes updated: personId={}, userId={}", personId, userId);
        return toResponseDTO(people);
    }

    public List<PersonResponseDTO> listPeople(Long userId) {
        return peopleRepository.findByUserId(userId).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public PageResponseDTO<PersonResponseDTO> listPeople(Long userId, int page, int size) {
        int offset = page * size;
        List<People> list = peopleRepository.findByUserId(userId, size, offset);
        long total = peopleRepository.countByUserId(userId);
        List<PersonResponseDTO> content = list.stream().map(this::toResponseDTO).collect(Collectors.toList());
        return new PageResponseDTO<>(content, total, page, size);
    }

    private PersonResponseDTO toResponseDTO(People p) {
        PersonResponseDTO dto = new PersonResponseDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setNotes(p.getNotes());
        dto.setImportantDates(p.getImportantDates());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
