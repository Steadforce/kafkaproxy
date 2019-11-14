package com.dajudge.kafkaproxy;

import com.dajudge.kafkaproxy.config.ApplicationConfig;
import com.dajudge.kafkaproxy.config.RealEnvironment;
import com.dajudge.kafkaproxy.config.broker.BrokerConfig;
import com.dajudge.kafkaproxy.config.Environment;
import com.dajudge.kafkaproxy.networking.downstream.DownstreamChannelFactory;
import com.dajudge.kafkaproxy.networking.downstream.KafkaSslConfig;
import com.dajudge.kafkaproxy.networking.upstream.ForwardChannelFactory;
import com.dajudge.kafkaproxy.networking.upstream.ProxyChannel;
import com.dajudge.kafkaproxy.networking.upstream.ProxySslConfig;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;

import static java.util.stream.Collectors.toList;

public class Startup {
    private static final Logger LOG = LoggerFactory.getLogger(Startup.class);
    private static final String PREFIX = "KAFKAPROXY_";

    public static void main(final String argv[]) {
        final Environment environment = new RealEnvironment();
        final ApplicationConfig appConfig = new ApplicationConfig(environment);
        final BrokerConfig brokerConfig = appConfig.get(BrokerConfig.class);
        final KafkaSslConfig kafkaSslConfig = appConfig.get(KafkaSslConfig.class);
        final ProxySslConfig proxySslConfig = appConfig.get(ProxySslConfig.class);
        final NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();
        final NioEventLoopGroup upstreamWorkerGroup = new NioEventLoopGroup();
        final NioEventLoopGroup downstreamWorkerGroup = new NioEventLoopGroup();
        final Collection<ProxyChannel> proxies = brokerConfig.getBrokersToProxy().stream()
                .map(brokerToProxy -> {
                    final ForwardChannelFactory forwardChannelFactory = new DownstreamChannelFactory(
                            brokerConfig.getBrokerMap(),
                            brokerToProxy.getBroker().getHost(),
                            brokerToProxy.getBroker().getPort(),
                            kafkaSslConfig,
                            downstreamWorkerGroup
                    );
                    final ProxyChannel proxyChannel = new ProxyChannel(
                            brokerToProxy.getProxy().getPort(),
                            proxySslConfig,
                            serverWorkerGroup,
                            upstreamWorkerGroup,
                            forwardChannelFactory
                    );
                    LOG.info(
                            "Started proxy listener for {}:{} on port {}",
                            brokerToProxy.getBroker().getHost(),
                            brokerToProxy.getBroker().getPort(),
                            brokerToProxy.getProxy().getPort()
                    );
                    return proxyChannel;
                }).collect(toList());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            proxies.stream()
                    .map(ProxyChannel::close)
                    .collect(toList())
                    .forEach(future -> {
                        try {
                            future.sync();
                        } catch (final Exception e) {
                            LOG.error("Failed to sync with proxy channel", e);
                        }
                    });
            serverWorkerGroup.shutdownGracefully();
            upstreamWorkerGroup.shutdownGracefully();
            downstreamWorkerGroup.shutdownGracefully();
        }));
    }

    private static File file(final String propName) {
        final String prop = System.getProperty(propName);
        return prop == null ? null : new File(prop);
    }


}
