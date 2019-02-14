
/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import org.opencv.core.*;



public final class Main {
  public static final int PORT = 14580;
  public static final String IP = "10.45.72.25";

  static SocketHandler socketHandler = new SocketHandler(PORT,IP);
  public static void main(String... args) {
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME);


      System.out.println("Getting the camera");
      UsbCamera theCamera = new UsbCamera("USB Camera 0", 0);
      theCamera.setResolution(320, 240);

      System.out.println("Creating the sink");
      HttpCamera ll = new HttpCamera("LimeLight", "http://10.45.72.59:5800", HttpCamera.HttpCameraKind.kMJPGStreamer) ;
      ll.setResolution(320, 240);
      CvSink theSink = new CvSink("LimeLight") ;
      theSink.setSource(ll);

      CvSource outStream = CameraServer.getInstance().putVideo("Blur", 320, 240);
      MjpegServer videoServer = new MjpegServer("processed_video_server", 8081);
      videoServer.setSource(outStream);

      Mat image = new Mat();

      System.out.println("Setting the sink source");

      theSink.setSource(theCamera);

      LLProcessImage LLProcessor = new LLProcessImage();
      Lidar lidar = new Lidar();

      Mat theImage = new Mat();
      while (true) {
        //System.out.println("Top of the loop") ;
        long startTime = System.nanoTime();
        long result = theSink.grabFrame(theImage);
        long processStart = System.nanoTime();

        RotatedRect[] rects = new RotatedRect[0];

        if (result != 0) {
          rects = LLProcessor.process(theImage);
        } else {
          System.out.println("Failed to get frame");
        }

        AlignmentPacket nPacket = new AlignmentPacket(null,rects,null,0,0,lidar.getDistace());
        socketHandler.sendData(nPacket);

        long endTime = System.nanoTime();
        long elapsed = (endTime - startTime) / 1000000;
        long pipeTime = (endTime - processStart) / 1000000;
        int targetCount = rects.length;


        System.out.println("Sent the data!");
        String outString = String.format("Targets %d, Elapsed Time = %4d, PipeTime = %4d", targetCount, elapsed, pipeTime);
        System.out.println(outString);

    }
  }
}