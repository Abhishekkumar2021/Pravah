package io.pravah.pipeline.api.dto;

import java.util.List;

public record PipelineListResponse(
    List<PipelineResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {}
