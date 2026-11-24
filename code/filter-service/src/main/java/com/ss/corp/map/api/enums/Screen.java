package com.ss.corp.map.api.enums;

import com.ss.corp.map.api.exception.BadRequestException;
import com.ss.corp.map.api.exception.FieldError;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every screen of the app. The key is kebab-case, as in the URL ({@code /screens/attribute-tagging/...}).
 * Adding a screen: add it here with its own option enum (like {@link AttributeTaggingOption}).
 */
@Getter
public enum Screen {

    ATTRIBUTE_TAGGING("attribute-tagging", AttributeTaggingOption.values());

    static final String UNKNOWN_FILTER = "Unknown filter for this screen";

    private final String key;

    /** The screen's dropdowns, in screen order. */
    private final List<ScreenOption> options;

    Screen(String key, ScreenOption[] options) {
        this.key = key;
        this.options = List.of(options);
    }

    public Optional<ScreenOption> findOption(String optionKey) {
        return options.stream()
                .filter(option -> option.key().equals(optionKey))
                .findFirst();
    }

    /** @throws BadRequestException (400) for an unknown screen key */
    public static Screen fromKey(String key) {
        return Arrays.stream(values())
                .filter(screen -> screen.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown screen: " + key));
    }

    /** @throws BadRequestException (400) listing every filter type this screen doesn't have */
    public void requireKnownFilters(Map<String, ?> selected) {
        List<FieldError> errors = selected.keySet().stream()
                .filter(optionKey -> findOption(optionKey).isEmpty())
                .sorted()
                .map(optionKey -> new FieldError("filters." + optionKey, UNKNOWN_FILTER))
                .toList();
        if (!errors.isEmpty()) {
            throw new BadRequestException("Invalid request", errors);
        }
    }
}
