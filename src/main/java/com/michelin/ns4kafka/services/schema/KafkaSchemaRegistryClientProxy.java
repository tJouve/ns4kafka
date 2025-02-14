package com.michelin.ns4kafka.services.schema;

import com.michelin.ns4kafka.config.KafkaAsyncExecutorConfig;
import com.michelin.ns4kafka.utils.exceptions.ResourceValidationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Filter(KafkaSchemaRegistryClientProxy.SCHEMA_REGISTRY_PREFIX + "/**")
public class KafkaSchemaRegistryClientProxy implements HttpServerFilter {
    public static final String SCHEMA_REGISTRY_PREFIX = "/schema-registry-proxy";

    public static final String PROXY_HEADER_KAFKA_CLUSTER = "X-Kafka-Cluster";

    public static final String PROXY_HEADER_SECRET = "X-Proxy-Secret";

    public static final String PROXY_SECRET = UUID.randomUUID().toString();

    @Inject
    List<KafkaAsyncExecutorConfig> kafkaAsyncExecutorConfigs;

    @Inject
    ProxyHttpClient client;

    /**
     * Filter requests
     * @param request The request to filter
     * @param chain The servlet chain
     * @return A modified request
     */
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        // Check call is initiated from Micronaut and not from outside
        if (!request.getHeaders().contains(KafkaSchemaRegistryClientProxy.PROXY_HEADER_SECRET)) {
            return Publishers.just(new ResourceValidationException(List.of("Missing required header " + KafkaSchemaRegistryClientProxy.PROXY_HEADER_SECRET), null, null));
        }

        String secret = request.getHeaders().get(KafkaSchemaRegistryClientProxy.PROXY_HEADER_SECRET);
        if (!PROXY_SECRET.equals(secret)) {
            return Publishers.just(new ResourceValidationException(List.of("Invalid value " + secret + " for header " + KafkaSchemaRegistryClientProxy.PROXY_HEADER_SECRET), null, null));
        }

        if (!request.getHeaders().contains(KafkaSchemaRegistryClientProxy.PROXY_HEADER_KAFKA_CLUSTER)) {
            return Publishers.just(new ResourceValidationException(List.of("Missing required header " + KafkaSchemaRegistryClientProxy.PROXY_HEADER_KAFKA_CLUSTER), null, null));
        }

        String kafkaCluster = request.getHeaders().get(KafkaSchemaRegistryClientProxy.PROXY_HEADER_KAFKA_CLUSTER);

        Optional<KafkaAsyncExecutorConfig> config = kafkaAsyncExecutorConfigs.stream()
                .filter(kafkaAsyncExecutorConfig -> kafkaAsyncExecutorConfig.getName().equals(kafkaCluster))
                .findFirst();

        if (config.isEmpty()) {
            return Publishers.just(new ResourceValidationException(List.of("Kafka Cluster [" + kafkaCluster + "] not found"),null,null));
        }

        if (config.get().getSchemaRegistry() == null) {
            return Publishers.just(new ResourceValidationException(List.of("Kafka Cluster [" + kafkaCluster + "] has no schema registry"),null,null));
        }

        return client.proxy(mutateSchemaRegistryRequest(request, config.get()));
    }

    /**
     * Mutate a request to the Schema Registry by modifying the base URI by the Schema Registry URI from the
     * cluster config
     * @param request The request to modify
     * @param config The configuration used to modify the request
     * @return The modified request
     */
    public MutableHttpRequest<?> mutateSchemaRegistryRequest(HttpRequest<?> request, KafkaAsyncExecutorConfig config) {
        URI newURI = URI.create(config.getSchemaRegistry().getUrl());

        MutableHttpRequest<?> mutableHttpRequest = request.mutate()
                .uri(mutableRequest -> mutableRequest
                        .scheme(newURI.getScheme())
                        .host(newURI.getHost())
                        .port(newURI.getPort())
                        .replacePath(StringUtils.prependUri(newURI.getPath(),
                                request.getPath().substring(KafkaSchemaRegistryClientProxy.SCHEMA_REGISTRY_PREFIX.length())
                        ))
                );

        if (StringUtils.isNotEmpty(config.getSchemaRegistry().getBasicAuthUsername()) &&
                StringUtils.isNotEmpty(config.getSchemaRegistry().getBasicAuthPassword())) {
            mutableHttpRequest.basicAuth(config.getSchemaRegistry().getBasicAuthUsername(),
                    config.getSchemaRegistry().getBasicAuthPassword());
        }

        mutableHttpRequest.getHeaders().remove(HttpHeaders.HOST);
        return mutableHttpRequest;
    }
}
