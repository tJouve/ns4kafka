package com.michelin.ns4kafka.services;

import com.michelin.ns4kafka.config.KafkaAsyncExecutorConfig;
import com.michelin.ns4kafka.config.SecurityConfig;
import com.michelin.ns4kafka.models.AccessControlEntry;
import com.michelin.ns4kafka.models.ConnectCluster;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.repositories.ConnectClusterRepository;
import com.nimbusds.jose.JOSEException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.rxjava3.http.client.Rx3HttpClient;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectClusterServiceTest {
    @Mock
    ConnectClusterRepository connectClusterRepository;

    @Mock
    AccessControlEntryService accessControlEntryService;

    @Mock
    List<KafkaAsyncExecutorConfig> kafkaAsyncExecutorConfigList;

    @Mock
    SecurityConfig securityConfig;

    @InjectMocks
    ConnectClusterService connectClusterService;

    @Mock
    @Client("/")
    Rx3HttpClient httpClient;

    /**
     * Test find all
     */
    @Test
    void findAllEmpty() {
        Mockito.when(connectClusterRepository.findAll()).thenReturn(List.of());
        List<ConnectCluster> actual = connectClusterRepository.findAll();

        Assertions.assertTrue(actual.isEmpty());
    }

    /**
     * Test find all
     */
    @Test
    void findAll() {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        Mockito.when(connectClusterRepository.findAll()).thenReturn(List.of(connectCluster));
        List<ConnectCluster> actual = connectClusterService.findAll();

        Assertions.assertEquals(1L, actual.size());
    }

    /**
     * Test find all for namespace
     */
    @Test
    void findAllForNamespace() {
        Namespace namespace = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("myNamespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .build())
                .build();

        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix.connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        ConnectCluster connectClusterTwo = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix2.connect-two")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        ConnectCluster connectClusterThree = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix3.connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        ConnectCluster connectClusterFour = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("not-owner")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        when(connectClusterRepository.findAllForCluster("local"))
                .thenReturn(List.of(connectCluster, connectClusterTwo, connectClusterThree, connectClusterFour));

        when(accessControlEntryService.findAllGrantedToNamespace(namespace))
                .thenReturn(List.of(
                        AccessControlEntry.builder()
                                .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                                        .permission(AccessControlEntry.Permission.OWNER)
                                        .grantedTo("namespace")
                                        .resourcePatternType(AccessControlEntry.ResourcePatternType.PREFIXED)
                                        .resourceType(AccessControlEntry.ResourceType.CONNECT_CLUSTER)
                                        .resource("prefix.")
                                        .build())
                                .build(),
                        AccessControlEntry.builder()
                                .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                                        .permission(AccessControlEntry.Permission.OWNER)
                                        .grantedTo("namespace")
                                        .resourcePatternType(AccessControlEntry.ResourcePatternType.LITERAL)
                                        .resourceType(AccessControlEntry.ResourceType.CONNECT_CLUSTER)
                                        .resource("prefix2.connect-two")
                                        .build())
                                .build(),
                        AccessControlEntry.builder()
                                .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                                        .permission(AccessControlEntry.Permission.READ)
                                        .grantedTo("namespace")
                                        .resourcePatternType(AccessControlEntry.ResourcePatternType.PREFIXED)
                                        .resourceType(AccessControlEntry.ResourceType.CONNECT_CLUSTER)
                                        .resource("prefix3.")
                                        .build())
                                .build(),
                        AccessControlEntry.builder()
                                .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                                        .permission(AccessControlEntry.Permission.OWNER)
                                        .grantedTo("namespace")
                                        .resourcePatternType(AccessControlEntry.ResourcePatternType.PREFIXED)
                                        .resourceType(AccessControlEntry.ResourceType.TOPIC)
                                        .resource("topic.")
                                        .build())
                                .build()
                ));

        List<ConnectCluster> actual = connectClusterService.findAllByNamespace(namespace, List.of(AccessControlEntry.Permission.OWNER));

        Assertions.assertEquals(2, actual.size());
        // contains
        Assertions.assertTrue(actual.stream().anyMatch(connector -> connector.getMetadata().getName().equals("prefix.connect-cluster")));
        Assertions.assertTrue(actual.stream().anyMatch(connector -> connector.getMetadata().getName().equals("prefix2.connect-two")));
        // doesn't contain
        Assertions.assertFalse(actual.stream().anyMatch(connector -> connector.getMetadata().getName().equals("not-owner")));
        Assertions.assertFalse(actual.stream().anyMatch(connector -> connector.getMetadata().getName().equals("prefix3.connect-cluster")));
    }

    /**
     * Test find by namespace and name
     */
    @Test
    void findByNamespaceAndName() {
        Namespace namespace = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("myNamespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .build())
                .build();

        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix.connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        when(connectClusterRepository.findAllForCluster("local"))
                .thenReturn(List.of(connectCluster));

        when(accessControlEntryService.findAllGrantedToNamespace(namespace))
                .thenReturn(List.of(
                        AccessControlEntry.builder()
                                .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                                        .permission(AccessControlEntry.Permission.OWNER)
                                        .grantedTo("namespace")
                                        .resourcePatternType(AccessControlEntry.ResourcePatternType.PREFIXED)
                                        .resourceType(AccessControlEntry.ResourceType.CONNECT_CLUSTER)
                                        .resource("prefix.")
                                        .build())
                                .build()
                ));

        Optional<ConnectCluster> actual = connectClusterService.findByNamespaceAndNameOwner(namespace, "prefix.connect-cluster");

        Assertions.assertTrue(actual.isPresent());
        Assertions.assertEquals("prefix.connect-cluster", actual.get().getMetadata().getName());
    }

    /**
     * Test find by namespace and name empty response
     */
    @Test
    void findByNamespaceAndNameEmpty() {
        Namespace namespace = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("myNamespace")
                        .cluster("local")
                        .build())
                .spec(Namespace.NamespaceSpec.builder()
                        .build())
                .build();

        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix.connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        when(connectClusterRepository.findAllForCluster("local"))
                .thenReturn(List.of(connectCluster));

        when(accessControlEntryService.findAllGrantedToNamespace(namespace))
                .thenReturn(List.of(
                        AccessControlEntry.builder()
                                .spec(AccessControlEntry.AccessControlEntrySpec.builder()
                                        .permission(AccessControlEntry.Permission.OWNER)
                                        .grantedTo("namespace")
                                        .resourcePatternType(AccessControlEntry.ResourcePatternType.PREFIXED)
                                        .resourceType(AccessControlEntry.ResourceType.CONNECT_CLUSTER)
                                        .resource("prefix.")
                                        .build())
                                .build()
                ));

        Optional<ConnectCluster> actual = connectClusterService.findByNamespaceAndNameOwner(namespace, "does-not-exist");

        Assertions.assertTrue(actual.isEmpty());
    }

    /**
     * Test creation
     */
    @Test
    void create() throws IOException, JOSEException {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix.connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        when(connectClusterRepository.create(connectCluster)).thenReturn(connectCluster);

        ConnectCluster actual = connectClusterService.create(connectCluster);
        Assertions.assertEquals(actual, connectCluster);
    }


    /**
     * Test creation with encrypted credentials
     */
    @Test
    void createCredentialsEncrypted() throws IOException, JOSEException {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("prefix.connect-cluster")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .username("myUsername")
                        .password("myPassword")
                        .build())
                .build();

        when(connectClusterRepository.create(connectCluster)).thenReturn(connectCluster);
        when(securityConfig.getAes256EncryptionKey()).thenReturn("changeitchangeitchangeitchangeit");

        connectClusterService.create(connectCluster);
        Assertions.assertNotEquals("myPassword", connectCluster.getSpec().getPassword());
    }

    /**
     * Test validate connect cluster creation when Connect cluster is already defined in the
     * Ns4Kafka configuration
     */
    @Test
    void validateConnectClusterCreationAlreadyDefined() {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("test-connect")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        KafkaAsyncExecutorConfig kafka = new KafkaAsyncExecutorConfig("local");
        kafka.setConnects(Map.of("test-connect", new KafkaAsyncExecutorConfig.ConnectConfig()));
        when(kafkaAsyncExecutorConfigList.stream()).thenReturn(Stream.of(kafka));
        when(httpClient.exchange(any(MutableHttpRequest.class)))
                .thenReturn(Flowable.just(HttpResponse.ok()));

        List<String> errors = connectClusterService.validateConnectClusterCreation(connectCluster);

        Assertions.assertEquals(1L, errors.size());
        Assertions.assertEquals("A Connect cluster is already defined globally with the name test-connect. Please provide a different name.", errors.get(0));
    }

    /**
     * Test validate connect cluster creation when Connect cluster is down
     */
    @Test
    void validateConnectClusterCreationDown() {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("test-connect")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .username("username")
                        .password("password")
                        .build())
                .build();

        when(kafkaAsyncExecutorConfigList.stream()).thenReturn(Stream.of());
        when(httpClient.exchange(any(MutableHttpRequest.class))).thenReturn(Flowable.just(HttpResponse.serverError()));

        List<String> errors = connectClusterService.validateConnectClusterCreation(connectCluster);

        Assertions.assertEquals(1L, errors.size());
        Assertions.assertEquals("The Connect cluster test-connect is not healthy (HTTP code 500).", errors.get(0));
    }

    /**
     * Test validate connect cluster creation malformed URL
     */
    @Test
    void validateConnectClusterCreationMalformedUrl() {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("test-connect")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("malformed-url")
                        .build())
                .build();

        when(kafkaAsyncExecutorConfigList.stream()).thenReturn(Stream.of());

        List<String> errors = connectClusterService.validateConnectClusterCreation(connectCluster);

        Assertions.assertEquals(1L, errors.size());
        Assertions.assertEquals("The Connect cluster test-connect has a malformed URL \"malformed-url\".", errors.get(0));
    }

    /**
     * Test validate connect cluster creation throws http client exception
     */
    @Test
    void validateConnectClusterCreationHttpClientException() {
        ConnectCluster connectCluster = ConnectCluster.builder()
                .metadata(ObjectMeta.builder().name("test-connect")
                        .build())
                .spec(ConnectCluster.ConnectClusterSpec.builder()
                        .url("https://after")
                        .build())
                .build();

        when(kafkaAsyncExecutorConfigList.stream()).thenReturn(Stream.of());
        when(httpClient.exchange(any(MutableHttpRequest.class)))
                .thenThrow(new HttpClientException("Error"));

        List<String> errors = connectClusterService.validateConnectClusterCreation(connectCluster);

        Assertions.assertEquals(1L, errors.size());
        Assertions.assertEquals("The following error occurred trying to check the Connect cluster test-connect health: Error.", errors.get(0));
    }
}
