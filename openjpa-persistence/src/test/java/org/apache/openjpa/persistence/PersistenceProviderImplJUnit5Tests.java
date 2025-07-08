/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */



package org.apache.openjpa.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.spi.LoadState;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;

/**
 * Tests for PersistenceProviderImpl class.
 * Adapted from EvoSuite-generated tests.
 */
public class PersistenceProviderImplJUnit5Tests {

    private PersistenceProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new PersistenceProviderImpl();
    }

    @Test
    void testIsLoadedWithNullObject() {
        LoadState loadState = provider.isLoaded(null);
        assertEquals(LoadState.UNKNOWN, loadState);
    }

    @Test
    void testIsLoadedWithoutReferenceWithNullObject() {
        LoadState loadState = provider.isLoadedWithoutReference(null, "dynamic-agent");
        assertEquals(LoadState.UNKNOWN, loadState);
    }

    @Test
    void testIsLoadedWithReferenceWithNullAttribute() {
        Object object = new Object();
        LoadState loadState = provider.isLoadedWithReference(object, null);
        assertEquals(LoadState.UNKNOWN, loadState);
    }

    @Test
    void testGetProviderUtil() {
        ProviderUtil providerUtil = provider.getProviderUtil();
        assertSame(provider, providerUtil);
    }

    @Test
    void testGetDefaultBrokerAlias() {
        String alias = provider.getDefaultBrokerAlias();
        assertEquals("non-finalizing", alias);
    }

    @Test
    void testAcceptProviderWithEmptyMap() {
        Map<String, Object> map = new HashMap<>();
        boolean result = provider.acceptProvider(map);
        assertTrue(result);
    }

    @Test
    void testAcceptProviderWithNullMap() {
        assertThrows(NullPointerException.class, () -> {
            provider.acceptProvider(null);
        });
    }

    @Test
    void testSetPersistenceEnvironmentInfoWithNullConfig() {
        PersistenceUnitInfoImpl puInfo = new PersistenceUnitInfoImpl();
        provider.setPersistenceEnvironmentInfo(null, puInfo);
        assertNull(puInfo.getNonJtaDataSourceName());
    }
    
    @Test
    void testCreateEntityManagerFactoryWithNullMap() {
        assertDoesNotThrow(() -> provider.createEntityManagerFactory("test", null));
    }
    
    @Test
    void testCreateEntityManagerFactoryWithNullName() {
        Map<String, Object> props = new HashMap<>();
        assertDoesNotThrow(() -> provider.createEntityManagerFactory(null, props));
    }
}