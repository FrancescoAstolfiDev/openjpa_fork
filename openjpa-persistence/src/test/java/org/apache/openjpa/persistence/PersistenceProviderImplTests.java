package org.apache.openjpa.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
public class PersistenceProviderImplTests {

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
}
