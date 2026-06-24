package com.wotb.web;

import java.util.List;
import java.util.Map;

/** 前端 JSON 用的数据结构。 */
public final class Dtos {

    private Dtos() {
    }

    /** 单名玩家(用 Map 承载, 键与 Columns 的 key 对齐, 便于前端通用渲染)。 */
    public record PlayerRow(java.util.Map<String, Object> cells, int team,
                            java.util.Map<String, String> raw) {
    }

    public record BattleDto(String arenaId, String mapName, String version,
                            Double durationS, Long startTime, Integer winnerTeam,
                            String sourceName, List<String> rawColumns,
                            List<String> battleRawColumns,
                            Map<String, String> battleRaw,
                            ReplayTraceDto replayTrace,
                            List<PlayerRow> players) {
    }

    public record ReplayTraceDto(boolean present,
                                 String clientVersion,
                                 int packetCount,
                                 Map<Integer, Integer> packetTypes,
                                 Map<Integer, Integer> packetTypeMaxPayloadBytes,
                                 Map<Integer, Integer> entityMethodSubtypes,
                                 Map<Integer, Integer> entityMethodSubtypeMaxPayloadBytes,
                                 List<TraceGroupDto> packetGroups,
                                 List<TraceGroupDto> entityMethodGroups,
                                 double firstClock,
                                 double lastClock,
                                 int maxPayloadBytes,
                                 String error) {
    }

    public record TraceGroupDto(int id,
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

    public record AggRow(java.util.Map<String, Object> cells) {
    }

    public record RatingRow(java.util.Map<String, Object> cells) {
    }

    /** 列定义 (纯数据形状: 字段键 + 是否数值)。中文显示名由前端/导出层各自映射。 */
    public record ColumnDef(String key, boolean num) {
    }

    public record PreviewResponse(List<BattleDto> battles,
                                  List<AggRow> aggregate,
                                  List<String[]> duplicates,
                                  List<String[]> failures,
                                  List<ColumnDef> playerColumns,
                                  List<ColumnDef> aggregateColumns) {
    }

    public record RatingResponse(List<RatingRow> rows,
                                 List<String[]> duplicates,
                                 List<String[]> failures,
                                 List<ColumnDef> ratingColumns) {
    }
}
