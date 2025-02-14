package com.michelin.ns4kafka.models.schema;

import com.michelin.ns4kafka.models.ObjectMeta;
import io.micronaut.core.annotation.Introspected;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@Introspected
@NoArgsConstructor
@AllArgsConstructor
public class Schema {
    private final String apiVersion = "v1";
    private final String kind = "Schema";

    @Valid
    @NotNull
    private ObjectMeta metadata;

    @Valid
    @NotNull
    private SchemaSpec spec;

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SchemaSpec {
        private Integer id;
        private Integer version;
        private String schema;

        @Builder.Default
        private SchemaType schemaType = SchemaType.AVRO;

        @Builder.Default
        private Compatibility compatibility = Compatibility.GLOBAL;
        private List<Reference> references;

        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        @Setter
        public static class Reference {
            private String name;
            private String subject;
            private Integer version;
        }
    }

    @Introspected
    public enum Compatibility {
        GLOBAL,
        BACKWARD,
        BACKWARD_TRANSITIVE,
        FORWARD,
        FORWARD_TRANSITIVE,
        FULL,
        FULL_TRANSITIVE,
        NONE
    }

    @Introspected
    public enum SchemaType {
        AVRO,
        JSON,
        PROTOBUF
    }
}
