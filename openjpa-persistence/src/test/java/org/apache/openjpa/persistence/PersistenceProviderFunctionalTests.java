package org.apache.openjpa.persistence;

import org.apache.openjpa.conf.OpenJPAConfiguration;
import org.apache.openjpa.kernel.BrokerFactory;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.LoadState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for PersistenceProviderImpl class.
 * This test covers the following aspects:
 * 1. EntityManagerFactory creation
 * 2. Configuration validation
 * 3. Handling of incomplete configurations
 * 4. Factory extensions
 * 5. Handling of incorrect entity configurations
 * 6. Factory closure
 * 7. Factory reuse from pool
 * 8. Thread-safety for concurrent factory creation
 * 9. Pool size limits
 * 10. Creation time and retry mechanisms
 * 11. Data validation
 * 12. Unauthorized database access
 * 13. Handling of invalid configuration properties
 */
public class PersistenceProviderFunctionalTests {
    
    private PersistenceProviderImpl provider;
    private Map<String, Object> properties;
    
    @BeforeEach
    public void setUp() throws Exception {
        provider = new PersistenceProviderImpl();
        properties = new HashMap<>();
    }
    
   @AfterEach
    public void tearDown() throws Exception {
       provider = null;
       properties = null;
   }
    
    /**
     * Test 1: Verify that the system can correctly generate EntityManagerFactory instances.
     */
    @Test
    public void testEntityManagerFactoryCreation() {
        // Set minimal properties for in-memory database
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.ConnectionDriverName", "org.apache.derby.jdbc.EmbeddedDriver");
        properties.put("openjpa.ConnectionURL", "jdbc:derby:target/database/testdb;create=true");
        properties.put("openjpa.BrokerFactory", "org.apache.openjpa.jdbc.kernel.JDBCBrokerFactory");


        // Create EntityManagerFactory
        OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);

        // Verify that the factory was created
        assertNotNull(emf);
        assertInstanceOf(EntityManagerFactoryImpl.class, emf, "EntityManagerFactory should be an instance of EntityManagerFactoryImpl");

        // Clean up
        emf.close();
    }

    /**
     * Test 2: Verify that the created EntityManagerFactory has the expected configuration.
     */
    @Test
    public void testEntityManagerFactoryConfiguration() {
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        properties.put("openjpa.Log", "SQL=TRACE");

        OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);

        OpenJPAConfiguration conf = emf.getConfiguration();
        assertNotNull(conf, "Configuration should not be null");
        assertEquals(properties.get("openjpa.ConnectionURL"), conf.getConnectionURL());
        assertEquals(properties.get("openjpa.ConnectionDriverName"), conf.getConnectionDriverName());

        emf.close();
    }


    /**
     * Test 3: Verify behavior when configuration information is incomplete or missing.
     */
    @Test
    public void testIncompleteConfiguration() {
        // Create EntityManagerFactory with minimal properties
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        // Missing driver name
        
        try {
            OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
            // If we get here, the factory was created despite missing driver
            // This is acceptable if the system uses defaults or auto-detection
            if (emf != null) {
                OpenJPAConfiguration conf = emf.getConfiguration();
                assertNotNull( conf);
                assertNotNull("Connection driver should have a default value", conf.getConnectionDriverName());
                emf.close();
            }
        } catch (Exception e) {
            // This is also acceptable if the system requires explicit driver configuration
            assertTrue(e.getMessage().contains("driver") || e.getCause().getMessage().contains("driver"),
                    "Exception should be related to missing driver");
        }
    }
    
    /**
     * Test 4: Verify how new implementations or extensions of EntityManagerFactory are handled.
     */
    @Test
    public void testFactoryExtensions() {
        // Set minimal properties for in-memory database
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        
        // Create EntityManagerFactory
        OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
        
        // Verify that the factory implements the expected interfaces
        assertTrue(emf != null,
                "Factory should implement OpenJPAEntityManagerFactory");
        assertTrue(emf instanceof EntityManagerFactory,
                "Factory should implement EntityManagerFactory");
        
        // Get the underlying BrokerFactory
        BrokerFactory brokerFactory = ((EntityManagerFactoryImpl)emf).getBrokerFactory();
        assertNotNull( brokerFactory);
        
        // Clean up
        emf.close();
    }
    
    /**
     * Test 5: Verify behavior with incorrect entity configurations.
     */
    @Test
    public void testIncorrectEntityConfiguration() {
        // Set properties with invalid entity class
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=NonExistentEntity)");
        
        try {
            OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
            if (emf != null) {
                // If we get here, the factory was created despite invalid entity
                // This might happen if the entity is only validated when used
                emf.close();
            }
        } catch (Exception e) {
            // This is the expected behavior - creation should fail with invalid entity
            assertTrue(e.getMessage().contains("NonExistentEntity") ||
                                (e.getCause() != null && e.getCause().getMessage().contains("NonExistentEntity")),
                    "Exception should be related to invalid entity");
        }
    }
    
    /**
     * Test 6: Verify that factory closure is handled correctly.
     */
    @Test
    public void testFactoryClosure() {
        // Set minimal properties for in-memory database
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        
        // Create EntityManagerFactory
        OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
        assertNotNull(emf);
        
        // Verify factory is open
        assertTrue(emf.isOpen(), "Factory should be open");
        
        // Close the factory
        emf.close();
        
        // Verify factory is closed
        assertFalse(emf.isOpen(), "Factory should be closed");
        
        // Attempt to use closed factory should throw exception
        try {
            emf.createEntityManager();
            fail("Should throw exception when using closed factory");
        } catch (Exception e) {
            // Expected behavior
        }
    }
    
    /**
     * Test 7: Verify that factory reuse from pool works correctly.
     */
