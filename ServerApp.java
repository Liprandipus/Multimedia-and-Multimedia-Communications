package com.mycompany.polumesa_project;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/* ServerApp handles video streaming requests from clients using Java sockets.
 * It supports adaptive streaming, format selection, and protocol selection (UDP, TCP, RTP).
 */
public class ServerApp implements Runnable {

    private static ServerSocket server;
    private static Process currentStreamProcess = null;
    private static int PORT = 5000; // default port. can be changed by args
    private static final Logger logger = AppLogger.getLogger(); //Logger to keep track of events

    //Starts the server in listening to client connections
    public static void startServer() {
        try {
            //Initiliazing server to port
            server = new ServerSocket(PORT);
            logger.info("Server started on port " + PORT);
            //Load available videos from "videos/" folder
            File folder = new File("videos/");
            File[] video_list = folder.listFiles();
            //Accepting clients continiously
            while (true) {
                Socket socket = server.accept();
                String clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                logger.info("New client connected: " + clientInfo);

                //Handle each client as a new thread
                new Thread(() -> handleClient(socket, video_list)).start();
            }
            //Handling error in case server fails
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Server error", ex);
        }
    }

    //Handles interaction with a single client
    private static void handleClient(Socket socket, File[] video_list) {
        try (
                //Initiliazing both input and output streams for incoming or outcoming requests between server and client
                ObjectInputStream input_stream = new ObjectInputStream(socket.getInputStream()); ObjectOutputStream output_stream = new ObjectOutputStream(socket.getOutputStream())) {
            Object request = input_stream.readObject();
            //checks if requests is a list of strings 
            if (request instanceof ArrayList) {
                ArrayList<String> received_request = (ArrayList<String>) request;

                // Adaptive stream switch request
                if (received_request.get(0).equals("ADAPTIVE_SWITCH")) {
                    int newRes = Integer.parseInt(received_request.get(1));
                    String format = received_request.get(2);
                    logger.info("Received ADAPTIVE_SWITCH to " + newRes + "p");

                    // Stop any current streaming process
                    if (currentStreamProcess != null) {
                        currentStreamProcess.destroy();
                        logger.info("Previous stream process destroyed.");
                    }
                    // Find a video file at lower resolution
                    File newFile = findLowerResolutionFile(video_list, format, newRes);
                    if (newFile != null) {
                        logger.info("Switching stream to file: " + newFile.getName());
                        startFfmpegStream(newFile.getAbsolutePath(), "UDP");
                    } else {
                        //Error in case a lower resolution video isnt found
                        logger.warning("No suitable video found for adaptive switch.");
                    }
                    return; // Return early, as adaptive switch doesn’t continue to full handshake
                }

                // Handle initial request for video list
                float maxResolution = Integer.parseInt(received_request.get(0)); //Max resolution client supports (not filtered yet)
                String selected_format = received_request.get(1); //Format requested (e.g mp4 etc)
                logger.info("Client requested videos <= " + maxResolution + "p, format: " + selected_format);

                ArrayList<String> available_videos = new ArrayList<>();

                //Filter and collect matching videos based on format
                for (File video : video_list) {
                    System.out.println("DEBUG: Ελέγχεται αρχείο: " + video.getName());
                    String current_video = video.getName();
                    if (current_video.endsWith("." + selected_format)) {
                        String[] parts = current_video.split("-");
                        if (parts.length >= 2) {
                            try {
                                //add matching formats to available videos list
                                available_videos.add(current_video);

                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
                // Send list of filtered video filenames back to client
                logger.info("Sending " + available_videos.size() + " available videos to client.");
                output_stream.writeObject(available_videos);
                // Wait for client's selected video and protocol (e.g., "movie-720p.mp4", "UDP")
                ArrayList<String> stream_specs = (ArrayList<String>) input_stream.readObject();
                String selected_video = stream_specs.get(0);
                String selected_protocol = stream_specs.get(1);
                logger.info("Client selected video: " + selected_video + ", protocol: " + selected_protocol);

                //synchronize each client connection in order to avoid race conditions
                synchronized (ServerApp.class) {
                    //Stop current stream if active
                    if (currentStreamProcess != null) {
                        currentStreamProcess.destroy();
                        logger.info("Destroyed existing streaming process.");
                    }
                    // Start ffmpeg streaming process for selected file and protocol
                    String fullPath = System.getProperty("user.dir") + "/videos/" + selected_video;
                    currentStreamProcess = startFfmpegStream(fullPath, selected_protocol);
                }
                // Clean up: close streams and socket (clean every buffer)
                logger.info("Started ffmpeg stream for: " + selected_video);
                output_stream.close();
                input_stream.close();
                socket.close();
            }

        } catch (IOException | ClassNotFoundException e) {
            logger.warning("Client handling error: " + e.getMessage());
        }
    }

    /* Starts an ffmpeg process to stream the specified video file using the specified protocol.
      @param filePath full path to the video file
      @param protocol "UDP", "TCP", or "RTP"
      @return the started Process
      @throws IOException if process fails to start
     */
    private static Process startFfmpegStream(String filePath, String protocol) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        //Build the ffmpeg command depending on protocol
        switch (protocol) {
            case "UDP":
                cmd.add("-re"); //Read input at native frame rate
                cmd.add("-i");
                cmd.add(filePath);
                cmd.add("-f");
                cmd.add("mpegts");
                cmd.add("udp://127.0.0.1:6000"); // Stream to localhost UDP port 6000
                break;
            case "TCP":
                cmd.add("-i");
                cmd.add(filePath);
                cmd.add("-f");
                cmd.add("mpegts");
                cmd.add("tcp://127.0.0.1:5100?listen"); // Listen on TCP port 5100
                break;
            case "RTP":
                // Delete old SDP file if it exists
                /*Disable audio stream entirely
                 Reason: RTP streaming typically requires separate ports for audio and video
                 and handling both adds complexity (especially in demo/simple setups)
                 By disabling audio, we avoid:
                  - Port collisions
                  - Additional SDP entries
                  - Synchronization issues between audio and video
                 */
                File sdp = new File("video.sdp");
                if (sdp.exists()) {
                    sdp.delete();
                }

                cmd.add("-re"); //real-time read
                cmd.add("-i");
                cmd.add(filePath);
                cmd.add("-an"); //disable video
                cmd.add("-c:v");
                cmd.add("copy"); // Avoid re-encoding video
                cmd.add("-f");
                cmd.add("rtp");
                cmd.add("-sdp_file");
                cmd.add(System.getProperty("user.dir") + "/video.sdp"); //SDP file output
                cmd.add("rtp://127.0.0.1:5004?rtcpport=5008"); // RTP stream destination
                break;
        }

        logger.info("Starting ffmpeg command: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO(); //output ffmpeg logs to terminal
        return pb.start();
    }

    /* Finds a video file in the list that matches the given format and resolution.
      @param files list of files in the video directory
      @param format file extension (e.g., "mp4")
      @param resolution desired resolution (e.g., 480)
      @return the matching file or null
     */
    private static File findLowerResolutionFile(File[] files, String format, int resolution) {
        for (File f : files) {
            if (f.getName().endsWith("." + format)) {
                String[] parts = f.getName().split("-");
                if (parts.length >= 2) {
                    try {
                        int res = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                        if (res == resolution) {
                            return f;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null; //No match found
    }

    // Thread run method. Just calls startServer().
    @Override
    public void run() {
        startServer();
    }

    public static void main(String[] args) {
        new Thread(new ServerApp()).start(); //Start the server in seperate thread

    }
    /* Load Balancer 
        if ( args.lenght > 0 ){
            try {
                PORT = Integer.parseInt(args[0]);
                }catch(NumberFormatException e){
                System.err.println("Invalid port argument, using default 5000.");
                }
            }
            new Thread(new ServerApp()).start();
        }
     */
}

