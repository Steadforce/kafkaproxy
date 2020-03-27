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

import com.dajudge.kafkaproxy.config.ApplicationConfig;
import com.dajudge.kafkaproxy.networking.Endpoint;
import com.dajudge.kafkaproxy.networking.FilterFactory;
import com.dajudge.kafkaproxy.networking.downstream.DownstreamChannelFactory;
import com.dajudge.kafkaproxy.networking.downstream.DownstreamSslConfig;
import com.dajudge.kafkaproxy.networking.upstream.DownstreamSinkFactory;
import com.dajudge.kafkaproxy.networking.upstream.ProxyChannel;
import com.dajudge.kafkaproxy.networking.upstream.UpstreamSslConfig;
import com.dajudge.kafkaproxy.protocol.KafkaMessageSplitter;
import com.dajudge.kafkaproxy.protocol.KafkaRequestProcessor;
import com.dajudge.kafkaproxy.protocol.KafkaRequestStore;
import com.dajudge.kafkaproxy.protocol.KafkaResponseProcessor;
import com.dajudge.kafkaproxy.protocol.rewrite.CompositeRewriter;
import com.dajudge.kafkaproxy.protocol.rewrite.FindCoordinatorRewriter;
import com.dajudge.kafkaproxy.protocol.rewrite.MetadataRewriter;
import com.dajudge.kafkaproxy.protocol.rewrite.ResponseRewriter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;

public class KafkaProxyChannelFactory {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaProxyChannelFactory.class);
    private final ApplicationConfig appConfig;
    private final BrokerMapper brokerMapper;
    private final NioEventLoopGroup downstreamWorkerGroup;
    private final NioEventLoopGroup serverWorkerGroup;
    private final NioEventLoopGroup upstreamWorkerGroup;
    private final ClientCertificateAuthorityImpl clientCertificateAuthority;

    public KafkaProxyChannelFactory(
            final ApplicationConfig appConfig,
            final BrokerMapper brokerMapper,
            final NioEventLoopGroup downstreamWorkerGroup,
            final NioEventLoopGroup serverWorkerGroup,
            final NioEventLoopGroup upstreamWorkerGroup
    ) {
        this.appConfig = appConfig;
        this.brokerMapper = brokerMapper;
        this.downstreamWorkerGroup = downstreamWorkerGroup;
        this.serverWorkerGroup = serverWorkerGroup;
        this.upstreamWorkerGroup = upstreamWorkerGroup;
        this.clientCertificateAuthority = new ClientCertificateAuthorityImpl(appConfig);
    }

    public ProxyChannel create(final ProxyChannelManager manager, final Endpoint endpoint) {
        final BrokerMapping brokerToProxy = brokerMapper.getBrokerMapping(endpoint);
        if (brokerToProxy == null) {
            throw new IllegalArgumentException("No proxy configuration provided for " + endpoint);
        }
        final ResponseRewriter rewriter = new CompositeRewriter(asList(
                new MetadataRewriter(manager),
                new FindCoordinatorRewriter(manager)
        ));
        final KafkaRequestStore requestStore = new KafkaRequestStore(rewriter);
        final FilterFactory<ByteBuf> upstreamFilterFactory = upstream ->
                new KafkaMessageSplitter(new KafkaResponseProcessor(upstream, requestStore));
        final FilterFactory<ByteBuf> downstreamFilterFactory = downstream ->
                new KafkaMessageSplitter(new KafkaRequestProcessor(downstream, requestStore));
        final Endpoint downstreamEndpoint = brokerToProxy.getBroker();
        final Endpoint upstreamEndpoint = brokerToProxy.getProxy();
        final DownstreamSinkFactory downstreamSinkFactory = new DownstreamChannelFactory(
                downstreamEndpoint,
                appConfig.get(DownstreamSslConfig.class),
                downstreamWorkerGroup,
                clientCertificateAuthority
        );
        final ProxyChannel proxyChannel = new ProxyChannel(
                upstreamEndpoint,
                appConfig.get(UpstreamSslConfig.class),
                serverWorkerGroup,
                upstreamWorkerGroup,
                downstreamSinkFactory,
                upstreamFilterFactory,
                downstreamFilterFactory
        );
        LOG.info("Proxying {} as {}", downstreamEndpoint, upstreamEndpoint);
        return proxyChannel;
    }

    public BrokerMapping bootstrap(final ProxyChannelManager manager) {
        return manager.getByBrokerEndpoint(brokerMapper.getBootstrapBroker());
    }
}