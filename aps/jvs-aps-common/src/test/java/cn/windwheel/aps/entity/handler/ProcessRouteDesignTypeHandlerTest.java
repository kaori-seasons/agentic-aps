package cn.windwheel.aps.entity.handler;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.graph.Graph;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessRouteDesignTypeHandlerTest {

    private final ProcessRouteDesignTypeHandler handler = new ProcessRouteDesignTypeHandler();

    @Test
    void shouldParseLegacyStringNodeData() {
        String json = """
                {
                  "edges": [{"source": "assy", "target": "test"}],
                  "nodes": [
                    {
                      "id": "assy",
                      "data": "{\\"code\\":\\"ASSY\\",\\"name\\":\\"总成装配\\",\\"processRelationship\\":\\"ES\\",\\"useMainResources\\":[{\\"id\\":\\"r-assy01\\",\\"capacity\\":1,\\"throughput\\":\\"6pH\\"}],\\"useAuxiliaryResources\\":[],\\"useMaterials\\":[]}"
                    },
                    {
                      "id": "test",
                      "data": "{\\"code\\":\\"TEST\\",\\"name\\":\\"性能测试\\",\\"processRelationship\\":\\"ES\\",\\"useMainResources\\":[{\\"id\\":\\"r-test01\\",\\"capacity\\":1,\\"throughput\\":\\"12pH\\"}],\\"useAuxiliaryResources\\":[],\\"useMaterials\\":[]}"
                    }
                  ]
                }
                """;

        Graph<ProcessRouteNodePropertiesDTO> graph = handler.parse(json);

        assertNotNull(graph);
        assertEquals(2, graph.getNodes().size());
        assertEquals("ASSY", graph.getNode("assy").getData().getCode());
        assertEquals("TEST", graph.getNode("test").getData().getCode());
    }

    @Test
    void shouldWriteCanonicalObjectNodeData() {
        String json = """
                {
                  "edges": [],
                  "nodes": [
                    {
                      "id": "wind",
                      "data": "{\\"code\\":\\"WIND\\",\\"name\\":\\"转子绕线\\",\\"processRelationship\\":\\"ES\\",\\"useMainResources\\":[{\\"id\\":\\"r-wind01\\",\\"capacity\\":1,\\"throughput\\":\\"5pH\\"}],\\"useAuxiliaryResources\\":[],\\"useMaterials\\":[]}"
                    }
                  ]
                }
                """;

        Graph<ProcessRouteNodePropertiesDTO> graph = handler.parse(json);
        String normalized = handler.toJson(graph);
        JSONObject normalizedJson = JSON.parseObject(normalized);
        Object nodeData = normalizedJson.getJSONArray("nodes").getJSONObject(0).get("data");

        assertTrue(nodeData instanceof JSONObject);
        assertEquals("WIND", ((JSONObject) nodeData).getString("code"));
    }
}
