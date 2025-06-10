
package com.mycompany.polumesa_project;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    //Port where the LoadBalancer listens for incoming client connections
    private static final int LISTEN_PORT = 5000;
    // List of backend server addresses in the form "host:port"
    // These are the actual streaming servers that clients will be redirected to
    private static final String[] BACKEND_SERVERS = {
        "localhost:5001",
        "localhost:5002"
    };

    // Atomic index used for round-robin selection of backends (thread-safe)
    private static AtomicInteger nextServerIndex = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
          // Start a ServerSocket to accept client connections
        ServerSocket lbSocket = new ServerSocket(LISTEN_PORT);
        System.out.println("Load Balancer listening on port " + LISTEN_PORT);
         // Loop forever, accepting new client connections
        while (true) {
            //Accept each client and handle it as a new thread
            Socket clientSocket = lbSocket.accept();
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }
    /*Handles communication between a single client and a chosen backend server.
     Implements a basic TCP tunnel between them.
     */
    private static void handleClient(Socket clientSocket) {
         // Choose the next backend server using round-robin policy
        String backend = getNextBackend();
        String[] parts = backend.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
         
        // Typing to stdout where the client has been redirected
        String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        System.out.println("Redirecting client " + clientInfo + " → " + backend);

        try (Socket backendSocket = new Socket(host, port)) {
            //Establish a new connection to the selected backend server
            System.out.println("Redirecting client to backend " + backend);
            //Create bidirectional forwarding threads
            // One for client -> backend, and one for backend -> client
            Thread forward = new Thread(() -> forwardData(clientSocket, backendSocket));
            Thread backward = new Thread(() -> forwardData(backendSocket, clientSocket));
             // Start both threads to allow full duplex communication
            forward.start();
            backward.start();
        } catch (IOException e) {
             // Log connection failure to backend
            e.printStackTrace();
        }
    }
     /* Forwards data from one socket to another.
     This method is used in both directions: client → backend and backend → client.
     */
    private static void forwardData(Socket inputSocket, Socket outputSocket) {
        try (
            InputStream in = inputSocket.getInputStream();
            OutputStream out = outputSocket.getOutputStream()
        ) {
              // Continuously read data from input and write it to output
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush(); //Ensure immediate transmission
            }
        } catch (IOException e) {
            // Connection might be closed or interrupted; safe to ignore in many use cases
        }
    }
     /* Selects the next backend server in a round-robin fashion.
      Uses AtomicInteger to ensure thread-safety in concurrent environments.
     */
    private static String getNextBackend() {
        int index = nextServerIndex.getAndUpdate(i -> (i + 1) % BACKEND_SERVERS.length);
        return BACKEND_SERVERS[index];
    }
}
