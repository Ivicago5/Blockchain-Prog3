package Network;

import Blockchain.Blockchain;
import Util.Logger;
import Wallet.Wallet;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Node {

    private final String nodeId;
    private final int port;
    private final String nodeAddress;
    private final String bootstrapNode;
    private final boolean isBootstrap;

    private final Set<String> peers;
    private final HttpClient httpClient;

    private final Blockchain blockchain;
    private final Wallet wallet;

    private HttpServer server;

    public Node(String nodeId, int port, String nodeAddress, String bootstrapNode, boolean isBootstrap) {
        this.nodeId = nodeId;
        this.port = port;
        this.nodeAddress = nodeAddress;
        this.bootstrapNode = bootstrapNode;
        this.isBootstrap = isBootstrap;

        this.peers = Collections.synchronizedSet(new LinkedHashSet<>());
        this.httpClient = HttpClient.newHttpClient();

        this.blockchain = new Blockchain();
        this.wallet = new Wallet();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/status", this::handleStatus);
            server.createContext("/health", this::handleHealth);
            server.createContext("/peers", this::handlePeers);
            server.createContext("/peers/request", this::handlePeerRequest);
            server.createContext("/peers/response", this::handlePeerResponse);

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();

            Logger.info("Node " + nodeId + " started on port " + port);
            Logger.info("Node address: " + nodeAddress);
            Logger.info("Wallet public key: " + wallet.getPublicKeyBase64());
            Logger.info("Bootstrap node: " + bootstrapNode);
            Logger.info("Is bootstrap: " + isBootstrap);

            if (!isBootstrap && bootstrapNode != null && !bootstrapNode.isEmpty()) {
                registerWithBootstrap();
            }
            
        } catch (IOException e) {
            Logger.error("Failed to start node: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void registerWithBootstrap() {
        String json = "{"
                + "\"type\":\"PEER_DISCOVERY_REQUEST\","
                + "\"nodeId\":\"" + escape(nodeId) + "\","
                + "\"address\":\"" + escape(nodeAddress) + "\""
                + "}";

        /*
         * Small retry loop because Docker containers can start at slightly different times.
         */
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(bootstrapNode + "/peers/request"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    Logger.info("Registered with bootstrap node successfully");
                    Logger.info("Discovery response: " + response.body());

                    addPeer(bootstrapNode);

                    for (String peer : extractJsonArrayStrings(response.body(), "peers")) {
                        addPeer(peer);
                    }

                    Logger.info("Peers after discovery: " + peers);
                    return;
                }

                Logger.warn("Bootstrap registration failed with status "
                        + response.statusCode()
                        + ": "
                        + response.body());

            } catch (Exception e) {
                Logger.warn("Bootstrap registration attempt "
                        + attempt
                        + " failed: "
                        + e.getMessage());
            }

            sleep(1000);
        }

        Logger.error("Could not register with bootstrap node after retries");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Node interrupted", e);
        }
    }

    private List<String> extractJsonArrayStrings(String json, String key) {
        List<String> result = new ArrayList<>();

        if (json == null || key == null) {
            return result;
        }

        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);

        if (keyIndex == -1) {
            return result;
        }

        int arrayStart = json.indexOf("[", keyIndex);
        int arrayEnd = json.indexOf("]", arrayStart);

        if (arrayStart == -1 || arrayEnd == -1) {
            return result;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();

        if (arrayContent.isEmpty()) {
            return result;
        }

        String[] parts = arrayContent.split(",");

        for (String part : parts) {
            String cleaned = part.trim();

            if (cleaned.startsWith("\"")) {
                cleaned = cleaned.substring(1);
            }

            if (cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }

            cleaned = cleaned.trim();

            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }

        return result;
    }

    private void addPeer(String peerAddress) {
        if (peerAddress == null || peerAddress.isEmpty()) {
            return;
        }

        if (peerAddress.equals(nodeAddress)) {
            return;
        }

        peers.add(peerAddress);
    }

    private void handlePeerResponse(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = readRequestBody(exchange);
        String newAddress = extractJsonString(body, "address");

        if (newAddress == null || newAddress.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Missing peer address\"}");
            return;
        }

        addPeer(newAddress);

        Logger.info("Peer announced and added: " + newAddress);

        sendJson(exchange, 200, "{\"status\":\"peer added\"}");
    }

    private void handlePeerRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        if (!isBootstrap) {
            sendJson(exchange, 403, "{\"error\":\"Only bootstrap node accepts peer registration\"}");
            return;
        }

        String body = readRequestBody(exchange);

        String newNodeId = extractJsonString(body, "nodeId");
        String newAddress = extractJsonString(body, "address");

        if (newAddress == null || newAddress.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Missing peer address\"}");
            return;
        }

        if (!newAddress.equals(nodeAddress)) {
            peers.add(newAddress);
        }

        Logger.info("Registered peer: " + newNodeId + " at " + newAddress);

        /*
         * Tell existing peers about the new peer.
         * This makes node2 learn node3 when node3 joins later.
         */
        announcePeerToKnownPeers(newAddress);

        /*
         * Return bootstrap address + all known peers to the new node.
         */
        String response = "{"
                + "\"type\":\"PEER_DISCOVERY_RESPONSE\","
                + "\"bootstrap\":\"" + escape(nodeAddress) + "\","
                + "\"peers\":" + discoveryPeersFor(newAddress)
                + "}";

        sendJson(exchange, 200, response);
    }

    private String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);

        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(":", keyIndex);

        if (colonIndex == -1) {
            return null;
        }

        int firstQuote = json.indexOf("\"", colonIndex + 1);

        if (firstQuote == -1) {
            return null;
        }

        int secondQuote = json.indexOf("\"", firstQuote + 1);

        if (secondQuote == -1) {
            return null;
        }

        return json.substring(firstQuote + 1, secondQuote);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    private String discoveryPeersFor(String requestingAddress) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;

        /*
         * Always include bootstrap node address.
         */
        if (!nodeAddress.equals(requestingAddress)) {
            sb.append("\"").append(escape(nodeAddress)).append("\"");
            first = false;
        }

        synchronized (peers) {
            for (String peer : peers) {
                if (peer.equals(requestingAddress)) {
                    continue;
                }

                if (!first) {
                    sb.append(",");
                }

                sb.append("\"").append(escape(peer)).append("\"");
                first = false;
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private void announcePeerToKnownPeers(String newAddress) {
        String json = "{"
                + "\"type\":\"PEER_DISCOVERY_RESPONSE\","
                + "\"address\":\"" + escape(newAddress) + "\""
                + "}";

        Set<String> snapshot;

        synchronized (peers) {
            snapshot = new LinkedHashSet<>(peers);
        }

        for (String peer : snapshot) {
            if (peer.equals(newAddress)) {
                continue;
            }

            if (peer.equals(nodeAddress)) {
                continue;
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(peer + "/peers/response"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            } catch (Exception e) {
                Logger.warn("Failed to announce peer to " + peer + ": " + e.getMessage());
            }
        }
    }

    private void handlePeers(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String json = "{" + "\"nodeId\":\"" + escape(nodeId) + "\"," + "\"peers\":" + peersToJson() + "}";

        sendJson(exchange, 200, json);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String json = "{"
                + "\"nodeId\":\"" + escape(nodeId) + "\","
                + "\"nodeAddress\":\"" + escape(nodeAddress) + "\","
                + "\"port\":" + port + ","
                + "\"isBootstrap\":" + isBootstrap + ","
                + "\"height\":" + blockchain.getHeight() + ","
                + "\"latestHash\":\"" + escape(blockchain.getLatestHash()) + "\","
                + "\"walletPublicKey\":\"" + escape(wallet.getPublicKeyBase64()) + "\","
                + "\"peers\":" + peersToJson()
                + "}";

        sendJson(exchange, 200, json);
    }

    private String peersToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        synchronized (peers) {
            int i = 0;

            for (String peer : peers) {
                sb.append("\"").append(escape(peer)).append("\"");

                if (i < peers.size() - 1) {
                    sb.append(",");
                }

                i++;
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private String escape(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<String> getPeers() {
        synchronized (peers) {
            return new ArrayList<>(peers);
        }
    }

    public String getNodeAddress() {
        return nodeAddress;
    }

    public boolean isBootstrap() {
        return isBootstrap;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getPort() {
        return port;
    }
}