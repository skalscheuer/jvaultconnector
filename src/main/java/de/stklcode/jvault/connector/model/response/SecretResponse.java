/*
 * Copyright 2016-2017 Stefan Kalscheuer
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

package de.stklcode.jvault.connector.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.stklcode.jvault.connector.exception.InvalidResponseException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Vault response for secret request.
 *
 * @author Stefan Kalscheuer
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecretResponse extends VaultDataResponse {
    private Map<String, Object> data;

    @Override
    public final void setData(final Map<String, Object> data) throws InvalidResponseException {
        this.data = data;
    }

    /**
     * Get complete data object.
     *
     * @return data map
     * @since 0.4.0
     */
    public final Map<String, Object> getData() {
        if (data == null)
            return new HashMap<>();
        return data;
    }

    /**
     * Get a single value for given key.
     *
     * @param key the key
     * @return the value or NULL if absent
     * @since 0.4.0
     */
    public final Object get(final String key) {
        if (data == null)
            return null;
        return getData().get(key);
    }

    /**
     * Get data element for key "value".
     * Method for backwards compatibility in case of simple secrets.
     *
     * @return the value
     * @deprecated Deprecated artifact, will be removed at latest at v1.0.0
     */
    @Deprecated
    public final String getValue() {
        if (get("value") == null)
            return null;
        return get("value").toString();
    }

    /**
     * Get response parsed as JSON
     *
     * @param type Class to parse response
     * @param <T>  Class to parse response
     * @return Parsed object
     * @throws InvalidResponseException on parsing error
     * @since 0.3
     * @deprecated Deprecated artifact, will be removed at latest at v1.0.0
     */
    @Deprecated
    public final <T> T getValue(final Class<T> type) throws InvalidResponseException {
        return get("value", type);
    }

    /**
     * Get response parsed as JSON
     *
     * @param key the key
     * @param type Class to parse response
     * @param <T>  Class to parse response
     * @return Parsed object
     * @throws InvalidResponseException on parsing error
     * @since 0.4.0
     */
    public final <T> T get(final String key, final Class<T> type) throws InvalidResponseException {
        try {
            return new ObjectMapper().readValue(get(key).toString(), type);
        } catch (IOException e) {
            throw new InvalidResponseException("Unable to parse response payload: " + e.getMessage());
        }
    }
}
