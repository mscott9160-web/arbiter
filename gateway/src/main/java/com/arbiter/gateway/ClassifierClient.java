package com.arbiter.gateway;

public interface ClassifierClient {
    ClassificationResult classify(String prompt);
}
