package com.ss.corp.map.api.filter;

import static com.ss.corp.map.api.filter.FilterDefinition.aeColumn;
import static com.ss.corp.map.api.filter.FilterDefinition.aeOwner;
import static com.ss.corp.map.api.filter.FilterDefinition.attribute;

import com.ss.corp.map.api.exception.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The Attribute Tagging screen's dropdowns, in screen order. */
@Component
public class AttributeTaggingFilters {

    public static final String SCREEN = "attribute-tagging";

    private final List<FilterDefinition> definitions = List.of(
            aeColumn("aeId", "aeId"),
            aeColumn("aeName", "aeName"),
            aeOwner(),
            attribute("est"),
            attribute("risk"),
            attribute("legalEntity"),
            attribute("regulation"),
            attribute("thirdParty"),
            attribute("application"),
            aeColumn("businessArea", "businessArea"),
            aeColumn("businessDivision", "division"),
            attribute("model"),
            attribute("archerRiskUnit"),
            attribute("costCenter"),
            attribute("location"),
            attribute("product"));

    private final Map<String, FilterDefinition> byType =
            definitions.stream().collect(Collectors.toUnmodifiableMap(FilterDefinition::type, Function.identity()));

    public List<FilterDefinition> all() {
        return definitions;
    }

    /** The definition for a filter type; 400 for a type this screen doesn't have. */
    public FilterDefinition get(String type) {
        FilterDefinition definition = byType.get(type);
        if (definition == null) {
            throw new ValidationException("filters." + type, "Unknown filter type");
        }
        return definition;
    }
}
