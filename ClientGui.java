package com.mycompany.polumesa_project;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import javax.swing.*;

public class ClientGui extends javax.swing.JFrame {

    public ClientGui() {
        initComponents();
        comboFormat.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "mp4", "mkv", "avi" }));
        comboProtocol.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "AUTO", "TCP", "UDP", "RTP" }));
        claimVideos.addActionListener(evt -> fetchAvailableVideos());
        jButton1.addActionListener(evt -> startStreaming());
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        comboFormat = new javax.swing.JComboBox<>();
        comboProtocol = new javax.swing.JComboBox<>();
        claimVideos = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        listOfVideos = new javax.swing.JList<>();
        jButton1 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        jScrollPane1.setViewportView(listOfVideos);
        jButton1.setText("Start Streaming");
        claimVideos.setText("Claim Videos");
        jLabel1.setText("Streaming Video Application");
        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane2.setViewportView(jTextArea1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup().addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(comboFormat)
                    .addComponent(comboProtocol)
                    .addComponent(claimVideos))
                .addGap(30, 30, 30)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addComponent(jButton1))
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
                        .addComponent(jButton1)))
                .addGap(10)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10))
        );

        pack();
    }

    private void fetchAvailableVideos() {
        String format = comboFormat.getSelectedItem().toString();
        int resolution = 1080; // δείξε όλα τα βίντεο

        jTextArea1.append("Ζητούνται όλα τα διαθέσιμα βίντεο (" + format + " έως 1080p)");

        try (Socket socket = new Socket("localhost", 5000)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            ArrayList<String> request = new ArrayList<>();
            request.add(String.valueOf(resolution));
            request.add(format);
            out.writeObject(request);

            ArrayList<String> videos = (ArrayList<String>) in.readObject();

            DefaultListModel<String> model = new DefaultListModel<>();
            for (String v : videos) model.addElement(v);
            listOfVideos.setModel(model);

            jTextArea1.append("Βρέθηκαν " + videos.size() + " βίντεο.");

        } catch (Exception e) {
            jTextArea1.append("Σφάλμα σύνδεσης: " + e.getMessage() + "");
        }
    }

   private void startStreaming() {
    String format = comboFormat.getSelectedItem().toString();
    String protocol = comboProtocol.getSelectedItem().toString();
    String selectedVideo = listOfVideos.getSelectedValue();

    if (selectedVideo == null) {
        jTextArea1.append("Διάλεξε πρώτα ένα βίντεο.\n");
        return;
    }

    if (protocol.equals("AUTO")) {
        try {
            int res = Integer.parseInt(selectedVideo.split("-")[1].replaceAll("[^0-9]", ""));
            protocol = (res <= 240) ? "TCP" : (res <= 480) ? "UDP" : "RTP";
            jTextArea1.append("Auto-selected protocol: " + protocol + "\n");
        } catch (Exception e) {
            jTextArea1.append("Αποτυχία ανάγνωσης ανάλυσης. Επιλογή TCP.\n");
            protocol = "TCP";
        }
    }

    
    final String finalProtocol = protocol;
    final String finalSelectedVideo = selectedVideo;
    final String finalFormat = format;

    ClientApp client = new ClientApp();

    new Thread(() -> {
        client.startStreaming(finalFormat, finalSelectedVideo, finalProtocol);
        jTextArea1.append("Ξεκίνησε ροή: " + finalSelectedVideo + " μέσω " + finalProtocol + "\n");
    }).start();
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
