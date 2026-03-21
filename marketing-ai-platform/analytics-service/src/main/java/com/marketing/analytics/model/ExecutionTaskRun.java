package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_task_runs")
public class ExecutionTaskRun {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID taskId;

    @Column(nullable = false)
    int attempt;

    @Column(nullable = false)
    String status;

    @Column(columnDefinition = "jsonb")
    String inputJson;

    @Column(columnDefinition = "jsonb")
    String outputJson;

    String errorMessage;

    @Column(nullable = false)
    Instant startedAt;

    Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID v) { taskId = v; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int v) { attempt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String v) { inputJson = v; }
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String v) { outputJson = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { startedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { completedAt = v; }
}
