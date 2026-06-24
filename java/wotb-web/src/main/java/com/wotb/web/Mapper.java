package com.wotb.web;

import com.wotb.core.Aggregator;
import com.wotb.core.Columns;
import com.wotb.core.DataReplayParser;
import com.wotb.core.Players;
import com.wotb.core.RatingAnalyzer;
import com.wotb.core.Tankopedia;
import com.wotb.core.model.Battle;
import com.wotb.core.model.PlayerResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/** model -> 前端 DTO (复用 core 的列定义, 保证与 Excel/桌面一致)。 */
final class Mapper {

    private Mapper() {
    }

    /** 玩家表列定义 (纯数据: key + 是否数值; 中文名由前端映射)。 */
    static List<Dtos.ColumnDef> playerColumns() {
        List<Dtos.ColumnDef> out = new ArrayList<>();
        for (Columns.Col c : Columns.PLAYER) {
            out.add(new Dtos.ColumnDef(c.key(), c.num()));
        }
        return out;
    }

    /** 汇总表列定义 (key + 是否数值 + 取值函数; 中文名由前端/导出层各自映射)。 */
    record AggCol(String key, boolean num, Function<Aggregator.Agg, Object> get) {
    }

    static final List<AggCol> AGG_COLS = List.of(
            new AggCol("nickname", false, a -> a.nickname),
            new AggCol("clan", false, a -> a.clan),
            new AggCol("battles", true, a -> a.battles),
            new AggCol("wins", true, a -> a.wins),
            new AggCol("win_rate", true, a -> r1(a.winRate())),
            new AggCol("survival_rate", true, a -> r1(a.survivalRate())),
            new AggCol("rating_avg", true, a -> Math.round(a.avgRating())),
            new AggCol("kills", true, a -> a.kills),
            new AggCol("kills_avg", true, a -> r2(a.avg(a.kills))),
            new AggCol("damage", true, a -> a.damage),
            new AggCol("damage_avg", true, a -> r1(a.avg(a.damage))),
            new AggCol("assisted", true, a -> a.assisted),
            new AggCol("assisted_avg", true, a -> r1(a.avg(a.assisted))),
            new AggCol("received_avg", true, a -> r1(a.avg(a.received))),
            new AggCol("blocked_avg", true, a -> r1(a.avg(a.blocked))),
            new AggCol("hit_rate", true, a -> r1(a.hitRate())),
            new AggCol("pen_rate", true, a -> r1(a.penRate())),
            new AggCol("enemies_damaged_avg", true, a -> r2(a.avg(a.enemiesDamaged))),
            new AggCol("tanks", false, Aggregator.Agg::tanksStr),
            new AggCol("account_id", true, a -> a.accountId)
    );

    static List<Dtos.ColumnDef> aggregateColumns() {
        List<Dtos.ColumnDef> out = new ArrayList<>();
        for (AggCol c : AGG_COLS) {
            out.add(new Dtos.ColumnDef(c.key(), c.num()));
        }
        return out;
    }

    static final List<Dtos.ColumnDef> RATING_COLS = List.of(
            new Dtos.ColumnDef("nickname", false),
            new Dtos.ColumnDef("clan", false),
            new Dtos.ColumnDef("battles", true),
            new Dtos.ColumnDef("wins", true),
            new Dtos.ColumnDef("win_rate", true),
            new Dtos.ColumnDef("rating", true),
            new Dtos.ColumnDef("kast", true),
            new Dtos.ColumnDef("contribution", true),
            new Dtos.ColumnDef("influence", true),
            new Dtos.ColumnDef("damage_avg", true),
            new Dtos.ColumnDef("kills", true),
            new Dtos.ColumnDef("kills_avg", true),
            new Dtos.ColumnDef("account_id", true)
    );

    static List<Dtos.ColumnDef> ratingColumns() {
        return RATING_COLS;
    }

