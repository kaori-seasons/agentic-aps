package cn.windwheel.aps.entity.handler;

import cn.windwheel.aps.entity.dto.ReportFieldDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportFieldTypeHandlerTest {

    private final ReportFieldTypeHandler handler = new ReportFieldTypeHandler();

    @Test
    void shouldParseLegacyStringArray() {
        List<ReportFieldDTO> fields = handler.parse("[\"code\", \"material\", \"quantity\"]");

        assertNotNull(fields);
        assertEquals(3, fields.size());
        assertEquals("code", fields.get(0).getFieldKey());
        assertEquals("code", fields.get(0).getFieldName());
    }
}
