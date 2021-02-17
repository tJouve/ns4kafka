package com.michelin.ns4kafka.repositories;


import com.michelin.ns4kafka.models.AccessControlEntry;
import com.michelin.ns4kafka.models.Connector;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.services.KafkaAsyncExecutor;
import com.michelin.ns4kafka.services.KafkaAsyncExecutorConfig;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.RxHttpClientFactory;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;


@Singleton
public class ConnectRepository {

    @Inject
    @Client("/")
    RxHttpClient httpClient;

    @Inject
    List<KafkaAsyncExecutorConfig> kafkaAsyncExecutorConfigs;

    @Inject
    NamespaceRepository namespaceRepository;
    @Inject
    AccessControlEntryRepository accessControlEntryRepository;


    public Flowable<Connector> list(String namespace){
        Optional<Namespace> namespaceOptional = namespaceRepository.findByName(namespace);
        String url = kafkaAsyncExecutorConfigs.stream()
                .filter(c -> c.getName().equals(namespaceOptional.get().getCluster()))
                .findFirst().get()
                .getConnect().getUrl();
        List<AccessControlEntry> acls = accessControlEntryRepository.findAllGrantedToNamespace(namespace);
        return httpClient.retrieve(HttpRequest.GET(url+"/connectors?expand=info&expand=status"),
                Argument.mapOf(String.class,ConnectItem.class))
                .flatMapIterable(stringConnectItemMap -> stringConnectItemMap.entrySet())
                .filter(entry -> acls.stream()
                        .anyMatch(accessControlEntry -> {
                            //no need to check accessControlEntry.Permission, we want READ, WRITE or OWNER
                            if(accessControlEntry.getSpec().getResourceType() == AccessControlEntry.ResourceType.CONNECT){
                                switch (accessControlEntry.getSpec().getResourcePatternType()){
                                    case PREFIXED:
                                        return entry.getKey().startsWith(accessControlEntry.getSpec().getResource());
                                    case LITERAL:
                                        return entry.getKey().equals(accessControlEntry.getSpec().getResource());
                                }
                            }
                            return false;
                        }))
                .map(entry -> Connector.builder()
                        .metadata(ObjectMeta.builder()
                                .name(entry.getKey())
                                .cluster(namespaceOptional.get().getCluster())
                                .namespace(namespace)
                                .labels(Map.of("type",entry.getValue().getInfo().getType()))
                                .build())
                        .spec(entry.getValue().getInfo().getConfig())
                        .status(Connector.ConnectorStatus.builder()
                                .state(Connector.TaskState.valueOf(entry.getValue().getStatus().getConnector().get("state")))
                                .tasks(entry.getValue().getStatus().getTasks()
                                        .stream()
                                        .map(task -> Connector.TaskStatus.builder()
                                                .id(task.get("id"))
                                                .state(Connector.TaskState.valueOf(task.get("state")))
                                                .worker_id(task.get("worker_id"))
                                                .trace(task.get("trace"))
                                                .build()
                                        )
                                        .collect(Collectors.toList())
                                )
                                .build()

                        )
                        .build()
                );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConnectItem {
        ConnectInfo info;
        ConnectStatus status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConnectInfo {
        String name;
        String type;
        Map<String,String> config;
        List<Map<String,String>> tasks;
    }
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConnectStatus{
        String name;
        String type;
        Map<String,String> connector;
        List<Map<String,String>> tasks;
    }

}