    static Dtos.BattleDto toBattle(Battle b, String sourceName, Tankopedia tp) {
        Function<Long, String> platoon = Players.platoonLabeler();
        List<Dtos.PlayerRow> rows = new ArrayList<>();
        TreeSet<Integer> rawFields = new TreeSet<>();
        for (PlayerResult p : b.players) {
            if (p.raw != null) {
                rawFields.addAll(p.raw.keySet());
            }
        }
        List<String> rawColumns = rawFields.stream().map(n -> "#" + n).toList();
        TreeSet<Integer> battleRawFields = new TreeSet<>();
        if (b.raw != null) {
            battleRawFields.addAll(b.raw.keySet());
        }
        List<String> battleRawColumns = battleRawFields.stream().map(n -> "#" + n).toList();
        for (PlayerResult p : Players.sorted(b.players)) {
            Players.enrich(p, tp);
            p.platoonLabel = platoon.apply(p.platoonId);
            Map<String, Object> cells = new LinkedHashMap<>();
            for (Columns.Col c : Columns.PLAYER) {
                cells.put(c.key(), c.get().apply(p));
            }
            rows.add(new Dtos.PlayerRow(cells, p.team, rawCells(p, rawFields)));
        }
        return new Dtos.BattleDto(b.arenaId, b.mapName, b.version, b.durationS,
                b.startTime, b.winnerTeam, sourceName, rawColumns, battleRawColumns,
                rawCells(b.raw, battleRawFields), toReplayTrace(b.replayTrace), rows);
    }

    static List<Dtos.AggRow> toAggregate(Map<Long, Aggregator.Agg> aggMap) {
        List<Aggregator.Agg> list = new ArrayList<>(aggMap.values());
        list.sort((x, y) -> Double.compare(y.avg(y.damage), x.avg(x.damage)));
        List<Dtos.AggRow> out = new ArrayList<>();
        for (Aggregator.Agg a : list) {
            Map<String, Object> cells = new LinkedHashMap<>();
            for (AggCol c : AGG_COLS) {
                cells.put(c.key(), c.get().apply(a));
            }
            out.add(new Dtos.AggRow(cells));
        }
        return out;
    }

    static List<Dtos.RatingRow> toRatings(List<RatingAnalyzer.Row> ratings) {
        List<Dtos.RatingRow> out = new ArrayList<>();
        for (RatingAnalyzer.Row r : ratings) {
            Map<String, Object> cells = new LinkedHashMap<>();
            cells.put("nickname", r.nickname);
            cells.put("clan", r.clan);
            cells.put("battles", r.battles);
            cells.put("wins", r.wins);
            cells.put("win_rate", r1(r.winRate()));
            cells.put("rating", r.rating);
            cells.put("kast", r1(r.kast));
            cells.put("contribution", r1(r.contribution));
            cells.put("influence", r1(r.influence));
            cells.put("damage_avg", r1(r.damageAvg));
            cells.put("kills", r.kills);
            cells.put("kills_avg", r2(r.killsAvg));
            cells.put("account_id", r.accountId);
            out.add(new Dtos.RatingRow(cells));
        }
        return out;
    }

    private static double r1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static Map<String, String> rawCells(PlayerResult p, TreeSet<Integer> rawFields) {
        return rawCells(p.raw, rawFields);
    }

    private static Map<String, String> rawCells(Map<Integer, List<Object>> raw, TreeSet<Integer> rawFields) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Integer field : rawFields) {
            List<Object> vals = raw == null ? null : raw.get(field);
            out.put("#" + field, rawValue(vals));
        }
        return out;
    }

    private static Dtos.ReplayTraceDto toReplayTrace(DataReplayParser.Summary s) {
        return new Dtos.ReplayTraceDto(s.present(), s.clientVersion(), s.packetCount(),
                s.packetTypes(), s.packetTypeMaxPayloadBytes(), s.entityMethodSubtypes(),
                s.entityMethodSubtypeMaxPayloadBytes(), toTraceGroups(s.packetGroups()),
                toTraceGroups(s.entityMethodGroups()), s.firstClock(), s.lastClock(), s.maxPayloadBytes(), s.error());
    }

    private static List<Dtos.TraceGroupDto> toTraceGroups(List<DataReplayParser.PayloadGroup> groups) {
        List<Dtos.TraceGroupDto> out = new ArrayList<>();
        for (DataReplayParser.PayloadGroup g : groups) {
            out.add(new Dtos.TraceGroupDto(g.id(), g.count(), g.minPayloadBytes(), g.maxPayloadBytes(),
                    g.avgPayloadBytes(), g.firstClock(), g.lastClock(), g.accountIdPayloads(), g.tankIdPayloads(),
                    g.sampleAccountIds(), g.sampleTankIds(), g.sampleHexPrefix()));
        }
        return out;
    }

    private static String rawValue(List<Object> vals) {
        if (vals == null || vals.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object v : vals) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v instanceof byte[] ? toHex((byte[]) v) : String.valueOf(v));
        }
        return sb.toString();
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
