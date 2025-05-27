package com.mycompany.polumesa_project;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerApp implements Runnable {

    private static ServerSocket server;
    private static Process currentStreamProcess = null;
    private static int PORT = 5000;
    private static final Logger logger = AppLogger.getLogger();

    public static void startServer() {
        try {
            server = new ServerSocket(PORT);
            logger.info("Server started on port " + PORT);

            File[] video_list = new File("videos/").listFiles();

            while (true) {
                Socket socket = server.accept();
                String clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                logger.info("New client connected: " + clientInfo);
                new Thread(() -> handleClient(socket, video_list)).start();
            }

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Server error", ex);
        }
    }

    private static void handleClient(Socket socket, File[] video_list) {
        try (
            ObjectInputStream input_stream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream output_stream = new ObjectOutputStream(socket.getOutputStream())
        ) {
            Object request = input_stream.readObject();

            if (request instanceof ArrayList) {
                ArrayList<String> received_request = (ArrayList<String>) request;

                // Adaptive stream switch request
                if (received_request.get(0).equals("ADAPTIVE_SWITCH")) {
                    int newRes = Integer.parseInt(received_request.get(1));
                    String format = received_request.get(2);
                    logger.info("Received ADAPTIVE_SWITCH to " + newRes + "p");

                    if (currentStreamProcess != null) {
                        currentStreamProcess.destroy();
                        logger.info("Previous stream process destroyed.");
                    }

                    File newFile = findLowerResolutionFile(video_list, format, newRes);
                    if (newFile != null) {
                        logger.info("Switching stream to file: " + newFile.getName());
                        startFfmpegStream(newFile.getAbsolutePath(), "UDP");
                    } else {
                        logger.warning("No suitable video found for adaptive switch.");
                    }
                    return;
                }

                // Initial video request
                float maxResolution = Integer.parseInt(received_request.get(0));
                String selected_format = received_request.get(1);
                logger.info("Client requested videos <= " + maxResolution + "p, format: " + selected_format);

                ArrayList<String> available_videos = new ArrayList<>();

                for (File video : video_list) {
                    String current_video = video.getName();
                    if (current_video.endsWith("." + selected_format)) {
                        String[] parts = current_video.split("-");
                        if (parts.length >= 2) {
                            try {
                                int videoResolution = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                                if (videoResolution <= maxResolution) {
                                    available_videos.add(current_video);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }

                logger.info("Sending " + available_videos.size() + " available videos to client.");
                output_stream.writeObject(available_videos);

                ArrayList<String> stream_specs = (ArrayList<String>) input_stream.readObject();
                String selected_video = stream_specs.get(0);
                String selected_protocol = stream_specs.get(1);
                logger.info("Client selected video: " + selected_video + ", protocol: " + selected_protocol);

                synchronized (ServerApp.class) {
                    if (currentStreamProcess != null) {
                        currentStreamProcess.destroy();
                        logger.info("Destroyed existing streaming process.");
                    }
                    String fullPath = System.getProperty("user.dir") + "/videos/" + selected_video;
                    currentStreamProcess = startFfmpegStream(fullPath, selected_protocol);
                }

                logger.info("Started ffmpeg stream for: " + selected_video);
                output_stream.close();
                input_stream.close();
                socket.close();
            }

        } catch (IOException | ClassNotFoundException e) {
            logger.warning("Client handling error: " + e.getMessage());
        }
    }

    private static Process startFfmpegStream(String filePath, String protocol) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");

        switch (protocol) {
            case "UDP":
                cmd.add("-re");
                cmd.add("-i");
                cmd.add(filePath);
                cmd.add("-f");
                cmd.add("mpegts");
                cmd.add("udp://127.0.0.1:6000");
                break;
            case "TCP":
                cmd.add("-i");
                cmd.add(filePath);
                cmd.add("-f");
                cmd.add("mpegts");
                cmd.add("tcp://127.0.0.1:5100?listen");
                break;
            case "RTP":
                cmd.add("-re");
                cmd.add("-i");
                cmd.add(filePath);
                cmd.add("-an");
                cmd.add("-c:v");
                cmd.add("copy");
                cmd.add("-f");
                cmd.add("rtp");
                cmd.add("-sdp_file");
                cmd.add(System.getProperty("user.dir") + "/video.sdp");
                cmd.add("rtp://127.0.0.1:5004?rtcpport=5008");
                break;
        }

        logger.info("Starting ffmpeg command: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        return pb.start();
    }

    private static File findLowerResolutionFile(File[] files, String format, int resolution) {
        for (File f : files) {
            if (f.getName().endsWith("." + format)) {
                String[] parts = f.getName().split("-");
                if (parts.length >= 2) {
                    try {
                        int res = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                        if (res == resolution) return f;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void run() {
        startServer();
    }

    public static void main(String[] args) {
        new Thread(new ServerApp()).start();
    }
}
