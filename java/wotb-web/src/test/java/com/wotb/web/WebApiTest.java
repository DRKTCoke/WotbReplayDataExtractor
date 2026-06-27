package com.wotb.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用 MockMvc 在进程内验证 REST API (不绑定端口, 规避本环境的 NIO selector 限制)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WebApiTest {

    @Autowired
    WebApplicationContext ctx;

    private final ObjectMapper om = new ObjectMapper();

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    private static List<Path> replays() throws Exception {
        Path dir = Path.of(System.getProperty("user.dir"), "..", "..", "common", "data").normalize();
        Assumptions.assumeTrue(Files.isDirectory(dir), "common/data 样本目录不存在, 跳过真实回放 API 回归");
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(p -> p.toString().toLowerCase().endsWith(".wotbreplay")).sorted().toList();
            Assumptions.assumeTrue(!files.isEmpty(), "common/data 中没有 .wotbreplay, 跳过真实回放 API 回归");
            return files;
        }
    }

    private static MockMultipartFile file(Path p) throws Exception {
        return new MockMultipartFile("files", p.getFileName().toString(),
                "application/octet-stream", Files.readAllBytes(p));
    }

    @Test
    void extendedPageAliasForwardsToStaticHtml() throws Exception {
        mvc().perform(get("/extended"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/extended.html"));
    }

    @Test
    void columnsEndpoint() throws Exception {
        String json = mvc().perform(get("/api/columns"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(json);
        assertTrue(n.get("player").size() > 10);
        assertTrue(stream(n.get("player")).anyMatch(c -> "rank".equals(c.get("key").asText())));
        assertTrue(stream(n.get("player")).anyMatch(c -> "alpha_damage".equals(c.get("key").asText())));
        assertTrue(stream(n.get("player")).anyMatch(c -> "potential_damage".equals(c.get("key").asText())));
        assertTrue(n.get("aggregate").size() > 10);
        assertTrue(n.get("rating").size() > 5);
    }

    @Test
    void ratingEndpointReturnsRealtimeLeaderboard() throws Exception {
        List<Path> files = replays();
        var req = multipart("/api/rating");
        for (Path p : files) {
            req = req.file(file(p));
        }
        req = req.file(new MockMultipartFile("files", "dup.wotbreplay",
                "application/octet-stream", Files.readAllBytes(files.get(0))));

        String json = mvc().perform(req.contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(json);
        assertEquals(1, n.get("duplicates").size(), "跳过 1 个重复");
        assertTrue(n.get("rows").size() >= 14, "应返回选手 rating 行");
        JsonNode cells = n.get("rows").get(0).get("cells");
        assertTrue(cells.has("rating"));
        assertTrue(cells.has("kast"));
        assertTrue(cells.has("contribution"));
        assertTrue(cells.has("influence"));
        assertTrue(cells.has("damage_avg"));
        assertTrue(cells.has("potential_damage_avg"));
        assertTrue(cells.has("potential_damage_supplement_avg"));
        assertTrue(cells.has("kills"));
        assertTrue(cells.get("rating").asInt() > 0);
    }

    @Test
    void previewMultipleWithDuplicate() throws Exception {
        List<Path> files = replays();
        var req = multipart("/api/preview");
        for (Path p : files) {
            req = req.file(file(p));
        }
        req = req.file(new MockMultipartFile("files", "dup.wotbreplay",
                "application/octet-stream", Files.readAllBytes(files.get(0))));

        String json = mvc().perform(req.contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(json);
        assertEquals(files.size(), n.get("battles").size(), "唯一战斗数");
        assertEquals(1, n.get("duplicates").size(), "跳过 1 个重复");
        assertTrue(n.get("aggregate").size() > 0, "多场应有汇总");
        // 校验首场玩家数据结构
        JsonNode b0 = n.get("battles").get(0);
        assertEquals(14, b0.get("players").size());
        assertTrue(b0.has("battleRawColumns"));
        assertTrue(b0.has("battleRaw"));
        assertTrue(b0.get("battleRawColumns").size() > 0, "应暴露 battle_results 顶层 protobuf 字段列");
        assertTrue(b0.get("rawColumns").size() > 10, "应暴露原始 protobuf 字段列");
        assertTrue(b0.has("replayTrace"));
        assertTrue(b0.get("replayTrace").has("packetCount"));
        assertTrue(b0.get("replayTrace").has("packetTypeMaxPayloadBytes"));
        assertTrue(b0.get("replayTrace").has("entityMethodSubtypes"));
        assertTrue(b0.get("replayTrace").has("entityMethodSubtypeMaxPayloadBytes"));
        assertTrue(b0.get("replayTrace").has("packetGroups"));
        assertTrue(b0.get("replayTrace").has("entityMethodGroups"));
        assertTrue(b0.get("players").get(0).get("cells").has("damage_dealt"));
        assertTrue(b0.get("players").get(0).get("cells").has("potential_damage"));
        assertTrue(b0.get("players").get(0).get("cells").has("potential_damage_supplement"));
        assertTrue(b0.get("players").get(0).has("raw"));
        assertTrue(b0.get("players").get(0).get("raw").has("#101"));
    }

    @Test
    void exportReturnsXlsx() throws Exception {
        var req = multipart("/api/export").file(file(replays().get(0)));
        byte[] body = mvc().perform(req.contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(body.length > 3000, "xlsx 应有内容");
        // xlsx = zip, 头两字节 PK
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    void exportEachReturnsZipWithOneXlsxPerReplay() throws Exception {
        List<Path> files = replays();
        var req = multipart("/api/export").param("mode", "each");
        for (Path p : files) {
            req = req.file(file(p));
        }

        byte[] body = mvc().perform(req.contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);

        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        assertEquals(files.size(), names.size());
        assertTrue(names.stream().allMatch(n -> n.endsWith(".xlsx")));
    }

    private static Stream<JsonNode> stream(JsonNode n) {
        List<JsonNode> nodes = new java.util.ArrayList<>();
        n.forEach(nodes::add);
        return nodes.stream();
    }
}
