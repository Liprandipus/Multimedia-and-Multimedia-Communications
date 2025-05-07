package com.mycompany.polumesa_project;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.JTextArea;

public class ServerApp implements Runnable {

    static Logger log = LogManager.getLogger(ServerApp.class);
    private static ServerSocket server;
    private static JTextArea server_log;

    public static void startServer() throws ClassNotFoundException, IOException {
        try {
            server = new ServerSocket(5000);
            File[] video_list = new File("video/").listFiles();

            while (true) {
                //server_log.append("Listening for requests...\n");
                System.out.println("Listening for requests...");
                Socket socket = server.accept();
                ObjectInputStream input_stream = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream output_stream = new ObjectOutputStream(socket.getOutputStream());

                ArrayList<String> received_request = (ArrayList<String>) input_stream.readObject();
                float selected_bitrate = Float.parseFloat(received_request.get(0));
                String selected_format = received_request.get(1);
                server_log.append("Received request for " + selected_bitrate + " bitrate and " + selected_format + " format, ");

                ArrayList<String> available_videos = new ArrayList<>();

                for (File video : video_list) {
                    String current_video = video.getName();

                    if (current_video.endsWith("." + selected_format)) {
                        String[] parts = current_video.split("-");

                        if (parts.length >= 2) {
                            try {

                                String bitrateWithFormat = parts[1];

                                String numericPart = bitrateWithFormat.split("M")[0];
                                float current_video_bitrate = Float.parseFloat(numericPart);

                                if (selected_bitrate >= current_video_bitrate) {
                                    available_videos.add(current_video);
                                }

                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                                log.warn("Skipping malformed filename: " + current_video);
                            }
                        }
                    }
                }
               output_stream.writeObject(available_videos);
               ArrayList<String> stream_specs = (ArrayList<String>)input_stream.readObject();
               String selected_video = stream_specs.get(0);
               String selected_protocol = stream_specs.get(1);
               
               server_log.append("using the " + selected_protocol + " protocol to stream '" + selected_video + "'\n\n");
               String videos_dir_fullpath = System.getProperty("user.dir") + "/videos";
               
               ArrayList<String> command_line_args = new ArrayList<>();
               command_line_args.add("ffmpeg");
               
               switch (selected_protocol){
                   case "UDP":
                       command_line_args.add("-re");
			command_line_args.add("-i");
			command_line_args.add(videos_dir_fullpath + "/" + selected_video);
			command_line_args.add("-f");
			command_line_args.add("mpegts");
			command_line_args.add("udp://127.0.0.1:6000");
                        break;
                   case "TCP":   
                       command_line_args.add("-i");
			command_line_args.add(videos_dir_fullpath + "/" + selected_video);
			command_line_args.add("-f");
			command_line_args.add("mpegts");
			command_line_args.add("tcp://127.0.0.1:5100?listen");
                        break;
                   case "RTP":
                       command_line_args.add("-re");
			command_line_args.add("-i");
			command_line_args.add(videos_dir_fullpath + "/" + selected_video);
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
			Process streamer_host = process_builder.start();
			
			output_stream.close();
			input_stream.close();
			socket.close();
            }
        



        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(ServerApp.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    

    @Override
    public void run() {
    try {
        startServer();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    public static void main(String[] args) {
        ServerApp serverApp = new ServerApp();
        Thread serverThread = new Thread(serverApp);
        serverThread.start();
    }
}


   
