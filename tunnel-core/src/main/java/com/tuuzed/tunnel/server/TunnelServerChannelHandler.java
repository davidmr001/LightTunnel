package com.tuuzed.tunnel.server;

import com.tuuzed.tunnel.common.logging.Logger;
import com.tuuzed.tunnel.common.logging.LoggerFactory;
import com.tuuzed.tunnel.common.protocol.TunnelMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

import static com.tuuzed.tunnel.common.protocol.TunnelConstants.*;

/**
 * Tunnel服务数据通道处理器
 */
@SuppressWarnings("Duplicates")
public class TunnelServerChannelHandler extends SimpleChannelInboundHandler<TunnelMessage> {
    private static final Logger logger = LoggerFactory.getLogger(TunnelServerChannelHandler.class);


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 隧道断开
        long tunnelToken = ctx.channel().attr(ATTR_TUNNEL_TOKEN).get();
        UserTunnelManager.getInstance().closeUserTunnel(tunnelToken);
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TunnelMessage msg) throws Exception {
        logger.info("Recv: {}", msg);
        switch (msg.getType()) {
            case MESSAGE_TYPE_HEARTBEAT_PING:
                handleHeartbeatPingMessage(ctx, msg);
                break;
            case MESSAGE_TYPE_OPEN_TUNNEL_REQUEST:
                handleOpenTunnelRequestMessage(ctx, msg);
                break;
            case MESSAGE_TYPE_TRANSFER:
                handleTransferMessage(ctx, msg);
                break;
            case MESSAGE_TYPE_LOCAL_TUNNEL_CONNECTED:
                handleLocalTunnelConnectedMessage(ctx, msg);
                break;
            case MESSAGE_TYPE_LOCAL_TUNNEL_DISCONNECT:
                handleLocalTunnelDisconnectMessage(ctx, msg);
                break;
            default:
                break;
        }
    }


    /**
     * 处理心跳消息
     */
    private void handleHeartbeatPingMessage(ChannelHandlerContext ctx, TunnelMessage msg) throws Exception {
        ctx.channel().writeAndFlush(TunnelMessage.newInstance(MESSAGE_TYPE_HEARTBEAT_PONG));
    }

    /**
     * 处理建立隧道请求消息
     */
    private void handleOpenTunnelRequestMessage(ChannelHandlerContext ctx, TunnelMessage msg) throws Exception {
        final byte[] head = msg.getHead();
        final String mapping = new String(head);
        final String[] mappingTuple = mapping.split("<-");
        if (mappingTuple.length != 2) {
            ctx.close();
            return;
        }
        final String[] localAddrAndPortTuple = mappingTuple[0].split(":");
        final String localAddr = localAddrAndPortTuple[0];
        final int localPort = Integer.parseInt(localAddrAndPortTuple[1]);
        final int remotePort = Integer.parseInt(mappingTuple[1]);

        ctx.channel().attr(ATTR_MAPPING).set(mapping);
        ctx.channel().attr(ATTR_LOCAL_ADDR).set(localAddr);
        ctx.channel().attr(ATTR_LOCAL_PORT).set(localPort);
        ctx.channel().attr(ATTR_REMOTE_PORT).set(remotePort);

        long tunnelToken = UserTunnelManager.getInstance().openUserTunnel(remotePort, ctx.channel());
        ctx.writeAndFlush(
                TunnelMessage.newInstance(MESSAGE_TYPE_OPEN_TUNNEL_RESPONSE)
                        .setHead(Unpooled.copyLong(tunnelToken).array())
        );
    }

    /**
     * 处理数据透传消息
     * 数据流向: TunnelClient  ->  UserTunnelManager
     */
    private void handleTransferMessage(ChannelHandlerContext ctx, TunnelMessage msg) throws Exception {
        final ByteBuf head = Unpooled.wrappedBuffer(msg.getHead());
        final long tunnelToken = head.readLong();
        final long sessionToken = head.readLong();
        final UserTunnel tunnel = UserTunnelManager.getInstance().getUserTunnelByTunnelToken(tunnelToken);
        if (tunnel != null) {
            Channel userTunnelChannel = tunnel.getUserTunnelChannel(tunnelToken, sessionToken);
            if (userTunnelChannel != null) {
                userTunnelChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));
            }
        }
    }

    private void handleLocalTunnelConnectedMessage(ChannelHandlerContext ctx, TunnelMessage msg) {
        final ByteBuf head = Unpooled.wrappedBuffer(msg.getHead());
        final long tunnelToken = head.readLong();
        final long sessionToken = head.readLong();
        final UserTunnel tunnel = UserTunnelManager.getInstance().getUserTunnelByTunnelToken(tunnelToken);
        if (tunnel != null) {
            Channel userTunnelChannel = tunnel.getUserTunnelChannel(tunnelToken, sessionToken);
            if (userTunnelChannel != null) {
                // 解决 HTTP/1.x 数据传输问题
                userTunnelChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
            }
        }
    }

    /**
     * 处理本地隧道断开连接消息
     */
    private void handleLocalTunnelDisconnectMessage(ChannelHandlerContext ctx, TunnelMessage msg) {
        ByteBuf head = Unpooled.wrappedBuffer(msg.getHead());
        long tunnelToken = head.readLong();
        long sessionToken = head.readLong();
        UserTunnel tunnel = UserTunnelManager.getInstance().getUserTunnelByTunnelToken(tunnelToken);
        if (tunnel != null) {
            Channel userTunnelChannel = tunnel.getUserTunnelChannel(tunnelToken, sessionToken);
            if (userTunnelChannel != null) {
                // 解决 HTTP/1.x 数据传输问题
                userTunnelChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

}
