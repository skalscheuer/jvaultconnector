/*
 * Copyright 2016-2021 Stefan Kalscheuer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.stklcode.jvault.connector;

import de.stklcode.jvault.connector.builder.VaultConnectorBuilder;
import de.stklcode.jvault.connector.exception.*;
import de.stklcode.jvault.connector.model.AppRole;
import de.stklcode.jvault.connector.model.AuthBackend;
import de.stklcode.jvault.connector.model.Token;
import de.stklcode.jvault.connector.model.TokenRole;
import de.stklcode.jvault.connector.model.response.*;
import de.stklcode.jvault.connector.test.Credentials;
import de.stklcode.jvault.connector.test.VaultConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonMap;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JUnit test for HTTP Vault connector.
 * This test requires Vault binary in executable Path as it instantiates a real Vault server on given test data.
 *
 * @author Stefan Kalscheuer
 * @since 0.1
 */
@Tag("online")
class HTTPVaultConnectorTest {
    private static String VAULT_VERSION = "1.7.2";  // the vault version this test is supposed to run against
    private static final String KEY1 = "E38bkCm0VhUvpdCKGQpcohhD9XmcHJ/2hreOSY019Lho";
    private static final String KEY2 = "O5OHwDleY3IiPdgw61cgHlhsrEm6tVJkrxhF6QAnILd1";
    private static final String KEY3 = "mw7Bm3nbt/UWa/juDjjL2EPQ04kiJ0saC5JEXwJvXYsB";
    private static final String TOKEN_ROOT = "30ug6wfy2wvlhhe5h7x0pbkx";
    private static final String USER_VALID = "validUser";
    private static final String PASS_VALID = "validPass";

    private Process vaultProcess;
    private VaultConnector connector;

    @BeforeAll
    public static void init() {
        // Override vault version if defined in sysenv.
        if (System.getenv("VAULT_VERSION") != null) {
            VAULT_VERSION = System.getenv("VAULT_VERSION");
            System.out.println("Vault version set to " + VAULT_VERSION);
        }
    }

    /**
     * Initialize Vault instance with generated configuration and provided file backend.
     * Requires "vault" binary to be in current user's executable path. Not using MLock, so no extended rights required.
     */
    @BeforeEach
    void setUp(TestInfo testInfo, @TempDir File tempDir) throws VaultConnectorException, IOException {
        /* Determine, if TLS is required */
        boolean isTls = testInfo.getTags().contains("tls");

        /* Initialize Vault */
        VaultConfiguration config = initializeVault(tempDir, isTls);
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /* Initialize connector */
        HTTPVaultConnectorBuilder builder = VaultConnectorBuilder.http()
                .withHost(config.getHost())
                .withPort(config.getPort())
                .withTLS(isTls);
        if (isTls) {
            builder.withTrustedCA(Paths.get(getClass().getResource("/tls/ca.pem").getPath()));
        }
        connector = builder.build();

        /* Unseal Vault and check result */
        SealResponse sealStatus = connector.unseal(KEY1);
        assumeTrue(sealStatus != null, "Seal status could not be determined after startup");
        assumeTrue(sealStatus.isSealed(), "Vault is not sealed after startup");
        sealStatus = connector.unseal(KEY2);
        assumeTrue(sealStatus != null, "Seal status could not be determined");
        assumeFalse(sealStatus.isSealed(), "Vault is not unsealed");
        assumeTrue(sealStatus.isInitialized(), "Vault is not initialized"); // Initialized flag of Vault 0.11.2 (#20).
    }

    @AfterEach
    void tearDown() {
        if (vaultProcess != null && vaultProcess.isAlive())
            vaultProcess.destroy();
    }

    @Nested
    @DisplayName("Read/Write Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ReadWriteTests {
        private static final String SECRET_PATH = "userstore";
        private static final String SECRET_KEY = "foo";
        private static final String SECRET_VALUE = "bar";
        private static final String SECRET_KEY_JSON = "json";
        private static final String SECRET_KEY_COMPLEX = "complex";

        /**
         * Test reading of secrets.
         */
        @Test
        @Order(10)
        @DisplayName("Read secrets")
        @SuppressWarnings("deprecation")
        void readSecretTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            /* Try to read path user has no permission to read */
            SecretResponse res = null;
            final String invalidPath = "invalid/path";

            VaultConnectorException e = assertThrows(
                    PermissionDeniedException.class,
                    () -> connector.readSecret(invalidPath),
                    "Invalid secret path should raise an exception"
            );

            /* Assert that the exception does not reveal secret or credentials */
            assertThat(stackTrace(e), not(stringContainsInOrder(invalidPath)));
            assertThat(stackTrace(e), not(stringContainsInOrder(USER_VALID)));
            assertThat(stackTrace(e), not(stringContainsInOrder(PASS_VALID)));
            assertThat(stackTrace(e), not(matchesPattern("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")));

            /* Try to read accessible path with known value */
            res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/" + SECRET_KEY),
                    "Valid secret path could not be read"
            );
            assertThat("Known secret returned invalid value.", res.getValue(), is(SECRET_VALUE));

