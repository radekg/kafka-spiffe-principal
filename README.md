## Kafka SPIFFE Principal Builder

A custom `KafkaPrincipalBuilder` implementation for Apache Kafka.
This class and documentation deals only with `SslAuthenticationContext`, we do not support any other context at the moment (Kerberos, SASL, Oauth)

#### Default behavior
The default `DefaultKafkaPrincipalBuilder` class that comes with Apache Kafka builds a principal
name according to the x509 Subject in the SSL certificate. Since there is no logic that deals with *Subject Alternative Name*,
this approach cannot handle a *SPIFFE ID*.

#### New behavior
The principal builder first looks for any valid *SPIFFE ID* in the certificate, if found, the *KafkaPrincipal* that will
be returned would be seen by an *ACL Authorizer* as **SPIFFE:spiffe://some.spiffe.id.uri**. If that fails, a normal usage of the Subject will
used with a normal **USER:CN=...**

## Istio SPIFFE IDs

Istio SPIFFE IDs have the following format:

```
spiffe://cluster.dns/ns/namespace-name/sa/service-account-name
```

To configure this principal provider to extract identities from Istio, set the `KAFKA_SPIFFE_PRINCIPAL_ISTIO_MODE` environment variable or `kafka.spiffe.principal.istio.mode` system property. The mode can be one of:

- `ns`: extract namespace only,
- `sa`: extract service account only,
- `ns+sa`: extract namespace and service account, values joined with `-`.

Optionally, provide a string prefix for the extracted principal using `KAFKA_SPIFFE_PRINCIPAL_PREFIX` environment variable or `kafka.spiffe.principal.prefix` system property. For example, for ID `spiffe://cluster.dns/ns/namespace/sa/account`, with mode `ns+sa` and prefix `CN=`, the result will be `CN=namespace-account`.
