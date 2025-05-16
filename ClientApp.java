package com.mycompany.polumesa_project;

import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import fr.bmartel.speedtest.SpeedTestReport;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

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

            // Step 0: Measure download speed
            float downloadSpeedMbps = runSpeedTest();
            System.out.printf("Download speed: %.2f Mbps\n", downloadSpeedMbps);

            // Resolution decision
            int autoResolution;
            if (downloadSpeedMbps < 1.0) {
                autoResolution = 240;
            } else if (downloadSpeedMbps < 3.0) {
                autoResolution = 480;
            } else if (downloadSpeedMbps < 6.0) {
                autoResolution = 720;
            } else {
                autoResolution = 1080;
            }

            System.out.println("Auto-selected resolution: " + autoResolution + "p");

            // Format input
            System.out.print("Enter desired format (e.g. mp4): ");
            String format = scanner.nextLine();

            ArrayList<String> request = new ArrayList<>();
            request.add(String.valueOf(autoResolution)); // Use the auto resolution
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

            System.out.print("Select streaming protocol (UDP, TCP, RTP or leave empty for automatic choice): ");
            String protocol = scanner.nextLine().trim().toUpperCase();

            if (protocol.isEmpty()) {
                try {
                    String[] parts = selectedVideo.split("-");
                    String resolutionPart = parts[1];
                    String resolutionNumericPart = resolutionPart.replaceAll("[^0-9]", "");
                    int res = Integer.parseInt(resolutionNumericPart);

                    if (res <= 240) {
                        protocol = "TCP";
                    } else if (res <= 480) {
                        protocol = "UDP";
                    } else {
                        protocol = "RTP";
                    }

                    System.out.println("Auto-selected protocol: " + protocol);
                } catch (Exception e) {
                    System.out.println("Could not determine resolution. Defaulting to TCP.");
                    protocol = "TCP";
                }
            }

            ArrayList<String> streamSpecs = new ArrayList<>();
            streamSpecs.add(selectedVideo);
            streamSpecs.add(protocol);

            out.writeObject(streamSpecs);
            System.out.println("Streaming request sent successfully.");

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Speed test function
    private float runSpeedTest() {
        SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        CountDownLatch latch = new CountDownLatch(1);

        final float[] speedResult = {0f};

        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                float bitsPerSecond = report.getTransferRateBit().floatValue();
                speedResult[0] = bitsPerSecond / (1024 * 1024); // Mbps
                latch.countDown();
            }

            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                System.out.println("Speed test error: " + errorMessage);
                latch.countDown();
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                // optional: print progress
            }
        });

       speedTestSocket.startDownload("http://speedtest.tele2.net/1MB.zip");

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return speedResult[0];
    }

    public static void main(String[] args) {
        ClientApp client = new ClientApp();
        Thread clientThread = new Thread(client);
        clientThread.start();
    }
}
