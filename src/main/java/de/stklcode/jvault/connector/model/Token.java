/*
 * Copyright 2016-2019 Stefan Kalscheuer
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

package de.stklcode.jvault.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Vault Token metamodel.
 *
 * @author Stefan Kalscheuer
 * @since 0.4.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Token {
    /**
     * Get {@link TokenBuilder} instance.
     *
     * @return Token Builder.
     * @since 0.8
     */
    public static TokenBuilder builder() {
        return new TokenBuilder();
    }

    @JsonProperty("id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String id;

    @JsonProperty("type")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String type;

    @JsonProperty("display_name")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String displayName;

    @JsonProperty("no_parent")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean noParent;

    @JsonProperty("no_default_policy")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean noDefaultPolicy;

    @JsonProperty("ttl")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer ttl;

    @JsonProperty("num_uses")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer numUses;

    @JsonProperty("policies")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> policies;

    @JsonProperty("meta")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> meta;

    @JsonProperty("renewable")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean renewable;

    /**
     * Construct complete {@link Token} object with default type.
     *
     * @param id              Token ID (optional)
     * @param displayName     Token display name (optional)
     * @param noParent        Token has no parent (optional)
     * @param noDefaultPolicy Do not add default policy (optional)
     * @param ttl             Token TTL in seconds (optional)
     * @param numUses         Number of uses (optional)
     * @param policies        List of policies (optional)
     * @param meta            Metadata (optional)
     * @param renewable       Is the token renewable (optional)
     * @deprecated As of 0.9, use {@link #Token(String, String, String, Boolean, Boolean, Integer, Integer, List, Map, Boolean)} instead.
     */
    @Deprecated
    public Token(final String id,
                 final String displayName,
                 final Boolean noParent,
                 final Boolean noDefaultPolicy,
                 final Integer ttl,
                 final Integer numUses,
                 final List<String> policies,
                 final Map<String, String> meta,
                 final Boolean renewable) {
        this(id, Type.DEFAULT.value(), displayName, noParent, noDefaultPolicy, ttl, numUses, policies, meta, renewable);
    }

    /**
     * Construct complete {@link Token} object.
     *
     * @param id              Token ID (optional)
     * @param displayName     Token display name (optional)
     * @param noParent        Token has no parent (optional)
     * @param noDefaultPolicy Do not add default policy (optional)
     * @param ttl             Token TTL in seconds (optional)
     * @param numUses         Number of uses (optional)
     * @param policies        List of policies (optional)
     * @param meta            Metadata (optional)
     * @param renewable       Is the token renewable (optional)
     */
    public Token(final String id,
                 final String type,
                 final String displayName,
                 final Boolean noParent,
                 final Boolean noDefaultPolicy,
                 final Integer ttl,
                 final Integer numUses,
                 final List<String> policies,
                 final Map<String, String> meta,
                 final Boolean renewable) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.ttl = ttl;
        this.numUses = numUses;
        this.noParent = noParent;
        this.noDefaultPolicy = noDefaultPolicy;
        this.policies = policies;
        this.meta = meta;
        this.renewable = renewable;
    }

    /**
     * @return Token ID
     */
    public String getId() {
        return id;
    }

    /**
     * @return Token type
     * @since 0.9
     */
    public String getType() {
        return type;
    }

    /**
     * @return Token display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return Token has no parent
     */
    public Boolean getNoParent() {
        return noParent;
    }

    /**
     * @return Token has no default policy
     */
    public Boolean getNoDefaultPolicy() {
        return noDefaultPolicy;
    }

    /**
     * @return Time-to-live in seconds
     */
    public Integer getTtl() {
        return ttl;
    }

    /**
     * @return Number of uses
     */
    public Integer getNumUses() {
        return numUses;
    }

    /**
     * @return List of policies
     */
    public List<String> getPolicies() {
        return policies;
    }

    /**
     * @return Metadata
     */
    public Map<String, String> getMeta() {
        return meta;
    }

    /**
     * @return Token is renewable
     */
    public Boolean isRenewable() {
        return renewable;
    }

    /**
     * Constants for token types.
     */
    public enum Type {
        DEFAULT("default"),
        BATCH("batch"),
        SERVICE("service"),
        DEFAULT_SERVICE("default-service"),
        DEFAULT_BATCH("default-batch");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
