package com.arbiter.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClassificationResult(
        double complexity,
    @JsonProperty("task_class") String taskClass,
    @JsonProperty("classifier_version") String classifierVersion,
    @JsonProperty("uncertainty_band") boolean uncertaintyBand,
    @JsonProperty("route_up") boolean routeUp) {
    public ClassificationResult {
        if (complexity < 0.0 || complexity > 1.0) {
            throw new IllegalArgumentException("complexity must be between 0 and 1");
        }
        if (taskClass == null || taskClass.isBlank() || classifierVersion == null || classifierVersion.isBlank()) {
            throw new IllegalArgumentException("classifier metadata is required");
        }
    }
}
