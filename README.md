# Java Vault Connector 

[![Build Status](https://travis-ci.org/stklcode/jvaultconnector.svg?branch=master)](https://travis-ci.org/stklcode/jvaultconnector)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=de.stklcode.jvault%3Ajvault-connector&metric=alert_status)](https://sonarcloud.io/dashboard?id=de.stklcode.jvault%3Ajvault-connector)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/stklcode/jvaultconnector/blob/master/LICENSE.txt) 
[![Maven Central](https://img.shields.io/maven-central/v/de.stklcode.jvault/jvault-connector.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.stklcode.jvault%22%20AND%20a%3A%22jvault-connector%22)

![Logo](https://raw.githubusercontent.com/stklcode/jvaultconnector/master/assets/logo.png)

Java Vault Connector is a connector library for [Vault](https://www.vaultproject.io) by [Hashicorp](https://www.hashicorp.com) written in Java. The connector allows simple usage of Vault's secret store in own applications.

## Features:

* HTTP(S) backend connector
    *  Ability to provide or enforce custom CA certificate
    * Optional initialization from environment variables
* Authorization methods
    * Token
    * Username/Password
    * AppRole (register and authenticate)
    * AppID (register and authenticate) [_deprecated_]
* Tokens
    * Creation and lookup of tokens
    * TokenBuilder for speaking creation of complex configuraitons
* Secrets
    * Read secrets
    * Write secrets
    * List secrets
    * Delete secrets
    * Renew/revoke leases
    * Raw secret content or JSON decoding
    * SQL secret handling
    * KV v1 and v2 support
* Connector Factory with builder pattern
* Tested against Vault 1.2.3


## Maven Artifact
```xml
<dependency>
    <groupId>de.stklcode.jvault</groupId>
    <artifactId>jvault-connector</artifactId>
    <version>0.8.2</version>
</dependency>
```

## Usage Examples

### Initialization

```java
// Instantiate using builder pattern style factory (TLS enabled by default)
VaultConnector vault = VaultConnectorBuilder.http()
 .withHost("127.0.0.1")
 .withPort(8200)
 .withTLS()
 .build();

// Instantiate with custom SSL context
VaultConnector vault = VaultConnectorBuilder.http()
 .withHost("example.com")
 .withPort(8200)
 .withTrustedCA(Paths.get("/path/to/CA.pem"))
 .build();

// Initialization from environment variables 
VaultConnector vault = VaultConnectorBuilder.http()
 .fromEnv()
 .build();
```

### Authentication

```java
// Authenticate with token.
vault.authToken("01234567-89ab-cdef-0123-456789abcdef");

// Authenticate with username and password.
vault.authUserPass("username", "p4ssw0rd");

// Authenticate with AppRole (secret - 2nd argument - is optional).
vault.authAppRole("01234567-89ab-cdef-0123-456789abcdef", "fedcba98-7654-3210-fedc-ba9876543210");
```

### Secret read & write

```java
// Retrieve secret (prefix "secret/" assumed, use read() to read arbitrary paths)
String secret = vault.readSecret("some/secret/key").get("value", String.class);

// Complex secret.
Map<String, Object> secretData = vault.readSecret("another/secret/key").getData();

// Write simple secret.
vault.writeSecret("new/secret/key", "secret value");

// Write complex data to arbitraty path.
Map<String, Object> map = ...;
vault.write("any/path/to/write", map);

// Delete secret.
vault.delete("any/path/to/write");
```

### Token and role creation

```java
// Create token using TokenBuilder
Token token = Token.builder()
                   .withId("token id")
                   .withDisplayName("new test token")
                   .withPolicies("pol1", "pol2")
                   .build();
vault.createToken(token);

// Create AppRole credentials
vault.createAppRole("testrole", policyList);
AppRoleSecretResponse secret = vault.createAppRoleSecret("testrole");
```

## Links

[Project Page](http://jvault.stklcode.de)

[JavaDoc API](http://jvault.stklcode.de/apidocs/)

## License

The project is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
