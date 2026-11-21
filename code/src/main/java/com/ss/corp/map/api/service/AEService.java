package com.ss.corp.map.api.service;

import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.PagedResponse;

/** Auditable Entities: list and search. */
public interface AEService {

    /**
     * One page of active AEs matching the selected filters, sorted by AE name.
     * OR within a dropdown, AND across dropdowns; empty filters = all AEs.
     *
     * @param request     the screen and the selected filter values
     * @param page        1-based page number
     * @param pageSize    1 to 100
     * @param currentUser the caller; decides isOwner on each card
     * @return the page of AE cards
     * @throws com.ss.corp.map.api.exception.ValidationException unknown screen or filter, or bad paging (400)
     */
    PagedResponse<AEResponse> search(FilterRequest request, int page, int pageSize, String currentUser);
}
