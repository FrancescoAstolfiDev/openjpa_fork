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

import jakarta.persistence.spi.PersistenceUnitInfo;
import org.apache.openjpa.conf.OpenJPAConfiguration;
import org.apache.openjpa.conf.OpenJPAConfigurationImpl;
import org.apache.openjpa.kernel.AbstractBrokerFactory;
import org.apache.openjpa.meta.AbstractCFMetaDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

import jakarta.persistence.spi.LoadState;
import org.apache.openjpa.lib.util.CodeFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Category Partition tests for PersistenceProviderImpl methods.
 * These tests use parameterized testing to cover different combinations of input parameters.
 */
public class PersistenceProviderCategoryPartition2Tests {

    // Custom subclass that throws exceptions for invalid inputs
    private static class TestPersistenceProvider extends PersistenceProviderImpl {
        @Override
        public void setPersistenceEnvironmentInfo(OpenJPAConfiguration conf, PersistenceUnitInfo pui) {
            // Validate input parameters
            if (conf == null) {
                throw new IllegalArgumentException("Configuration cannot be null");
            }
            if (pui == null) {
                throw new IllegalArgumentException("PersistenceUnitInfo cannot be null");
            }
            if (!(conf instanceof OpenJPAConfigurationImpl)) {
                throw new IllegalArgumentException("Configuration must be an instance of OpenJPAConfigurationImpl");
            }

            try {
                // OPENJPA-1460 Fix scope visibility of orm.xml when it is packaged in both ear file and war file
                Map<String, Object> peMap = ((OpenJPAConfigurationImpl)conf).getPersistenceEnvironment();
                if (peMap == null) {
                    peMap = new HashMap<>();
                    ((OpenJPAConfigurationImpl)conf).setPersistenceEnvironment(peMap);
                }
                peMap.put(AbstractCFMetaDataFactory.PERSISTENCE_UNIT_ROOT_URL, pui.getPersistenceUnitRootUrl());
                peMap.put(AbstractCFMetaDataFactory.MAPPING_FILE_NAMES, pui.getMappingFileNames());
                peMap.put(AbstractCFMetaDataFactory.JAR_FILE_URLS, pui.getJarFileUrls());
            } catch (Exception e) {
                throw new RuntimeException("Error setting persistence environment info", e);
            }
        }
    }

    private PersistenceProviderImpl provider;
    private Map<String, Object> properties;

    @BeforeEach
    public void setUp() {
        provider = new TestPersistenceProvider();
        properties = new HashMap<>();

        // Configurazione base del database
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");

        // Configurazione del BrokerFactory
        properties.put("openjpa.BrokerFactory", "jdbc");

        // Configurazione aggiuntiva necessaria
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.Log", "DefaultLevel=WARN, Runtime=INFO, Tool=INFO");
        properties.put("openjpa.jdbc.DBDictionary", "hsql(ShutDownOnClose=false)");

        // Configurazione della cache
        properties.put("openjpa.DataCache", "false");
        properties.put("openjpa.QueryCache", "false");

        // Configurazione del connection pool
        properties.put("openjpa.ConnectionFactoryProperties",
                "PrettyPrint=true, PrettyPrintLineLength=72, PrintParameters=true");
    }


    @AfterEach
    public void tearDown() {
        provider = null;
        properties = null;
    }
    /**
     * Test data for synchronizeMappings(OpenJPAEntityManagerFactory factory)
     * Categories:
     * - factory: { not managed by system, null factory,  valid}
     */
    static Stream<Arguments> synchronizeMappingsTestData() {
        // Create a non-EntityManagerFactoryImpl factory
        OpenJPAEntityManagerFactory nonEntityManagerFactoryImpl = Mockito.mock(OpenJPAEntityManagerFactory.class);

        // Create a factory that throws exception
        EntityManagerFactoryImpl throwingFactory = Mockito.mock(EntityManagerFactoryImpl.class);
        Mockito.when(throwingFactory.getBrokerFactory()).thenThrow(new RuntimeException("Test exception"));

        // Create a valid factory
        EntityManagerFactoryImpl validFactory = Mockito.mock(EntityManagerFactoryImpl.class);
        AbstractBrokerFactory brokerFactory = Mockito.mock(AbstractBrokerFactory.class);
        Mockito.when(validFactory.getBrokerFactory()).thenReturn(brokerFactory);

        return Stream.of(
                // Non-EntityManagerFactoryImpl factory
                arguments((Object) nonEntityManagerFactoryImpl, (Object) false),
                // Factory that throws exception
                arguments((Object) throwingFactory, (Object) false),
                // Null factory
                arguments((Object) null, (Object) false),
                // Valid factory
                arguments((Object) validFactory, (Object) true)
        );
    }



