package net.io.netty.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.dipatch.Content;
import net.dipatch.IContent;
import net.dipatch.IDispatchManager;
import net.dipatch.ISender;
import net.io.netty.INettyContentFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttpHandler extends SimpleChannelInboundHandler<Object> {
	
	private static final Logger log = LoggerFactory.getLogger(NettyHttpHandler.class);
	
	private static final AttributeKey<NettyHttpSession> SESSION = AttributeKey.valueOf("NettyHttpSession");
	
	private static final String CONTENT_LENGHT = "Content-Length";
	
	private static final String MESSAGE_ID = "messageId";
	
	private final IDispatchManager dispatchManager;
	
	private final INettyContentFactory nettyContentFactory;
	
	public NettyHttpHandler(IDispatchManager dispatchManager, INettyContentFactory nettyContentFactory) {
		this.dispatchManager = dispatchManager;
		this.nettyContentFactory = nettyContentFactory;
	}
	
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel channel = ctx.channel();
		if (msg instanceof HttpRequest) { // http请求头
			readHead(channel, (HttpRequest) msg);
		} else if (msg instanceof HttpContent) { // http请求体
			HttpContent httpContent = (HttpContent) msg;
			ByteBuf content = httpContent.content();
			Attribute<NettyHttpSession> session = channel.attr(SESSION);
			NettyHttpSession httpSession = session.get();
			if (httpSession != null) {
				readContent(channel, httpSession, content);
			}
		}
	}
	
	private void readHead(Channel channel, HttpRequest req) {
		HttpHeaders headers = req.headers();
		Attribute<NettyHttpSession> session = channel.attr(SESSION);
		NettyHttpSession httpSession = session.get() ;
		if (httpSession == null) {
			httpSession = new NettyHttpSession(channel.id().asLongText(), HttpHeaders.isKeepAlive(req), channel);
			session.set(httpSession);
		}
		httpSession.setMessageId(Integer.parseInt(headers.get(MESSAGE_ID)));
		httpSession.setContentLength(Integer.parseInt(headers.get(CONTENT_LENGHT)));
	}
	
	private void readContent(Channel channel, NettyHttpSession httpSession, ByteBuf buf) {
		IContent content = nettyContentFactory.createContent(channel, buf, httpSession);
//		log.debug("sessionId : {}", content.getSessionId());
		dispatchManager.addDispatch(content);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Channel channel = ctx.channel();
		log.info("[Coming Out]IP:{}", channel.remoteAddress());
		Attribute<NettyHttpSession> session = channel.attr(SESSION);
		NettyHttpSession httpSession = session.get();
		if (httpSession != null) {
			String sessionId = httpSession.getId();
			dispatchManager.disconnect(new Content(sessionId, 0, null, null, new NettyHttpSender(false, sessionId, channel)));
		}
		channel.close();
		ctx.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.info("[Coming Error]IP:{} ; Error:{}", ctx.channel().remoteAddress(), cause.getLocalizedMessage());
		ctx.channel().close();
		ctx.close();
		if (!cause.getLocalizedMessage().contains("远程主机强迫关闭了一个现有的连接。")) {
			cause.printStackTrace();
		}
	}
	
	private static class NettyHttpSession implements INettyHttpSession {
		
		private int contentLength;
		
		private int messageId;
		
		private final String id;
		
		private final boolean isKeepAlive;
		
		private final ISender sender;
		
		public NettyHttpSession(String id, boolean isKeepAlive, Channel channel) {
			this.id = id;
			this.isKeepAlive = isKeepAlive;
			sender = new NettyHttpSender(isKeepAlive, id, channel);
		}

		@Override
		public int getContentLength() {
			return contentLength;
		}

		public void setContentLength(int contentLength) {
			this.contentLength = contentLength;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public int getMessageId() {
			return messageId;
		}

		public void setMessageId(int messageId) {
			this.messageId = messageId;
		}

		@Override
		public boolean isKeepAlive() {
			return isKeepAlive;
		}

		@Override
		public ISender getSender() {
			return sender;
		}
		
	}

}