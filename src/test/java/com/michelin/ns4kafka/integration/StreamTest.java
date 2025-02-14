package com.michelin.ns4kafka.integration;

import com.michelin.ns4kafka.integration.TopicTest.BearerAccessRefreshToken;
import com.michelin.ns4kafka.models.AccessControlEntry;
import com.michelin.ns4kafka.models.AccessControlEntry.AccessControlEntrySpec;
import com.michelin.ns4kafka.models.AccessControlEntry.Permission;
import com.michelin.ns4kafka.models.AccessControlEntry.ResourcePatternType;
import com.michelin.ns4kafka.models.AccessControlEntry.ResourceType;
import com.michelin.ns4kafka.models.KafkaStream;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.Namespace.NamespaceSpec;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.services.executors.AccessControlEntryAsyncExecutor;
import com.michelin.ns4kafka.validation.TopicValidator;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.rxjava3.http.client.Rx3HttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

@MicronautTest
@Property(name = "micronaut.security.gitlab.enabled", value = "false")
class StreamTest extends AbstractIntegrationTest {
    @Inject
    @Client("/")
    Rx3HttpClient client;

    @Inject
    List<AccessControlEntryAsyncExecutor> aceAsyncExecutorList;

    private String token;

    @BeforeAll
    void init() {
        Namespace ns1 = Namespace.builder()
            .metadata(ObjectMeta.builder()
                      .name("nskafkastream")
                      .cluster("test-cluster")
                      .build())
            .spec(NamespaceSpec.builder()
                  .kafkaUser("user1")
                  .connectClusters(List.of("test-connect"))
                  .topicValidator(TopicValidator.makeDefaultOneBroker())
                  .build())
            .build();

        AccessControlEntry acl1 = AccessControlEntry.builder()
            .metadata(ObjectMeta.builder()
                      .name("nskafkastream-acl-topic")
                      .build())
            .spec(AccessControlEntrySpec.builder()
                  .resourceType(ResourceType.TOPIC)
                  .resource("kstream-")
                  .resourcePatternType(ResourcePatternType.PREFIXED)
                  .permission(Permission.OWNER)
                  .grantedTo("nskafkastream")
                  .build())
            .build();

        AccessControlEntry acl2 = AccessControlEntry.builder()
                .metadata(ObjectMeta.builder()
                        .name("nskafkastream-acl-group")
                        .build())
                .spec(AccessControlEntrySpec.builder()
                        .resourceType(ResourceType.GROUP)
                        .resource("kstream-")
                        .resourcePatternType(ResourcePatternType.PREFIXED)
                        .permission(Permission.OWNER)
                        .grantedTo("nskafkastream")
                        .build())
                .build();


        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("admin","admin");
        HttpResponse<BearerAccessRefreshToken> response = client.exchange(HttpRequest.POST("/login", credentials), BearerAccessRefreshToken.class).blockingFirst();

        token = response.getBody().get().getAccessToken();

        client.exchange(HttpRequest.create(HttpMethod.POST, "/api/namespaces").bearerAuth(token).body(ns1)).blockingFirst();
        client.exchange(HttpRequest.create(HttpMethod.POST,"/api/namespaces/nskafkastream/acls").bearerAuth(token).body(acl1)).blockingFirst();
        client.exchange(HttpRequest.create(HttpMethod.POST,"/api/namespaces/nskafkastream/acls").bearerAuth(token).body(acl2)).blockingFirst();
    }

    @Test
    void verifyCreationOfAcl() throws InterruptedException, ExecutionException {

        KafkaStream stream = KafkaStream.builder()
                .metadata(ObjectMeta.builder()
                        .name("kstream-test")
                        .build())
                .build();
        client.exchange(HttpRequest.create(HttpMethod.POST,"/api/namespaces/nskafkastream/streams")
                        .bearerAuth(token)
                        .body(stream))
                        .blockingFirst();
        //force ACL Sync
        aceAsyncExecutorList.forEach(AccessControlEntryAsyncExecutor::run);
        Admin kafkaClient = getAdminClient();

        var aclTopic = kafkaClient.describeAcls(new AclBindingFilter(
                new ResourcePatternFilter(org.apache.kafka.common.resource.ResourceType.TOPIC,
                        stream.getMetadata().getName(),
                        PatternType.PREFIXED),
                AccessControlEntryFilter.ANY)).values().get();
        var aclTransactionalId = kafkaClient.describeAcls(new AclBindingFilter(
                new ResourcePatternFilter(org.apache.kafka.common.resource.ResourceType.TRANSACTIONAL_ID,
                        stream.getMetadata().getName(),
                        PatternType.PREFIXED),
                AccessControlEntryFilter.ANY)).values().get();

        Assertions.assertEquals(2, aclTopic.size());
        Assertions.assertTrue(aclTopic.stream()
                .allMatch(aclBinding -> List.of(AclOperation.CREATE, AclOperation.DELETE).contains(aclBinding.entry().operation())));

        Assertions.assertEquals(1, aclTransactionalId.size());
        Assertions.assertEquals(AclOperation.WRITE, aclTransactionalId.stream().findFirst().get().entry().operation());
    }
}
