
package com.mycompany.polumesa_project;

import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import fr.bmartel.speedtest.SpeedTestReport;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class ClientApp {

    private final Logger logger = AppLogger.getLogger();
    private final Logger statsLogger = AppLogger.getStatsLogger();

    public void startStreaming(String format, String selectedVideo, String protocol) {
        try (Socket socket = new Socket("localhost", 5000)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            float speed = runSpeedTest();
            int autoResolution = calculateResolution(speed);
            logger.info("Download speed: " + speed + " Mbps");
            logger.info("Auto-selected resolution: " + autoResolution + "p");

            ArrayList<String> request = new ArrayList<>();
            request.add(String.valueOf(autoResolution));
            request.add(format);
            out.writeObject(request);

            ArrayList<String> videoList = (ArrayList<String>) in.readObject();
            if (!videoList.contains(selectedVideo)) {
                logger.warning("Selected video not found in available list.");
                return;
            }

            ArrayList<String> streamSpecs = new ArrayList<>();
            streamSpecs.add(selectedVideo);
            streamSpecs.add(protocol);
            out.writeObject(streamSpecs);
            logger.info("Streaming request sent for: " + selectedVideo);

            startFfmpeg(protocol, selectedVideo);

        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Client error: " + e.getMessage());
        }
    }

    public float runSpeedTest() {
        SpeedTestSocket socket = new SpeedTestSocket();
        CountDownLatch latch = new CountDownLatch(1);
        final float[] result = {0f};

        socket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                result[0] = report.getTransferRateBit().floatValue() / (1024 * 1024);
                latch.countDown();
            }

            @Override
            public void onError(SpeedTestError error, String message) {
                result[0] = 2.0f;
                latch.countDown();
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {}
        });

        socket.startDownload("http://speedtest.tele2.net/1MB.zip");

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result[0];
    }

    private int calculateResolution(float speed) {
        if (speed < 1.0) return 240;
        else if (speed < 3.0) return 480;
        else if (speed < 6.0) return 720;
        else return 1080;
    }

    private void startFfmpeg(String protocol, String fileName) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");

        switch (protocol) {
            case "UDP" -> {
                cmd.add("-i");
                cmd.add("udp://127.0.0.1:6000");
            }
            case "TCP" -> {
                cmd.add("-i");
                cmd.add("tcp://127.0.0.1:5100");
            }
            case "RTP" -> {
                cmd.add("-i");
                cmd.add("video.sdp");
            }
        }

        cmd.add("-c");
        cmd.add("copy");
        cmd.add("-y");
        cmd.add("received_" + fileName);

        new ProcessBuilder(cmd).inheritIO().start();
        logger.info("Started ffmpeg recording for: " + fileName);
    }
}
