package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.repository.EstMasterRepository;
import com.ss.corp.map.api.service.AttributeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dropdown values for the attribute types. Each type has its own master table and repository.
 * Adding a type: its entity + repository, one case here, one line in the screen's option enum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttributeServiceImpl implements AttributeService {

    private final EstMasterRepository estMasterRepository;

    @Override
    public List<String> findNamesByType(String attributeType) {
        log.debug("Loading options for attribute type {}", attributeType);
        return switch (attributeType) {
            case "est" -> estMasterRepository.findDistinctEstNames();
            default -> throw new IllegalStateException("No master table for attribute type " + attributeType);
        };
    }
}
