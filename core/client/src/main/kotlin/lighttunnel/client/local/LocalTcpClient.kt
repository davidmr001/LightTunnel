package lighttunnel.client.local

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import lighttunnel.client.util.AttributeKeys
import lighttunnel.logger.loggerDelegate
import java.util.concurrent.ConcurrentHashMap

class LocalTcpClient(
    workerGroup: NioEventLoopGroup
) {
    private val logger by loggerDelegate()
    private val bootstrap = Bootstrap()
    private val cachedChannels = ConcurrentHashMap<String, Channel>()

    init {
        this.bootstrap
            .group(workerGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.AUTO_READ, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel?) {
                    ch ?: return
                    ch.pipeline()
                        .addLast("handler", LocalTcpClientChannelHandler(this@LocalTcpClient))
                }
            })
    }

    fun getLocalChannel(
        localAddr: String, localPort: Int,
        tunnelId: Long, sessionId: Long,
        tunnelClientChannel: Channel,
        callback: OnGetLocalChannelCallback?
    ) {
        logger.trace("cachedChannels: {}", cachedChannels)
        val cachedLocalChannel = getCachedChannel(tunnelId, sessionId)
        if (cachedLocalChannel != null && cachedLocalChannel.isActive) {
            callback?.onSuccess(cachedLocalChannel)
            return
        }
        bootstrap.connect(localAddr, localPort).addListener(ChannelFutureListener { future ->
            // 二次检查是否有可用的Channel缓存
            val localChannel = getCachedChannel(tunnelId, sessionId)
            if (localChannel != null && localChannel.isActive) {
                callback?.onSuccess(localChannel)
                future.channel().close()
                return@ChannelFutureListener
            }
            removeLocalChannel(tunnelId, sessionId)
            if (future.isSuccess) {
                future.channel().attr(AttributeKeys.AK_TUNNEL_ID).set(tunnelId)
                future.channel().attr(AttributeKeys.AK_SESSION_ID).set(sessionId)
                future.channel().attr(AttributeKeys.AK_NEXT_CHANNEL).set(tunnelClientChannel)
                putCachedChannel(tunnelId, sessionId, future.channel())
                callback?.onSuccess(future.channel())
            } else {
                callback?.onError(future.cause())
            }
        })
    }

    fun removeLocalChannel(tunnelId: Long, sessionId: Long): Channel? {
        return removeCachedChannel(tunnelId, sessionId)
    }

    fun destroy() {
        cachedChannels.values.forEach {
            it.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
        cachedChannels.clear()
    }

    private fun getCachedChannel(tunnelId: Long, sessionId: Long): Channel? {
        val key = getCachedChannelKey(tunnelId, sessionId)
        synchronized(cachedChannels) {
            return cachedChannels[key]
        }
    }

    private fun putCachedChannel(tunnelId: Long, sessionId: Long, channel: Channel) {
        val key = getCachedChannelKey(tunnelId, sessionId)
        synchronized(cachedChannels) {
            cachedChannels.put(key, channel)
        }
    }

    private fun removeCachedChannel(tunnelId: Long, sessionId: Long): Channel? {
        val key = getCachedChannelKey(tunnelId, sessionId)
        synchronized(cachedChannels) {
            return cachedChannels.remove(key)
        }
    }

    private fun getCachedChannelKey(tunnelId: Long, sessionId: Long): String {
        return String.format("tunnelId:%d, sessionId:%d", tunnelId, sessionId)
    }

    interface OnGetLocalChannelCallback {
        fun onSuccess(localChannel: Channel) {}

        fun onError(cause: Throwable) {}
    }

}