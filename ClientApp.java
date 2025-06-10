package com.mycompany.polumesa_project;

import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import fr.bmartel.speedtest.SpeedTestReport;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class ClientApp {
    //Reference to the video stream process (ffplay)
    public static Process streamProcess = null;
    // Loggers: one for general logs, one for stats/logging to file
    private final Logger logger = AppLogger.getLogger();
    private final Logger statsLogger = AppLogger.getStatsLogger();
    //Initializing streaming parameters
    private volatile int currentResolution = 0;
    private volatile String currentFormat = "";
    private volatile String selectedVideo = "";
   
    /*
     Initiates a video streaming session based on the selected protocol, video, and format.
     Also starts an adaptive streaming timer to monitor and switch quality.
     */
    public void startStreaming(String format, String selectedVideo, String protocol) throws InterruptedException {
        this.currentFormat = format;
        this.selectedVideo = selectedVideo;

        try (Socket socket = new Socket("localhost", 5000)) {
             // Create object streams to communicate with the server, both input and output streams
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            //Measure curret download speed
            float speed = runSpeedTest();
            int autoResolution = calculateResolution(speed);
            this.currentResolution = autoResolution;

            logger.info("Download speed: " + speed + " Mbps");
            logger.info("Auto-selected resolution: " + autoResolution + "p");

            
            statsLogger.info("Initial download speed: " + speed + " Mbps");
            statsLogger.info("Selected resolution: " + autoResolution + "p");
            //Send resolution and format to the server
            ArrayList<String> request = new ArrayList<>();
            request.add(String.valueOf(autoResolution));
            request.add(format);
            out.writeObject(request);
            //Receive list of all available videos from server
            ArrayList<String> videoList = (ArrayList<String>) in.readObject();
            if (!videoList.contains(selectedVideo)) {
                logger.warning("Selected video not found in available list.");
                return;
            }
             // Send selected video and streaming protocol to server
            ArrayList<String> streamSpecs = new ArrayList<>();
            streamSpecs.add(selectedVideo);
            streamSpecs.add(protocol);
            out.writeObject(streamSpecs);
            logger.info("Streaming request sent for: " + selectedVideo);
             // Define stream address depending on protocol
            String address = protocol.equalsIgnoreCase("TCP")
                    ? "tcp://127.0.0.1:5100"
                    : protocol.equalsIgnoreCase("UDP")
                    ? "udp://127.0.0.1:6000"
                    : "video.sdp"; //For RTP, use the generated SDP file
 
            ProcessBuilder pb;
            //Special handling for RTP protocol due to the requirment of reading from .sdp file
            if (protocol.equalsIgnoreCase("RTP")) {
                Thread.sleep(1500); // wait for SDP generation
                pb = new ProcessBuilder(
                        "ffplay",
                        "-protocol_whitelist", "file,udp,rtp",
                        "-fflags", "nobuffer",
                        "-flags", "low_delay",
                        "-probesize", "32",
                        "-i", "video.sdp"  // use SDP file as input
                );
            } else {
                // Non-RTP protocols can directly use address
                pb = new ProcessBuilder(
                        "ffplay",
                        "-fflags", "nobuffer",
                        "-flags", "low_delay",
                        "-probesize", "32",
                        "-i", address
                );
            }

            pb.inheritIO(); //Display output in same console
            Thread.sleep(1000); // small delay before starting playback 
            streamProcess = pb.start(); //start video playback

            //  Adaptive streaming timer
            Timer adaptiveTimer = new Timer();
            adaptiveTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    //rerun speedtest
                    float newSpeed = runSpeedTest();
                    int newResolution = calculateResolution(newSpeed);

                    logger.info(" [ADAPTIVE] Speed = " + newSpeed + " Mbps → Resolution = " + newResolution + "p");
                    statsLogger.info("[ADAPTIVE] Download speed: " + newSpeed + " Mbps");
                    statsLogger.info("[ADAPTIVE] Suggested resolution: " + newResolution + "p");
                      // Only trigger switch if resolution should change
                    if (newResolution != currentResolution) {
                        logger.info("� Switching to " + newResolution + "p stream...");
                        statsLogger.info("Resolution switched from " + currentResolution + "p to " + newResolution + "p");

                        try (Socket switchSocket = new Socket("localhost", 5000)) {
                               // Send adaptive switch request to server
                            ObjectOutputStream outSwitch = new ObjectOutputStream(switchSocket.getOutputStream());
                            ArrayList<String> switchRequest = new ArrayList<>();
                            switchRequest.add("ADAPTIVE_SWITCH");
                            switchRequest.add(String.valueOf(newResolution));
                            switchRequest.add(currentFormat);
                            outSwitch.writeObject(switchRequest);
                            currentResolution = newResolution;
                        } catch (IOException e) {
                            logger.warning("Adaptive switch failed: " + e.getMessage());
                            statsLogger.warning("Adaptive switch failed: " + e.getMessage());
                        }
                    }
                }
            }, 30000, 30000); //every 30 seconds

        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Client error: " + e.getMessage());
            statsLogger.severe("Client error: " + e.getMessage());
        }
    }
     /* Runs a speed test using the SpeedTestSocket library (Tele2 1MB file).
     Blocks until download completes or fails. Returns speed in Mbps.
     */
    public float runSpeedTest() {
        SpeedTestSocket socket = new SpeedTestSocket();
        CountDownLatch latch = new CountDownLatch(1);
        final float[] result = {0f};

        socket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                result[0] = report.getTransferRateBit().floatValue() / (1024 * 1024); // Convert to Mbps
                latch.countDown(); 
            }

            @Override
            public void onError(SpeedTestError error, String message) {
                result[0] = 2.0f; // fallback speed on error
                latch.countDown();
            }

            @Override
            public void onProgress(float percent, SpeedTestReport report) {}
        });

        socket.startDownload("http://speedtest.tele2.net/1MB.zip");

        try { 
            latch.await(); // Wait until speed test is done
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result[0];
    }
    
     /*
      Suggests a resolution based on current network speed.
      The thresholds can be fine-tuned for better responsiveness.
     */
    private int calculateResolution(float speed) {
        if (speed < 1.0) return 240;
        else if (speed < 3.0) return 480;
        else if (speed < 6.0) return 720;
        else return 1080;
    }
}
