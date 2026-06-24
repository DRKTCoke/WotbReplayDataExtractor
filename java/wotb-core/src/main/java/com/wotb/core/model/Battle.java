package com.wotb.core.model;

import java.util.List;
import java.util.Map;
import com.wotb.core.DataReplayParser;

/** 一场战斗的基本信息 + 全部玩家战绩。 */
public class Battle {
    public String arenaId;
    public Integer winnerTeam;
    public Long modeMapId;
    public String version = "";
    public String mapName = "";
    public Object mapId = "";
    public Double durationS;
    public Long startTime;
    public String recorder = "";
    public String recorderVehicle = "";
    public DataReplayParser.Summary replayTrace = DataReplayParser.Summary.missing();
    public Map<Integer, List<Object>> raw;
    public List<PlayerResult> players;

    public int nPlayers() {
        return players == null ? 0 : players.size();
    }
}
