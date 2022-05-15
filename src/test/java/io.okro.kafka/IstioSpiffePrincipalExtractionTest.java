package io.okro.kafka;

import org.junit.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IstioSpiffePrincipalExtractionTest {

    @Test
    public void TestLoadValidIstioSpiffeId() {
        String expectedNamespace = "namespace";
        String expectedServiceAccount = "account";
        String expectedNsAndSa = String.format("%s-%s", expectedNamespace, expectedServiceAccount);
        String inputSpiffeId = String.format("spiffe://cluster.local/ns/%s/sa/%s",
                expectedNamespace, expectedServiceAccount);

        Vector<String> ids = new Vector<>();
        ids.add("URI");
        ids.add(inputSpiffeId);

        SpiffePrincipalBuilder builder = new SpiffePrincipalBuilder();

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "ns");
        assertEquals(expectedNamespace,
                builder.loadSpiffeId(Collections.singleton(ids)));

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "sa");
        assertEquals(expectedServiceAccount,
                builder.loadSpiffeId(Collections.singleton(ids)));

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "ns+sa");
        assertEquals(expectedNsAndSa,
                builder.loadSpiffeId(Collections.singleton(ids)));

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "unknown");
        assertNull(builder.loadSpiffeId(Collections.singleton(ids)));

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "ns+sa");
        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_PREFIX_PROPERTY, "CN=");
        assertEquals(String.format("CN=%s", expectedNsAndSa),
                builder.loadSpiffeId(Collections.singleton(ids)));

        System.clearProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY);
        System.clearProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_PREFIX_PROPERTY);

        // fall back to the default processing mode:
        assertEquals(inputSpiffeId,
                builder.loadSpiffeId(Collections.singleton(ids)));
    }

    @Test
    public void TestLoadInvalidValidIstioSpiffeId() {
        String inputSpiffeId = "spiffe://ns/namespace/sa/account";

        Vector<String> ids = new Vector<>();
        ids.add("URI");
        ids.add(inputSpiffeId);

        SpiffePrincipalBuilder builder = new SpiffePrincipalBuilder();

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "ns+sa");
        assertNull(builder.loadSpiffeId(Collections.singleton(ids)));

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "ns");
        assertNull(builder.loadSpiffeId(Collections.singleton(ids)));

        System.setProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY, "sa");
        assertNull(builder.loadSpiffeId(Collections.singleton(ids)));

        System.clearProperty(SpiffePrincipalBuilder.KAFKA_SPIFFE_PRINCIPAL_PREFIX_PROPERTY);
    }

}
