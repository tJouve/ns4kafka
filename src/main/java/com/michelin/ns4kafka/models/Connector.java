package com.michelin.ns4kafka.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Introspected
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Connector {
    private final String apiVersion = "v1";
    private final String kind = "Connector";
    @Valid
    @NotNull
    private ObjectMeta metadata;

    @NotNull
    private Map<String,String> spec;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private ConnectorStatus status;

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public static class ConnectorStatus {
        private TaskState state;
        private String worker_id;

        private List<TaskStatus> tasks;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Date lastUpdateTime;

    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public static class TaskStatus{
        String id;
        TaskState state;
        String trace;
        String worker_id;
    }

    public enum TaskState {
        // From https://github.com/apache/kafka/blob/trunk/connect/runtime/src/main/java/org/apache/kafka/connect/runtime/AbstractStatus.java
        UNASSIGNED,
        RUNNING,
        PAUSED,
        FAILED,
        DESTROYED,
    }

}
