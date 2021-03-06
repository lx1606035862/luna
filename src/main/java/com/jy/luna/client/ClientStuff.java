package com.jy.luna.client;

import com.jy.luna.client.proxy.ProxyFactory;
import com.jy.luna.protocol.RpcDecoder;
import com.jy.luna.protocol.RpcEncoder;
import com.jy.luna.protocol.RpcRequest;
import com.jy.luna.protocol.RpcResponse;
import com.jy.luna.stuff.common.LunaConfigure;
import com.jy.luna.stuff.common.LunaUtils;
import com.jy.luna.stuff.exception.LunaException;
import com.jy.luna.xsd.LunaXsdHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * Created by neo on 2017/6/20.
 */
public class ClientStuff {

    public ClientStuff initProxy2Spring(ApplicationContext applicationContext) {

        Set<String> servicePathList = LunaXsdHandler.serviceTimeoutMap.keySet();

        if(servicePathList != null && !servicePathList.isEmpty()) {
            for(String se : servicePathList) {

                Class<?> clz = null;
                Object pox = null;

                try {
                    clz = Class.forName(se);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                try {
                    pox =  ProxyFactory.createProxy(clz);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(pox == null) throw new LunaException("Luna: There was an error in the init proxy : " + se);

                DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
                beanFactory.registerSingleton(LunaUtils.lowcaseFirst(clz.getSimpleName()), pox);
            }
        }

        return this;
    }

    EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    public void connectServerProcessor(InetSocketAddress remotePeer, String serviceFullName) throws Exception {
        LunaConfigure.execuService.submit(() -> {
            Bootstrap b = new Bootstrap();
            try {
                b.group(eventLoopGroup).channel(NioSocketChannel.class).handler(
                        new ChannelInitializer() {
                            @Override
                            protected void initChannel(Channel socketChannel) throws Exception {
                                socketChannel.pipeline()
//                                    .addLast(new LengthFieldBasedFrameDecoder(65537, 0, 4, 0, 0))
                                    .addLast(new RpcEncoder(RpcRequest.class))
                                    .addLast(new RpcDecoder(RpcResponse.class))
                                    .addLast(new ClientHandler());
                            }
                        }
                )/*.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 6000)*/;

            ChannelFuture f = b.connect(remotePeer).addListener(
                        new ChannelFutureListener() {
                            @Override
                            public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                                if (channelFuture.isSuccess()) {

                                    ClientHandler handler = channelFuture.channel().pipeline().get(ClientHandler.class);
                                    ClientCoreProcessor.getInstance().addHandler(handler, serviceFullName);
                                }
                            }
                        }
                ).sync();


            /*why i add this code then channel.writeAndFlush(request) will give me --- netty event executor terminated(exception)
            is it shutdown now?
            because i use the same eventLoopGroup to connect server*/

            f.channel().closeFuture().sync();// Wait until the connection is closed.

            } catch (InterruptedException e) {
                e.printStackTrace();
            }/* finally {
                eventLoopGroup.shutdownGracefully();
            }*/
        });
    }

}
