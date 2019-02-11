
/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.*;

import org.opencv.core.Mat;
import org.opencv.core.*;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.highgui.*;
import org.opencv.imgcodecs.*;
import org.opencv.imgproc.*;
import org.opencv.core.*;
import org.opencv.videoio.*;

/*
   JSON format:
   {
       "team": <team number>,
       "ntmode": <"client" or "server", "client" if unspecified>
       "cameras": [
           {
               "name": <camera name>
               "path": <path, e.g. "/dev/video0">
               "pixel format": <"MJPEG", "YUYV", etc>   // optional
               "width": <video mode width>              // optional
               "height": <video mode height>            // optional
               "fps": <video mode fps>                  // optional
               "brightness": <percentage brightness>    // optional
               "white balance": <"auto", "hold", value> // optional
               "exposure": <"auto", "hold", value>      // optional
               "properties": [                          // optional
                   {
                       "name": <property name>
                       "value": <property value>
                   }
               ],
               "stream": {                              // optional
                   "properties": [
                       {
                           "name": <stream property name>
                           "value": <stream property value>
                       }
                   ]
               }
           }
       ]
   }
 */

public final class Main {
  private static String configFile = "/boot/frc.json";

  @SuppressWarnings("MemberName")
  public static class CameraConfig {
    public String name;
    public String path;
    public JsonObject config;
    public JsonElement streamConfig;
  }

  public static int team;
  public static boolean server;
  public static List<CameraConfig> cameraConfigs = new ArrayList<>();

  private Main() {
  }

  /**
   * Report parse error.
   */
  public static void parseError(String str) {
    System.err.println("config error in '" + configFile + "': " + str);
  }

  /**
   * Read single camera configuration.
   */
  public static boolean readCameraConfig(JsonObject config) {
    CameraConfig cam = new CameraConfig();

    // name
    JsonElement nameElement = config.get("name");
    if (nameElement == null) {
      parseError("could not read camera name");
      return false;
    }
    cam.name = nameElement.getAsString();

    // path
    JsonElement pathElement = config.get("path");
    if (pathElement == null) {
      parseError("camera '" + cam.name + "': could not read path");
      return false;
    }
    cam.path = pathElement.getAsString();

    // stream properties
    cam.streamConfig = config.get("stream");

    cam.config = config;

    cameraConfigs.add(cam);
    return true;
  }

  /**
   * Read configuration file.
   */
  @SuppressWarnings("PMD.CyclomaticComplexity")
  public static boolean readConfig() {
    // parse file
    JsonElement top;
    try {
      top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
    } catch (IOException ex) {
      System.err.println("could not open '" + configFile + "': " + ex);
      return false;
    }

    // top level must be an object
    if (!top.isJsonObject()) {
      parseError("must be JSON object");
      return false;
    }
    JsonObject obj = top.getAsJsonObject();

    // team number
    JsonElement teamElement = obj.get("team");
    if (teamElement == null) {
      parseError("could not read team number");
      return false;
    }
    team = teamElement.getAsInt();

    // ntmode (optional)
    if (obj.has("ntmode")) {
      String str = obj.get("ntmode").getAsString();
      if ("client".equalsIgnoreCase(str)) {
        server = false;
      } else if ("server".equalsIgnoreCase(str)) {
        server = true;
      } else {
        parseError("could not understand ntmode value '" + str + "'");
      }
    }

    // cameras
    JsonElement camerasElement = obj.get("cameras");
    if (camerasElement == null) {
      parseError("could not read cameras");
      return false;
    }
    JsonArray cameras = camerasElement.getAsJsonArray();
    for (JsonElement camera : cameras) {
      if (!readCameraConfig(camera.getAsJsonObject())) {
        return false;
      }
    }

    return true;
  }

  /**
   * Start running the camera.
   */
  public static VideoSource startCamera(CameraConfig config) {
    System.out.println("Starting camera '" + config.name + "' on " + config.path);
    CameraServer inst = CameraServer.getInstance();
    UsbCamera camera = new UsbCamera(config.name, config.path);
    MjpegServer server = inst.startAutomaticCapture(camera);

    Gson gson = new GsonBuilder().create();

    camera.setConfigJson(gson.toJson(config.config));
    camera.setConnectionStrategy(VideoSource.ConnectionStrategy.kKeepOpen);

    if (config.streamConfig != null) {
      server.setConfigJson(gson.toJson(config.streamConfig));
    }

    return camera;
  }


    public static void main( String ...args) {
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME);


      System.out.println("Getting the camera") ;
      UsbCamera theCamera = new UsbCamera("USB Camera 0", 0) ;
      theCamera.setResolution(320, 240) ;

      System.out.println("Creating the sink") ;
      CvSink theSink = new CvSink("Raspberry Pi Camera") ;

      System.out.println("Setting the sink source") ;
      theSink.setSource(theCamera);