            /* Try to read accessible path with JSON value */
            res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/" + SECRET_KEY_JSON),
                    "Valid secret path could not be read"
            );
            assertThat("Known secret returned null value.", res.getValue(), notNullValue());

            SecretResponse finalRes = res;
            Credentials parsedRes = assertDoesNotThrow(() -> finalRes.getValue(Credentials.class), "JSON response could not be parsed");
            assertThat("JSON response was null", parsedRes, notNullValue());
            assertThat("JSON response incorrect", parsedRes.getUsername(), is("user"));
            assertThat("JSON response incorrect", parsedRes.getPassword(), is("password"));

            /* Try to read accessible path with JSON value */
            res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/" + SECRET_KEY_JSON),
                    "Valid secret path could not be read"
            );
            assertThat("Known secret returned null value.", res.getValue(), notNullValue());

            SecretResponse finalRes1 = res;
            parsedRes = assertDoesNotThrow(() -> finalRes1.getValue(Credentials.class), "JSON response could not be parsed");
            assertThat("JSON response was null", parsedRes, notNullValue());
            assertThat("JSON response incorrect", parsedRes.getUsername(), is("user"));
            assertThat("JSON response incorrect", parsedRes.getPassword(), is("password"));

            /* Try to read accessible complex secret */
            res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/" + SECRET_KEY_COMPLEX),
                    "Valid secret path could not be read"
            );
            assertThat("Known secret returned null value.", res.getData(), notNullValue());
            assertThat("Unexpected value size", res.getData().keySet(), hasSize(2));
            assertThat("Unexpected value", res.get("key1"), is("value1"));
            assertThat("Unexpected value", res.get("key2"), is("value2"));
        }

        /**
         * Test listing secrets.
         */
        @Test
        @Order(20)
        @DisplayName("List secrets")
        void listSecretsTest() {
            authRoot();
            assumeTrue(connector.isAuthorized());
            /* Try to list secrets from valid path */
            List<String> secrets = assertDoesNotThrow(
                    () -> connector.listSecrets(SECRET_PATH),
                    "Secrets could not be listed"
            );
            assertThat("Invalid nmber of secrets.", secrets.size(), greaterThan(0));
            assertThat("Known secret key not found", secrets, hasItem(SECRET_KEY));
        }

        /**
         * Test writing secrets.
         */
        @Test
        @Order(30)
        @DisplayName("Write secrets")
        @SuppressWarnings("deprecation")
        void writeSecretTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            /* Try to write to null path */
            assertThrows(
                    InvalidRequestException.class,
                    () -> connector.writeSecret(null, "someValue"),
                    "Secret written to null path."
            );

            /* Try to write to invalid path */
            assertThrows(
                    InvalidRequestException.class,
                    () -> connector.writeSecret("", "someValue"),
                    "Secret written to invalid path."
            );

            /* Try to write to a path the user has no access for */
            assertThrows(
                    PermissionDeniedException.class,
                    () -> connector.writeSecret("invalid/path", "someValue"),
                    "Secret written to inaccessible path."
            );

            /* Perform a valid write/read roundtrip to valid path. Also check UTF8-encoding. */
            assertDoesNotThrow(
                    () -> connector.writeSecret(SECRET_PATH + "/temp", "Abc123äöü,!"),
                    "Failed to write secret to accessible path."
            );
            SecretResponse res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/temp"),
                    "Written secret could not be read."
            );
            assertThat(res.getValue(), is("Abc123äöü,!"));
        }

        /**
         * Test deletion of secrets.
         */
        @Test
        @Order(40)
        @DisplayName("Delete secrets")
        void deleteSecretTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            /* Write a test secret to vault */
            assertDoesNotThrow(
                    () -> connector.writeSecret(SECRET_PATH + "/toDelete", "secret content"),
                    "Secret written to inaccessible path."
            );
            SecretResponse res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/toDelete"),
                    "Written secret could not be read."
            );
            assumeTrue(res != null);

            /* Delete secret */
            assertDoesNotThrow(
                    () -> connector.deleteSecret(SECRET_PATH + "/toDelete"),
                    "Revocation threw unexpected exception."
            );

            /* Try to read again */
            InvalidResponseException e = assertThrows(
                    InvalidResponseException.class,
                    () -> connector.readSecret(SECRET_PATH + "/toDelete"),
                    "Successfully read deleted secret."
            );
            assertThat(e.getStatusCode(), is(404));
        }

        /**
         * Test revocation of secrets.
         */
        @Test
        @Order(50)
        @DisplayName("Revoke secrets")
        void revokeTest() {
            authRoot();
            assumeTrue(connector.isAuthorized());

            /* Write a test secret to vault */
            assertDoesNotThrow(
                    () -> connector.writeSecret(SECRET_PATH + "/toRevoke", "secret content"),
                    "Secret written to inaccessible path."
            );
            SecretResponse res = assertDoesNotThrow(
                    () -> connector.readSecret(SECRET_PATH + "/toRevoke"),
                    "Written secret could not be read."
            );
            assumeTrue(res != null);

            /* Revoke secret */
            assertDoesNotThrow(
                    () -> connector.revoke(SECRET_PATH + "/toRevoke"),
                    "Revocation threw unexpected exception."
            );
        }
    }

    @Nested
    @DisplayName("KV v2 Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class KvV2Tests {
        // KV v2 secret with 2 versions.
        private static final String MOUNT_KV2 = "kv";
        private static final String SECRET2_KEY = "foo2";
        private static final String SECRET2_VALUE1 = "bar2";
        private static final String SECRET2_VALUE2 = "bar3";
        private static final String SECRET2_VALUE3 = "bar4";
        private static final String SECRET2_VALUE4 = "bar4";

        /**
         * Test reading of secrets from KV v2 store.
         */
        @Test
        @Order(10)
        @DisplayName("Read v2 secret")
        void readSecretTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            // Try to read accessible path with known value.
            SecretResponse res = assertDoesNotThrow(
                    () -> connector.readSecretData(MOUNT_KV2, SECRET2_KEY),
                    "Valid secret path could not be read."
            );
            assertThat("Metadata not populated for KV v2 secret", res.getMetadata(), is(notNullValue()));
            assertThat("Unexpected secret version", res.getMetadata().getVersion(), is(2));
            assertThat("Known secret returned invalid value.", res.getValue(), is(SECRET2_VALUE2));

            // Try to read different version of same secret.
            res = assertDoesNotThrow(
                    () -> connector.readSecretVersion(MOUNT_KV2, SECRET2_KEY, 1),
                    "Valid secret version could not be read."
            );
            assertThat("Unexpected secret version", res.getMetadata().getVersion(), is(1));
            assertThat("Known secret returned invalid value.", res.getValue(), is(SECRET2_VALUE1));
        }

        /**
         * Test writing of secrets to KV v2 store.
         */
        @Test
        @Order(20)
        @DisplayName("Write v2 secret")
        void writeSecretTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            // First get the current version of the secret.
            MetadataResponse res = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading secret metadata failed."
            );
            int currentVersion = res.getMetadata().getCurrentVersion();

            // Now write (update) the data and verify the version.
            Map<String, Object> data = new HashMap<>();
            data.put("value", SECRET2_VALUE3);
            SecretVersionResponse res2 = assertDoesNotThrow(
                    () -> connector.writeSecretData(MOUNT_KV2, SECRET2_KEY, data),
                    "Writing secret to KV v2 store failed."
            );
            assertThat("Version not updated after writing secret", res2.getMetadata().getVersion(), is(currentVersion + 1));
            int currentVersion2 = res2.getMetadata().getVersion();

            // Verify the content.
            SecretResponse res3 = assertDoesNotThrow(
                    () -> connector.readSecretData(MOUNT_KV2, SECRET2_KEY),
                    "Reading secret from KV v2 store failed."
            );
            assertThat("Data not updated correctly", res3.getValue(), is(SECRET2_VALUE3));

            // Now try with explicit CAS value (invalid).
            Map<String, Object> data4 = singletonMap("value", SECRET2_VALUE4);
            assertThrows(
                    InvalidResponseException.class,
                    () -> connector.writeSecretData(MOUNT_KV2, SECRET2_KEY, data4, currentVersion2 - 1),
                    "Writing secret to KV v2 with invalid CAS value succeeded"
            );

            // And finally with a correct CAS value.
            Map<String, Object> data5 = singletonMap("value", SECRET2_VALUE4);
            assertDoesNotThrow(() -> connector.writeSecretData(MOUNT_KV2, SECRET2_KEY, data5, currentVersion2));
        }

        /**
         * Test reading of secret metadata from KV v2 store.
         */
        @Test
        @Order(30)
        @DisplayName("Read v2 metadata")
        void readSecretMetadataTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            // Read current metadata first.
            MetadataResponse res = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading secret metadata failed."
            );
            Integer maxVersions = res.getMetadata().getMaxVersions();
            assumeTrue(10 == res.getMetadata().getMaxVersions(), "Unexpected maximum number of versions");

            // Now update the metadata.
            assertDoesNotThrow(
                    () -> connector.updateSecretMetadata(MOUNT_KV2, SECRET2_KEY, maxVersions + 1, true),
                    "Updating secret metadata failed."
            );

            // And verify the result.
            res = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading secret metadata failed."
            );
            assertThat("Unexpected maximum number of versions", res.getMetadata().getMaxVersions(), is(maxVersions + 1));
        }

        /**
         * Test updating secret metadata in KV v2 store.
         */
        @Test
        @Order(40)
        @DisplayName("Update v2 metadata")
        void updateSecretMetadataTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            // Try to read accessible path with known value.
            MetadataResponse res = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Valid secret path could not be read."
            );
            assertThat("Metadata not populated for KV v2 secret", res.getMetadata(), is(notNullValue()));
            assertThat("Unexpected secret version", res.getMetadata().getCurrentVersion(), is(2));
            assertThat("Unexpected number of secret versions", res.getMetadata().getVersions().size(), is(2));
            assertThat("Creation date should be present", res.getMetadata().getCreatedTime(), is(notNullValue()));
            assertThat("Update date should be present", res.getMetadata().getUpdatedTime(), is(notNullValue()));
            assertThat("Unexpected maximum number of versions", res.getMetadata().getMaxVersions(), is(10));
        }

        /**
         * Test deleting specific secret versions from KV v2 store.
         */
        @Test
        @Order(50)
        @DisplayName("Version handling")
        void handleSecretVersionsTest() {
            authUser();
            assumeTrue(connector.isAuthorized());

            // Try to delete non-existing versions.
            assertDoesNotThrow(
                    () -> connector.deleteSecretVersions(MOUNT_KV2, SECRET2_KEY, 5, 42),
                    "Revealed non-existence of secret versions"
            );
            assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Revealed non-existence of secret versions"
            );

            // Now delete existing version and verify.
            assertDoesNotThrow(
                    () -> connector.deleteSecretVersions(MOUNT_KV2, SECRET2_KEY, 1),
                    "Deleting existing version failed"
            );
            MetadataResponse meta = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading deleted secret metadata failed"
            );
            assertThat(
                    "Expected deletion time for secret 1",
                    meta.getMetadata().getVersions().get(1).getDeletionTime(),
                    is(notNullValue())
            );

            // Undelete the just deleted version.
            assertDoesNotThrow(
                    () -> connector.undeleteSecretVersions(MOUNT_KV2, SECRET2_KEY, 1),
                    "Undeleting existing version failed"
            );
            meta = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading deleted secret metadata failed"
            );
            assertThat(
                    "Expected deletion time for secret 1 to be reset",
                    meta.getMetadata().getVersions().get(1).getDeletionTime(),
                    is(nullValue())
            );

            // Now destroy it.
            assertDoesNotThrow(
                    () -> connector.destroySecretVersions(MOUNT_KV2, SECRET2_KEY, 1),
                    "Destroying existing version failed"
            );
            meta = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading destroyed secret metadata failed"
            );
            assertThat(
                    "Expected secret 1 to be marked destroyed",
                    meta.getMetadata().getVersions().get(1).isDestroyed(),
                    is(true)
            );

            // Delete latest version.
            assertDoesNotThrow(
                    () -> connector.deleteLatestSecretVersion(MOUNT_KV2, SECRET2_KEY),
                    "Deleting latest version failed"
            );
            meta = assertDoesNotThrow(
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading deleted secret metadata failed"
            );
            assertThat(
                    "Expected secret 2 to be deleted",
                    meta.getMetadata().getVersions().get(2).getDeletionTime(),
                    is(notNullValue())
            );

            // Delete all versions.
            assertDoesNotThrow(
                    () -> connector.deleteAllSecretVersions(MOUNT_KV2, SECRET2_KEY),
                    "Deleting latest version failed"
            );
            assertThrows(
                    InvalidResponseException.class,
                    () -> connector.readSecretMetadata(MOUNT_KV2, SECRET2_KEY),
                    "Reading metadata of deleted secret should not succeed"
            );
        }
    }

    @Nested
    @DisplayName("App-ID Tests")
    class AppIdTests {
        private static final String APP_ID = "152AEA38-85FB-47A8-9CBD-612D645BFACA";
        private static final String USER_ID = "5ADF8218-D7FB-4089-9E38-287465DBF37E";

        /**
         * App-ID authentication roundtrip.
         */
        @Test
        @Order(10)
        @DisplayName("Authenticate with App-ID")
        @SuppressWarnings("deprecation")
        void authAppIdTest() {
            /* Try unauthorized access first. */
            assumeFalse(connector.isAuthorized());

            assertThrows(
                    AuthorizationRequiredException.class,
                    () -> connector.registerAppId("", "", ""),
                    "Expected exception not thrown"
            );
            assertThrows(
                    AuthorizationRequiredException.class,
                    () -> connector.registerUserId("", ""),
                    "Expected exception not thrown"
            );
        }

        /**
         * App-ID authentication roundtrip.
         */
        @Test
        @Order(20)
        @DisplayName("Register App-ID")
        @SuppressWarnings("deprecation")
        void registerAppIdTest() {
            /* Authorize. */
            authRoot();
            assumeTrue(connector.isAuthorized());

            /* Register App-ID */
            boolean res = assertDoesNotThrow(
                    () -> connector.registerAppId(APP_ID, "user", "App Name"),
                    "Failed to register App-ID"
            );
            assertThat("Failed to register App-ID", res, is(true));

            /* Register User-ID */
            res = assertDoesNotThrow(
                    () -> connector.registerUserId(APP_ID, USER_ID),
                    "Failed to register App-ID"
            );
            assertThat("Failed to register App-ID", res, is(true));

            connector.resetAuth();
            assumeFalse(connector.isAuthorized());

            /* Authenticate with created credentials */
            AuthResponse resp = assertDoesNotThrow(
                    () -> connector.authAppId(APP_ID, USER_ID),
                    "Failed to authenticate using App-ID"
            );
            assertThat("Authorization flag not set after App-ID login.", connector.isAuthorized(), is(true));
        }
    }

    @Nested
    @DisplayName("AppRole Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AppRoleTests {
        private static final String APPROLE_ROLE_NAME = "testrole1";                          // role with secret ID
        private static final String APPROLE_ROLE = "06eae026-7d4b-e4f8-0ec4-4107eb483975";
        private static final String APPROLE_SECRET = "20320293-c1c1-3b22-20f8-e5c960da0b5b";
        private static final String APPROLE_SECRET_ACCESSOR = "3b45a7c2-8d1c-abcf-c732-ecf6db16a8e1";
        private static final String APPROLE_ROLE2_NAME = "testrole2";                         // role with CIDR subnet
        private static final String APPROLE_ROLE2 = "40224890-1563-5193-be4b-0b4f9f573b7f";

        /**
         * App-ID authentication roundtrip.
         */
        @Test
        @Order(10)
        @DisplayName("Authenticate with AppRole")
        void authAppRole() {
            assumeFalse(connector.isAuthorized());

            /* Authenticate with correct credentials */
            assertDoesNotThrow(
                    () -> connector.authAppRole(APPROLE_ROLE, APPROLE_SECRET),
                    "Failed to authenticate using AppRole."
            );
            assertThat("Authorization flag not set after AppRole login.", connector.isAuthorized(), is(true));

            /* Authenticate with valid secret ID against unknown role */
            final String invalidRole = "foo";
            InvalidResponseException e = assertThrows(
                    InvalidResponseException.class,
                    () -> connector.authAppRole(invalidRole, APPROLE_SECRET),
                    "Successfully logged in with unknown role"
            );
            /* Assert that the exception does not reveal role ID or secret */
            assertThat(stackTrace(e), not(stringContainsInOrder(invalidRole)));
            assertThat(stackTrace(e), not(stringContainsInOrder(APPROLE_SECRET)));

            /* Authenticate without wrong secret ID */
            final String invalidSecret = "foo";
            e = assertThrows(
                    InvalidResponseException.class,
                    () -> connector.authAppRole(APPROLE_ROLE, "foo"),
                    "Successfully logged in without secret ID"
            );
            /* Assert that the exception does not reveal role ID or secret */
            assertThat(stackTrace(e), not(stringContainsInOrder(APPROLE_ROLE)));
            assertThat(stackTrace(e), not(stringContainsInOrder(invalidSecret)));

            /* Authenticate without secret ID */
            e = assertThrows(
                    InvalidResponseException.class,
                    () -> connector.authAppRole(APPROLE_ROLE),
                    "Successfully logged in without secret ID"
            );
            /* Assert that the exception does not reveal role ID */
            assertThat(stackTrace(e), not(stringContainsInOrder(APPROLE_ROLE)));

            /* Authenticate with secret ID on role with CIDR whitelist */
            assertDoesNotThrow(
                    () -> connector.authAppRole(APPROLE_ROLE2, APPROLE_SECRET),
                    "Failed to log in without secret ID"
            );
            assertThat("Authorization flag not set after AppRole login.", connector.isAuthorized(), is(true));
        }

        /**
         * Test listing of AppRole roles and secrets.
         */
        @Test
        @Order(20)
        @DisplayName("List AppRoles")
        void listAppRoleTest() {
            /* Try unauthorized access first. */
            assumeFalse(connector.isAuthorized());

            assertThrows(AuthorizationRequiredException.class, () -> connector.listAppRoles());

            assertThrows(AuthorizationRequiredException.class, () -> connector.listAppRoleSecrets(""));

            /* Authorize. */
            authRoot();
            assumeTrue(connector.isAuthorized());

            /* Verify pre-existing rules */
            List<String> res = assertDoesNotThrow(() -> connector.listAppRoles(), "Role listing failed.");
            assertThat("Unexpected number of AppRoles", res, hasSize(2));
            assertThat("Pre-configured roles not listed", res, containsInAnyOrder(APPROLE_ROLE_NAME, APPROLE_ROLE2_NAME));

            /* Check secret IDs */
            res = assertDoesNotThrow(() -> connector.listAppRoleSecrets(APPROLE_ROLE_NAME), "AppRole secret listing failed.");
            assertThat("Unexpected number of AppRole secrets", res, hasSize(1));
            assertThat("Pre-configured AppRole secret not listed", res, contains(APPROLE_SECRET_ACCESSOR));
        }

        /**
         * Test creation of a new AppRole.
         */
        @Test
        @Order(30)
        @DisplayName("Create AppRole")
        void createAppRoleTest() {
            /* Try unauthorized access first. */
            assumeFalse(connector.isAuthorized());
            assertThrows(AuthorizationRequiredException.class, () -> connector.createAppRole(new AppRole()));
            assertThrows(AuthorizationRequiredException.class, () -> connector.lookupAppRole(""));
            assertThrows(AuthorizationRequiredException.class, () -> connector.deleteAppRole(""));
            assertThrows(AuthorizationRequiredException.class, () -> connector.getAppRoleID(""));
            assertThrows(AuthorizationRequiredException.class, () -> connector.setAppRoleID("", ""));
            assertThrows(AuthorizationRequiredException.class, () -> connector.createAppRoleSecret("", ""));
            assertThrows(AuthorizationRequiredException.class, () -> connector.lookupAppRoleSecret("", ""));
            assertThrows(AuthorizationRequiredException.class, () -> connector.destroyAppRoleSecret("", ""));

            /* Authorize. */
            authRoot();
            assumeTrue(connector.isAuthorized());

            String roleName = "TestRole";

            /* Create role model */
            AppRole role = AppRole.builder(roleName).build();

            /* Create role */
            boolean createRes = assertDoesNotThrow(() -> connector.createAppRole(role), "Role creation failed.");
            assertThat("Role creation failed.", createRes, is(true));

            /* Lookup role */
            AppRoleResponse res = assertDoesNotThrow(() -> connector.lookupAppRole(roleName), "Role lookup failed.");
            assertThat("Role lookup returned no role.", res.getRole(), is(notNullValue()));

            /* Lookup role ID */
            String roleID = assertDoesNotThrow(() -> connector.getAppRoleID(roleName), "Role ID lookup failed.");
            assertThat("Role ID lookup returned empty ID.", roleID, is(not(emptyString())));

            /* Set custom role ID */
            String roleID2 = "custom-role-id";
            assertDoesNotThrow(() -> connector.setAppRoleID(roleName, roleID2), "Setting custom role ID failed.");

            /* Verify role ID */
            String res2 = assertDoesNotThrow(() -> connector.getAppRoleID(roleName), "Role ID lookup failed.");
            assertThat("Role ID lookup returned wrong ID.", res2, is(roleID2));

            /* Update role model with custom flags */
            AppRole role2 = AppRole.builder(roleName)
                    .withTokenPeriod(321)
                    .build();

            /* Create role */
            boolean res3 = assertDoesNotThrow(() -> connector.createAppRole(role2), "Role creation failed.");
            assertThat("No result given.", res3, is(notNullValue()));

            /* Lookup updated role */
            res = assertDoesNotThrow(() -> connector.lookupAppRole(roleName), "Role lookup failed.");
            assertThat("Role lookup returned no role.", res.getRole(), is(notNullValue()));
            assertThat("Token period not set for role.", res.getRole().getTokenPeriod(), is(321));

            /* Create role by name */
            String roleName2 = "RoleByName";
            assertDoesNotThrow(() -> connector.createAppRole(roleName2), "Creation of role by name failed.");
            res = assertDoesNotThrow(() -> connector.lookupAppRole(roleName2), "Creation of role by name failed.");
            assertThat("Role lookuo returned not value", res.getRole(), is(notNullValue()));

            /* Create role by name with custom ID */
            String roleName3 = "RoleByName";
            String roleID3 = "RolyByNameID";
            assertDoesNotThrow(() -> connector.createAppRole(roleName3, roleID3), "Creation of role by name failed.");
            res = assertDoesNotThrow(() -> connector.lookupAppRole(roleName3), "Creation of role by name failed.");
            assertThat("Role lookuo returned not value", res.getRole(), is(notNullValue()));

            res2 = assertDoesNotThrow(() -> connector.getAppRoleID(roleName3), "Creation of role by name failed.");
            assertThat("Role lookuo returned wrong ID", res2, is(roleID3));

            /* Create role by name with policies */
            assertDoesNotThrow(
                    () -> connector.createAppRole(roleName3, Collections.singletonList("testpolicy")),
                    "Creation of role by name failed."
            );
            res = assertDoesNotThrow(() -> connector.lookupAppRole(roleName3), "Creation of role by name failed.");
            // Note: As of Vault 0.8.3 default policy is not added automatically, so this test should return 1, not 2.
            assertThat("Role lookup returned wrong policy count (before Vault 0.8.3 is should be 2)", res.getRole().getTokenPolicies(), hasSize(1));
            assertThat("Role lookup returned wrong policies", res.getRole().getTokenPolicies(), hasItem("testpolicy"));

            /* Delete role */
            assertDoesNotThrow(() -> connector.deleteAppRole(roleName3), "Deletion of role failed.");
            assertThrows(
                    InvalidResponseException.class,
                    () -> connector.lookupAppRole(roleName3),
                    "Deleted role could be looked up."
            );
        }

        /**
         * Test creation of AppRole secrets.
         */
        @Test
        @Order(40)
        @DisplayName("Create AppRole secrets")
        void createAppRoleSecretTest() {
            authRoot();
            assumeTrue(connector.isAuthorized());

            /* Create default (random) secret for existing role */
            AppRoleSecretResponse res = assertDoesNotThrow(
                    () -> connector.createAppRoleSecret(APPROLE_ROLE_NAME),
                    "AppRole secret creation failed."
            );
            assertThat("No secret returned", res.getSecret(), is(notNullValue()));

            /* Create secret with custom ID */
            String secretID = "customSecretId";
            res = assertDoesNotThrow(
                    () -> connector.createAppRoleSecret(APPROLE_ROLE_NAME, secretID),
                    "AppRole secret creation failed."
            );
            assertThat("Unexpected secret ID returned", res.getSecret().getId(), is(secretID));

            /* Lookup secret */
            res = assertDoesNotThrow(
                    () -> connector.lookupAppRoleSecret(APPROLE_ROLE_NAME, secretID),
                    "AppRole secret lookup failed."
            );
            assertThat("No secret information returned", res.getSecret(), is(notNullValue()));

            /* Destroy secret */
            assertDoesNotThrow(
                    () -> connector.destroyAppRoleSecret(APPROLE_ROLE_NAME, secretID),
                    "AppRole secret destruction failed."
            );
            assertThrows(
                    InvalidResponseException.class,
                    () -> connector.lookupAppRoleSecret(APPROLE_ROLE_NAME, secretID),
                    "Destroyed AppRole secret successfully read."
            );
        }
    }

    @Nested
    @DisplayName("Token Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TokenTests {
        /**
         * Test authentication using token.
         */
        @Test
        @Order(10)
        @DisplayName("Authenticate with token")
        void authTokenTest() {
            final String invalidToken = "52135869df23a5e64c5d33a9785af5edb456b8a4a235d1fe135e6fba1c35edf6";
            VaultConnectorException e = assertThrows(
                    VaultConnectorException.class,
                    () -> connector.authToken(invalidToken),
                    "Logged in with invalid token"
            );
            /* Assert that the exception does not reveal the token */
            assertThat(stackTrace(e), not(stringContainsInOrder(invalidToken)));


            TokenResponse res = assertDoesNotThrow(
                    () -> connector.authToken(TOKEN_ROOT),
                    "Login failed with valid token"
            );
            assertNotNull(res, "Login failed with valid token");
            assertThat("Login failed with valid token", connector.isAuthorized(), is(true));
        }

        /**
         * Test token creation.
         */
        @Test
        @Order(20)
        @DisplayName("Create token")
        void createTokenTest() {
            authRoot();
            assumeTrue(connector.isAuthorized());

            /* Create token */
            Token token = Token.builder()
                    .withId("test-id")
                    .withType(Token.Type.SERVICE)
                    .withDisplayName("test name")
                    .build();

            /* Create token */
            AuthResponse res = assertDoesNotThrow(() -> connector.createToken(token), "Token creation failed");
            assertThat("No result given.", res, is(notNullValue()));
            assertThat("Invalid token ID returned.", res.getAuth().getClientToken(), is("test-id"));
            assertThat("Invalid number of policies returned.", res.getAuth().getPolicies(), hasSize(1));
            assertThat("Root policy not inherited.", res.getAuth().getPolicies(), contains("root"));
            assertThat("Invalid number of token policies returned.", res.getAuth().getTokenPolicies(), hasSize(1));
            assertThat("Root policy not inherited for token.", res.getAuth().getTokenPolicies(), contains("root"));
            assertThat("Unexpected token type.", res.getAuth().getTokenType(), is(Token.Type.SERVICE.value()));
            assertThat("Metadata unexpected.", res.getAuth().getMetadata(), is(nullValue()));
            assertThat("Root token should not be renewable", res.getAuth().isRenewable(), is(false));
            assertThat("Root token should not be orphan", res.getAuth().isOrphan(), is(false));

            // Starting with Vault 1.0 a warning "custom ID uses weaker SHA1..." is given.
            if (VAULT_VERSION.startsWith("1.")) {
                assertThat("Token creation did not return expected warning.", res.getWarnings(), hasSize(1));
            } else {
                assertThat("Token creation returned warnings.", res.getWarnings(), is(nullValue()));
            }

            /* Create token with attributes */
            Token token2 = Token.builder()
                    .withId("test-id2")
                    .withDisplayName("test name 2")
                    .withPolicies(Collections.singletonList("testpolicy"))
                    .withoutDefaultPolicy()
                    .withMeta("foo", "bar")
                    .build();
            res = assertDoesNotThrow(() -> connector.createToken(token2), "Token creation failed");
            assertThat("Invalid token ID returned.", res.getAuth().getClientToken(), is("test-id2"));
            assertThat("Invalid number of policies returned.", res.getAuth().getPolicies(), hasSize(1));
            assertThat("Custom policy not set.", res.getAuth().getPolicies(), contains("testpolicy"));
            assertThat("Metadata not given.", res.getAuth().getMetadata(), is(notNullValue()));
            assertThat("Metadata not correct.", res.getAuth().getMetadata().get("foo"), is("bar"));
            assertThat("Token should be renewable", res.getAuth().isRenewable(), is(true));

            /* Overwrite token should fail as of Vault 0.8.0 */
            Token token3 = Token.builder()
                    .withId("test-id2")
                    .withDisplayName("test name 3")
                    .withPolicies(Arrays.asList("pol1", "pol2"))
                    .withDefaultPolicy()
                    .withMeta("test", "success")
                    .withMeta("key", "value")
                    .withTtl(1234)
                    .build();
            InvalidResponseException e = assertThrows(
                    InvalidResponseException.class,
                    () -> connector.createToken(token3),
                    "Overwriting token should fail as of Vault 0.8.0"
            );
            assertThat(e.getStatusCode(), is(400));
            /* Assert that the exception does not reveal token ID */
            assertThat(stackTrace(e), not(stringContainsInOrder(token3.getId())));

            /* Create token with batch type */
            Token token4 = Token.builder()
                    .withDisplayName("test name 3")
                    .withPolicy("batchpolicy")
                    .withoutDefaultPolicy()
                    .withType(Token.Type.BATCH)
                    .build();
            res = assertDoesNotThrow(() -> connector.createToken(token4), "Token creation failed");
            assertThat("Unexpected token prefix", res.getAuth().getClientToken(), startsWith("b."));
            assertThat("Invalid number of policies returned.", res.getAuth().getPolicies(), hasSize(1));
            assertThat("Custom policy policy not set.", res.getAuth().getPolicies(), contains("batchpolicy"));
            assertThat("Token should not be renewable", res.getAuth().isRenewable(), is(false));
            assertThat("Token should not be orphan", res.getAuth().isOrphan(), is(false));
            assertThat("Specified token Type not set", res.getAuth().getTokenType(), is(Token.Type.BATCH.value()));
        }

        /**
         * Test token lookup.
         */
        @Test
        @Order(30)
        @DisplayName("Lookup token")
        void lookupTokenTest() {
            authRoot();
            assumeTrue(connector.isAuthorized());

            /* Create token with attributes */
            Token token = Token.builder()
                    .withId("my-token")
                    .withType(Token.Type.SERVICE)
                    .build();
            assertDoesNotThrow(() -> connector.createToken(token), "Token creation failed.");

            authRoot();
            assumeTrue(connector.isAuthorized());

            TokenResponse res = assertDoesNotThrow(() -> connector.lookupToken("my-token"), "Token creation failed.");
            assertThat("Unexpected token ID", res.getData().getId(), is(token.getId()));
            assertThat("Unexpected number of policies", res.getData().getPolicies(), hasSize(1));
            assertThat("Unexpected policy", res.getData().getPolicies(), contains("root"));
            assertThat("Unexpected token type", res.getData().getType(), is(token.getType()));
            assertThat("Issue time expected to be filled", res.getData().getIssueTime(), is(notNullValue()));
        }

        /**
         * Test token role handling.
         */
        @Test
        @Order(40)
        @DisplayName("Token roles")
        void tokenRolesTest() {
            authRoot();
            assumeTrue(connector.isAuthorized());

            // Create token role.
            final String roleName = "test-role";
            final TokenRole role = TokenRole.builder().build();

            boolean creationRes = assertDoesNotThrow(
                    () -> connector.createOrUpdateTokenRole(roleName, role),
                    "Token role creation failed."
            );
            assertThat("Token role creation failed.", creationRes, is(true));

            // Read the role.
            TokenRoleResponse res = assertDoesNotThrow(
                    () -> connector.readTokenRole(roleName),
                    "Reading token role failed."
            );
            assertThat("Token role response must not be null", res, is(notNullValue()));
            assertThat("Token role must not be null", res.getData(), is(notNullValue()));
            assertThat("Token role name not as expected", res.getData().getName(), is(roleName));
            assertThat("Token role expected to be renewable by default", res.getData().getRenewable(), is(true));
            assertThat("Token role not expected to be orphan by default", res.getData().getOrphan(), is(false));
            assertThat("Unexpected default token type", res.getData().getTokenType(), is(Token.Type.DEFAULT_SERVICE.value()));

            // Update the role, i.e. change some attributes.
            final TokenRole role2 = TokenRole.builder()
                    .forName(roleName)
                    .withPathSuffix("suffix")
                    .orphan(true)
                    .renewable(false)
                    .withTokenNumUses(42)
                    .build();

            creationRes = assertDoesNotThrow(
                    () -> connector.createOrUpdateTokenRole(role2),
                    "Token role update failed."
            );
            assertThat("Token role update failed.", creationRes, is(true));

            res = assertDoesNotThrow(() -> connector.readTokenRole(roleName), "Reading token role failed.");
            assertThat("Token role response must not be null", res, is(notNullValue()));
            assertThat("Token role must not be null", res.getData(), is(notNullValue()));
            assertThat("Token role name not as expected", res.getData().getName(), is(roleName));
            assertThat("Token role not expected to be renewable  after update", res.getData().getRenewable(), is(false));
            assertThat("Token role expected to be orphan  after update", res.getData().getOrphan(), is(true));
            assertThat("Unexpected number of token uses after update", res.getData().getTokenNumUses(), is(42));

            // List roles.
            List<String> listRes = assertDoesNotThrow(() -> connector.listTokenRoles(), "Listing token roles failed.");
            assertThat("Token role list must not be null", listRes, is(notNullValue()));
            assertThat("Unexpected number of token roles", listRes, hasSize(1));
            assertThat("Unexpected token role in list", listRes, contains(roleName));

            // Delete the role.
            creationRes = assertDoesNotThrow(() -> connector.deleteTokenRole(roleName), "Token role deletion failed.");
            assertThat("Token role deletion failed.", creationRes, is(true));
            assertThrows(InvalidResponseException.class, () -> connector.readTokenRole(roleName), "Reading nonexistent token role should fail");
            assertThrows(InvalidResponseException.class, () -> connector.listTokenRoles(), "Listing nonexistent token roles should fail");
        }
    }

    @Nested
    @DisplayName("Misc Tests")
    class MiscTests {
        /**
         * Test listing of authentication backends
         */
        @Test
        @DisplayName("List auth methods")
        void authMethodsTest() {
            /* Authenticate as valid user */
            assertDoesNotThrow(() -> connector.authToken(TOKEN_ROOT));
            assumeTrue(connector.isAuthorized());

            List<AuthBackend> supportedBackends = assertDoesNotThrow(
                    () -> connector.getAuthBackends(),
                    "Could not list supported auth backends."
            );
            assertThat(supportedBackends, hasSize(4));
            assertThat(supportedBackends, hasItems(AuthBackend.TOKEN, AuthBackend.USERPASS, AuthBackend.APPID, AuthBackend.APPROLE));
        }

        /**
         * Test authentication using username and password.
         */
        @Test
        @DisplayName("Authenticate with UserPass")
        void authUserPassTest() {
            final String invalidUser = "foo";
            final String invalidPass = "bar";
            VaultConnectorException e = assertThrows(
                    VaultConnectorException.class,
                    () -> connector.authUserPass(invalidUser, invalidPass),
                    "Logged in with invalid credentials"
            );
            /* Assert that the exception does not reveal credentials */
            assertThat(stackTrace(e), not(stringContainsInOrder(invalidUser)));
            assertThat(stackTrace(e), not(stringContainsInOrder(invalidPass)));

            AuthResponse res = assertDoesNotThrow(
                    () -> connector.authUserPass(USER_VALID, PASS_VALID),
                    "Login failed with valid credentials: Exception thrown"
            );
            assertNotNull(res.getAuth(), "Login failed with valid credentials: Response not available");
            assertThat("Login failed with valid credentials: Connector not authorized", connector.isAuthorized(), is(true));
        }

        /**
         * Test TLS connection with custom certificate chain.
         */
        @Test
        @Tag("tls")
        @DisplayName("TLS connection test")
        void tlsConnectionTest() {
            assertThrows(
                    VaultConnectorException.class,
                    () -> connector.authToken("52135869df23a5e64c5d33a9785af5edb456b8a4a235d1fe135e6fba1c35edf6"),
                    "Logged in with invalid token"
            );

            TokenResponse res = assertDoesNotThrow(
                    () -> connector.authToken(TOKEN_ROOT),
                    "Login failed with valid token"
            );
            assertNotNull(res, "Login failed with valid token");
            assertThat("Login failed with valid token", connector.isAuthorized(), is(true));
        }

        /**
         * Test sealing and unsealing Vault.
         */
        @Test
        @DisplayName("Seal test")
        void sealTest() throws VaultConnectorException {
            SealResponse sealStatus = connector.sealStatus();
            assumeFalse(sealStatus.isSealed());

            /* Unauthorized sealing should fail */
            assertThrows(VaultConnectorException.class, connector::seal, "Unauthorized sealing succeeded");
            assertThat("Vault sealed, although sealing failed", sealStatus.isSealed(), is(false));

            /* Root user should be able to seal */
            authRoot();
            assumeTrue(connector.isAuthorized());
            assertDoesNotThrow(connector::seal, "Sealing failed");
            sealStatus = connector.sealStatus();
            assertThat("Vault not sealed", sealStatus.isSealed(), is(true));
            sealStatus = connector.unseal(KEY2);
            assertThat("Vault unsealed with only 1 key", sealStatus.isSealed(), is(true));
            sealStatus = connector.unseal(KEY3);
            assertThat("Vault not unsealed", sealStatus.isSealed(), is(false));
        }

        /**
         * Test health status
         */
        @Test
        @DisplayName("Health test")
        void healthTest() {
            HealthResponse res = assertDoesNotThrow(connector::getHealth, "Retrieving health status failed.");
            assertThat("Health response should be set", res, is(notNullValue()));
            assertThat("Unexpected version", res.getVersion(), is(VAULT_VERSION));
            assertThat("Unexpected init status", res.isInitialized(), is(true));
            assertThat("Unexpected seal status", res.isSealed(), is(false));
            assertThat("Unexpected standby status", res.isStandby(), is(false));

            // No seal vault and verify correct status.
            authRoot();
            assertDoesNotThrow(connector::seal, "Unexpected exception on sealing");
            SealResponse sealStatus = assertDoesNotThrow(connector::sealStatus);
            assumeTrue(sealStatus.isSealed());
            connector.resetAuth();  // Should work unauthenticated
            res = assertDoesNotThrow(connector::getHealth, "Retrieving health status failed when sealed");
            assertThat("Unexpected seal status", res.isSealed(), is(true));
        }

        /**
         * Test closing the connector.
         */
        @Test
        @DisplayName("Connector close test")
        void closeTest() throws NoSuchFieldException, IllegalAccessException {
            authUser();
            assumeTrue(connector.isAuthorized());

            assertDoesNotThrow(connector::close, "Closing the connector failed");
            assertThat("Not unauthorized after close().", connector.isAuthorized(), is(false));

            /* Verify that (private) token has indeed been removed */
            Field tokenField = HTTPVaultConnector.class.getDeclaredField("token");
            tokenField.setAccessible(true);
            assertThat("Token not removed after close().", tokenField.get(connector), is(nullValue()));
        }
    }

    /**
     * Initialize Vault with resource datastore and generated configuration.
     *
     * @param dir Directory to place test data.
     * @param tls Use TLS.
     * @return Vault Configuration
     * @throws IllegalStateException on error
     */
    private VaultConfiguration initializeVault(File dir, boolean tls) throws IllegalStateException, IOException {
        File dataDir = new File(dir, "data");
        copyDirectory(new File(getClass().getResource("/data_dir").getPath()), dataDir);

        /* Generate vault local unencrypted configuration */
        VaultConfiguration config = new VaultConfiguration()
                .withHost("localhost")
                .withPort(getFreePort())
                .withDataLocation(dataDir.toPath())
                .disableMlock();

        /* Enable TLS with custom certificate and key, if required */
        if (tls) {
            config.enableTLS()
                    .withCert(getClass().getResource("/tls/server.pem").getPath())
                    .withKey(getClass().getResource("/tls/server.key").getPath());
        }

        /* Write configuration file */
        BufferedWriter bw = null;
        File configFile;
        try {
            configFile = new File(dir, "vault.conf");
            bw = new BufferedWriter(new FileWriter(configFile));
            bw.write(config.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate config file.", e);
        } finally {
            try {
                if (bw != null)
                    bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* Start vault process */
        try {
            vaultProcess = Runtime.getRuntime().exec("vault server -config " + configFile.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start vault. Make sure vault binary is in your executable path.", e);
        }

        return config;
    }

    /**
     * Authenticate with root token.
     */
    private void authRoot() {
        /* Authenticate as valid user */
        assertDoesNotThrow(() -> connector.authToken(TOKEN_ROOT));
    }

    /**
     * Authenticate with user credentials.
     */
    private void authUser() {
        assertDoesNotThrow(() -> connector.authUserPass(USER_VALID, PASS_VALID));
    }

    /**
     * Find and return a free TCP port.
     *
     * @return port number
     */
    private static Integer getFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        throw new IllegalStateException("Unable to find a free TCP port.");
    }

    /**
     * Retrieve StackTrace from throwable as string
     *
     * @param th the throwable
     * @return the stack trace
     */
    private static String stackTrace(final Throwable th) {
        StringWriter sw = new StringWriter();
        th.printStackTrace(new PrintWriter(sw, true));
        return sw.getBuffer().toString();
    }
}
