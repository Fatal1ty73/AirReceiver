package org.phlo.AirReceiver;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;

final class DAAPParserUtil {
    public static Map<String, String> getContent(ByteBuf buf, Map<String, String> map) {
        if (buf.isReadable()) {
            String code = getCode(buf.readBytes(4));
            int lenght = getLenght(buf.readBytes(4).nioBuffer());

            if (code.equals("mlit")) {
                map.put(code, "");
                getContent(buf.readBytes(lenght), map);
            } else {
                String value = getCode(buf.readBytes(lenght));
                map.put(code, value);
                getContent(buf, map);
            }
        }
        return map;
    }

    private static String getCode(ByteBuf code) {

        return code.toString(Charset.forName("UTF-8"));
    }

    private static int getLenght(ByteBuffer arr) {
        int size = arr.getInt();
        return size;
    }
}
