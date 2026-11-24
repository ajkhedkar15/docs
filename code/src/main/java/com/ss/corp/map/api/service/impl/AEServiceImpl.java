package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.exception.BadRequestException;
import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.PagedResponse;
import com.ss.corp.map.api.repository.AESearchSpecification;
import com.ss.corp.map.api.repository.AuditableEntityRepository;
import com.ss.corp.map.api.service.AEService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AEServiceImpl implements AEService {

    private static final Sort BY_NAME_THEN_ID = Sort.by("aeName", "aeId");

    private final AuditableEntityRepository aeRepository;

    @Override
    public List<String> findAllAeIds() {
        return aeRepository.findDistinctAeIds();
    }

    @Override
    public List<String> findAllAeNames() {
        return aeRepository.findDistinctAeNames();
    }

    @Override
    public List<String> findAllAeOwners() {
        return aeRepository.findDistinctOwners();
    }

    @Override
    public List<String> findAllBusinessAreas() {
        return aeRepository.findDistinctBusinessAreas();
    }

    @Override
    public List<String> findAllBusinessDivisions() {
        return aeRepository.findDistinctDivisions();
    }

    @Override
    public PagedResponse<AEResponse> search(Screen screen, FilterRequest request, int page, int pageSize, String userName) {
        screen.requireKnownFilters(request.filters());
        log.debug("Searching AEs on screen {} with filters {}", screen.getKey(), request.filters().keySet());

        Page<AuditableEntity> result = aeRepository.findAll(
                AESearchSpecification.of(screen, request.filters()),
                PageRequest.of(page - 1, pageSize, BY_NAME_THEN_ID));

        if (page > 1 && page > result.getTotalPages()) {
            throw new BadRequestException("Page " + page + " is past the last page (" + result.getTotalPages() + ")");
        }
        List<AEResponse> items = result.getContent().stream()
                .map(ae -> toResponse(ae, userName))
                .toList();
        return PagedResponse.of(result, items);
    }

    private AEResponse toResponse(AuditableEntity ae, String userName) {
        return new AEResponse(
                ae.getAeId(),
                ae.getAeName(),
                ae.getAeDesc(),
                ae.getBusinessArea(),
                ae.getModifiedOn(),
                0,       // TODO recommendationCount: from the recommendations service once it exists
                ae.isOwnedBy(userName),
                null);   // TODO lockedBy: from the lock service once it exists
    }
}
