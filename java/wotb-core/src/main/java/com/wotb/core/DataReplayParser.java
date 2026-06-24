package com.wotb.core;

import com.wotb.core.model.PlayerResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight reader for the packet envelope in data.wotreplay. */
public final class DataReplayParser {

    private static final long MAGIC = 0x12345678L;

    private DataReplayParser() {
    }

    public record Summary(boolean present,
                          String clientVersion,
                          int packetCount,
                          Map<Integer, Integer> packetTypes,
                          Map<Integer, Integer> packetTypeMaxPayloadBytes,
                          Map<Integer, Integer> entityMethodSubtypes,
                          Map<Integer, Integer> entityMethodSubtypeMaxPayloadBytes,
                          List<PayloadGroup> packetGroups,
                          List<PayloadGroup> entityMethodGroups,
                          double firstClock,
                          double lastClock,
                          int maxPayloadBytes,
                          String error) {
        public static Summary missing() {
            return new Summary(false, "", 0, Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(),
                    0, 0, 0, "");
        }

        static Summary failed(String error) {
            return new Summary(true, "", 0, Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(),
                    0, 0, 0, error);
        }
    }

    public record PayloadGroup(int id,
                               int count,
                               int minPayloadBytes,
                               int maxPayloadBytes,
                               double avgPayloadBytes,
                               double firstClock,
                               double lastClock,
                               int accountIdPayloads,
                               int tankIdPayloads,
                               String sampleAccountIds,
                               String sampleTankIds,
                               String sampleHexPrefix) {
    }

    public static Summary parse(byte[] data) {
        return parse(data, List.of());
    }

