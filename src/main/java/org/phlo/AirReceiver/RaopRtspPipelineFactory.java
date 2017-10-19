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

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;

/**
 * Factory for AirTunes/RAOP RTSP channels
 */
public class RaopRtspPipelineFactory extends ChannelInitializer {


    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("executionHandler", AirReceiver.ChannelExecutionHandler);
        pipeline.addLast("closeOnShutdownHandler", AirReceiver.CloseChannelOnShutdownHandler);
        pipeline.addLast("decoder", new RtspDecoder());
        pipeline.addLast("encoder", new RtspEncoder());
        pipeline.addLast("httpServerCodec", new HttpServerCodec());
        pipeline.addLast("httpObjectAggregator", new HttpObjectAggregator(1024 * 1024));
        pipeline.addLast("logger", new RtspLoggingHandler());
        pipeline.addLast("challengeResponse", new RaopRtspChallengeResponseHandler(AirReceiver.HardwareAddressBytes));
        pipeline.addLast("header", new RaopRtspHeaderHandler());
        pipeline.addLast("options", new RaopRtspOptionsHandler());
        pipeline.addLast("audio", new RaopAudioHandler(AirReceiver.ExecutorService));
        pipeline.addLast("unsupportedResponse", new RtspUnsupportedResponseHandler());
        pipeline.addLast("errorResponse", new RtspErrorResponseHandler());
        pipeline.addLast("exceptionLogger", new ExceptionLoggingHandler());

    }
}
