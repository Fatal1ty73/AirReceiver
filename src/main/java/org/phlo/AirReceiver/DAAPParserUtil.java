package org.phlo.AirReceiver;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.util.Map;

final class DAAPParserUtil {
    public static Map<String, String> getContent(ByteBuf buf, Map<String, String> map) {
        if (buf.isReadable()) {
            String code = getCode(buf.readBytes(4));
            int lenght = getLength(buf.readBytes(4));

            if (code.equals("mlit")) {
                map.put(code, "");
                getContent(buf.readSlice(lenght), map);
            } else {
                String value = getCode(buf.readBytes(lenght));
                map.put(code, value);
                getContent(buf, map);
            }
        }
        if(buf.refCnt() != 0) {
            buf.release();
        }
        return map;
    }

    private static String getCode(ByteBuf arr) {
        String code = arr.toString(Charset.forName("UTF-8"));
        arr.release();
        return code;
    }

    private static int getLength(ByteBuf arr) {
        int size = arr.readInt();
        arr.release();
        return size;
    }
}