    public static Summary parse(byte[] data, List<PlayerResult> players) {
        if (data == null || data.length == 0) {
            return Summary.missing();
        }
        try {
            return parseStrict(data, IdIndex.of(players));
        } catch (RuntimeException e) {
            return Summary.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static Summary parseStrict(byte[] data, IdIndex ids) {
        Reader r = new Reader(data);
        long magic = r.u32();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("invalid data.wotreplay magic: " + magic);
        }

        r.skip(8);
        r.bytes(r.u8()); // client hash
        String clientVersion = new String(r.bytes(r.u8()), StandardCharsets.UTF_8);
        r.skip(1);

        Map<Integer, Integer> packetTypes = new LinkedHashMap<>();
        Map<Integer, Integer> packetTypeMaxPayloadBytes = new LinkedHashMap<>();
        Map<Integer, Integer> subtypes = new LinkedHashMap<>();
        Map<Integer, Integer> subtypeMaxPayloadBytes = new LinkedHashMap<>();
        Map<Integer, PayloadStats> packetStats = new LinkedHashMap<>();
        Map<Integer, PayloadStats> subtypeStats = new LinkedHashMap<>();
        int packetCount = 0;
        int maxPayloadBytes = 0;
        double firstClock = 0;
        double lastClock = 0;
        boolean hasClock = false;

        while (r.remaining() >= 12) {
            int payloadLength = Math.toIntExact(r.u32());
            int packetType = Math.toIntExact(r.u32());
            float clock = r.f32();
            if (payloadLength < 0 || payloadLength > r.remaining()) {
                throw new IllegalArgumentException("truncated packet payload");
            }
            byte[] payload = r.bytes(payloadLength);

            packetCount++;
            packetTypes.merge(packetType, 1, Integer::sum);
            packetTypeMaxPayloadBytes.merge(packetType, payloadLength, Math::max);
            packetStats.computeIfAbsent(packetType, PayloadStats::new).add(clock, payload, ids);
            maxPayloadBytes = Math.max(maxPayloadBytes, payloadLength);
            if (!hasClock) {
                firstClock = clock;
                hasClock = true;
            }
            lastClock = clock;

            if (packetType == 8) {
                Integer subtype = entitySubtype(payload);
                if (subtype != null) {
                    subtypes.merge(subtype, 1, Integer::sum);
                    subtypeMaxPayloadBytes.merge(subtype, payloadLength, Math::max);
                    subtypeStats.computeIfAbsent(subtype, PayloadStats::new).add(clock, payload, ids);
                }
            }
        }

        return new Summary(true, clientVersion, packetCount, packetTypes, packetTypeMaxPayloadBytes,
                subtypes, subtypeMaxPayloadBytes, groups(packetStats), groups(subtypeStats), round3(firstClock), round3(lastClock),
                maxPayloadBytes, "");
    }

    private static Integer entitySubtype(byte[] payload) {
        if (payload.length < 8) {
            return null;
        }
        Reader r = new Reader(payload);
        r.skip(4);
        return Math.toIntExact(r.u32());
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static List<PayloadGroup> groups(Map<Integer, PayloadStats> stats) {
        List<PayloadGroup> out = new ArrayList<>();
        stats.values().stream()
                .sorted(Comparator.comparingInt(s -> s.id))
                .forEach(s -> out.add(s.group()));
        return List.copyOf(out);
    }

    private static String hexPrefix(byte[] data) {
        int len = Math.min(data.length, 48);
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", data[i]));
        }
        return sb.toString();
    }

    private static List<Long> matchingIds(byte[] data, List<IdPattern> patterns) {
        List<Long> out = new ArrayList<>();
        for (IdPattern pattern : patterns) {
            if (contains(data, pattern.le4) || contains(data, pattern.le8)) {
                out.add(pattern.id);
            }
        }
        return out;
    }

    private static boolean contains(byte[] data, byte[] pattern) {
        if (pattern.length == 0 || data.length < pattern.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String sampleIds(Set<Long> ids) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Long id : ids) {
            if (n >= 3) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(id);
            n++;
        }
        return sb.toString();
    }

    private static final class PayloadStats {
        final int id;
        int count;
        int minPayloadBytes = Integer.MAX_VALUE;
        int maxPayloadBytes;
        long totalPayloadBytes;
        double firstClock;
        double lastClock;
        int accountIdPayloads;
        int tankIdPayloads;
        final Set<Long> sampleAccountIds = new LinkedHashSet<>();
        final Set<Long> sampleTankIds = new LinkedHashSet<>();
        String sampleHexPrefix = "";

        PayloadStats(int id) {
            this.id = id;
        }

        void add(double clock, byte[] payload, IdIndex ids) {
            int len = payload.length;
            if (count == 0) {
                firstClock = clock;
                sampleHexPrefix = hexPrefix(payload);
            }
            count++;
            lastClock = clock;
            minPayloadBytes = Math.min(minPayloadBytes, len);
            maxPayloadBytes = Math.max(maxPayloadBytes, len);
            totalPayloadBytes += len;

            List<Long> accountMatches = matchingIds(payload, ids.accounts);
            if (!accountMatches.isEmpty()) {
                accountIdPayloads++;
                sampleAccountIds.addAll(accountMatches);
            }
            List<Long> tankMatches = matchingIds(payload, ids.tanks);
            if (!tankMatches.isEmpty()) {
                tankIdPayloads++;
                sampleTankIds.addAll(tankMatches);
            }
        }

        PayloadGroup group() {
            return new PayloadGroup(id, count, minPayloadBytes, maxPayloadBytes,
                    round3((double) totalPayloadBytes / count), round3(firstClock), round3(lastClock),
                    accountIdPayloads, tankIdPayloads, sampleIds(sampleAccountIds), sampleIds(sampleTankIds),
                    sampleHexPrefix);
        }
    }

    private record IdIndex(List<IdPattern> accounts, List<IdPattern> tanks) {
        static IdIndex of(List<PlayerResult> players) {
            if (players == null || players.isEmpty()) {
                return new IdIndex(List.of(), List.of());
            }
            Map<Long, IdPattern> accounts = new LinkedHashMap<>();
            Map<Long, IdPattern> tanks = new LinkedHashMap<>();
            for (PlayerResult p : players) {
                if (p.accountId > 0) {
                    accounts.putIfAbsent(p.accountId, IdPattern.of(p.accountId));
                }
                if (p.tankId > 0) {
                    tanks.putIfAbsent(p.tankId, IdPattern.of(p.tankId));
                }
            }
            return new IdIndex(List.copyOf(accounts.values()), List.copyOf(tanks.values()));
        }
    }

    private record IdPattern(long id, byte[] le4, byte[] le8) {
        static IdPattern of(long id) {
            ByteBuffer b4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            b4.putInt((int) id);
            ByteBuffer b8 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            b8.putLong(id);
            return new IdPattern(id, b4.array(), b8.array());
        }
    }

    private static final class Reader {
        private final ByteBuffer buf;

        Reader(byte[] data) {
            this.buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        }

        int remaining() {
            return buf.remaining();
        }

        int u8() {
            ensure(1);
            return buf.get() & 0xFF;
        }

        long u32() {
            ensure(4);
            return Integer.toUnsignedLong(buf.getInt());
        }

        float f32() {
            ensure(4);
            return buf.getFloat();
        }

        byte[] bytes(int len) {
            ensure(len);
            byte[] out = new byte[len];
            buf.get(out);
            return out;
        }

        void skip(int len) {
            ensure(len);
            buf.position(buf.position() + len);
        }

        private void ensure(int len) {
            if (len < 0 || buf.remaining() < len) {
                throw new IllegalArgumentException("unexpected end of data.wotreplay");
            }
        }
    }
}
