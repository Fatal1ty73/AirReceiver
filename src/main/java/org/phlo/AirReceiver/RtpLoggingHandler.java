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

import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Logs incoming and outgoing RTP packets
 */
@ChannelHandler.Sharable
public class RtpLoggingHandler extends SimpleChannelInboundHandler<RtpPacket> {
	private static final Logger s_logger = Logger.getLogger(RtpLoggingHandler.class.getName());

	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final RtpPacket msg)
		throws Exception {
		final Level level = getPacketLevel(msg);
//			if (s_logger.isLoggable(level))
		s_logger.log(Level.INFO, ctx.channel().remoteAddress() + "> " + msg.toString());
		ctx.fireChannelRead(msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof RtpPacket) {
			final RtpPacket packet = (RtpPacket)msg;

			final Level level = getPacketLevel(packet);
//			if (s_logger.isLoggable(level))
			s_logger.log(Level.INFO, ctx.channel().remoteAddress() + "< " + packet.toString());
		}
		super.write(ctx, msg, promise);
	}

	private Level getPacketLevel(final RtpPacket packet) {
		if (packet instanceof RaopRtpPacket.Audio)
			return Level.FINEST;
		else if (packet instanceof RaopRtpPacket.RetransmitRequest)
			return Level.FINEST;
		else if (packet instanceof RaopRtpPacket.Timing)
			return Level.FINEST;
		else
			return Level.FINE;
	}
}
