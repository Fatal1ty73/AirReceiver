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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.util.concurrent.DefaultEventExecutor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Handles the configuration, creation and destruction of RTP channels.
 */
public class RaopAudioHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final String HeaderTransport = "Transport";
    private static final String HeaderSession = "Session";

    ;
    private static Logger s_logger = Logger.getLogger(RaopAudioHandler.class.getName());
    /**
     * SDP line. Format is
     * <br>
     * {@code
     * <attribute>=<value>
     * }
     */
    private static Pattern s_pattern_sdp_line = Pattern.compile("^([a-z])=(.*)$");
    /**
     * SDP attribute {@code m}. Format is
     * <br>
     * {@code
     * <media> <port> <transport> <formats>
     * }
     * <p>
     * RAOP/AirTunes always required {@code <media>=audio, <transport>=RTP/AVP}
     * and only a single format is allowed. The port is ignored.
     */
    private static Pattern s_pattern_sdp_m = Pattern.compile("^audio ([^ ]+) RTP/AVP ([0-9]+)$");
    /**
     * SDP attribute {@code a}. Format is
     * <br>
     * {@code <flag>}
     * <br>
     * or
     * <br>
     * {@code <attribute>:<value>}
     * <p>
     * RAOP/AirTunes uses only the second case, with the attributes
     * <ul>
     * <li> {@code <attribute>=rtpmap}
     * <li> {@code <attribute>=fmtp}
     * <li> {@code <attribute>=rsaaeskey}
     * <li> {@code <attribute>=aesiv}
     * </ul>
     */
    private static Pattern s_pattern_sdp_a = Pattern.compile("^([^:]+):(.*)$");
    /**
     * SDP {@code a} attribute {@code rtpmap}. Format is
     * <br>
     * {@code <format> <encoding>}
     * for RAOP/AirTunes instead of {@code <format> <encoding>/<clock rate>}.
     * <p>
     * RAOP/AirTunes always uses encoding {@code AppleLossless}
     */
    private static Pattern s_pattern_sdp_a_rtpmap = Pattern.compile("^([0-9]+) (.*)$");
    /**
     * {@code Transport} header option format. Format of a single option is
     * <br>
     * {@code <name>=<value>}
     * <br>
     * format of the {@code Transport} header is
     * <br>
     * {@code <protocol>;<name1>=<value1>;<name2>=<value2>;...}
     * <p>
     * For RAOP/AirTunes, {@code <protocol>} is always {@code RTP/AVP/UDP}.
     */
    private static Pattern s_pattern_transportOption = Pattern.compile("^([A-Za-z0-9_-]+)(=(.*))?$");
    /**
     * SET_PARAMETER syntax. Format is
     * <br>
     * {@code <parameter>: <value>}
     * <p>
     */
    private static Pattern s_pattern_parameter = Pattern.compile("^([A-Za-z0-9_-]+): *(.*)$");
    /**
     * RSA cipher used to decrypt the AES session key
     */
    private final Cipher m_rsaPkCS1OaepCipher = AirTunesCrytography.getCipher("RSA/None/OAEPWithSHA1AndMGF1Padding");
    /**
     * Executor service used for the RTP channels
     */
    private final ExecutorService m_rtpExecutorService;
    private final ChannelHandler m_exceptionLoggingHandler = new ExceptionLoggingHandler();
    private final ChannelHandler m_decodeHandler = new RaopRtpDecodeHandler();
    private final ChannelHandler m_encodeHandler = new RtpEncodeHandler();
    private final ChannelHandler m_packetLoggingHandler = new RtpLoggingHandler();
    private final ChannelHandler m_inputToAudioRouterDownstreamHandler = new RaopRtpInputToAudioRouterUpstreamHandler();
    private final ChannelHandler m_audioToOutputRouterUpstreamHandler = new RaopRtpAudioToOutputRouterDownstreamHandler();
    private final ChannelHandler m_audioEnqueueHandler = new RaopRtpAudioEnqueueHandler();
    /**
     * All RTP channels belonging to this RTSP connection
     */
    private final ChannelGroup m_rtpChannels = new DefaultChannelGroup(new DefaultEventExecutor());
    private ChannelHandler m_decryptionHandler;
    private ChannelHandler m_audioDecodeHandler;
    private ChannelHandler m_resendRequestHandler;
    private ChannelHandler m_timingHandler;
    private AudioStreamInformationProvider m_audioStreamInformationProvider;
    private AudioOutputQueue m_audioOutputQueue;
    private Channel m_audioChannel;
    private Channel m_controlChannel;
    private Channel m_timingChannel;

    /**
     * Creates an instance, using the ExecutorService for the RTP channel's datagram socket factory
     *
     * @param rtpExecutorService
     */
    public RaopAudioHandler(final ExecutorService rtpExecutorService) {
        m_rtpExecutorService = rtpExecutorService;
        reset();
    }

    /**
     * Resets stream-related data (i.e. undoes the effect of ANNOUNCE, SETUP and RECORD
     */
    private void reset() {
        if (m_audioOutputQueue != null)
            m_audioOutputQueue.close();

        m_rtpChannels.close();

        m_decryptionHandler = null;
        m_audioDecodeHandler = null;
        m_resendRequestHandler = null;
        m_timingHandler = null;

        m_audioStreamInformationProvider = null;
        m_audioOutputQueue = null;

        m_audioChannel = null;
        m_controlChannel = null;
        m_timingChannel = null;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        s_logger.info("RTSP connection was shut down, closing RTP channels and audio output queue");

        synchronized (this) {
            reset();
        }
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) throws Exception {
        final HttpMethod method = msg.method();

        if (RaopRtspMethods.ANNOUNCE.equals(method)) {
            announceReceived(ctx, msg);
            return;
        } else if (RaopRtspMethods.SETUP.equals(method)) {
            setupReceived(ctx, msg);
            return;
        } else if (RaopRtspMethods.RECORD.equals(method)) {
            recordReceived(ctx, msg);
            return;
        } else if (RaopRtspMethods.FLUSH.equals(method)) {
            flushReceived(ctx, msg);
            return;
        } else if (RaopRtspMethods.TEARDOWN.equals(method)) {
            teardownReceived(ctx, msg);
            return;
        } else if (RaopRtspMethods.SET_PARAMETER.equals(method)) {
            setParameterReceived(ctx, msg);
            return;
        } else if (RaopRtspMethods.GET_PARAMETER.equals(method)) {
            getParameterReceived(ctx, msg);
            return;
        } else {
            s_logger.warning("Unsupported method: " + method.toString());
        }
        msg.retain();
        ctx.fireChannelRead(msg);
    }

    /**
     * Handles ANNOUNCE requests and creates an {@link AudioOutputQueue} and
     * the following handlers for RTP channels
     * <ul>
     * <li>{@link RaopRtpTimingHandler}
     * <li>{@link RaopRtpRetransmitRequestHandler}
     * <li>{@link RaopRtpAudioDecryptionHandler}
     * <li>{@link RaopRtpAudioAlacDecodeHandler}
     * </ul>
     */
    public synchronized void announceReceived(final ChannelHandlerContext ctx, final FullHttpRequest req)
            throws Exception {
		/* ANNOUNCE must contain stream information in SDP format */
        if (!req.headers().contains(HttpHeaderNames.CONTENT_TYPE))
            throw new ProtocolException("No Content-Type header");
        if (!"application/sdp".equals(req.headers().get(HttpHeaderNames.CONTENT_TYPE)))
            throw new ProtocolException("Invalid Content-Type header, expected application/sdp but got " + req.headers().get("Content-Type"));

        reset();

		/* Get SDP stream information */
        final String dsp = req.content().toString(Charset.forName("ASCII")).replace("\r", "");

        SecretKey aesKey = null;
        IvParameterSpec aesIv = null;
        int alacFormatIndex = -1;
        int audioFormatIndex = -1;
        int descriptionFormatIndex = -1;
        String[] formatOptions = null;

        for (final String line : dsp.split("\n")) {
			/* Split SDP line into attribute and setting */
            final Matcher line_matcher = s_pattern_sdp_line.matcher(line);
            if (!line_matcher.matches())
                throw new ProtocolException("Cannot parse SDP line " + line);
            final char attribute = line_matcher.group(1).charAt(0);
            final String setting = line_matcher.group(2);

			/* Handle attributes */
            switch (attribute) {
                case 'm':
					/* Attribute m. Maps an audio format index to a stream */
                    final Matcher m_matcher = s_pattern_sdp_m.matcher(setting);
                    if (!m_matcher.matches())
                        throw new ProtocolException("Cannot parse SDP " + attribute + "'s setting " + setting);
                    audioFormatIndex = Integer.valueOf(m_matcher.group(2));
                    break;

                case 'a':
					/* Attribute a. Defines various session properties */
                    final Matcher a_matcher = s_pattern_sdp_a.matcher(setting);
                    if (!a_matcher.matches())
                        throw new ProtocolException("Cannot parse SDP " + attribute + "'s setting " + setting);
                    final String key = a_matcher.group(1);
                    final String value = a_matcher.group(2);

                    if ("rtpmap".equals(key)) {
						/* Sets the decoder for an audio format index */
                        final Matcher a_rtpmap_matcher = s_pattern_sdp_a_rtpmap.matcher(value);
                        if (!a_rtpmap_matcher.matches())
                            throw new ProtocolException("Cannot parse SDP " + attribute + "'s rtpmap entry " + value);

                        final int formatIdx = Integer.valueOf(a_rtpmap_matcher.group(1));
                        final String format = a_rtpmap_matcher.group(2);
                        if ("AppleLossless".equals(format))
                            alacFormatIndex = formatIdx;
                    } else if ("fmtp".equals(key)) {
						/* Sets the decoding parameters for a audio format index */
                        final String[] parts = value.split(" ");
                        if (parts.length > 0)
                            descriptionFormatIndex = Integer.valueOf(parts[0]);
                        if (parts.length > 1)
                            formatOptions = Arrays.copyOfRange(parts, 1, parts.length);
                    } else if ("rsaaeskey".equals(key)) {
						/* Sets the AES key required to decrypt the audio data. The key is
						 * encrypted wih the AirTunes private key
						 */
                        byte[] aesKeyRaw;

                        m_rsaPkCS1OaepCipher.init(Cipher.DECRYPT_MODE, AirTunesCrytography.PrivateKey);
                        aesKeyRaw = m_rsaPkCS1OaepCipher.doFinal(Base64.decodeUnpadded(value));

                        aesKey = new SecretKeySpec(aesKeyRaw, "AES");
                    } else if ("aesiv".equals(key)) {
						/* Sets the AES initialization vector */
                        aesIv = new IvParameterSpec(Base64.decodeUnpadded(value));
                    }
                    break;

                default:
					/* Ignore */
                    break;
            }
        }

		/* Validate SDP information */

		/* The format index of the stream must match the format index from the rtpmap attribute */
        if (alacFormatIndex != audioFormatIndex)
            throw new ProtocolException("Audio format " + audioFormatIndex + " not supported");

		/* The format index from the rtpmap attribute must match the format index from the fmtp attribute */
        if (audioFormatIndex != descriptionFormatIndex)
            throw new ProtocolException("Auido format " + audioFormatIndex + " lacks fmtp line");

		/* The fmtp attribute must have contained format options */
        if (formatOptions == null)
            throw new ProtocolException("Auido format " + audioFormatIndex + " incomplete, format options not set");

		/* Create decryption handler if an AES key and IV was specified */
        if ((aesKey != null) && (aesIv != null))
            m_decryptionHandler = new RaopRtpAudioDecryptionHandler(aesKey, aesIv);

		/* Create an ALAC decoder. The ALAC decoder is our stream information provider */
        final RaopRtpAudioAlacDecodeHandler handler = new RaopRtpAudioAlacDecodeHandler(formatOptions);
        m_audioStreamInformationProvider = handler;
        m_audioDecodeHandler = handler;

		/* Create audio output queue with the format information provided by the ALAC decoder */
        m_audioOutputQueue = new AudioOutputQueue(m_audioStreamInformationProvider);

		/* Create timing handle, using the AudioOutputQueue as time source */
        m_timingHandler = new RaopRtpTimingHandler(m_audioOutputQueue);

		/* Create retransmit request handler using the audio output queue as time source */
        m_resendRequestHandler = new RaopRtpRetransmitRequestHandler(m_audioStreamInformationProvider, m_audioOutputQueue);

        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        ctx.channel().write(response);
    }

    /**
     * Handles SETUP requests and creates the audio, control and timing RTP channels
     */
    public synchronized void setupReceived(final ChannelHandlerContext ctx, final HttpRequest req)
            throws ProtocolException {
		/* Request must contain a Transport header */
        if (!req.headers().contains(HeaderTransport))
            throw new ProtocolException("No Transport header");

		/* Split Transport header into individual options and prepare reponse options list */
        final Deque<String> requestOptions = new java.util.LinkedList<String>(Arrays.asList(req.headers().get(HeaderTransport).split(";")));
        final List<String> responseOptions = new java.util.LinkedList<String>();

		/* Transport header. Protocol must be RTP/AVP/UDP */
        final String requestProtocol = requestOptions.removeFirst();
        if (!"RTP/AVP/UDP".equals(requestProtocol))
            throw new ProtocolException("Transport protocol must be RTP/AVP/UDP, but was " + requestProtocol);
        responseOptions.add(requestProtocol);

		/* Parse incoming transport options and build response options */
        for (final String requestOption : requestOptions) {
			/* Split option into key and value */
            final Matcher m_transportOption = s_pattern_transportOption.matcher(requestOption);
            if (!m_transportOption.matches())
                throw new ProtocolException("Cannot parse Transport option " + requestOption);
            final String key = m_transportOption.group(1);
            final String value = m_transportOption.group(3);

            if ("interleaved".equals(key)) {
				/* Probably means that two channels are interleaved in the stream. Included in the response options */
                if (!"0-1".equals(value))
                    throw new ProtocolException("Unsupported Transport option, interleaved must be 0-1 but was " + value);
                responseOptions.add("interleaved=0-1");
            } else if ("mode".equals(key)) {
				/* Means the we're supposed to receive audio data, not send it. Included in the response options */
                if (!"record".equals(value))
                    throw new ProtocolException("Unsupported Transport option, mode must be record but was " + value);
                responseOptions.add("mode=record");
            } else if ("control_port".equals(key)) {
				/* Port number of the client's control socket. Response includes port number of *our* control port */
                final int clientControlPort = Integer.valueOf(value);
                m_controlChannel = createRtpChannel(
                        substitutePort((InetSocketAddress) ctx.channel().localAddress(), 0),
                        substitutePort((InetSocketAddress) ctx.channel().remoteAddress(), clientControlPort),
                        RaopRtpChannelType.Control
                );
                s_logger.info("Launched RTP control service on " + m_controlChannel.localAddress());
                responseOptions.add("control_port=" + ((InetSocketAddress) m_controlChannel.localAddress()).getPort());
            } else if ("timing_port".equals(key)) {
				/* Port number of the client's timing socket. Response includes port number of *our* timing port */
                final int clientTimingPort = Integer.valueOf(value);
                m_timingChannel = createRtpChannel(
                        substitutePort((InetSocketAddress) ctx.channel().localAddress(), 0),
                        substitutePort((InetSocketAddress) ctx.channel().remoteAddress(), clientTimingPort),
                        RaopRtpChannelType.Timing
                );
                s_logger.info("Launched RTP timing service on " + m_timingChannel.localAddress());
                responseOptions.add("timing_port=" + ((InetSocketAddress) m_timingChannel.localAddress()).getPort());
            } else {
				/* Ignore unknown options */
                responseOptions.add(requestOption);
            }
        }

		/* Create audio socket and include it's port in our response */
        m_audioChannel = createRtpChannel(
                substitutePort((InetSocketAddress) ctx.channel().localAddress(), 0),
                null,
                RaopRtpChannelType.Audio
        );
        s_logger.info("Launched RTP audio service on " + m_audioChannel.localAddress());
        responseOptions.add("server_port=" + ((InetSocketAddress) m_audioChannel.localAddress()).getPort());

		/* Build response options string */
        final StringBuilder transportResponseBuilder = new StringBuilder();
        for (final String responseOption : responseOptions) {
            if (transportResponseBuilder.length() > 0)
                transportResponseBuilder.append(";");
            transportResponseBuilder.append(responseOption);
        }

		/* Send response */
        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        response.headers().add(HeaderTransport, transportResponseBuilder.toString());
        response.headers().add(HeaderSession, "DEADBEEEF");
        ctx.channel().write(response);
    }

    /**
     * Handles RECORD request. We did all the work during ANNOUNCE and SETUP, so there's nothing
     * more to do.
     * <p>
     * iTunes reports the initial RTP sequence and playback time here, which would actually be
     * helpful. But iOS doesn't, so we ignore it all together.
     */
    public synchronized void recordReceived(final ChannelHandlerContext ctx, final HttpRequest req)
            throws Exception {
        if (m_audioStreamInformationProvider == null)
            throw new ProtocolException("Audio stream not configured, cannot start recording");

        s_logger.info("Client started streaming");

        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        ctx.channel().write(response);
    }

    /**
     * Handle FLUSH requests.
     * <p>
     * iTunes reports the last RTP sequence and playback time here, which would actually be
     * helpful. But iOS doesn't, so we ignore it all together.
     */
    private synchronized void flushReceived(final ChannelHandlerContext ctx, final HttpRequest req) {
        if (m_audioOutputQueue != null)
            m_audioOutputQueue.flush();

        s_logger.info("Client paused streaming, flushed audio output queue");

        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        ctx.channel().write(response);
    }

    /**
     * Handle TEARDOWN requests.
     */
    private synchronized void teardownReceived(final ChannelHandlerContext ctx, final HttpRequest req) {
        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
//		ctx.channel().setReadable(false);
        ctx.channel().write(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                future.channel().close();
                s_logger.info("RTSP connection closed after client initiated teardown");
            }
        });
    }

    /**
     * Handle SET_PARAMETER request. Currently only {@code volume} is supported
     */
    public synchronized void setParameterReceived(final ChannelHandlerContext ctx, final FullHttpRequest req)
            throws ProtocolException {
		/* Body in ASCII encoding with unix newlines */
        final String body = req.content().toString(Charset.forName("UTF-8")).replace("\r", "");

        if (req.headers().contains(HttpHeaderNames.CONTENT_TYPE)) {
            s_logger.info("Header value: " + req.headers().get(HttpHeaderNames.CONTENT_TYPE));
            if (req.headers().get(HttpHeaderNames.CONTENT_TYPE).equals("application/x-dmap-tagged")) {
                Map<String, String> map = new HashMap<String, String>();
                DAAPParserUtil.getContent(req.content().copy(), map);
                CurrentTrack.setSongInfo(SongInfo.createSongInfo(map));

            }
            if (req.headers().get(HttpHeaderNames.CONTENT_TYPE).equals("image/jpeg")) {
                CurrentTrack.getSongInfo().setPicture(req.content().nioBuffer());

            }
            if (req.headers().get(HttpHeaderNames.CONTENT_TYPE).equals("text/parameters")) {
				/* Handle parameters */
                for (final String line : body.split("\n")) {
                    try {
				/* Split parameter into name and value */
                        final Matcher m_parameter = s_pattern_parameter.matcher(line);
                        if (!m_parameter.matches())
                            throw new ProtocolException("Cannot parse line " + line);

                        final String name = m_parameter.group(1);
                        final String value = m_parameter.group(2);

                        if ("volume".equals(name)) {
                            s_logger.info("Volume set to " + value);

					/* Set output gain */
                            if (m_audioOutputQueue != null)
                                m_audioOutputQueue.setGain(Float.parseFloat(value));

                        }
                    } catch (final Throwable e) {
                        throw new ProtocolException("Unable to parse line " + line);
                    }
                }
            }
        }


        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        ctx.channel().write(response);
    }

    /**
     * Handle GET_PARAMETER request. Currently only {@code volume} is supported
     */
    public synchronized void getParameterReceived(final ChannelHandlerContext ctx, final HttpRequest req)
            throws ProtocolException {
        final StringBuilder body = new StringBuilder();

        if (m_audioOutputQueue != null) {
			/* Report output gain */
            body.append("volume: ");
            body.append(m_audioOutputQueue.getGain());
            body.append("\r\n");
        }

        final FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        response.content().writeBytes(Unpooled.wrappedBuffer(body.toString().getBytes(Charset.forName("ASCII"))));
        ctx.channel().write(response);
    }

    /**
     * Creates an UDP socket and handler pipeline for RTP channels
     *
     * @param local       local end-point address
     * @param remote      remote end-point address
     * @param channelType channel type. Determines which handlers are put into the pipeline
     * @return open data-gram channel
     */
    private Channel createRtpChannel(final SocketAddress local, final SocketAddress remote, final RaopRtpChannelType channelType) {
		/* Create bootstrap helper for a data-gram socket using NIO */
        final Bootstrap bootstrap = new Bootstrap();

		/* Set the buffer size predictor to 1500 bytes to ensure that
		 * received packets will fit into the buffer. Packets are
		 * truncated if they are larger than that!
		 */
        final EventLoopGroup bossGroup = new NioEventLoopGroup(4);
//		final EventLoopGroup workerGroup = new NioEventLoopGroup(4);
        bootstrap.group(bossGroup);

        bootstrap.channel(NioDatagramChannel.class);
		/* Set the buffer size predictor to 1500 bytes to ensure that
		 * received packets will fit into the buffer. Packets are
		 * truncated if they are larger than that!
		 */

        bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(1500));

		/* Set the socket's receive buffer size. We set it to 1MB */
        bootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
		/* Set pipeline factory for the RTP channel */
        bootstrap.handler(new ChannelInitializer() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("executionHandler", AirReceiver.ChannelExecutionHandler);
                pipeline.addLast("decoder", m_decodeHandler);
                pipeline.addLast("encoder", m_encodeHandler);
				/* We pretend that all communication takes place on the audio channel,
				 * and simply re-route packets from and to the control and timing channels
				 */
                if (!channelType.equals(RaopRtpChannelType.Audio)) {
                    pipeline.addLast("inputToAudioRouter", m_inputToAudioRouterDownstreamHandler);
					/* Must come *after* the router, otherwise incoming packets are logged twice */
                    pipeline.addLast("packetLogger", m_packetLoggingHandler);
                } else {
					/* Must come *before* the router, otherwise outgoing packets are logged twice */
                    pipeline.addLast("packetLogger", m_packetLoggingHandler);
                    pipeline.addLast("audioToOutputRouter", m_audioToOutputRouterUpstreamHandler);
                    pipeline.addLast("timing", m_timingHandler);
                    pipeline.addLast("resendRequester", m_resendRequestHandler);
                    if (m_decryptionHandler != null)
                        pipeline.addLast("decrypt", m_decryptionHandler);
                    if (m_audioDecodeHandler != null)
                        pipeline.addLast("audioDecode", m_audioDecodeHandler);
                    pipeline.addLast("enqueue", m_audioEnqueueHandler);
                }
                pipeline.addLast("exceptionLogger", m_exceptionLoggingHandler);
            }
        });

        Channel channel = null;
        boolean didThrow = true;
        try {
			/* Bind to local address */
            channel = bootstrap.bind(local).syncUninterruptibly().channel();

			/* Add to group of RTP channels beloging to this RTSP connection */
            m_rtpChannels.add(channel);

			/* Connect to remote address if one was provided */
            if (remote != null)
                channel.connect(remote);

            didThrow = false;
            return channel;
        } finally {
            if (didThrow && (channel != null))
                channel.close();
        }
    }

    /**
     * Modifies the port component of an {@link InetSocketAddress} while
     * leaving the other parts unmodified.
     *
     * @param address socket address
     * @param port    new port
     * @return socket address with port substitued
     */
    private InetSocketAddress substitutePort(final InetSocketAddress address, final int port) {
		/*
		 * The more natural way of doing this would be
		 *   new InetSocketAddress(address.getAddress(), port),
		 * but this leads to a JVM crash on Windows when the
		 * new socket address is used to connect() an NIO socket.
		 *
		 * According to
		 *   http://stackoverflow.com/questions/1512578/jvm-crash-on-opening-a-return-socketchannel
		 * converting to address to a string first fixes the problem.
		 */
        return new InetSocketAddress(address.getAddress().getHostAddress(), port);
    }

    /**
     * The RTP channel type
     */
    static enum RaopRtpChannelType {
        Audio, Control, Timing
    }

    /**
     * Routes incoming packets from the control and timing channel to
     * the audio channel
     */
    @Sharable
    private class RaopRtpInputToAudioRouterUpstreamHandler extends SimpleChannelInboundHandler {
        @Override
        public void channelRead0(final ChannelHandlerContext ctx, final Object msg)
                throws Exception {
            /* Get audio channel from the enclosing RaopAudioHandler */
            Channel audioChannel = null;
            synchronized (RaopAudioHandler.this) {
                audioChannel = m_audioChannel;
            }
            if ((m_audioChannel != null) && m_audioChannel.isOpen() && m_audioChannel.isWritable()) {
                audioChannel.pipeline().firstContext().fireChannelRead(msg);
            }
        }
    }

    /**
     * Routes outgoing packets on audio channel to the control or timing
     * channel if appropriate
     */
    private class RaopRtpAudioToOutputRouterDownstreamHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            final RaopRtpPacket packet = (RaopRtpPacket) msg;

			/* Get control and timing channel from the enclosing RaopAudioHandler */
            Channel controlChannel = null;
            Channel timingChannel = null;
            synchronized (RaopAudioHandler.this) {
                controlChannel = m_controlChannel;
                timingChannel = m_timingChannel;
            }

            if (packet instanceof RaopRtpPacket.RetransmitRequest) {
                if ((controlChannel != null) && controlChannel.isOpen() && controlChannel.isWritable())
                    controlChannel.write(msg);
            } else if (packet instanceof RaopRtpPacket.TimingRequest) {
                if ((timingChannel != null) && timingChannel.isOpen() && timingChannel.isWritable())
                    timingChannel.write(msg);
            } else {
                super.write(ctx, msg, promise);
            }

        }

    }

    /**
     * Places incoming audio data on the audio output queue
     */
    public class RaopRtpAudioEnqueueHandler extends SimpleChannelInboundHandler {
        @Override
        public void channelRead0(final ChannelHandlerContext ctx, final Object msg)
                throws Exception {
            if (!(msg instanceof RaopRtpPacket.Audio)) {
                ctx.fireChannelRead(msg);
                return;
            }

            final RaopRtpPacket.Audio audioPacket = (RaopRtpPacket.Audio) msg;

			/* Get audio output queue from the enclosing RaopAudioHandler */
            AudioOutputQueue audioOutputQueue;
            synchronized (RaopAudioHandler.this) {
                audioOutputQueue = m_audioOutputQueue;
            }

            if (audioOutputQueue != null) {
                final byte[] samples = new byte[audioPacket.getPayload().capacity()];
                audioPacket.getPayload().getBytes(0, samples);
                m_audioOutputQueue.enqueue(audioPacket.getTimeStamp(), samples);
                if (s_logger.isLoggable(Level.FINEST))
                    s_logger.finest("Packet with sequence " + audioPacket.getSequence() + " for playback at " + audioPacket.getTimeStamp() + " submitted to audio output queue");
            } else {
                s_logger.warning("No audio queue available, dropping packet");
            }

            ctx.fireChannelRead(msg);
        }

    }
}
