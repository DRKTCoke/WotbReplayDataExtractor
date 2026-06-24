package com.wotb.core;

import com.wotb.core.model.PlayerResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataReplayParserTest {

    @Test
    void parsesPacketAndEntitySubtypeSummary() {
        DataReplayParser.Summary s = DataReplayParser.parse(sampleDataReplay());

        assertTrue(s.present());
        assertEquals("9.8.5_apple", s.clientVersion());
        assertEquals(2, s.packetCount());
        assertEquals(Map.of(8, 1, 42, 1), s.packetTypes());
        assertEquals(Map.of(8, 8, 42, 0), s.packetTypeMaxPayloadBytes());
        assertEquals(Map.of(47, 1), s.entityMethodSubtypes());
        assertEquals(Map.of(47, 8), s.entityMethodSubtypeMaxPayloadBytes());
        assertEquals(2, s.packetGroups().size());
        assertEquals(8, s.packetGroups().get(0).id());
        assertEquals(8, s.packetGroups().get(0).maxPayloadBytes());
        assertEquals("640000002f000000", s.packetGroups().get(0).sampleHexPrefix());
        assertEquals(1, s.entityMethodGroups().size());
        assertEquals(47, s.entityMethodGroups().get(0).id());
        assertEquals(8.0, s.entityMethodGroups().get(0).avgPayloadBytes());
        assertEquals(1.25, s.firstClock());
        assertEquals(2.5, s.lastClock());
        assertEquals(8, s.maxPayloadBytes());
        assertEquals("", s.error());
    }

    @Test
    void marksPayloadsThatContainKnownPlayerOrTankIds() {
        PlayerResult player = new PlayerResult();
        player.accountId = 1_234_567_890_123L;
        player.tankId = 4481;

        DataReplayParser.Summary s = DataReplayParser.parse(sampleDataReplayWithIds(player), java.util.List.of(player));
        DataReplayParser.PayloadGroup packet = s.packetGroups().get(0);
        DataReplayParser.PayloadGroup subtype = s.entityMethodGroups().get(0);

        assertEquals(1, packet.accountIdPayloads());
        assertEquals(1, packet.tankIdPayloads());
        assertEquals(String.valueOf(player.accountId), packet.sampleAccountIds());
        assertEquals(String.valueOf(player.tankId), packet.sampleTankIds());
        assertEquals(1, subtype.accountIdPayloads());
        assertEquals(1, subtype.tankIdPayloads());
    }

    private static byte[] sampleDataReplay() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU32(out, 0x12345678);
        writeU64(out, 0);
        writeLenString(out, "6CF2A9EFA5C52D6F6CE43A6D4A699C05");
        writeLenString(out, "9.8.5_apple");
        out.write(0);

        writeU32(out, 8);
        writeU32(out, 8);
        writeF32(out, 1.25f);
        writeU32(out, 100);
        writeU32(out, 47);

        writeU32(out, 0);
        writeU32(out, 42);
        writeF32(out, 2.5f);
        return out.toByteArray();
    }

    private static byte[] sampleDataReplayWithIds(PlayerResult player) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU32(out, 0x12345678);
        writeU64(out, 0);
        writeLenString(out, "6CF2A9EFA5C52D6F6CE43A6D4A699C05");
        writeLenString(out, "9.8.5_apple");
        out.write(0);

        writeU32(out, 20);
        writeU32(out, 8);
        writeF32(out, 1.25f);
        writeU32(out, 100);
        writeU32(out, 47);
        writeU64(out, player.accountId);
        writeU32(out, player.tankId);
        return out.toByteArray();
    }

    private static void writeLenString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) value).array());
    }

    private static void writeU64(ByteArrayOutputStream out, long value) {
        out.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value).array());
    }

    private static void writeF32(ByteArrayOutputStream out, float value) {
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(value).array());
    }
}
