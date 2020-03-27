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

package com.dajudge.kafkaproxy.networking.downstream;

import com.dajudge.kafkaproxy.ProxyChannelManager;
import com.dajudge.kafkaproxy.ca.KeyStoreWrapper;
import com.dajudge.kafkaproxy.ca.ProxyClientCertificateAuthorityFactory.CertificateAuthority;
import com.dajudge.kafkaproxy.ca.UpstreamCertificateSupplier;
import com.dajudge.kafkaproxy.config.ApplicationConfig;
import com.dajudge.kafkaproxy.networking.upstream.ForwardChannel;
import com.dajudge.kafkaproxy.networking.upstream.ForwardChannelFactory;
import com.dajudge.kafkaproxy.protocol.KafkaMessageSplitter;
import com.dajudge.kafkaproxy.protocol.KafkaRequestProcessor;
import com.dajudge.kafkaproxy.protocol.KafkaRequestStore;
import com.dajudge.kafkaproxy.protocol.KafkaResponseProcessor;
import com.dajudge.kafkaproxy.protocol.rewrite.CompositeRewriter;
import com.dajudge.kafkaproxy.protocol.rewrite.FindCoordinatorRewriter;
import com.dajudge.kafkaproxy.protocol.rewrite.MetadataRewriter;
import com.dajudge.kafkaproxy.protocol.rewrite.ResponseRewriter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.function.Supplier;

import static com.dajudge.kafkaproxy.ca.ProxyClientCertificateAuthorityFactoryRegistry.createCertificateFactory;
import static java.util.Arrays.asList;

public class DownstreamChannelFactory implements ForwardChannelFactory {
    private final ProxyChannelManager proxyChannelManager;
    private final String downstreamHostname;
    private final int downstreamPort;
    private final ApplicationConfig appConfig;
    private final EventLoopGroup downstreamWorkerGroup;

    public DownstreamChannelFactory(
            final ProxyChannelManager proxyChannelManager,
            final String downstreamHostname,
            final int downstreamPort,
            final ApplicationConfig appConfig,
            final EventLoopGroup downstreamWorkerGroup
    ) {
        this.proxyChannelManager = proxyChannelManager;
        this.downstreamHostname = downstreamHostname;
        this.downstreamPort = downstreamPort;
        this.appConfig = appConfig;
        this.downstreamWorkerGroup = downstreamWorkerGroup;
    }

    @Override
    public ForwardChannel<ByteBuf> create(
            final UpstreamCertificateSupplier certificateSupplier,
            final ForwardChannel<ByteBuf> upstreamSink
    ) {
        final ResponseRewriter rewriter = new CompositeRewriter(asList(
                new MetadataRewriter(proxyChannelManager),
                new FindCoordinatorRewriter(proxyChannelManager)
        ));
        final KafkaRequestStore requestStore = new KafkaRequestStore(rewriter);
        final KafkaResponseProcessor responseProcessor = new KafkaResponseProcessor(upstreamSink, requestStore);
        final KafkaMessageSplitter responseStreamSplitter = new KafkaMessageSplitter(responseProcessor);
        final Supplier<KeyStoreWrapper> clientKeystoreSupplier = createClientKeyStoreSupplier(certificateSupplier);
        final DownstreamClient downstreamClient = new DownstreamClient(
                downstreamHostname,
                downstreamPort,
                appConfig,
                responseStreamSplitter,
                downstreamWorkerGroup,
                clientKeystoreSupplier
        );
        final KafkaRequestProcessor requestProcessor = new KafkaRequestProcessor(
                downstreamClient,
                requestStore
        );
        return new KafkaMessageSplitter(requestProcessor);
    }

    private Supplier<KeyStoreWrapper> createClientKeyStoreSupplier(
            final UpstreamCertificateSupplier certificateSupplier
    ) {
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
        final CertificateAuthority clientAuthority = createCertificateFactory(
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
