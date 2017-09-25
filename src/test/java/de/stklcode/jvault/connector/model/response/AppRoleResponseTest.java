package de.stklcode.jvault.connector.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.stklcode.jvault.connector.exception.InvalidResponseException;
import de.stklcode.jvault.connector.model.AppRole;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * JUnit Test for {@link AppRoleResponse} model.
 *
 * @author Stefan Kalscheuer
 * @since 0.6.2
 */
public class AppRoleResponseTest {
    private static final Integer ROLE_TOKEN_TTL = 1200;
    private static final Integer ROLE_TOKEN_MAX_TTL = 1800;
    private static final Integer ROLE_SECRET_TTL = 600;
    private static final Integer ROLE_SECRET_NUM_USES = 40;
    private static final String ROLE_POLICY = "default";
    private static final Integer ROLE_PERIOD = 0;
    private static final Boolean ROLE_BIND_SECRET = true;

    private static final String RES_JSON = "{\n" +
            "  \"auth\": null,\n" +
            "  \"warnings\": null,\n" +
            "  \"wrap_info\": null,\n" +
            "  \"data\": {\n" +
            "    \"token_ttl\": " + ROLE_TOKEN_TTL + ",\n" +
            "    \"token_max_ttl\": " + ROLE_TOKEN_MAX_TTL + ",\n" +
            "    \"secret_id_ttl\": " + ROLE_SECRET_TTL + ",\n" +
            "    \"secret_id_num_uses\": " + ROLE_SECRET_NUM_USES + ",\n" +
            "    \"policies\": [\n" +
            "      \"" + ROLE_POLICY + "\"\n" +
            "    ],\n" +
            "    \"period\": " + ROLE_PERIOD + ",\n" +
            "    \"bind_secret_id\": " + ROLE_BIND_SECRET + ",\n" +
            "    \"bound_cidr_list\": \"\"\n" +
            "  },\n" +
            "  \"lease_duration\": 0,\n" +
            "  \"renewable\": false,\n" +
            "  \"lease_id\": \"\"\n" +
            "}";

    private static final Map<String, Object> INVALID_DATA = new HashMap<>();

    static {
        INVALID_DATA.put("policies", "fancy-policy");
    }

    /**
     * Test getter, setter and get-methods for response data.
     */
    @Test
    public void getDataRoundtrip() {
        // Create empty Object.
        AppRoleResponse res = new AppRoleResponse();
        assertThat("Initial data should be empty", res.getRole(), is(nullValue()));

        // Parsing invalid auth data map should fail.
        try {
            res.setData(INVALID_DATA);
            fail("Parsing invalid data succeeded");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(InvalidResponseException.class)));
        }
    }

    /**
     * Test creation from JSON value as returned by Vault (JSON example copied from Vault documentation).
     */
    @Test
    public void jsonRoundtrip() {
        try {
            AppRoleResponse res = new ObjectMapper().readValue(RES_JSON, AppRoleResponse.class);
            assertThat("Parsed response is NULL", res, is(notNullValue()));
            // Extract role data.
            AppRole role = res.getRole();
            assertThat("Role data is NULL", role, is(notNullValue()));
            assertThat("Incorrect token TTL", role.getTokenTtl(), is(ROLE_TOKEN_TTL));
            assertThat("Incorrect token max TTL", role.getTokenMaxTtl(), is(ROLE_TOKEN_MAX_TTL));
            assertThat("Incorrect secret ID TTL", role.getSecretIdTtl(), is(ROLE_SECRET_TTL));
            assertThat("Incorrect secret ID umber of uses", role.getSecretIdNumUses(), is(ROLE_SECRET_NUM_USES));
            assertThat("Incorrect number of policies", role.getPolicies(), hasSize(1));
            assertThat("Incorrect role policies", role.getPolicies(), contains(ROLE_POLICY));
            assertThat("Incorrect role period", role.getPeriod(), is(ROLE_PERIOD));
            assertThat("Incorrect role bind secret ID flag", role.getBindSecretId(), is(ROLE_BIND_SECRET));
            assertThat("Incorrect biund CIDR list", role.getBoundCidrList(), is(nullValue()));
            assertThat("Incorrect biund CIDR list string", role.getBoundCidrListString(), is(emptyString()));
        } catch (IOException e) {
            fail("AuthResponse deserialization failed: " + e.getMessage());
        }
    }
}