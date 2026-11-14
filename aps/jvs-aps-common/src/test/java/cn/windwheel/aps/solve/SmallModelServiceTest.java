package cn.windwheel.aps.solve;

import cn.windwheel.aps.solve.presolve.*;
import cn.windwheel.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import javax.validation.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.windwheel.aps.solve.HybridSchedulingTest.START;
import static org.junit.jupiter.api.Assertions.*;

class SmallModelServiceTest {
    HttpServer server;
    ValidatorFactory validators;
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final AtomicReference<String> response = new AtomicReference<>();
    final AtomicReference<String> request = new AtomicReference<>();
    final AtomicReference<String> upgrade = new AtomicReference<>();
    final AtomicInteger status = new AtomicInteger(200);
    SmallModelProperties properties;
    SmallModelService service;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status.get(),bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        validators = Validation.buildDefaultValidatorFactory();
        properties = new SmallModelProperties();
        properties.setEnabled(true);
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
        service = new SmallModelService(properties,mapper,validators.getValidator(),new DynamicSchedulingService());
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
        validators.close();
    }

    void proposal(String json) throws Exception {
        response.set(mapper.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content",json))))));
    }

    @Test
    void validatesModelProposalWithoutPublishingIt() throws Exception {
        proposal("{\"events\":[{\"type\":\"RESOURCE_UNAVAILABLE\",\"targetId\":\"R\",\"start\":\"2026-11-13T09:00:00\",\"end\":\"2026-11-13T10:00:00\"}],\"seeds\":[],\"recommendedProfile\":\"FFD_TABU\"}");
        var result = service.propose("R设备9点到10点故障",START,DynamicSchedulingTest.data());
        assertEquals("R",result.getEvents().get(0).getTargetId());
        assertEquals(START,result.getFreezeUntil());
        assertNull(upgrade.get(),"local model servers must not receive h2c upgrade requests");
    }

    @Test
    void jsonObjectProvidersReceiveTheExactSchemaInThePrompt() throws Exception {
        proposal("{\"events\":[{\"type\":\"RESOURCE_UNAVAILABLE\",\"targetId\":\"R\",\"start\":\"2026-11-13T09:00:00\",\"end\":\"2026-11-13T10:00:00\"}],\"seeds\":[]}");
        service.propose("R downtime",START,DynamicSchedulingTest.data());
        var sent = mapper.readTree(request.get());
        assertEquals("json_object",sent.at("/response_format/type").asText());
        String instruction = sent.at("/messages/0/content").asText();
        assertTrue(instruction.contains("\"availableAt\""));
        assertTrue(instruction.contains("\"deliveryTime\""));
        assertTrue(instruction.contains("\"additionalProperties\":false"));
    }

    @Test
    void schemaBindsEventTypeDatesAndTenantIds() throws Exception {
        properties.setResponseFormat(SmallModelProperties.ResponseFormat.JSON_SCHEMA);
        proposal("{\"events\":[{\"type\":\"RESOURCE_UNAVAILABLE\",\"targetId\":\"R\",\"start\":\"2026-11-13T09:00:00\",\"end\":\"2026-11-13T10:00:00\"}],\"seeds\":[]}");
        service.propose("R downtime",START,DynamicSchedulingTest.data());
        var schema = mapper.readTree(request.get()).at("/response_format/json_schema/schema");
        var alternatives = schema.at("/properties/events/items/anyOf");
        assertTrue(alternatives.isArray());
        var outage = java.util.stream.StreamSupport.stream(alternatives.spliterator(),false)
                .filter(node -> "RESOURCE_UNAVAILABLE".equals(node.at("/properties/type/enum/0").asText()))
                .findFirst().orElseThrow();
        assertEquals("R",outage.at("/properties/targetId/enum/0").asText());
        assertTrue(outage.get("required").toString().contains("start"));
        assertTrue(outage.get("required").toString().contains("end"));
        assertFalse(outage.at("/properties/start/pattern").asText().isEmpty());
    }

    @Test
    void rejectsInventedIdsUnknownFieldsAndMalformedJson() throws Exception {
        proposal("{\"events\":[{\"type\":\"RESOURCE_UNAVAILABLE\",\"targetId\":\"UNKNOWN\",\"start\":\"2026-11-13T09:00:00\",\"end\":\"2026-11-13T10:00:00\"}]}");
        assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
        proposal("{\"events\":[],\"publish\":true}");
        assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
        proposal("not-json");
        assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
    }

    @Test
    void distinguishesConnectionFailuresFromInvalidModelJson() {
        server.stop(0);
        var error = assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
        assertTrue(error.getMessage().contains("小模型连接失败"));
        assertFalse(error.getMessage().contains("模型返回内容无效"));
    }

    @Test
    void reportsProviderStatusWithoutReflectingSensitiveResponseBody() {
        status.set(401);
        response.set("provider-internal-sensitive-message");
        var error = assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
        assertTrue(error.getMessage().contains("HTTP 401"));
        assertFalse(error.getMessage().contains("provider-internal"));
    }

    @Test
    void rejectsOversizedResponsesAndDisabledModel() {
        response.set("x".repeat(128001));
        assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
        properties.setEnabled(false);
        assertThrows(BusinessException.class,()->service.propose("fault",START,DynamicSchedulingTest.data()));
        assertTrue(service.explain(Map.of()).contains("未启用"));
    }
}
