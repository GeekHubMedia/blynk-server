package cc.blynk.server.api.websockets.handlers;

import cc.blynk.server.Holder;
import cc.blynk.server.application.handlers.main.AppChannelStateHandler;
import cc.blynk.server.application.handlers.main.auth.AppLoginHandler;
import cc.blynk.server.application.handlers.main.auth.GetServerHandler;
import cc.blynk.server.core.protocol.handlers.DefaultExceptionHandler;
import cc.blynk.server.core.protocol.model.messages.appllication.LoginMessage;
import cc.blynk.server.handlers.common.HardwareNotLoggedHandler;
import cc.blynk.server.handlers.common.UserNotLoggedHandler;
import cc.blynk.server.hardware.handlers.hardware.HardwareChannelStateHandler;
import cc.blynk.server.hardware.handlers.hardware.auth.HardwareLoginHandler;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 01.03.17.
 */
@ChannelHandler.Sharable
public class WebSocketsGenericLoginHandler extends SimpleChannelInboundHandler<LoginMessage>
        implements DefaultExceptionHandler {

    private static final Logger log = LogManager.getLogger(WebSocketsGenericLoginHandler.class);

    private final int hardTimeoutSecs;
    private final HardwareLoginHandler hardwareLoginHandler;
    private final HardwareChannelStateHandler hardwareChannelStateHandler;

    private final AppChannelStateHandler appChannelStateHandler;
    private final AppLoginHandler appLoginHandler;
    private final UserNotLoggedHandler userNotLoggedHandler;
    private final GetServerHandler getServerHandler;

    public WebSocketsGenericLoginHandler(Holder holder, int port) {
        this.hardTimeoutSecs = holder.limits.hardwareIdleTimeout;
        this.hardwareLoginHandler = new HardwareLoginHandler(holder, port);
        this.hardwareChannelStateHandler = new HardwareChannelStateHandler(holder);

        this.appChannelStateHandler = new AppChannelStateHandler(holder.sessionDao);
        this.appLoginHandler = new AppLoginHandler(holder);
        this.userNotLoggedHandler = new UserNotLoggedHandler();
        this.getServerHandler = new GetServerHandler(holder);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LoginMessage message) throws Exception {
        if (message.body.contains(StringUtils.BODY_SEPARATOR_STRING)) {
            initAppPipeline(ctx);
        } else {
            initHardwarePipeline(ctx);
        }
        ctx.fireChannelRead(message);
    }

    private void initAppPipeline(ChannelHandlerContext ctx) {
        ctx.pipeline()
            .addLast("AChannelState", appChannelStateHandler)
            .addLast("AGetServer", getServerHandler)
            .addLast("ALogin", appLoginHandler)
            .addLast("ANotLogged", userNotLoggedHandler)
            .remove(this);
    }


    private void initHardwarePipeline(ChannelHandlerContext ctx) {
        ctx.pipeline()
            .addFirst("WSIdleStateHandler", new IdleStateHandler(hardTimeoutSecs, hardTimeoutSecs, 0))
            .addLast("WSChannelState", hardwareChannelStateHandler)
            .addLast("WSLogin", hardwareLoginHandler)
            .addLast("WSNotLogged", new HardwareNotLoggedHandler())
            .remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        handleGeneralException(ctx, cause);
    }

}
