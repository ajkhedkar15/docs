package com.ss.corp.map.api.controller;

import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.FilterResponse;
import com.ss.corp.map.api.model.PagedResponse;
import com.ss.corp.map.api.service.AEService;
import com.ss.corp.map.api.service.FilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/attribute-tagging", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attribute Tagging", description = "Find Auditable Entities and tag attributes to them")
public class AttributeTaggingController {

    private final FilterService filterService;
    private final AEService aeService;

    @Operation(
            summary = "Get filter dropdowns",
            description = "Every dropdown of the Attribute Tagging screen, in screen order, "
                    + "each with all its values as plain text. Same for every user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dropdowns and their values"),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/filters")
    public FilterResponse getFilters() {
        return filterService.getFilters(Screen.ATTRIBUTE_TAGGING.getValue());
    }

    @Operation(
            summary = "Search Auditable Entities",
            description = "Active AEs matching the selected filters, sorted by AE name. "
                    + "OR within a dropdown, AND across dropdowns. Empty filters return all AEs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of AE cards"),
            @ApiResponse(responseCode = "400", description = "Unknown screen or filter, or bad paging",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/aes/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PagedResponse<AEResponse> searchAEs(
            @Valid @RequestBody FilterRequest request,

            @Parameter(description = "Page number, starts at 1", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Items per page, 1 to 100", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,

            // TODO: replace with RequestDetailsHolder.getUserId() once RequestContextFilter exists (plan.md 4.8)
            @Parameter(description = "Caller's user id", example = "jdoe")
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        return aeService.search(request, page, pageSize, userId);
    }
}
