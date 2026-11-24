package com.ss.corp.map.api.service;

import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.PagedResponse;
import java.util.List;

public interface AEService {

    // ---- Dropdown values: active AEs only, sorted, no duplicates ----

    List<String> findAllAeIds();

    List<String> findAllAeNames();

    List<String> findAllAeOwners();

    List<String> findAllBusinessAreas();

    List<String> findAllBusinessDivisions();

    // ---- Search ----

    /**
     * Active AEs matching the selected filters, sorted by AE name then AE id. Pages start at 1.
     * 400 for a filter the screen doesn't have, or a page past the last one.
     */
    PagedResponse<AEResponse> search(Screen screen, FilterRequest request, int page, int pageSize, String userName);
}
