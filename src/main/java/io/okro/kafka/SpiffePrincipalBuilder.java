package io.okro.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.message.DefaultPrincipalData;
import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.protocol.MessageUtil;
import org.apache.kafka.common.security.auth.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SpiffePrincipalBuilder implements KafkaPrincipalBuilder, KafkaPrincipalSerde {
    private static final Logger LOG = LoggerFactory.getLogger(SpiffePrincipalBuilder.class);

    private static final String SPIFFE_TYPE = "SPIFFE";

    public static final String KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_ENV = "KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE";
    public static final String KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY = "kafka.spiffe.principal.istio.mode";
    public static final String KAFKA_SPIFFE_PRINCIPAL_PREFIX_ENV = "KAFKA_SPIFFE_PRINCIPAL_PREFIX";
    public static final String KAFKA_SPIFFE_PRINCIPAL_PREFIX_PROPERTY = "kafka.spiffe.principal.prefix";

    public KafkaPrincipal build(AuthenticationContext context) {
        if (!(context instanceof SslAuthenticationContext)) {
            LOG.trace("non-SSL connection coerced to ANONYMOUS");
            return KafkaPrincipal.ANONYMOUS;
        }

        SSLSession session = ((SslAuthenticationContext) context).session();
        X509Certificate cert = firstX509(session);
        if (cert == null) {
            LOG.trace("first peer certificate missing / not x509");
            return KafkaPrincipal.ANONYMOUS;
        }

        String spiffeId = spiffeId(cert);
        if (spiffeId == null) {
            LOG.info("SpiffePrincipal: there was no spiffeId, falling back to the subject principal");
            return new KafkaPrincipal(KafkaPrincipal.USER_TYPE, cert.getSubjectX500Principal().getName());
        }

        LOG.info(String.format("SpiffePrincipal: fetched spiffeId: %s", spiffeId));

        // return new KafkaPrincipal(SPIFFE_TYPE, spiffeId);
        return new KafkaPrincipal(KafkaPrincipal.USER_TYPE, spiffeId);
    }

    private @Nullable X509Certificate firstX509(SSLSession session) {
        try {
            Certificate[] peerCerts = session.getPeerCertificates();
            if (peerCerts.length == 0) {
                return null;
            }
            Certificate first = peerCerts[0];
            if (!(first instanceof X509Certificate)) {
                return null;
            }
            return (X509Certificate) first;
        } catch (SSLPeerUnverifiedException e) {
            LOG.warn("failed to extract certificate", e);
            return null;
        }
    }

    private @Nullable String spiffeId(X509Certificate cert) {
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                return null;
            }
            return loadSpiffeId(sans);
        } catch (CertificateParsingException e) {
            LOG.warn("failed to parse SAN", e);
            return null;
        }
    }

    final @Nullable String loadSpiffeId(Collection<List<?>> sans) {
        String istioMode = getIstioMode();
        if (istioMode != null) {
            return loadIstioModeSpiffeId(sans, istioMode);
        }
        return sans.stream()
                .map(san -> (String) san.get(1))
                .filter(uri -> uri.startsWith("spiffe://"))
                .findFirst()
                .orElse(null);
    }

    final @Nullable String loadIstioModeSpiffeId(Collection<List<?>> sans, String mode) {
        return sans.stream()
                .map(san -> (String) san.get(1))
                .filter(uri -> uri.startsWith("spiffe://"))
                .findFirst()
                .map(uri -> {
                    String parsed = parseIstioSpiffeURI(uri, mode);
                    if (parsed == null) {
                        return null;
                    }
                    return getPrefix() + parsed;
                })
                .orElse(null);
    }

    private @Nullable String getIstioMode() {
        String envMode = System.getenv().get(KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_ENV);
        if (envMode != null) {
            return envMode;
        }
        Object propMode = System.getProperties().get(KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE_PROPERTY);
        if (propMode != null) {
            return propMode.toString();
        }
        return null;
    }

    private String getPrefix() {
        String envPrefix = System.getenv().get(KAFKA_SPIFFE_PRINCIPAL_PREFIX_ENV);
        if (envPrefix != null) {
            return envPrefix;
        }
        Object propPrefix = System.getProperties().get(KAFKA_SPIFFE_PRINCIPAL_PREFIX_PROPERTY);
        if (propPrefix != null) {
            return propPrefix.toString();
        }
        return "";
    }

    private @Nullable String parseIstioSpiffeURI(String uri, String mode) {
        IstioIdentifier id = new IstioIdentifier();
        String[] parts = uri.replaceAll("spiffe://", "").split("/");
        if (parts.length % 2 == 0) {
            LOG.warn("istio spiffe URI number of components must be odd, got even");
            return null;
        }
        for (int i=1; i<parts.length; i=i+2) {
            if (parts[i].equals("ns")) {
                if (mode.equals("ns")) {
                    return parts[i+1];
                }
                id.setNs(parts[i+1]);
            }
            if (parts[i].equals("sa")) {
                if (mode.equals("sa")) {
                    return parts[i+1];
                }
                id.setSa(parts[i+1]);
            }
        }
        if (!mode.equals("ns+sa")) {
            LOG.warn("invalid istio mode, expected 'ns+sa', 'ns', or 'sa'");
            return null;
        }
        if (!id.isOkay()) {
            LOG.warn("istio identifier without namespace or account, expected spiffe://cluster.name/ns/namespace/sa/account");
            return null;
        }
        return id.toString();
    }

    // -- serde:

    @Override
    public byte[] serialize(KafkaPrincipal principal) {
        DefaultPrincipalData data = new DefaultPrincipalData()
                .setType(principal.getPrincipalType())
                .setName(principal.getName())
                .setTokenAuthenticated(principal.tokenAuthenticated());
        return MessageUtil.toVersionPrefixedBytes(DefaultPrincipalData.HIGHEST_SUPPORTED_VERSION, data);
    }

    @Override
    public KafkaPrincipal deserialize(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        short version = buffer.getShort();
        if (version < DefaultPrincipalData.LOWEST_SUPPORTED_VERSION || version > DefaultPrincipalData.HIGHEST_SUPPORTED_VERSION) {
            throw new SerializationException("Invalid principal data version " + version);
        }

        DefaultPrincipalData data = new DefaultPrincipalData(new ByteBufferAccessor(buffer), version);
        return new KafkaPrincipal(data.type(), data.name(), data.tokenAuthenticated());
    }
}