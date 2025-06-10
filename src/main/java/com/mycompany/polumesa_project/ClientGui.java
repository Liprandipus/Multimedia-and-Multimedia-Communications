//THIS GUI WAS MADE BY THE DESIGN TOOL OF NETBEANS. ONLY THE FUNCTIONALITY OF EACH BUTTON AND ALL THE FUNCTIONS WERE BUILT BY THE AUTHOR
package com.mycompany.polumesa_project;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

public class ClientGui extends javax.swing.JFrame {

    private void endStream() {
        if (ClientApp.streamProcess != null && ClientApp.streamProcess.isAlive()) {
            ClientApp.streamProcess.destroy();
            ClientApp.streamProcess = null;
            System.out.println("Streaming process ended.");
        }
    }

    public ClientGui() {
        initComponents();
        //initiating compoments (pushing formats and protocols in the comboboxes) and creating the action-listener methods
        comboFormat.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "mp4", "mkv", "avi" }));
        comboProtocol.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "AUTO", "TCP", "UDP", "RTP" }));
        claimVideos.addActionListener(evt -> fetchAvailableVideos());
        jButton1.addActionListener(evt -> startStreaming());
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        JButton jButtonEnd = new javax.swing.JButton();
        comboFormat = new javax.swing.JComboBox<>();
        comboProtocol = new javax.swing.JComboBox<>();
        claimVideos = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        listOfVideos = new javax.swing.JList<>();
        jButton1 = new javax.swing.JButton();
        jButtonEnd = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        jScrollPane1.setViewportView(listOfVideos);
        jButton1.setText("Start Streaming");
        claimVideos.setText("Claim Videos");
        jLabel1.setText("Streaming Video Application");
        jButtonEnd.setText("End Stream");
        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane2.setViewportView(jTextArea1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        jButtonEnd.addActionListener(evt -> endStream());
        layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup().addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(comboFormat)
                    .addComponent(comboProtocol)
                    .addComponent(claimVideos))
                .addGap(30, 30, 30)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addComponent(jButton1)
                    .addComponent(jButtonEnd))
                .addGap(20, 20, 20))
            .addComponent(jScrollPane2)
            .addGroup(layout.createSequentialGroup().addGap(200, 200, 200).addComponent(jLabel1).addGap(0, 200, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup().addGap(20, 20, 20)
                .addComponent(jLabel1)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(comboFormat)
                        .addGap(10)
                        .addComponent(comboProtocol)
                        .addGap(10)
                        .addComponent(claimVideos))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10)
                        .addComponent(jButton1)
                    .addComponent(jButtonEnd)))
                .addGap(10)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10))
        );

        pack();
    }
    //Function that claims the list of the available videos based on a format 
    private void fetchAvailableVideos() {
        //Selecting the format from the combo box
        String format = comboFormat.getSelectedItem().toString();
        int resolution = 1080; 
        //JTextArea text which informing the user that the searching of the videos is beginning
        jTextArea1.append("Claiming available videos (" + format + " up to 1080p \n");

        try (Socket socket = new Socket("localhost", 5000)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); //Creating Output Stream for sending objects to the server
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream()); //Creating Input Stream for receiving objecs from the server
            //List that contains both format and protocol
            ArrayList<String> request = new ArrayList<>();
            request.add(String.valueOf(resolution));
            request.add(format);
            out.writeObject(request); // Sending the request to the server

            ArrayList<String> videos = (ArrayList<String>) in.readObject(); //Receiving all the videos based on the request
            //Creating the list that contains all the available videos based on the request
            DefaultListModel<String> model = new DefaultListModel<>();
            for (String v : videos) model.addElement(v);
            listOfVideos.setModel(model);
            //Printing to the TextArea the number of available videos based on the request
            jTextArea1.append("Was found " + videos.size() + " videos.");

        } catch (Exception e) {
            jTextArea1.append("Error : " + e.getMessage() + ""); //Exception in case nothing is found
        }
    }
    
   //Function responsible for streaming the video
   private void startStreaming() {
       //Grabbing the format, the protocol and the selected video of each ComboBox
    String format = comboFormat.getSelectedItem().toString();
    String protocol = comboProtocol.getSelectedItem().toString();
    String selectedVideo = listOfVideos.getSelectedValue();
    //If there is no video selected, print the text below
    if (selectedVideo == null) {
        jTextArea1.append("Choose a video first.\n");
        return;
    }
    //If the selected protocol is AUTO ( meaning that user isnt choosing a specific protocol)
    if (protocol.equals("AUTO")) {
        try {
            int res = Integer.parseInt(selectedVideo.split("-")[1].replaceAll("[^0-9]", ""));
            //examining each resolution in order to select the best protocol for each resolution
            protocol = (res <= 240) ? "TCP" : (res <= 480) ? "UDP" : "RTP";
            //Update the TextArea with the protocol that was selected
            jTextArea1.append("Auto-selected protocol: " + protocol + "\n");
        } catch (Exception e) { //In case there is an error, choose TCP instead
            jTextArea1.append("Fail reading format. Using TCP Protocol\n");
            protocol = "TCP";
        }
    }

    //Final values that cannot be overwritten (protocol, video, format) in order to start the streaming process
    final String finalProtocol = protocol;
    final String finalSelectedVideo = selectedVideo;
    final String finalFormat = format;
    //Creating new object for each client
    ClientApp client = new ClientApp();
    //Run new thread for the streaming in order not to block the GUI 
    new Thread(() -> {
        try {
            //startStreaming() call with the final values 
            client.startStreaming(finalFormat, finalSelectedVideo, finalProtocol);
        } catch (InterruptedException ex) {
            //Error logging in case of thread error
            Logger.getLogger(ClientGui.class.getName()).log(Level.SEVERE, null, ex);
        }
        //Informing user that the streaming has started
        jTextArea1.append("Streaming has started: " + finalSelectedVideo + " with " + finalProtocol + "\n");
    }).start(); //start the new thread
}

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new ClientGui().setVisible(true));
    }

    private javax.swing.JButton claimVideos;
    private javax.swing.JComboBox<String> comboFormat;
    private javax.swing.JComboBox<String> comboProtocol;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JList<String> listOfVideos;
}
