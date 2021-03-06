package lighttunnel.proto

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import lighttunnel.logger.loggerDelegate
import java.util.concurrent.TimeUnit

/**
 * 心跳处理器
 */
class HeartbeatHandler(
    observeOutput: Boolean = false,
    readerIdleTime: Long = 60 * 5L,
    writerIdleTime: Long = 60 * 3L,
    allIdleTime: Long = 0L,
    unit: TimeUnit = TimeUnit.SECONDS
) : IdleStateHandler(observeOutput, readerIdleTime, writerIdleTime, allIdleTime, unit) {

    private val logger by loggerDelegate()

    @Throws(Exception::class)
    override fun channelIdle(ctx: ChannelHandlerContext, evt: IdleStateEvent) {
        when (evt) {
            IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT -> {
                logger.trace("channel write timeout {}", ctx)
                ctx.channel().writeAndFlush(ProtoMessage(ProtoMessageType.PING))
            }
            IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT -> {
                logger.trace("channel read timeout {}", ctx)
                ctx.channel().close()
            }
        }
        super.channelIdle(ctx, evt)
    }

}
