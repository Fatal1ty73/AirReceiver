/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.AirReceiver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

import java.util.logging.Logger;

/**
 * Sends a METHOD NOT VALID response if no other channel handler
 * takes responsibility for a RTSP message.
 */
public class RtspUnsupportedResponseHandler extends SimpleChannelInboundHandler {
	private static Logger s_logger = Logger.getLogger(RtspUnsupportedResponseHandler.class.getName());


	@Override
	protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
		final HttpRequest req = (HttpRequest)msg;

		s_logger.warning("Method " + req.method() + " is not supported");

		final HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,  RtspResponseStatuses.METHOD_NOT_VALID);
		ctx.channel().write(response);
	}
}
