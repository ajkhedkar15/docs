package com.ss.corp.map.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.exception.BadRequestException;
import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.PagedResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * AE search against a real PostgreSQL (needs the aur_map_app tables; ae_owner is text[]).
 * Every test runs in a rolled-back transaction, and every search is limited to the seeded TEST-AE ids,
 * so existing rows don't change the results.
 */
@SpringBootTest
@Transactional
class AESearchIT {

    private static final List<String> SEEDED = List.of("TEST-AE-1", "TEST-AE-2", "TEST-AE-3", "TEST-AE-9");

    @Autowired
    private AEService aeService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        ae("TEST-AE-3", "Zeta", true, "{Jane Doe}", "Area A", "Div 1");
        ae("TEST-AE-1", "Alpha", true, "{Jane Doe,John Smith}", "Area A", "Div 2");
        ae("TEST-AE-2", "Alpha", true, "{John Smith}", "Area B", "Div 1");
        ae("TEST-AE-9", "Inactive", false, "{Jane Doe}", "Area A", "Div 1");
        tag("TEST-AE-1", "est", "Custody", true);
        tag("TEST-AE-2", "est", "Fund Accounting", true);
        tag("TEST-AE-3", "est", "Custody", false);              // removed tag: doesn't count
        tag("TEST-AE-3", "est", "Securities Lending", true);
    }

    @Test
    void noFiltersReturnsActiveAEsByNameThenId() {
        assertThat(search(Map.of())).containsExactly("TEST-AE-1", "TEST-AE-2", "TEST-AE-3");
    }

    @Test
    void ownerMatchesAnyOwnerOfTheAE() {
        assertThat(search(Map.of("aeOwner", List.of("John Smith")))).containsExactly("TEST-AE-1", "TEST-AE-2");
        assertThat(search(Map.of("aeOwner", List.of("O'Brien' or 1=1 --")))).isEmpty();
    }

    @Test
    void orWithinAFilterAndAcrossFilters() {
        assertThat(search(Map.of("businessDivision", List.of("Div 1", "Div 2"))))
                .containsExactly("TEST-AE-1", "TEST-AE-2", "TEST-AE-3");
        assertThat(search(Map.of("businessDivision", List.of("Div 1"), "aeOwner", List.of("Jane Doe"))))
                .containsExactly("TEST-AE-3");
    }

    @Test
    void attributeFiltersUseActiveTagsOnly() {
        assertThat(search(Map.of("est", List.of("Custody")))).containsExactly("TEST-AE-1");
        assertThat(search(Map.of("est", List.of("Custody", "Fund Accounting"), "businessArea", List.of("Area B"))))
                .containsExactly("TEST-AE-2");
        assertThat(search(Map.of("est", List.of("Securities Lending")))).containsExactly("TEST-AE-3");
    }

    @Test
    void emptyValueListIsNoFilter() {
        assertThat(search(Map.of("aeName", List.of()))).hasSize(3);
    }

    @Test
    void pagingAndIsOwner() {
        PagedResponse<AEResponse> page2 = aeService.search(
                Screen.ATTRIBUTE_TAGGING, new FilterRequest(withSeededIds(Map.of())), 2, 2, "Jane Doe");
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.totalRecords()).isEqualTo(3);
        assertThat(page2.totalPages()).isEqualTo(2);
        assertThat(page2.items()).extracting(AEResponse::aeId).containsExactly("TEST-AE-3");
        assertThat(page2.items().get(0).isOwner()).isTrue();

        assertThatThrownBy(() -> aeService.search(
                Screen.ATTRIBUTE_TAGGING, new FilterRequest(withSeededIds(Map.of())), 3, 2, "Jane Doe"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unknownFilterIsBadRequest() {
        assertThatThrownBy(() -> search(Map.of("bogus", List.of("x")))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> search(Map.of("legalEntity", List.of("x")))).isInstanceOf(BadRequestException.class);
    }

    private List<String> search(Map<String, List<String>> filters) {
        return aeService.search(Screen.ATTRIBUTE_TAGGING, new FilterRequest(withSeededIds(filters)), 1, 20, "Jane Doe")
                .items().stream().map(AEResponse::aeId).toList();
    }

    private static Map<String, List<String>> withSeededIds(Map<String, List<String>> filters) {
        Map<String, List<String>> limited = new HashMap<>(filters);
        limited.putIfAbsent("aeId", SEEDED);
        return limited;
    }

    private void ae(String aeId, String name, boolean active, String owners, String area, String division) {
        jdbc.update("""
                insert into aur_map_app.auditable_entity
                  (id, ae_id, ae_name, ae_status, ae_internal_id, ae_owner, business_area, division)
                values (gen_random_uuid(), ?, ?, ?, ?, ?::text[], ?, ?)""",
                aeId, name, active, "INT-" + aeId, owners, area, division);
    }

    private void tag(String aeId, String type, String name, boolean active) {
        jdbc.update("""
                insert into aur_map_app.auditable_entity_attributes (id, ae_id, attr_type, attr_id, attr_name, attr_status)
                values (gen_random_uuid(), ?, ?, ?, ?, ?)""",
                aeId, type, name, name, active);
    }
}
