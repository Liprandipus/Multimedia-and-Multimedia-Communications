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
        //added basic logger
        private static final Logger logger = Logger.getLogger("PolumesaLogger");
        //added staticis logger
        private static final Logger statsLogger = Logger.getLogger("StatsLogger");
    static{
       try{
           //Initializing each logger
           logger.setLevel(Level.INFO); //this logger will contain info
           //Console handler to keep track what is printed in console
           ConsoleHandler ch = new ConsoleHandler();
           ch.setLevel(Level.INFO); 
           logger.addHandler(ch); //add the handler to logger
           
           //File handler for writing to a log file
           FileHandler fh = new FileHandler("polymesa.log", true); //true means append
           fh.setLevel(Level.INFO);
           fh.setFormatter(new SimpleFormatter()); //Using simple formatter for reading
           logger.addHandler(fh); //add filehandler to logger
           
           
           //stats logger
           FileHandler statsHandler = new FileHandler("streaming_stats.log",true); //append mode
           statsHandler.setLevel(Level.INFO); 
           statsHandler.setFormatter(new SimpleFormatter()); //formatter for reading
           statsLogger.addHandler(statsHandler); //add filehandler to the stats logger
           
           
           


    }catch(IOException e){
        //Track tracing in case of error initializing loggers
    System.err.println("Failed to initialize logger " + e.getMessage());
}
    
}   //Getters of each logger (the are used in ServerApp and ClientApp)
    public static Logger getLogger(){
        return logger;
    }
    
    public static Logger getStatsLogger(){
        return statsLogger;
    }
}