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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

/**
 * Handles RTSP OPTIONS requests.
 * <p>
 * iTunes sends those to verify that we're a legitimate device,
 * by including a Apple-Request header and expecting an appropriate
 * Apple-Response
 */
public class RaopRtspOptionsHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final String Options =
		RaopRtspMethods.ANNOUNCE.name() + ", " +
		RaopRtspMethods.SETUP.name() + ", " +
		RaopRtspMethods.RECORD.name() + ", " +
		RaopRtspMethods.PAUSE.name() + ", " +
		RaopRtspMethods.FLUSH.name() + ", " +
		RtspMethods.TEARDOWN.name() + ", " +
		RaopRtspMethods.OPTIONS.name() + ", " +
		RaopRtspMethods.GET_PARAMETER.name() + ", " +
		RaopRtspMethods.SET_PARAMETER.name();

	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final FullHttpRequest msg) throws Exception {


		if (RtspMethods.OPTIONS.equals(msg.method())) {

			final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
	        response.headers().set(RtspHeaderNames.PUBLIC, Options);
			ctx.write(response);
		}
		else {
			msg.retain();
			ctx.fireChannelRead(msg);
		}
	}
}
