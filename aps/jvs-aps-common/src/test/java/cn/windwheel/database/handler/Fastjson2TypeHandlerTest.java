package cn.windwheel.database.handler;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Fastjson2TypeHandlerTest {

    @Test
    void shouldParseLegacyProcessInfoMainResourcesAsStringArray() {
        Fastjson2TypeHandler handler = new Fastjson2TypeHandler(ProcessRouteNodePropertiesDTO.class);
        String json = """
                {
                  "duration": "4H",
                  "sortOrder": 4,
                  "processCode": "P010",
                  "processName": "装配",
                  "useMainResources": ["rg-assy"]
                }
                """;

        ProcessRouteNodePropertiesDTO process = (ProcessRouteNodePropertiesDTO) handler.parse(json);

        assertNotNull(process);
        assertNotNull(process.getUseMainResources());
        assertEquals(1, process.getUseMainResources().size());
        assertEquals("rg-assy", process.getUseMainResources().get(0).getId());
    }
}
