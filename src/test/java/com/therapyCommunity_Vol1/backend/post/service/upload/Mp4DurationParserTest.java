package com.therapyCommunity_Vol1.backend.post.service.upload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class Mp4DurationParserTest {

    private final Mp4DurationParser parser = new Mp4DurationParser();

    @Test
    void parse_faststart_version0_returnsSeconds() {
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", mvhdV0(1000, 120000)));
        assertThat(parser.parse(head, new byte[0])).hasValue(120);
    }

    @Test
    void parse_nonFaststart_moovInTail_returnsSeconds() {
        byte[] head = concat(box("ftyp", new byte[8]), box("mdat", new byte[64]));
        byte[] tail = box("moov", mvhdV0(1000, 90000));
        assertThat(parser.parse(head, tail)).hasValue(90);
    }

    @Test
    void parse_version1_64bitDuration_returnsSeconds() {
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", mvhdV1(600, 180000)));
        assertThat(parser.parse(head, new byte[0])).hasValue(300);
    }

    @Test
    void parse_noMoov_returnsEmpty() {
        byte[] head = concat(box("ftyp", new byte[8]), box("mdat", new byte[64]));
        assertThat(parser.parse(head, new byte[0])).isEmpty();
    }

    @Test
    void parse_zeroTimescale_returnsEmpty() {
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", mvhdV0(0, 120000)));
        assertThat(parser.parse(head, new byte[0])).isEmpty();
    }

    @Test
    void parse_emptyOrNullInputs_returnsEmpty() {
        assertThat(parser.parse(new byte[0], new byte[0])).isEmpty();
        assertThat(parser.parse(null, null)).isEmpty();
    }

    @Test
    void parse_truncatesFractionalSeconds_byDesign() {
        // 300.999초 → 300 (정수 내림). 인코딩 오차 흡수를 위해 약간의 초과는 의도적으로 허용.
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", mvhdV0(1000, 300_999)));
        assertThat(parser.parse(head, new byte[0])).hasValue(300);
    }

    @Test
    void parse_durationOverBoundary_returnsCeiledInteger() {
        // 301.0초 → 301 (정책 거부는 MediaKindPolicy 계층에서 수행)
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", mvhdV0(1000, 301_000)));
        assertThat(parser.parse(head, new byte[0])).hasValue(301);
    }

    @Test
    void parse_zeroDuration_returnsEmpty() {
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", mvhdV0(1000, 0)));
        assertThat(parser.parse(head, new byte[0])).isEmpty();
    }

    @Test
    void parse_unsupportedVersion_returnsEmpty() {
        byte[] p = new byte[100];
        p[0] = 2; // version 2 (미지원)
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", box("mvhd", p)));
        assertThat(parser.parse(head, new byte[0])).isEmpty();
    }

    @Test
    void parse_truncatedMvhd_returnsEmpty() {
        // mvhd 시그니처는 있으나 payload 가 잘려 timescale/duration 을 못 읽음
        byte[] head = concat(box("ftyp", new byte[8]), box("moov", box("mvhd", new byte[4])));
        assertThat(parser.parse(head, new byte[0])).isEmpty();
    }

    // --- ISO BMFF fixtures ---

    private static byte[] box(String type, byte[] payload) {
        int size = 8 + payload.length;
        byte[] b = new byte[size];
        putUInt32(b, 0, size);
        b[4] = (byte) type.charAt(0);
        b[5] = (byte) type.charAt(1);
        b[6] = (byte) type.charAt(2);
        b[7] = (byte) type.charAt(3);
        System.arraycopy(payload, 0, b, 8, payload.length);
        return b;
    }

    // mvhd payload: version(1)+flags(3) | creation(4) | modification(4) | timescale(4) | duration(4) | ...
    private static byte[] mvhdV0(int timescale, long duration) {
        byte[] p = new byte[100];
        putUInt32(p, 12, timescale);
        putUInt32(p, 16, duration);
        return box("mvhd", p);
    }

    // mvhd payload v1: version(1)+flags(3) | creation(8) | modification(8) | timescale(4) | duration(8) | ...
    private static byte[] mvhdV1(int timescale, long duration) {
        byte[] p = new byte[120];
        p[0] = 1;
        putUInt32(p, 20, timescale);
        putUInt64(p, 24, duration);
        return box("mvhd", p);
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (byte[] a : arrays) {
                out.write(a);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void putUInt32(byte[] b, int offset, long value) {
        b[offset] = (byte) ((value >> 24) & 0xFF);
        b[offset + 1] = (byte) ((value >> 16) & 0xFF);
        b[offset + 2] = (byte) ((value >> 8) & 0xFF);
        b[offset + 3] = (byte) (value & 0xFF);
    }

    private static void putUInt64(byte[] b, int offset, long value) {
        b[offset] = (byte) ((value >> 56) & 0xFF);
        b[offset + 1] = (byte) ((value >> 48) & 0xFF);
        b[offset + 2] = (byte) ((value >> 40) & 0xFF);
        b[offset + 3] = (byte) ((value >> 32) & 0xFF);
        b[offset + 4] = (byte) ((value >> 24) & 0xFF);
        b[offset + 5] = (byte) ((value >> 16) & 0xFF);
        b[offset + 6] = (byte) ((value >> 8) & 0xFF);
        b[offset + 7] = (byte) (value & 0xFF);
    }
}
