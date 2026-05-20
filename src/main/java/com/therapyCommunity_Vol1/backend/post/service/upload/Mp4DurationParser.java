package com.therapyCommunity_Vol1.backend.post.service.upload;

import org.springframework.stereotype.Component;

import java.util.OptionalInt;

/**
 * MP4/MOV (ISO BMFF) 영상의 moov > mvhd box 에서 duration 을 추출.
 * head(파일 앞 일부)와 tail(파일 끝 일부) 두 단편만으로 동작 — faststart 는 head 에서,
 * 비-faststart 는 tail 에서 moov 가 발견된다. 정상 box tree 에 의존하지 않고
 * 'moov' / 'mvhd' 4바이트 시그니처를 직접 스캔.
 */
@Component
public class Mp4DurationParser {

    public OptionalInt parse(byte[] head, byte[] tail) {
        OptionalInt fromHead = findDuration(head);
        if (fromHead.isPresent()) {
            return fromHead;
        }
        return findDuration(tail);
    }

    private OptionalInt findDuration(byte[] data) {
        if (data == null || data.length < 16) {
            return OptionalInt.empty();
        }
        int moovTypeOffset = findSignature(data, 0, MOOV);
        while (moovTypeOffset >= 4) {
            int moovPayloadEnd = computeMoovPayloadEnd(data, moovTypeOffset);
            int searchFrom = moovTypeOffset + 4;
            int mvhdTypeOffset = findSignature(data, searchFrom, MVHD);
            if (mvhdTypeOffset >= 4 && mvhdTypeOffset < moovPayloadEnd) {
                OptionalInt duration = readMvhdDuration(data, mvhdTypeOffset + 4);
                if (duration.isPresent()) {
                    return duration;
                }
            }
            moovTypeOffset = findSignature(data, moovTypeOffset + 4, MOOV);
        }
        return OptionalInt.empty();
    }

    private int computeMoovPayloadEnd(byte[] data, int moovTypeOffset) {
        long size = readUInt32(data, moovTypeOffset - 4);
        long boxStart = moovTypeOffset - 4L;
        long end;
        if (size == 1L) {
            // largesize 는 type 다음 8바이트
            int largeOffset = moovTypeOffset + 4;
            if (largeOffset + 8 > data.length) {
                return data.length;
            }
            long large = readUInt64(data, largeOffset);
            end = boxStart + large;
        } else if (size == 0L) {
            end = data.length;
        } else {
            end = boxStart + size;
        }
        if (end < 0 || end > data.length) {
            return data.length;
        }
        return (int) end;
    }

    private OptionalInt readMvhdDuration(byte[] data, int payloadOffset) {
        if (payloadOffset < 0 || payloadOffset + 4 > data.length) {
            return OptionalInt.empty();
        }
        int version = data[payloadOffset] & 0xFF;
        int afterFlags = payloadOffset + 4; // version(1) + flags(3)
        long timescale;
        long duration;
        if (version == 0) {
            if (afterFlags + 16 > data.length) {
                return OptionalInt.empty();
            }
            timescale = readUInt32(data, afterFlags + 8);
            duration = readUInt32(data, afterFlags + 12);
        } else if (version == 1) {
            if (afterFlags + 28 > data.length) {
                return OptionalInt.empty();
            }
            timescale = readUInt32(data, afterFlags + 16);
            duration = readUInt64(data, afterFlags + 20);
        } else {
            return OptionalInt.empty();
        }
        if (timescale <= 0 || duration <= 0) {
            return OptionalInt.empty();
        }
        long seconds = duration / timescale;
        if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) seconds);
    }

    private int findSignature(byte[] data, int start, byte[] sig) {
        int limit = data.length - sig.length;
        for (int i = Math.max(start, 0); i <= limit; i++) {
            if (data[i] == sig[0] && data[i + 1] == sig[1]
                    && data[i + 2] == sig[2] && data[i + 3] == sig[3]) {
                return i;
            }
        }
        return -1;
    }

    private long readUInt32(byte[] b, int offset) {
        return ((long) (b[offset] & 0xFF) << 24)
                | ((long) (b[offset + 1] & 0xFF) << 16)
                | ((long) (b[offset + 2] & 0xFF) << 8)
                | (long) (b[offset + 3] & 0xFF);
    }

    private long readUInt64(byte[] b, int offset) {
        long high = readUInt32(b, offset);
        long low = readUInt32(b, offset + 4);
        return (high << 32) | (low & 0xFFFFFFFFL);
    }

    private static final byte[] MOOV = {'m', 'o', 'o', 'v'};
    private static final byte[] MVHD = {'m', 'v', 'h', 'd'};
}
