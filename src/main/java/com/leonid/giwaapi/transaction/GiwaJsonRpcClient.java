package com.leonid.giwaapi.transaction;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
class GiwaJsonRpcClient implements GiwaRpcClient {

    private static final long DEFAULT_TIMEOUT_MS = 10000L;

    private final BlockchainRpcProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicLong requestId = new AtomicLong();

    GiwaJsonRpcClient(
            BlockchainRpcProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs()))
                .build();
    }

    @Override
    public GiwaRpcProof getTransactionProof(String txHash) {
        JsonNode chainId = call("eth_chainId", List.of());
        JsonNode transaction = call(
                "eth_getTransactionByHash",
                List.of(txHash)
        );
        JsonNode receipt = call(
                "eth_getTransactionReceipt",
                List.of(txHash)
        );
        JsonNode latestBlockNumber = call("eth_blockNumber", List.of());

        GiwaRpcProof.Receipt parsedReceipt = parseReceipt(receipt);
        JsonNode canonicalBlock = parsedReceipt == null
                || parsedReceipt.blockNumber() == null
                ? null
                : call(
                        "eth_getBlockByNumber",
                        List.of(parsedReceipt.blockNumber(), false)
                );

        return new GiwaRpcProof(
                text(chainId),
                text(latestBlockNumber),
                parseTransaction(transaction),
                parsedReceipt,
                parseBlock(canonicalBlock)
        );
    }

    private JsonNode call(String method, List<?> params) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "id", requestId.incrementAndGet(),
                    "method", method,
                    "params", params
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getRpcUrl()))
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GiwaRpcClientException(
                        "GIWA RPC returned HTTP " + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                throw new GiwaRpcClientException("GIWA RPC returned a JSON-RPC error");
            }
            JsonNode result = root.get("result");
            if (result == null) {
                throw new GiwaRpcClientException("GIWA RPC response has no result");
            }
            return result.isNull() ? null : result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GiwaRpcClientException("GIWA RPC request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new GiwaRpcClientException("GIWA RPC request failed", exception);
        }
    }

    private GiwaRpcProof.Transaction parseTransaction(JsonNode node) {
        if (node == null) return null;
        return new GiwaRpcProof.Transaction(
                text(node.get("hash")),
                text(node.get("from")),
                text(node.get("to")),
                text(node.get("input")),
                text(node.get("value")),
                text(node.get("chainId")),
                text(node.get("blockNumber")),
                text(node.get("blockHash")),
                text(node.get("gasPrice"))
        );
    }

    private GiwaRpcProof.Receipt parseReceipt(JsonNode node) {
        if (node == null) return null;
        return new GiwaRpcProof.Receipt(
                text(node.get("transactionHash")),
                text(node.get("from")),
                text(node.get("to")),
                text(node.get("blockNumber")),
                text(node.get("blockHash")),
                text(node.get("status")),
                text(node.get("gasUsed")),
                text(node.get("effectiveGasPrice")),
                parseLogs(node.get("logs"))
        );
    }

    private List<GiwaRpcProof.Log> parseLogs(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<GiwaRpcProof.Log> logs = new ArrayList<>();
        for (JsonNode log : node) {
            List<String> topics = new ArrayList<>();
            JsonNode topicNodes = log.get("topics");
            if (topicNodes != null && topicNodes.isArray()) {
                for (JsonNode topic : topicNodes) {
                    topics.add(text(topic));
                }
            }
            JsonNode removed = log.get("removed");
            logs.add(new GiwaRpcProof.Log(
                    text(log.get("address")),
                    List.copyOf(topics),
                    text(log.get("data")),
                    removed != null && removed.asBoolean(false)
            ));
        }
        return List.copyOf(logs);
    }

    private GiwaRpcProof.Block parseBlock(JsonNode node) {
        if (node == null) return null;
        return new GiwaRpcProof.Block(
                text(node.get("number")),
                text(node.get("hash"))
        );
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asString();
    }

    private long timeoutMs() {
        Long configured = properties.getRpcTimeoutMs();
        return configured != null && configured > 0
                ? configured
                : DEFAULT_TIMEOUT_MS;
    }
}