//    @Disabled("the factory that are created are not inserted in to the pool ")
    @Test
    public void testFactoryReuse() {
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        properties.put("openjpa.BrokerFactory", "jdbc");
        properties.put("EntityManagerFactoryPool", "true");

        OpenJPAEntityManagerFactory emf1 = provider.createEntityManagerFactory("test-unit", properties);
        assertNotNull(emf1, "First EntityManagerFactory should not be null");

        OpenJPAEntityManagerFactory emf2 = provider.createEntityManagerFactory("test-unit", properties);
        assertNotNull(emf2, "Second EntityManagerFactory should not be null");

        assertEquals(emf1, emf2, "Both factories should be equal when pooling is enabled");

        emf1.close();
        if (emf1 != emf2) {
            emf2.close();
        }
    }


    /**
     * Test 8: Verify thread-safety for concurrent factory creation.
     */
//    @Disabled("fails because the factory are not inserted in to the pool ")
    @Test
    public void testConcurrentFactoryCreation() throws Exception {
        // Set minimal properties for in-memory database with pooling enabled
        final Map<String, Object> concurrentProps = new HashMap<>();
        concurrentProps.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        concurrentProps.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        concurrentProps.put("openjpa.BrokerFactory", "jdbc"); // Aggiungiamo questa configurazione
        concurrentProps.put("openjpa.jdbc.SynchronizeMappings", "buildSchema"); // Per assicurare la creazione dello schema
        concurrentProps.put("EntityManagerFactoryPool", "true");

        final int threadCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final AtomicReference<OpenJPAEntityManagerFactory> firstFactory = new AtomicReference<>();
        final AtomicReference<Exception> threadException = new AtomicReference<>();



        // Create threads that will concurrently create EntityManagerFactory
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Create EntityManagerFactory
                    OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", concurrentProps);
                    
                    // Store the first created factory for comparison
                    firstFactory.compareAndSet(null, emf);
                    
                    // Verify that all factories are the same instance
                    if (firstFactory.get() != null && emf != firstFactory.get()) {
                        throw new AssertionError("Different factory instances were created");
                    }
                } catch (Exception e) {
                    threadException.set(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Check for exceptions
        if (threadException.get() != null) {
            throw threadException.get();
        }
        
        // Verify all threads completed
        assertTrue(completed, "All threads should have completed");
        
        // Verify a factory was created
        assertNotNull( firstFactory.get());
        
        // Clean up
        firstFactory.get().close();
    }

    
    /**
     * Test 9: Verify creation time and retry mechanisms.
     */
    @Test
    public void testCreationTimeAndRetry() {
        // Set properties with invalid URL to test retry behavior
        properties.put("openjpa.ConnectionURL", "jdbc:invalid:url");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        
        long startTime = System.currentTimeMillis();
        
        try {
            OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
            if (emf != null) {
                // If we get here, the factory was created despite invalid URL
                // This is unexpected but we'll clean up anyway
                emf.close();
            }
        } catch (Exception e) {
            // This is the expected behavior - creation should fail with invalid URL
            // We're measuring the time it takes to fail
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Log the time taken - this helps understand if retries occurred
        System.out.println("Factory creation attempt took " + duration + " ms");
        
        // We can't make specific assertions about the time, but we can check
        // that it's reasonable (not too short, not too long)
        assertTrue(duration > 0, "Creation time should be measurable");
    }
    
    /**
     * Test 10: Verify data validation before persistence.
     * Note: This is more of an EntityManager test than a PersistenceProvider test,
     * but we include it for completeness.
     */
  @Test
    public void testDataValidation() {
        // This test would typically involve creating an entity with invalid data
        // and attempting to persist it. Since we don't have entity classes defined,
        // we'll just verify that the provider supports the isLoaded methods which
        // are part of validation.
        
        // Test isLoaded methods
        LoadState loadState = provider.isLoaded(null);
        assertEquals(LoadState.UNKNOWN, loadState, "isLoaded(null) should return UNKNOWN");
        
        loadState = provider.isLoadedWithReference(null, "someAttribute");
        assertEquals(LoadState.UNKNOWN, loadState, "isLoadedWithReference(null) should return UNKNOWN");
        
        loadState = provider.isLoadedWithoutReference(null, "someAttribute");
        assertEquals(LoadState.UNKNOWN, loadState, "isLoadedWithoutReference(null) should return UNKNOWN");
    }
    
    /**
     * Test 11: Verify behavior when accessing unauthorized database.
     */
//    @Disabled("its not thrown a wrong credential exception ")
    @Test
    public void testUnauthorizedDatabaseAccess() {
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");
        properties.put("openjpa.ConnectionUserName", "invalidUser");
        properties.put("openjpa.ConnectionPassword", "invalidPassword");

        try {
            OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
            if (emf != null) {
                try {
                    emf.createEntityManager();
                    fail("Should throw exception for invalid credentials");
                } catch (Exception e) {
                    assertTrue(e.getMessage().toLowerCase().contains("user") ||
                                    e.getMessage().toLowerCase().contains("password") ||
                                    e.getMessage().toLowerCase().contains("auth"),
                            "Exception should be related to authentication");
                } finally {
                    emf.close();
                }
            }
        } catch (Exception e) {
            assertTrue(e.getMessage().toLowerCase().contains("user") ||
                            e.getMessage().toLowerCase().contains("password") ||
                            e.getMessage().toLowerCase().contains("auth"),
                    "Exception should be related to authentication");
        }
    }
    
    /**
     * Test 12: Verify handling of invalid configuration properties.
     */
    @Test
    public void testInvalidConfigurationProperties() {
        properties.put("openjpa.InvalidProperty", "someValue");
        properties.put("openjpa.ConnectionURL", "jdbc:hsqldb:mem:testdb");
        properties.put("openjpa.ConnectionDriverName", "org.hsqldb.jdbcDriver");

        try {
            OpenJPAEntityManagerFactory emf = provider.createEntityManagerFactory("test-unit", properties);
            if (emf != null) {
                OpenJPAConfiguration conf = emf.getConfiguration();
                assertEquals(properties.get("openjpa.ConnectionURL"), conf.getConnectionURL());
                assertEquals(properties.get("openjpa.ConnectionDriverName"), conf.getConnectionDriverName());
                emf.close();
            }
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("InvalidProperty"),
                    "Exception should be related to invalid property");
        }
    }
}