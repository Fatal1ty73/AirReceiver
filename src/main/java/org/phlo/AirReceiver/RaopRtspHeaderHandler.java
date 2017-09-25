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
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

/**
 * Adds a few default headers to every RTSP response
 */
public class RaopRtspHeaderHandler extends SimpleChannelInboundHandler<FullHttpRequest>
{
	private static final String HeaderCSeq = "CSeq";

	private static final String HeaderAudioJackStatus = "Audio-Jack-Status";
	private static final String HeaderAudioJackStatusDefault = "connected; type=analog";

	/*
	private static final String HeaderAudioLatency = "Audio-Latency";
	private static final long   HeaderAudioLatencyFrames = 88400;
	*/

	private String m_cseq;

	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final FullHttpRequest msg)
		throws Exception
	{

		synchronized(this) {
			if (msg.headers().contains(HeaderCSeq)) {
				m_cseq = msg.headers().getAndConvert(HeaderCSeq);
			}
			else {
				throw new ProtocolException("No CSeq header");
			}
		}
msg.retain();
		ctx.fireChannelRead(msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		final FullHttpResponse resp = (FullHttpResponse)msg;

		synchronized(this) {
			if (m_cseq != null)
				resp.headers().set(HeaderCSeq, m_cseq);

			resp.headers().set(HeaderAudioJackStatus, HeaderAudioJackStatusDefault);
			//resp.setHeader(HeaderAudioLatency, Long.toString(HeaderAudioLatencyFrames));
		}
		super.write(ctx, msg, promise);
	}

}
