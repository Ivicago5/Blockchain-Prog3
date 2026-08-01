package Network;

import Blockchain.Blockchain;
import Blockchain.Block;
import Util.JsonUtil;
import Util.Logger;
import Wallet.Wallet;
import Transaction.Transaction;
import Blockchain.GenesisConfig;

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
    private final Set<String> seenTransactions;
    private final Set<String> seenBlocks;
    private final Set<String> faucetClaims;

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

        this.seenTransactions = Collections.synchronizedSet(new LinkedHashSet<>());
        this.seenBlocks = Collections.synchronizedSet(new LinkedHashSet<>());
        this.faucetClaims = Collections.synchronizedSet(new HashSet<>());

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
            server.createContext("/wallet", this::handleWallet);
            server.createContext("/wallet/send", this::handleWalletSend);
            server.createContext("/faucet", this::handleFaucet);
            server.createContext("/transaction", this::handleTransaction);
            server.createContext("/block", this::handleBlock);
            server.createContext("/mine", this::handleMine);
            server.createContext("/chain", this::handleChain);

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

    private void handleFaucet(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange,405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }


        String body = readRequestBody(exchange);


        String receiver = JsonUtil.extractString(body,"receiver");

        if (receiver != null){
            receiver = receiver.trim();
        }


        if(receiver == null || receiver.isEmpty()) {

            sendJson(exchange,400,
                    "{\"error\":\"Missing receiver\"}");

            return;
        }


        if(!faucetClaims.add(receiver)) {

            sendJson(exchange,400,
                    "{\"error\":\"Wallet already claimed faucet\"}");

            return;
        }


        Transaction faucetTx =
                new Transaction(
                        "FAUCET",
                        GenesisConfig.SYSTEM_SENDER,
                        receiver,
                        100,
                        new ArrayList<>(),
                        ""
                );


        if(!blockchain.addTransaction(faucetTx)) {

            faucetClaims.remove(receiver);

            sendJson(exchange,400,
                    "{\"error\":\"Faucet transaction rejected\"}");

            return;
        }


        seenTransactions.add(faucetTx.getTxId());

        broadcastTransaction(faucetTx);


        sendJson(exchange,200,
                "{"
                        +"\"status\":\"Faucet coins created\","
                        +"\"txId\":\""
                        +JsonUtil.escape(faucetTx.getTxId())
                        +"\""
                        +"}");
    }

    private void registerWithBootstrap() {
        String json = "{"
                + "\"type\":\"PEER_DISCOVERY_REQUEST\","
                + "\"nodeId\":\"" + JsonUtil.escape(nodeId) + "\","
                + "\"address\":\"" + JsonUtil.escape(nodeAddress) + "\""
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

                    for (String peer : JsonUtil.extractStringArray(response.body(), "peers")) {
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
        String newAddress = JsonUtil.extractString(body, "address");

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

        String newNodeId = JsonUtil.extractString(body, "nodeId");
        String newAddress = JsonUtil.extractString(body, "address");

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
                + "\"bootstrap\":\"" + JsonUtil.escape(nodeAddress) + "\","
                + "\"peers\":" + discoveryPeersFor(newAddress)
                + "}";

        sendJson(exchange, 200, response);
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
            sb.append("\"").append(JsonUtil.escape(nodeAddress)).append("\"");
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

                sb.append("\"").append(JsonUtil.escape(peer)).append("\"");
                first = false;
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private void announcePeerToKnownPeers(String newAddress) {
        String json = "{"
                + "\"type\":\"PEER_DISCOVERY_RESPONSE\","
                + "\"address\":\"" + JsonUtil.escape(newAddress) + "\""
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

        String json = "{" + "\"nodeId\":\"" + JsonUtil.escape(nodeId) + "\"," + "\"peers\":" + peersToJson() + "}";

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
                + "\"nodeId\":\"" + JsonUtil.escape(nodeId) + "\","
                + "\"nodeAddress\":\"" + JsonUtil.escape(nodeAddress) + "\","
                + "\"port\":" + port + ","
                + "\"isBootstrap\":" + isBootstrap + ","
                + "\"height\":" + blockchain.getHeight() + ","
                + "\"balance\":" + wallet.getBalance(blockchain) + ","
                + "\"latestHash\":\"" + JsonUtil.escape(blockchain.getLatestHash()) + "\","
                + "\"walletPublicKey\":\"" + JsonUtil.escape(wallet.getPublicKeyBase64()) + "\","
                + "\"peers\":" + peersToJson() + ","
                + "\"pendingTransactions\":"
                + blockchain.getPendingTransactionCount()
                + "}";

        sendJson(exchange, 200, json);
    }

    private void handleWallet(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        String json = "{"
                + "\"nodeId\":\"" + JsonUtil.escape(nodeId) + "\","
                + "\"publicKey\":\"" + JsonUtil.escape(wallet.getPublicKeyBase64()) + "\""
                + "}";


        sendJson(exchange, 200, json);
    }

    private void handleWalletSend(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {

            String body = readRequestBody(exchange);


            String receiver =
                    JsonUtil.extractString(body, "receiver").trim();


            int amount =
                    JsonUtil.extractInt(body, "amount");

            if (receiver == null || receiver.isEmpty()) {

                sendJson(exchange, 400,
                        "{\"error\":\"Missing receiver\"}");

                return;
            }

            if (amount <= 0) {

                sendJson(exchange, 400,
                        "{\"error\":\"Amount must be positive\"}");

                return;
            }

            Transaction tx =
                    wallet.createTransaction(
                            receiver,
                            amount,
                            blockchain
                    );


            if (tx == null) {

                sendJson(exchange, 400,
                        "{\"error\":\"Could not create transaction\"}");

                return;
            }

            if (!blockchain.addTransaction(tx)) {

                sendJson(exchange, 400,
                        "{\"error\":\"Transaction rejected\"}");

                return;
            }

            seenTransactions.add(tx.getTxId());


            broadcastTransaction(tx);


            Logger.info(
                    "Created and broadcast transaction "
                            + tx.getTxId()
            );


            String response = "{"
                    + "\"status\":\"Transaction created\","
                    + "\"txId\":\""
                    + JsonUtil.escape(tx.getTxId())
                    + "\""
                    + "}";


            sendJson(exchange, 200, response);



        } catch (Exception e) {

            Logger.error(
                    "Failed creating transaction: "
                            + e.getMessage()
            );


            sendJson(exchange, 400,
                    "{\"error\":\"Invalid request\"}");
        }
    }

    private void handleTransaction(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {

            String body = readRequestBody(exchange);

            Transaction transaction = Transaction.fromJson(body);

            if (!seenTransactions.add(transaction.getTxId())) {
                sendJson(exchange, 200, "{\"status\":\"Transaction already processed\"}");
                return;
            }

            if (!blockchain.addTransaction(transaction)) {
                seenTransactions.remove(transaction.getTxId());

                sendJson(exchange, 400,
                        "{\"error\":\"Transaction validation failed\"}");
                return;
            }

            Logger.info("Accepted transaction " + transaction.getTxId());

            broadcastTransaction(transaction);

            sendJson(exchange, 200,
                    "{\"status\":\"Transaction accepted\"}");

        } catch (Exception e) {

            Logger.error("Failed to process transaction: " + e.getMessage());

            sendJson(exchange, 400,
                    "{\"error\":\"Invalid transaction\"}");
        }
    }

    private void handleBlock(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {

            String body = readRequestBody(exchange);

            Block block = Block.fromJson(body);

            if (seenBlocks.contains(block.getHash())) {
                sendJson(exchange, 200,
                        "{\"status\":\"Block already processed\"}");
                return;
            }

            if (!blockchain.acceptBlock(block)) {

                Logger.info("Trying chain synchronization...");

                for (String peer : peers) {

                    List<Block> downloaded =
                            downloadChain(peer);

                    if (blockchain.replaceChain(downloaded)) {

                        seenBlocks.clear();

                        for (Block b : blockchain.getBlocks()) {
                            seenBlocks.add(b.getHash());
                        }

                        Logger.info(
                                "Blockchain synchronized from "
                                        + peer
                        );

                        sendJson(exchange,
                                200,
                                "{\"status\":\"Chain synchronized\"}");

                        return;
                    }
                }

                sendJson(exchange,
                        400,
                        "{\"error\":\"Block rejected\"}");

                return;
            }

            Logger.info("Accepted block #" + block.getIndex());

            seenBlocks.add(block.getHash());

            broadcastBlock(block);

            sendJson(exchange, 200,
                    "{\"status\":\"Block accepted\"}");

        } catch (Exception e) {

            Logger.error(
                    "Failed processing block: "
                            + e.getMessage()
            );

            sendJson(exchange,
                    400,
                    "{\"error\":\"Invalid block\"}");
        }
    }

    private void handleMine(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        Block block =
                blockchain.minePendingTransactions(
                        wallet.getPublicKeyBase64()
                );

        if (block == null) {

            sendJson(exchange, 400,
                    "{\"error\":\"Nothing to mine\"}");

            return;
        }

        if (!blockchain.acceptBlock(block)) {

            sendJson(exchange, 400,
                    "{\"error\":\"Failed to accept mined block\"}");

            return;
        }

        seenBlocks.add(block.getHash());

        broadcastBlock(block);

        Logger.info(
                "Successfully mined block #" + block.getIndex()
        );

        sendJson(
                exchange,
                200,
                "{"
                        + "\"status\":\"Block mined\","
                        + "\"height\":" + blockchain.getHeight()
                        + "}"
        );
    }

    private void handleChain(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJson(exchange,405,"{\"error\":\"Method not allowed\"}");
            return;
        }

        sendJson(exchange,200, blockchain.toJson());
    }

    private List<Block> downloadChain(String peer) {

        List<Block> blocks = new ArrayList<>();

        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(peer + "/chain"))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                Logger.warn("Failed to download chain from " + peer);
                return blocks;
            }

            String body = response.body();

            String blocksJson =
                    JsonUtil.extractArray(body, "blocks");

            for (String blockJson :
                    JsonUtil.splitJsonArray(blocksJson)) {

                blocks.add(
                        Block.fromJson(blockJson)
                );
            }

        } catch (Exception e) {

            Logger.warn(
                    "Chain download failed: "
                            + e.getMessage()
            );
        }

        return blocks;
    }

    private void broadcastTransaction(Transaction transaction) {

        String json = transaction.toJson();

        Set<String> snapshot;

        synchronized (peers) {
            snapshot = new LinkedHashSet<>(peers);
        }

        for (String peer : snapshot) {

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(peer + "/transaction"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

            } catch (Exception e) {

                Logger.warn(
                        "Failed to broadcast transaction to "
                                + peer
                                + ": "
                                + e.getMessage()
                );
            }
        }
    }

    private void broadcastBlock(Block block) {

        String json = block.toJson();

        Set<String> snapshot;

        synchronized (peers) {
            snapshot = new LinkedHashSet<>(peers);
        }

        for (String peer : snapshot) {

            try {

                Logger.info("Broadcasting block to: " + peer);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(peer + "/block"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

            } catch (Exception e) {

                Logger.warn(
                        "Failed to broadcast block to "
                                + peer
                                + ": "
                                + e.getMessage()
                );
            }
        }
    }

    private String peersToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        synchronized (peers) {
            int i = 0;

            for (String peer : peers) {
                sb.append("\"").append(JsonUtil.escape(peer)).append("\"");

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
    

    public Blockchain getBlockchain() {
        return blockchain;
    }
}