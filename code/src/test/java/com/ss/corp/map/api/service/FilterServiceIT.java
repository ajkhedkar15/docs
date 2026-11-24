package com.ss.corp.map.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ss.corp.map.api.enums.FilterGroup;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.model.FilterOption;
import com.ss.corp.map.api.model.FilterResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Filter dropdowns against a real PostgreSQL. Rolled back after each test. */
@SpringBootTest
@Transactional
class FilterServiceIT {

    @Autowired
    private FilterService filterService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void basicGroupIsTheFourAeDropdowns() {
        FilterResponse basic = filterService.getFilters(Screen.ATTRIBUTE_TAGGING, FilterGroup.BASIC);
        assertThat(basic.screen()).isEqualTo("attribute-tagging");
        assertThat(basic.filters()).extracting(FilterOption::type)
                .containsExactly("aeId", "aeName", "aeOwner", "businessDivision");
    }

    @Test
    void estOptionsComeFromEstMaster() {
        jdbc.update("insert into aur_map_app.est_master (est_id, est_name) values "
                + "('TEST-EST-1', 'ZZ Test EST'), ('TEST-EST-2', 'ZZ Test EST'), ('TEST-EST-3', 'AA Test EST')");

        FilterResponse advanced = filterService.getFilters(Screen.ATTRIBUTE_TAGGING, FilterGroup.ADVANCED);

        assertThat(advanced.filters()).extracting(FilterOption::type).containsExactly("est", "businessArea");
        assertThat(advanced.filters().get(0).options())
                .contains("AA Test EST", "ZZ Test EST")
                .isSorted()
                .doesNotHaveDuplicates();
    }
}
