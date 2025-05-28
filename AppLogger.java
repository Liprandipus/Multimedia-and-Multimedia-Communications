/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.polumesa_project;
import java.io.IOException;
import java.util.logging.*;
/**
 *
 * @author Lyprandos
 */
public class AppLogger {
        private static final Logger logger = Logger.getLogger("PolumesaLogger");
        //added staticis logger
        private static final Logger statsLogger = Logger.getLogger("StatsLogger");
    static{
       try{
           //loggs
           logger.setLevel(Level.INFO);
           
           ConsoleHandler ch = new ConsoleHandler();
           ch.setLevel(Level.INFO);
           logger.addHandler(ch);
           
           FileHandler fh = new FileHandler("polymesa.log", true);
           fh.setLevel(Level.INFO);
           fh.setFormatter(new SimpleFormatter());
           logger.addHandler(fh);
           
           //stats logger
           
           FileHandler statsHandler = new FileHandler("streaming_stats.log",true);
           statsHandler.setLevel(Level.INFO);
           statsLogger.addHandler(statsHandler);
           statsHandler.setFormatter(new SimpleFormatter());
           
           


    }catch(IOException e){
    System.err.println("Failed to initialize logger " + e.getMessage());
}
    
}
    public static Logger getLogger(){
        return logger;
    }
    
    public static Logger getStatsLogger(){
        return statsLogger;
    }
}
