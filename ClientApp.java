
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
    private volatile int autoResolution = 0;
    private volatile String format = "";

    @Override
    public void run() {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            System.out.println("Connected to server.");

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Scanner scanner = new Scanner(System.in);

            float downloadSpeedMbps = runSpeedTest();
            System.out.printf("Download speed: %.2f Mbps\n", downloadSpeedMbps);

            if (downloadSpeedMbps < 1.0) autoResolution = 240;
            else if (downloadSpeedMbps < 3.0) autoResolution = 480;
            else if (downloadSpeedMbps < 6.0) autoResolution = 720;
            else autoResolution = 1080;

            System.out.println("Auto-selected resolution: " + autoResolution + "p");

            System.out.print("Enter desired format (e.g. mp4): ");
            format = scanner.nextLine();

            ArrayList<String> request = new ArrayList<>();
            request.add(String.valueOf(autoResolution));
            request.add(format);
            out.writeObject(request);

            ArrayList<String> availableVideos = (ArrayList<String>) in.readObject();
            if (availableVideos.isEmpty()) {
                System.out.println("No videos available with these specs.");
                return;
            }

            System.out.println("Available videos:");
            for (int i = 0; i < availableVideos.size(); i++) {
                System.out.println("  [" + i + "] " + availableVideos.get(i));
            }

            System.out.print("Select video by index: ");
            int videoIndex = Integer.parseInt(scanner.nextLine());
            String selectedVideo = availableVideos.get(videoIndex);

            System.out.print("Select streaming protocol (UDP, TCP, RTP or leave empty for automatic choice): ");
            String protocol = scanner.nextLine().trim().toUpperCase();

            if (protocol.isEmpty()) {
                try {
                    String[] parts = selectedVideo.split("-");
                    String resolutionPart = parts[1].replaceAll("[^0-9]", "");
                    int res = Integer.parseInt(resolutionPart);
                    if (res <= 240) protocol = "TCP";
                    else if (res <= 480) protocol = "UDP";
                    else protocol = "RTP";
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

            // FFmpeg stream recording (optional)
            ArrayList<String> recordCommand = new ArrayList<>();
            recordCommand.add("ffmpeg");
            switch (protocol) {
                case "UDP":
                    recordCommand.add("-i");
                    recordCommand.add("udp://127.0.0.1:6000");
                    break;
                case "TCP":
                    recordCommand.add("-i");
                    recordCommand.add("tcp://127.0.0.1:5100");
                    break;
                case "RTP":
                    recordCommand.add("-i");
                    recordCommand.add("video.sdp");
                    break;
            }
            recordCommand.add("-c");
            recordCommand.add("copy");
            recordCommand.add("-y");
            recordCommand.add("received_" + selectedVideo);

            try {
                ProcessBuilder builder = new ProcessBuilder(recordCommand);
                builder.inheritIO();
                builder.start();
                System.out.println("FFmpeg recording started locally.");
            } catch (IOException e) {
                System.out.println("Failed to start ffmpeg for recording.");
                e.printStackTrace();
            }

            // Adaptive streaming thread
            new Thread(() -> {
                try {
                    while (true) {
                        Thread.sleep(10000);
                        float newSpeed = runSpeedTest();
                        System.out.println("Adaptive check: speed = " + newSpeed + " Mbps");

                        int newRes;
                        if (newSpeed < 1.0) newRes = 240;
                        else if (newSpeed < 3.0) newRes = 480;
                        else if (newSpeed < 6.0) newRes = 720;
                        else newRes = 1080;

                        if (newRes < autoResolution) {
                            System.out.println("Requesting adaptive switch to " + newRes + "p");
                            try (Socket adaptSocket = new Socket(serverHost, serverPort)) {
                                ObjectOutputStream adaptOut = new ObjectOutputStream(adaptSocket.getOutputStream());
                                ArrayList<String> adaptiveRequest = new ArrayList<>();
                                adaptiveRequest.add("ADAPTIVE_SWITCH");
                                adaptiveRequest.add(String.valueOf(newRes));
                                adaptiveRequest.add(format);
                                adaptOut.writeObject(adaptiveRequest);
                            }
                            autoResolution = newRes;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private float runSpeedTest() {
        SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        CountDownLatch latch = new CountDownLatch(1);
        final float[] speedResult = {0f};

        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                float bitsPerSecond = report.getTransferRateBit().floatValue();
                speedResult[0] = bitsPerSecond / (1024 * 1024);
                latch.countDown();
            }
            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                System.out.println("Speed test error: " + errorMessage);
                latch.countDown();
            }
            @Override
            public void onProgress(float percent, SpeedTestReport report) {}
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
        new Thread(client).start();
    }
}
