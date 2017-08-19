package de.stklcode.jvault.connector.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.stklcode.jvault.connector.exception.InvalidResponseException;
import de.stklcode.jvault.connector.model.response.embedded.AuthData;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * JUnit Test for {@link AuthResponse} model.
 *
 * @author Stefan Kalscheuer
 * @since 0.6.2
 */
public class AuthResponseTest {
    private static final String AUTH_ACCESSOR = "2c84f488-2133-4ced-87b0-570f93a76830";
    private static final String AUTH_CLIENT_TOKEN = "ABCD";
    private static final String AUTH_POLICY_1 = "web";
    private static final String AUTH_POLICY_2 = "stage";
    private static final String AUTH_META_KEY = "user";
    private static final String AUTH_META_VALUE = "armon";
    private static final Integer AUTH_LEASE_DURATION = 3600;
    private static final Boolean AUTH_RENEWABLE = true;

    private static final String RES_JSON = "{\n" +
            "  \"auth\": {\n" +
            "    \"accessor\": \"" + AUTH_ACCESSOR + "\",\n" +
            "    \"client_token\": \"" + AUTH_CLIENT_TOKEN + "\",\n" +
            "    \"policies\": [\n" +
            "      \"" + AUTH_POLICY_1 + "\", \n" +
            "      \"" + AUTH_POLICY_2 + "\"\n" +
            "    ],\n" +
            "    \"metadata\": {\n" +
            "      \"" + AUTH_META_KEY + "\": \"" + AUTH_META_VALUE + "\"\n" +
            "    },\n" +
            "    \"lease_duration\": " + AUTH_LEASE_DURATION + ",\n" +
            "    \"renewable\": " + AUTH_RENEWABLE + "\n" +
            "  }\n" +
            "}";

    private static final Map<String, Object> INVALID_AUTH_DATA = new HashMap<>();

    static {
        INVALID_AUTH_DATA.put("policies", "fancy-policy");
    }

    /**
     * Test getter, setter and get-methods for response data.
     */
    @Test
    public void getDataRoundtrip() {
        // Create empty Object.
        AuthResponse res = new AuthResponse();
        assertThat("Initial data should be empty", res.getData(), is(nullValue()));

        // Parsing invalid auth data map should fail.
        try {
            res.setAuth(INVALID_AUTH_DATA);
            fail("Parsing invalid auth data succeeded");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(InvalidResponseException.class)));
        }

        // Data method should be agnostic.
        res.setData(INVALID_AUTH_DATA);
        assertThat("Data not passed through", res.getData(), is(INVALID_AUTH_DATA));
    }

    /**
     * Test creation from JSON value as returned by Vault (JSON example copied from Vault documentation).
     */
    @Test
    public void jsonRoundtrip() {
        try {
            AuthResponse res = new ObjectMapper().readValue(RES_JSON, AuthResponse.class);
            assertThat("Parsed response is NULL", res, is(notNullValue()));
            // Extract auth data.
            AuthData data = res.getAuth();
            assertThat("Auth data is NULL", data, is(notNullValue()));
            assertThat("Incorrect auth accessor", data.getAccessor(), is(AUTH_ACCESSOR));
            assertThat("Incorrect auth client token", data.getClientToken(), is(AUTH_CLIENT_TOKEN));
            assertThat("Incorrect auth lease duration", data.getLeaseDuration(), is(AUTH_LEASE_DURATION));
            assertThat("Incorrect auth renewable flag", data.isRenewable(), is(AUTH_RENEWABLE));
            assertThat("Incorrect number of policies", data.getPolicies(), hasSize(2));
            assertThat("Incorrect auth policies", data.getPolicies(), containsInAnyOrder(AUTH_POLICY_1, AUTH_POLICY_2));
        } catch (IOException e) {
            fail("AuthResponse deserialization failed: " + e.getMessage());
        }
    }
}
