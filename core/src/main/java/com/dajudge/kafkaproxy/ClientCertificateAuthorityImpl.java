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

package com.dajudge.kafkaproxy;

import com.dajudge.kafkaproxy.ca.KeyStoreWrapper;
import com.dajudge.kafkaproxy.ca.ProxyClientCertificateAuthorityFactory;
import com.dajudge.kafkaproxy.ca.UpstreamCertificateSupplier;
import com.dajudge.kafkaproxy.config.ApplicationConfig;
import com.dajudge.kafkaproxy.networking.downstream.ClientCertificateAuthority;
import com.dajudge.kafkaproxy.networking.downstream.DownstreamSslConfig;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.function.Supplier;

import static com.dajudge.kafkaproxy.ca.ProxyClientCertificateAuthorityFactoryRegistry.createCertificateFactory;

public class ClientCertificateAuthorityImpl implements ClientCertificateAuthority {
    private final ApplicationConfig appConfig;

    public ClientCertificateAuthorityImpl(final ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public Supplier<KeyStoreWrapper> apply(final UpstreamCertificateSupplier certificateSupplier) {
        final DownstreamSslConfig sslConfig = appConfig.get(DownstreamSslConfig.class);
        switch (sslConfig.getClientCertificateStrategy()) {
            case KEYSTORE:
                return createKeyStoreSupplier(sslConfig);
            case CA:
                return createCaKeyStoreSupplier(sslConfig, certificateSupplier);
            case NONE:
                return emptyKeyStoreSupplier();
            default:
                throw new IllegalArgumentException("Unhandled client certificate strategy: "
                        + sslConfig.getClientCertificateStrategy());
        }
    }

    private Supplier<KeyStoreWrapper> emptyKeyStoreSupplier() {
        return () -> {
            try {
                final KeyStore keyStore = KeyStore.getInstance("jks");
                keyStore.load(null, null);
                return new KeyStoreWrapper(keyStore, "noPassword");
            } catch (final KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                throw new RuntimeException("Failed to create empty key store", e);
            }
        };
    }

    private Supplier<KeyStoreWrapper> createKeyStoreSupplier(final DownstreamSslConfig sslConfig) {
        try (final InputStream is = sslConfig.getKeyStore().get()) {
            final KeyStore keyStore = KeyStore.getInstance("jks");
            keyStore.load(is, sslConfig.getKeyStorePassword().toCharArray());
            final KeyStoreWrapper wrapper = new KeyStoreWrapper(keyStore, sslConfig.getKeyPassword());
            return () -> wrapper;
        } catch (final IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Failed to load client key store", e);
        }
    }

    private Supplier<KeyStoreWrapper> createCaKeyStoreSupplier(
            final DownstreamSslConfig sslConfig,
            final UpstreamCertificateSupplier certificateSupplier) {
        final ProxyClientCertificateAuthorityFactory.CertificateAuthority clientAuthority = createCertificateFactory(
                sslConfig.getCertificateFactory(),
                appConfig
        );
        return () -> {
            try {
                return clientAuthority.createClientCertificate(certificateSupplier);
            } catch (final SSLPeerUnverifiedException e) {
                throw new RuntimeException("Client did not provide certificate", e);
            }
        };
    }
}