package com.leonid.giwaapi.transaction;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GiwaJsonRpcClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private BlockchainRpcProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        properties = new BlockchainRpcProperties();
        properties.setRpcUrl(
                "http://127.0.0.1:" + server.getAddress().getPort()
        );
        properties.setRpcTimeoutMs(2000L);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void loadsAndParsesACompleteTransactionProof() {
        server.createContext("/", this::respondWithProof);
        server.start();

        GiwaRpcProof proof = new GiwaJsonRpcClient(
                properties,
                objectMapper
        ).getTransactionProof("0x" + "a".repeat(64));

        assertThat(proof.chainId()).isEqualTo("0x164ce");
        assertThat(proof.latestBlockNumber()).isEqualTo("0x65");
        assertThat(proof.transaction().from())
                .isEqualTo("0x" + "1".repeat(40));
        assertThat(proof.receipt().gasUsed()).isEqualTo("0x5208");
        assertThat(proof.receipt().logs()).singleElement()
                .satisfies(log -> {
                    assertThat(log.address())
                            .isEqualTo("0x" + "3".repeat(40));
                    assertThat(log.topics()).containsExactly(
                            "0x" + "4".repeat(64)
                    );
                    assertThat(log.removed()).isFalse();
                });
        assertThat(proof.canonicalBlock().hash())
                .isEqualTo("0x" + "b".repeat(64));
    }

    @Test
    void preservesNullTransactionAndReceiptForRetryableVerification() {
        server.createContext("/", exchange -> {
            String method = requestMethod(exchange);
            Object result = switch (method) {
                case "eth_chainId" -> "0x164ce";
                case "eth_blockNumber" -> "0x65";
                case "eth_getTransactionByHash",
                     "eth_getTransactionReceipt" -> null;
                default -> throw new IllegalStateException(method);
            };
            respond(exchange, result);
        });
        server.start();

        GiwaRpcProof proof = new GiwaJsonRpcClient(
                properties,
                objectMapper
        ).getTransactionProof("0x" + "a".repeat(64));

        assertThat(proof.transaction()).isNull();
        assertThat(proof.receipt()).isNull();
        assertThat(proof.canonicalBlock()).isNull();
    }

    @Test
    void convertsJsonRpcErrorsWithoutExposingTheRemoteBody() {
        server.createContext("/", exchange -> {
            byte[] response = """
                    {"jsonrpc":"2.0","id":1,"error":{
                      "code":-32000,
                      "message":"secret provider detail"
                    }}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json"
            );
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> new GiwaJsonRpcClient(
                properties,
                objectMapper
        ).getTransactionProof("0x" + "a".repeat(64)))
                .isInstanceOf(GiwaRpcClientException.class)
                .hasMessage("GIWA RPC returned a JSON-RPC error")
                .hasMessageNotContaining("secret provider detail");
    }

    private void respondWithProof(HttpExchange exchange) throws IOException {
        String method = requestMethod(exchange);
        Object result = switch (method) {
            case "eth_chainId" -> "0x164ce";
            case "eth_blockNumber" -> "0x65";
            case "eth_getTransactionByHash" -> Map.of(
                    "hash", "0x" + "a".repeat(64),
                    "from", "0x" + "1".repeat(40),
                    "to", "0x" + "3".repeat(40),
                    "input", "0x1234",
                    "value", "0x0",
                    "chainId", "0x164ce",
                    "blockNumber", "0x64",
                    "blockHash", "0x" + "b".repeat(64),
                    "gasPrice", "0x3b9aca00"
            );
            case "eth_getTransactionReceipt" -> Map.of(
                    "transactionHash", "0x" + "a".repeat(64),
                    "from", "0x" + "1".repeat(40),
                    "to", "0x" + "3".repeat(40),
                    "blockNumber", "0x64",
                    "blockHash", "0x" + "b".repeat(64),
                    "status", "0x1",
                    "gasUsed", "0x5208",
                    "effectiveGasPrice", "0x3b9aca00",
                    "logs", List.of(Map.of(
                            "address", "0x" + "3".repeat(40),
                            "topics", List.of("0x" + "4".repeat(64)),
                            "data", "0x",
                            "removed", false
                    ))
            );
            case "eth_getBlockByNumber" -> Map.of(
                    "number", "0x64",
                    "hash", "0x" + "b".repeat(64)
            );
            default -> throw new IllegalStateException(method);
        };
        respond(exchange, result);
    }

    private String requestMethod(HttpExchange exchange) throws IOException {
        JsonNode request = objectMapper.readTree(
                exchange.getRequestBody().readAllBytes()
        );
        return request.path("method").asString();
    }

    private void respond(HttpExchange exchange, Object result)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 1);
        payload.put("result", result);
        byte[] response = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
