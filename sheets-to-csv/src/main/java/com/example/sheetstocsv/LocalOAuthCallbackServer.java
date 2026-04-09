package com.example.sheetstocsv;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class LocalOAuthCallbackServer {

    private final HttpServer server;
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile String authorizationCode;

    public LocalOAuthCallbackServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/callback", this::handleCallback);
    }

    public void start() {
        server.start();
    }

    public String waitForAuthorizationCode() throws InterruptedException {
        latch.await();
        return authorizationCode;
    }

    public void stop() {
        server.stop(0);
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        URI requestUri = exchange.getRequestURI();
        String query = requestUri.getQuery();

        if (query != null && query.contains("code=")) {
            authorizationCode = extractCode(query);
        }

        String response = "<html><body><h1>Stored Authorization code successfully. You can close this window.</h1></body></html>";
        exchange.sendResponseHeaders(200, response.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }

        latch.countDown();
    }

    private String extractCode(String query) {
        for (String param : query.split("&")) {
            if (param.startsWith("code=")) {
                return param.substring("code=".length());
            }
        }
        return null;
    }
}
