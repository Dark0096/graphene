package net.iponweb.disthene.carbon;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import net.engio.mbassy.bus.MBassador;
import net.iponweb.disthene.config.DistheneConfiguration;
import net.iponweb.disthene.events.DistheneEvent;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * @author Andrei Ivanov
 */
@Component
public class CarbonServer {
    private static final int MAX_FRAME_LENGTH = 8192 ;
    private Logger logger = Logger.getLogger(CarbonServer.class);

    private DistheneConfiguration configuration;

    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    private MBassador<DistheneEvent> bus;

    public CarbonServer(DistheneConfiguration configuration, MBassador<DistheneEvent> bus) {
        this.bus = bus;
        this.configuration = configuration;
    }

    @PostConstruct
    public void run() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new DelimiterBasedFrameDecoder(MAX_FRAME_LENGTH, false, Delimiters.lineDelimiter()));
                        p.addLast(new CarbonServerHandler(bus, configuration.getCarbon().getBaseRollup()));
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        logger.error(cause);
                        super.exceptionCaught(ctx, cause);
                    }
                });

        // Start the server.
        b.bind(configuration.getCarbon().getBind(), configuration.getCarbon().getPort()).sync();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down boss group");
        bossGroup.shutdownGracefully().awaitUninterruptibly(60000);

        logger.info("Shutting down worker group");
        workerGroup.shutdownGracefully().awaitUninterruptibly(60000);
    }
}