      MyPipeline pipeline = new MyPipeline();


      Mat theImage = new Mat() ;
      while ( true ) {
        //System.out.println("Top of the loop") ;
        long startTime = System.nanoTime() ;
        long result = theSink.grabFrame(theImage) ;
        long processStart = System.nanoTime() ;

        if ( result != 0 ) {
          pipeline.process(theImage) ;
        } else {
          System.out.println("Failed to get frame") ;
        }

        long endTime = System.nanoTime() ;
        long elapsed = (endTime - startTime) / 1000000 ;
        long pipeTime = ( endTime - processStart)/1000000 ;
        int targetCount = 0;
        if ( pipeline.getTargets() != null ) {
          targetCount = pipeline.getTargets().size() ;
        }
        String outString = String.format("Targets %d, Elapsed Time = %4d, PipeTime = %4d", targetCount, elapsed, pipeTime) ;
        System.out.println( outString) ;
        
      }



    }


  public static void saved_main(String... args) {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

    // start NetworkTables
    // NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
    // if (server) {
    //   System.out.println("Setting up NetworkTables server");
    //   ntinst.startServer();
    // } else {
    //   System.out.println("Setting up NetworkTables client for team " + team);
    //   ntinst.startClientTeam(team);
    // }
    

    MyPipeline pipeline = new MyPipeline();
    UsbCamera camera = CameraServer.getInstance().startAutomaticCapture();
    camera.setResolution(320, 240);
    CvSink cvSink = CameraServer.getInstance().getVideo();
    CvSource outputStream = CameraServer.getInstance().putVideo("Blur", 320, 240);
    MjpegServer processedVideoServer = new MjpegServer("processed_video_server", 8082);
    processedVideoServer.setSource(outputStream);

    CvSource thresholdStream = CameraServer.getInstance().putVideo("Blur", 320, 240);
    MjpegServer thresholdVideoServer = new MjpegServer("processed_video_server", 8081);
    thresholdVideoServer.setSource(thresholdStream);


    Mat source = new Mat();
    // Mat output = new Mat();
    long totalTime = 0 ;
    int totalTimeSamples = 0 ;
    double averageTime = 0.0;
    while (true) {
      long startTime = System.nanoTime() ;

      long stepStart = System.nanoTime() ;
      cvSink.grabFrame(source);
      long stepEnd = System.nanoTime() ;
      long grabTime = (stepEnd - stepStart)/1000000 ;
      //System.out.println("grab frame time " + (stepEnd - stepStart)/1000000) ;


      long pipelineTime = 0 ;
      if (!source.empty()) {
        stepStart = System.nanoTime() ;
        pipeline.process(source);
        stepEnd = System.nanoTime() ;
        pipelineTime = (stepEnd - stepStart)/1000000 ;

        //System.out.println("pipeline processing time " + (stepEnd - stepStart)/1000000) ;
        outputStream.putFrame(pipeline.getProcessedFrame());
        thresholdStream.putFrame(pipeline.getThresholdOutput());
      }
      long endTime = System.nanoTime() ;
      totalTime += ( endTime - startTime) ;
      totalTimeSamples++ ;
      // average time in milli seconds
      averageTime = (double) totalTime / (double) totalTimeSamples / 1000000.0  ;
      // if ( totalTimeSamples % 50 == 0) {
        String outString = String.format("Average = %.2f, Frame = %4d, Grab = %4d, Pipe = %4d", averageTime, (endTime-startTime)/1000000, grabTime, pipelineTime) ;
        System.out.println(outString) ;
        //System.out.println()
      // }

      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        return;
      }

    }
  }






  // wpk renamed this so I could use my own main. Kept it here for reference in
  // case I wanted to look at it again.
  /**
   * Main.
   */
  public static void firstmain(String... args) {
    if (args.length > 0) {
      configFile = args[0];
    }

    // read configuration
    if (!readConfig()) {
      return;
    }

    // // start NetworkTables
    // NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
    // if (server) {
    //   System.out.println("Setting up NetworkTables server");
    //   ntinst.startServer();
    // } else {
    //   System.out.println("Setting up NetworkTables client for team " + team);
    //   ntinst.startClientTeam(team);
    // }

    // start cameras
    List<VideoSource> cameras = new ArrayList<>();
    for (CameraConfig cameraConfig : cameraConfigs) {
      cameras.add(startCamera(cameraConfig));
    }

    // start image processing on camera 0 if present
    if (cameras.size() >= 1) {
      VisionThread visionThread = new VisionThread(cameras.get(0), new MyPipeline(), pipeline -> {
        // do something with pipeline results
      });
      /*
       * something like this for GRIP: VisionThread visionThread = new
       * VisionThread(cameras.get(0), new GripPipeline(), pipeline -> { ... });
       */
      visionThread.start();
    }

    // loop forever
    for (;;) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException ex) {
        return;
      }
    }
  }
}
