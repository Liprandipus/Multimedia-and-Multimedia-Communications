package com.mycompany.polumesa_project;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;

public class ServerApp implements Runnable {

    private static ServerSocket server;
    private static Process currentStreamProcess = null;
    private static int PORT = 5001; // default port but can be changed by args

    public static void startServer() {
        try {
            server = new ServerSocket(PORT);
            File[] video_list = new File("videos/").listFiles();

            while (true) {
                Socket socket = server.accept();
                new Thread(() -> handleClient(socket, video_list)).start();
            }

        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ServerApp.class.getName()).log(Level.SEVERE, null, ex);
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

                if (received_request.get(0).equals("ADAPTIVE_SWITCH")) {
                    int newRes = Integer.parseInt(received_request.get(1));
                    String format = received_request.get(2);
                    System.out.println("Received ADAPTIVE_SWITCH to " + newRes + "p");

                    if (currentStreamProcess != null) currentStreamProcess.destroy();

                    File newFile = findLowerResolutionFile(video_list, format, newRes);
                    if (newFile != null) {
                        System.out.println("Switching stream to file: " + newFile.getName());
                        startFfmpegStream(newFile.getAbsolutePath(), "UDP");
                    }

                    return;
                }

                float maxResolution = Integer.parseInt(received_request.get(0));
                String selected_format = received_request.get(1);

                ArrayList<String> available_videos = new ArrayList<>();

                for (File video : video_list) {
                    String current_video = video.getName();
                    if (current_video.endsWith("." + selected_format)) {
                        String[] parts = current_video.split("-");

                        if (parts.length >= 2) {
                            try {
                                String resolutionPart = parts[1].replaceAll("[^0-9]", "");
                                int videoResolution = Integer.parseInt(resolutionPart);

                                if (videoResolution <= maxResolution) {
                                    available_videos.add(current_video);
                                }
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {}
                        }
                    }
                }

                output_stream.writeObject(available_videos);

                ArrayList<String> stream_specs = (ArrayList<String>) input_stream.readObject();
                String selected_video = stream_specs.get(0);
                String selected_protocol = stream_specs.get(1);

                String videos_dir_fullpath = System.getProperty("user.dir") + "/videos";
                currentStreamProcess = startFfmpegStream(videos_dir_fullpath + "/" + selected_video, selected_protocol);

                output_stream.close();
                input_stream.close();
                socket.close();
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static Process startFfmpegStream(String filePath, String protocol) throws IOException {
        ArrayList<String> command_line_args = new ArrayList<>();
        command_line_args.add("ffmpeg");

        switch (protocol) {
            case "UDP":
                command_line_args.add("-re");
                command_line_args.add("-i");
                command_line_args.add(filePath);
                command_line_args.add("-f");
                command_line_args.add("mpegts");
                command_line_args.add("udp://127.0.0.1:6000");
                break;
            case "TCP":
                command_line_args.add("-i");
                command_line_args.add(filePath);
                command_line_args.add("-f");
                command_line_args.add("mpegts");
                command_line_args.add("tcp://127.0.0.1:5100?listen");
                break;
            case "RTP":
                command_line_args.add("-re");
                command_line_args.add("-i");
                command_line_args.add(filePath);
                command_line_args.add("-an");
                command_line_args.add("-c:v");
                command_line_args.add("copy");
                command_line_args.add("-f");
                command_line_args.add("rtp");
                command_line_args.add("-sdp_file");
                command_line_args.add(System.getProperty("user.dir") + "/video.sdp");
                command_line_args.add("rtp://127.0.0.1:5004?rtcpport=5008");
                break;
        }

        ProcessBuilder process_builder = new ProcessBuilder(command_line_args);
        process_builder.inheritIO();
        return process_builder.start();
    }

    private static File findLowerResolutionFile(File[] files, String format, int resolution) {
        for (File f : files) {
            if (f.getName().endsWith("." + format)) {
                String[] parts = f.getName().split("-");
                if (parts.length >= 2) {
                    try {
                        int res = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                        if (res == resolution) return f;
                    } catch (Exception ignored) {}
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
    if (args.length > 0) {
        try {
            PORT = Integer.parseInt(args[0]);
            System.out.println("Server running on port " + PORT); 
        } catch (NumberFormatException ignored) {
            System.out.println("Invalid port argument: " + args[0]);  //debug
        }
    }
    new Thread(new ServerApp()).start();
}
}
