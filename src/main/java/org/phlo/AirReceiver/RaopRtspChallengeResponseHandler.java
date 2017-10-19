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

import javax.crypto.Cipher;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Adds an {@code Apple-Response} header to a response if the request contain
 * an {@code Apple-Request} header.
 */
public class RaopRtspChallengeResponseHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final String HeaderChallenge = "Apple-Challenge";
    private static final String HeaderSignature = "Apple-Response";

    private final byte[] m_hwAddress;
    private final Cipher m_rsaPkCS1PaddingCipher = AirTunesCrytography.getCipher("RSA/None/PKCS1Padding");

    private byte[] m_challenge;
    private InetAddress m_localAddress;

    public RaopRtspChallengeResponseHandler(final byte[] hwAddress) {
        assert hwAddress.length == 6;

        m_hwAddress = hwAddress;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final FullHttpRequest msg)
            throws Exception {
        synchronized (this) {
            if (msg.headers().contains(HeaderChallenge)) {
                /* The challenge is sent without padding! */
                final byte[] challenge = Base64.decodeUnpadded(msg.headers().getAndConvert(HeaderChallenge));

				/* Verify that we got 16 bytes */
                if (challenge.length != 16)
                    throw new ProtocolException("Invalid Apple-Challenge header, " + challenge.length + " instead of 16 bytes");

				/* Remember challenge and local address.
				 * Both are required to compute the response
				 */
                m_challenge = challenge;
                m_localAddress = ((InetSocketAddress) ctx.channel().localAddress()).getAddress();
            } else {
				/* Forget last challenge */
                m_challenge = null;
                m_localAddress = null;
            }
        }
        msg.retain();
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        final FullHttpResponse resp = (FullHttpResponse) msg;

        synchronized (this) {
            if (m_challenge != null) {
                try {
					/* Get appropriate response to challenge and
					 * add to the response base-64 encoded. XXX
					 */
                    final String sig = Base64.encodePadded(getSignature());

                    resp.headers().set(HeaderSignature, sig);
                } finally {
					/* Forget last challenge */
                    m_challenge = null;
                    m_localAddress = null;
                }
            }
        }
        super.write(ctx, msg, promise);
    }


    private byte[] getSignature() {
        final ByteBuffer sigData = ByteBuffer.allocate(16 /* challenge */ + 16 /* ipv6 address */ + 6 /* hw address*/);

        sigData.put(m_challenge);
        sigData.put(m_localAddress.getAddress());
        sigData.put(m_hwAddress);
        while (sigData.hasRemaining())
            sigData.put((byte) 0);

        try {
            m_rsaPkCS1PaddingCipher.init(Cipher.ENCRYPT_MODE, AirTunesCrytography.PrivateKey);
            return m_rsaPkCS1PaddingCipher.doFinal(sigData.array());
        } catch (final Exception e) {
            throw new RuntimeException("Unable to sign response", e);
        }
    }
}
