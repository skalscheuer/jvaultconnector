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

package de.stklcode.jvault.connector.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Simple Vault data response.
 *
 * @author  Stefan Kalscheuer
 * @since   0.4.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RawDataResponse extends VaultDataResponse {
    private Map<String, Object> data;

    @Override
    public void setData(final Map<String, Object> data) {
        this.data = data;
    }

    /**
     * @return Raw data {@link Map}
     */
    public Map<String, Object> getData() {
        return data;
    }
}
