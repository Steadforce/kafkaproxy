/*
 * Copyright 2019-2020 Alex Stockinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.dajudge.kafkaproxy.config.kafkassl;

import com.dajudge.kafkaproxy.config.ConfigSource;
import com.dajudge.kafkaproxy.config.Environment;
import com.dajudge.kafkaproxy.networking.downstream.ClientCertificateStrategy;
import com.dajudge.kafkaproxy.networking.downstream.DownstreamSslConfig;

public class KafkaSslConfigSource implements ConfigSource<DownstreamSslConfig> {
    private static final String KAFKA_SSL_PREFIX = PREFIX + "KAFKA_SSL_";
    private static final String ENV_KAFKA_SSL_ENABLED = KAFKA_SSL_PREFIX + "ENABLED";
    private static final String ENV_KAFKA_SSL_TRUSTSTORE_LOCATION = KAFKA_SSL_PREFIX + "TRUSTSTORE_LOCATION";
    private static final String ENV_KAFKA_SSL_TRUSTSTORE_PASSWORD = KAFKA_SSL_PREFIX + "TRUSTSTORE_PASSWORD";
    private static final String ENV_KAFKA_SSL_KEYSTORE_LOCATION = KAFKA_SSL_PREFIX + "KEYSTORE_LOCATION";
    private static final String ENV_KAFKA_SSL_KEYSTORE_PASSWORD = KAFKA_SSL_PREFIX + "KEYSTORE_PASSWORD";
    private static final String ENV_KAFKA_SSL_KEY_PASSWORD = KAFKA_SSL_PREFIX + "KEY_PASSWORD";
    private static final String ENV_KAFKA_CLIENT_CERT_STRATEGY = KAFKA_SSL_PREFIX + "CLIENT_CERT_STRATEGY";
    private static final String ENV_KAFKA_SSL_VERIFY_HOSTNAME = KAFKA_SSL_PREFIX + "VERIFY_HOSTNAME";
    private static final String ENV_KAFKA_SSL_CERTIFICATE_FACTORY = KAFKA_SSL_PREFIX + "CERTIFICATE_FACTORY";
    private static final boolean DEFAULT_KAFKA_SSL_ENABLED = false;
    private static final boolean DEFAULT_KAFKA_SSL_VERIFY_HOSTNAME = true;
    private static final String DEFAULT_CERTIFICATE_FACTORY = "null";

    @Override
    public Class<DownstreamSslConfig> getConfigClass() {
        return DownstreamSslConfig.class;
    }

    @Override
    public DownstreamSslConfig parse(final Environment environment) {
        if (!environment.requiredBoolean(ENV_KAFKA_SSL_ENABLED, DEFAULT_KAFKA_SSL_ENABLED)) {
            return DownstreamSslConfig.DISABLED;
        }
        return new DownstreamSslConfig(
                environment.requiredBoolean(ENV_KAFKA_SSL_ENABLED, DEFAULT_KAFKA_SSL_ENABLED),
                environment.optionalFile(ENV_KAFKA_SSL_TRUSTSTORE_LOCATION).orElse(null),
                environment.optionalString(ENV_KAFKA_SSL_TRUSTSTORE_PASSWORD).orElse(null),
                environment.requiredBoolean(ENV_KAFKA_SSL_VERIFY_HOSTNAME, DEFAULT_KAFKA_SSL_VERIFY_HOSTNAME),
                environment.requiredString(ENV_KAFKA_SSL_CERTIFICATE_FACTORY, DEFAULT_CERTIFICATE_FACTORY),
                ClientCertificateStrategy.valueOf(environment.requiredString(ENV_KAFKA_CLIENT_CERT_STRATEGY, "NONE")),
                environment.optionalFile(ENV_KAFKA_SSL_KEYSTORE_LOCATION).orElse(null),
                environment.optionalString(ENV_KAFKA_SSL_KEYSTORE_PASSWORD).orElse(null),
                environment.optionalString(ENV_KAFKA_SSL_KEY_PASSWORD).orElse(null)
        );
    }
}
