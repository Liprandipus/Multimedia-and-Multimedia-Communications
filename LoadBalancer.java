
package com.mycompany.polumesa_project;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {

    private static final int LISTEN_PORT = 5000;
    private static final String[] BACKEND_SERVERS = {
        "localhost:5001",
        "localhost:5002"
    };

    private static AtomicInteger nextServerIndex = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        ServerSocket lbSocket = new ServerSocket(LISTEN_PORT);
        System.out.println("Load Balancer listening on port " + LISTEN_PORT);

        while (true) {
            Socket clientSocket = lbSocket.accept();
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket clientSocket) {
        String backend = getNextBackend();
        String[] parts = backend.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (Socket backendSocket = new Socket(host, port)) {
            System.out.println("Redirecting client to backend " + backend);

            Thread forward = new Thread(() -> forwardData(clientSocket, backendSocket));
            Thread backward = new Thread(() -> forwardData(backendSocket, clientSocket));
            forward.start();
            backward.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void forwardData(Socket inputSocket, Socket outputSocket) {
        try (
            InputStream in = inputSocket.getInputStream();
            OutputStream out = outputSocket.getOutputStream()
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException e) {
            // Silent fail
        }
    }

    private static String getNextBackend() {
        int index = nextServerIndex.getAndUpdate(i -> (i + 1) % BACKEND_SERVERS.length);
        return BACKEND_SERVERS[index];
    }
}
