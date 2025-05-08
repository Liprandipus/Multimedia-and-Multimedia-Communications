package com.mycompany.polumesa_project;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;

public class ClientApp implements Runnable {

    private final String serverHost = "localhost";
    private final int serverPort = 5000;

    @Override
    public void run() {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            System.out.println("Connected to server.");

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Scanner scanner = new Scanner(System.in);

            // Step 1: Send bitrate and format
            System.out.print("Enter desired bitrate (e.g. 2.0): ");
            String bitrate = scanner.nextLine();

            System.out.print("Enter desired format (e.g. mp4): ");
            String format = scanner.nextLine();

            ArrayList<String> request = new ArrayList<>();
            request.add(bitrate);
            request.add(format);

            out.writeObject(request);

            // Step 2: Receive available videos
            ArrayList<String> availableVideos = (ArrayList<String>) in.readObject();
            if (availableVideos.isEmpty()) {
                System.out.println("No videos available with these specs.");
                return;
            }

            System.out.println("Available videos:");
            for (int i = 0; i < availableVideos.size(); i++) {
                System.out.println("  [" + i + "] " + availableVideos.get(i));
            }

            // Step 3: Select video and protocol
            System.out.print("Select video by index: ");
            int videoIndex = Integer.parseInt(scanner.nextLine());
            String selectedVideo = availableVideos.get(videoIndex);

            System.out.print("Select streaming protocol (UDP, TCP, RTP): ");
            String protocol = scanner.nextLine();

            ArrayList<String> streamSpecs = new ArrayList<>();
            streamSpecs.add(selectedVideo);
            streamSpecs.add(protocol.toUpperCase());

            out.writeObject(streamSpecs);

            System.out.println("Streaming request sent successfully.");

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ClientApp client = new ClientApp();
        Thread clientThread = new Thread(client);
        clientThread.start();
    }
}
