package com.michelin.ns4kafka.controllers;

import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.models.RoleBinding;
import com.michelin.ns4kafka.security.ResourceBasedSecurityRule;
import com.michelin.ns4kafka.services.NamespaceService;
import com.michelin.ns4kafka.services.RoleBindingService;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleBindingControllerTest {
    @Mock
    NamespaceService namespaceService;

    @Mock
    RoleBindingService roleBindingService;

    @Mock
    ApplicationEventPublisher<?> applicationEventPublisher;

    @Mock
    SecurityService securityService;

    @InjectMocks
    RoleBindingController roleBindingController;

    @Test
    void applySuccess() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        RoleBinding rolebinding = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .build())
                .build();

        when(namespaceService.findByName(any())).thenReturn(Optional.of(ns));
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        var response = roleBindingController.apply("test", rolebinding, false);
        RoleBinding actual = response.body();
        Assertions.assertEquals("created", response.header("X-Ns4kafka-Result"));
        assertEquals(actual.getMetadata().getName(), rolebinding.getMetadata().getName());
    }

    @Test
    void applySuccess_AlreadyExists() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        RoleBinding rolebinding = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .build())
                .build();

        when(namespaceService.findByName(any())).thenReturn(Optional.of(ns));
        when(roleBindingService.findByName("test","test.rolebinding"))
                .thenReturn(Optional.of(rolebinding));

        var response = roleBindingController.apply("test", rolebinding, false);
        RoleBinding actual = response.body();
        Assertions.assertEquals("unchanged", response.header("X-Ns4kafka-Result"));
        assertEquals(actual.getMetadata().getName(), rolebinding.getMetadata().getName());
        verify(roleBindingService,never()).create(ArgumentMatchers.any());
    }

    @Test
    void applySuccess_Changed() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        RoleBinding rolebinding = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .build())
                .build();
        RoleBinding rolebindingOld = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .labels(Map.of("old", "label"))
                        .build())
                .build();

        when(namespaceService.findByName(any())).thenReturn(Optional.of(ns));
        when(roleBindingService.findByName("test","test.rolebinding"))
                .thenReturn(Optional.of(rolebindingOld));
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        var response = roleBindingController.apply("test", rolebinding, false);
        RoleBinding actual = response.body();
        Assertions.assertEquals("changed", response.header("X-Ns4kafka-Result"));
        assertEquals(actual.getMetadata().getName(), rolebinding.getMetadata().getName());
    }

    @Test
    void createDryRun() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        RoleBinding rolebinding = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .build())
                .build();

        when(namespaceService.findByName(any())).thenReturn(Optional.of(ns));

        var response = roleBindingController.apply("test", rolebinding, true);
        RoleBinding actual = response.body();
        Assertions.assertEquals("created", response.header("X-Ns4kafka-Result"));
        verify(roleBindingService, never()).create(rolebinding);
    }

    @Test
    void deleteSucess() {

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        RoleBinding rolebinding = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .build())
                .build();

        //when(namespaceService.findByName(any())).thenReturn(Optional.of(ns));
        when(roleBindingService.findByName(any(), any())).thenReturn(Optional.of(rolebinding));
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        Assertions.assertDoesNotThrow(
                () -> roleBindingController.delete("test", "test.rolebinding", false)
        );
    }

    @Test
    void deleteSucessDryRun() {

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        RoleBinding rolebinding = RoleBinding.builder()
                .metadata(ObjectMeta.builder()
                        .name("test.rolebinding")
                        .build())
                .build();

        //when(namespaceService.findByName(any())).thenReturn(Optional.of(ns));
        when(roleBindingService.findByName(any(), any())).thenReturn(Optional.of(rolebinding));

        roleBindingController.delete("test", "test.rolebinding", true);
        verify(roleBindingService, never()).delete(any());
    }
}