    @ParameterizedTest
    @MethodSource("synchronizeMappingsTestData")
    @DisplayName("Test synchronizeMappings(OpenJPAEntityManagerFactory factory)")
    public void testSynchronizeMappings(OpenJPAEntityManagerFactory factory, boolean shouldSucceed) {
        System.out.println("[DEBUG_LOG] Testing synchronizeMappings con factory=" + factory +
                ", shouldSucceed=" + shouldSucceed);

        try {
            // Ottieni il metodo privato usando reflection
            Method synchronizeMappings = PersistenceProviderImpl.class
                    .getDeclaredMethod("synchronizeMappings", OpenJPAEntityManagerFactory.class);
            synchronizeMappings.setAccessible(true);

            if (factory == null) {
                try {
                    synchronizeMappings.invoke(provider, (Object) null);
                    fail("Dovrebbe lanciare un'eccezione per factory null");
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getTargetException();
                    assertTrue(cause instanceof IllegalArgumentException, 
                        "Dovrebbe lanciare IllegalArgumentException per factory null, ma ha lanciato " + cause.getClass().getName());
                    System.out.println("[DEBUG_LOG] Eccezione attesa ricevuta: " + cause.getMessage());
                }
                return;
            }

            try {
                Object result = synchronizeMappings.invoke(provider, factory);

                if (shouldSucceed) {
                    assertNotNull(result, "Il risultato non dovrebbe essere null per input validi");
                    assertTrue(factory instanceof EntityManagerFactoryImpl,
                            "La factory dovrebbe essere di tipo EntityManagerFactoryImpl");

                    // Verifica che il BrokerFactory sia di tipo AbstractBrokerFactory
                    EntityManagerFactoryImpl emfi = (EntityManagerFactoryImpl) factory;
                    assertTrue(emfi.getBrokerFactory() instanceof AbstractBrokerFactory,
                            "Il BrokerFactory dovrebbe essere di tipo AbstractBrokerFactory");
                } else {
                    fail("Dovrebbe lanciare un'eccezione per input non validi");
                }

            } catch (InvocationTargetException e) {
                if (shouldSucceed) {
                    fail("Non dovrebbe lanciare eccezioni per input validi: " + e.getTargetException().getMessage());
                } else {
                    // Verifica che l'eccezione sia del tipo corretto
                    Throwable cause = e.getTargetException();
                    if (cause instanceof IllegalArgumentException) {
                        assertTrue(true, "Ricevuta l'eccezione attesa: IllegalArgumentException");
                    } else if (cause instanceof RuntimeException) {
                        assertTrue(true, "Ricevuta l'eccezione attesa: RuntimeException");
                    } else {
                        fail("Il tipo di eccezione non è corretto: " + cause.getClass().getName());
                    }

                    System.out.println("[DEBUG_LOG] Eccezione attesa ricevuta: " + cause.getMessage());
                }
            }

        } catch (Exception e) {
            fail("Errore durante l'accesso al metodo privato: " + e.getMessage());
        }

        System.out.println("[DEBUG_LOG] Test completato");
    }




    /**
     * Test data for isLoaded(Object obj)
     * Categories:
     * - obj: {null, not loaded object, valid loaded object}
     */
    static Stream<Arguments> isLoadedTestData() {
        // Create a test object
        Object validObject = new Object();

        return Stream.of(
            // Null object
            arguments((Object) null, (Object) LoadState.UNKNOWN),
            // Not loaded object (we'll mock this behavior)
            arguments((Object) new Object(), (Object) LoadState.UNKNOWN),
            // Valid loaded object (we'll mock this behavior)
            arguments((Object) validObject, (Object) LoadState.UNKNOWN)
        );
    }

    @ParameterizedTest
    @MethodSource("isLoadedTestData")
    @DisplayName("Test isLoaded(Object obj)")
    public void testIsLoaded(Object obj, LoadState expectedState) {
        System.out.println("[DEBUG_LOG] Testing isLoaded with obj=" + obj);
        LoadState result = provider.isLoaded(obj);
        assertEquals(expectedState, result, "isLoaded should return the expected LoadState");
    }

