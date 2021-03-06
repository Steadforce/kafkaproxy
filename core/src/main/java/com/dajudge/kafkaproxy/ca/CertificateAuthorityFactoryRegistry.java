/*
 * Copyright 2019-2020 The kafkaproxy developers (see CONTRIBUTORS)
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

package com.dajudge.kafkaproxy.ca;

import com.dajudge.kafkaproxy.config.ApplicationConfig;
import com.dajudge.proxybase.ca.CertificateAuthority;

import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.ServiceLoader.load;
import static java.util.function.Function.identity;
import static java.util.stream.StreamSupport.stream;

public class CertificateAuthorityFactoryRegistry {
    private static final Map<String, CertificateAuthorityFactory> FACTORIES = collectFactories();

    public static CertificateAuthority createCertificateFactory(
            final String name,
            final ApplicationConfig config
    ) {
        final CertificateAuthorityFactory factory = FACTORIES.get(name);
        return ofNullable(factory)
                .orElseThrow(() -> new IllegalArgumentException("No such proxy client-certificate factory: " + name))
                .createFactory(config);
    }

    private static Map<String, CertificateAuthorityFactory> collectFactories() {
        return stream(load(CertificateAuthorityFactory.class).spliterator(), false)
                .collect(Collectors.toMap(
                        CertificateAuthorityFactory::getName,
                        identity()
                ));
    }
}
