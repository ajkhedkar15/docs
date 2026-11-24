package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.repository.attribute.AttributeRepository;
import com.ss.corp.map.api.service.AttributeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AttributeServiceImpl implements AttributeService {

    /** attribute type key (e.g. legalEntity) -> its repository */
    private final Map<String, AttributeRepository<?>> repositoriesByType;

    /** Spring injects all 11 attribute repositories; adding a type is just a new entity + repository. */
    public AttributeServiceImpl(List<AttributeRepository<?>> repositories) {
        this.repositoriesByType = repositories.stream()
                .collect(Collectors.toMap(AttributeRepository::attributeType, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findNamesByType(String attributeType) {
        AttributeRepository<?> repository = repositoriesByType.get(attributeType);
        if (repository == null) {
            throw new IllegalStateException("No repository for attribute type " + attributeType);
        }
        log.debug("Loading options for attribute type {}", attributeType);
        return repository.findDistinctActiveNames();
    }
}