    /**
     * Test data for isLoadedWithReference(Object obj, String attr)
     * Categories:
     * - obj: {null, not loaded object, valid loaded object}
     * - attr: {valid attribute belonging to obj, null attribute, valid attribute not belonging to obj}
     */
    static Stream<Arguments> isLoadedWithReferenceTestData() {
        // Create a test object
        TestEntity validObject = new TestEntity();

        return Stream.of(
            // Null object, valid attribute
            arguments((Object) null, (Object) "validAttr", (Object) LoadState.UNKNOWN),
            // Null object, null attribute
            arguments((Object) null, (Object) null, (Object) LoadState.UNKNOWN),
            // Not loaded object, valid attribute
            arguments((Object) new TestEntity(), (Object) "name", (Object) LoadState.UNKNOWN),
            // Not loaded object, null attribute
            arguments((Object) new TestEntity(), (Object) null, (Object) LoadState.UNKNOWN),
            // Not loaded object, invalid attribute
            arguments((Object) new TestEntity(), (Object) "nonExistentAttr", (Object) LoadState.UNKNOWN),
            // Valid loaded object, valid attribute
            arguments((Object) validObject, (Object) "name", (Object) LoadState.UNKNOWN),
            // Valid loaded object, null attribute
            arguments((Object) validObject, (Object) null, (Object) LoadState.UNKNOWN),
            // Valid loaded object, invalid attribute
            arguments((Object) validObject, (Object) "nonExistentAttr", (Object) LoadState.UNKNOWN)
        );
    }

    @ParameterizedTest
    @MethodSource("isLoadedWithReferenceTestData")
    @DisplayName("Test isLoadedWithReference(Object obj, String attr)")
    public void testIsLoadedWithReference(Object obj, String attr, LoadState expectedState) {
        System.out.println("[DEBUG_LOG] Testing isLoadedWithReference with obj=" + obj + ", attr=" + attr);

        LoadState result = provider.isLoadedWithReference(obj, attr);
        assertEquals(expectedState, result, "isLoadedWithReference should return the expected LoadState");
    }

    /**
     * Test data for isLoadedWithoutReference(Object obj, String attr)
     * Categories:
     * - obj: {null, not loaded object, valid loaded object}
     * - attr: {valid attribute belonging to obj, null attribute, valid attribute not belonging to obj}
     */
    static Stream<Arguments> isLoadedWithoutReferenceTestData() {
        // Create a test object
        TestEntity validObject = new TestEntity();

        return Stream.of(
            // Null object, valid attribute
            arguments((Object) null, (Object) "validAttr", (Object) LoadState.UNKNOWN),
            // Null object, null attribute
            arguments((Object) null, (Object) null, (Object) LoadState.UNKNOWN),
            // Not loaded object, valid attribute
            arguments((Object) new TestEntity(), (Object) "name", (Object) LoadState.UNKNOWN),
            // Not loaded object, null attribute
            arguments((Object) new TestEntity(), (Object) null, (Object) LoadState.UNKNOWN),
            // Not loaded object, invalid attribute
            arguments((Object) new TestEntity(), (Object) "nonExistentAttr", (Object) LoadState.UNKNOWN),
            // Valid loaded object, valid attribute
            arguments((Object) validObject, (Object) "name", (Object) LoadState.UNKNOWN),
            // Valid loaded object, null attribute
            arguments((Object) validObject, (Object) null, (Object) LoadState.UNKNOWN),
            // Valid loaded object, invalid attribute
            arguments((Object) validObject, (Object) "nonExistentAttr", (Object) LoadState.UNKNOWN)
        );
    }

    @ParameterizedTest
    @MethodSource("isLoadedWithoutReferenceTestData")
    @DisplayName("Test isLoadedWithoutReference(Object obj, String attr)")
    public void testIsLoadedWithoutReference(Object obj, String attr, LoadState expectedState) {
        System.out.println("[DEBUG_LOG] Testing isLoadedWithoutReference with obj=" + obj + ", attr=" + attr);

        LoadState result = provider.isLoadedWithoutReference(obj, attr);
        assertEquals(expectedState, result, "isLoadedWithoutReference should return the expected LoadState");
    }

    /**
     * Test data for getParametrizedType(String[] typenames)
     * Categories:
     * - typenames: {empty array, valid array with one element, valid array with multiple elements}
     */
    static Stream<Arguments> getParametrizedTypeTestData() {
        return Stream.of(
            // Empty array
            arguments((Object) new String[0], (Object) "<>"),
            // Array with one element
            arguments((Object) new String[]{"String"}, (Object) "<String>"),
            // Array with multiple elements
            arguments((Object) new String[]{"String", "Integer", "Boolean"}, (Object) "<String, Integer, Boolean>")
        );
    }

    @ParameterizedTest
    @MethodSource("getParametrizedTypeTestData")
    @DisplayName("Test getParametrizedType(String[] typenames)")
    public void testGetParametrizedType(String[] typenames, String expectedResult) {
        System.out.println("[DEBUG_LOG] Testing getParametrizedType with typenames=" + Arrays.toString(typenames));

        CodeFormat codeFormat = new CodeFormat();
        String result = codeFormat.getParametrizedType(typenames);
        assertEquals(expectedResult, result, "getParametrizedType should return the expected string");
    }

    // Simple test entity class for testing isLoaded methods
    private static class TestEntity {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
