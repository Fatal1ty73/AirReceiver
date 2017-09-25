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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;
import java.util.logging.Logger;

/**
 * Decodes incoming packets, emitting instances of {@link RaopRtpPacket}
 */
@ChannelHandler.Sharable
public class RaopRtpDecodeHandler extends MessageToMessageDecoder<DatagramPacket> {
	private static final Logger s_logger = Logger.getLogger(RaopRtpDecodeHandler.class.getName());

	@Override
	protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List out) throws Exception
	{
		msg.retain();
			final ByteBuf buffer = msg.content();

			try {
				RaopRtpPacket decoded= RaopRtpPacket.decode(buffer);
				out.add(decoded);
			}
			catch (final InvalidPacketException e1) {
				s_logger.warning(e1.getMessage());
				out.add(buffer);
			}

	}
}
