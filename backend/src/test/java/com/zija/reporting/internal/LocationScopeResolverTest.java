package com.zija.reporting.internal;

import com.zija.location.LocationApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationScopeResolverTest {

    private LocationApi locationApi;
    private LocationScopeResolver resolver;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        locationApi = mock(LocationApi.class);
        resolver = new LocationScopeResolver(locationApi);
    }

    private LocationApi.LocationNode node(UUID id, UUID parentId, List<LocationApi.LocationNode> children) {
        return new LocationApi.LocationNode(id, parentId, "name", 0, false, 0, children);
    }

    @Test
    void leafWithoutChildrenExpandsToItselfOnly() {
        UUID leaf = UUID.randomUUID();
        when(locationApi.tree(householdId)).thenReturn(new LocationApi.LocationTree(List.of(node(leaf, null, List.of()))));

        var result = resolver.expandWithDescendants(householdId, leaf);

        assertThat(result).containsExactly(leaf);
    }

    @Test
    void parentExpandsToSelfAndAllDescendants() {
        UUID root = UUID.randomUUID();
        UUID fridge = UUID.randomUUID();
        UUID cupboard = UUID.randomUUID();
        UUID drawer = UUID.randomUUID();
        when(locationApi.tree(householdId)).thenReturn(new LocationApi.LocationTree(List.of(
                node(root, null, List.of(
                        node(fridge, root, List.of(
                                node(drawer, fridge, List.of()))),
                        node(cupboard, root, List.of()))))));

        var result = resolver.expandWithDescendants(householdId, root);

        assertThat(result).containsExactlyInAnyOrder(root, fridge, cupboard, drawer);
    }

    @Test
    void unknownLocationFallsBackToItself() {
        UUID unknown = UUID.randomUUID();
        when(locationApi.tree(householdId)).thenReturn(new LocationApi.LocationTree(List.of()));

        var result = resolver.expandWithDescendants(householdId, unknown);

        assertThat(result).containsExactly(unknown);
    }
}
