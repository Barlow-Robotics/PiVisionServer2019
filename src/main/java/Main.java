
/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import com.google.gson.JsonElement;
import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import org.opencv.core.*;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class Main {
  public static final int PORT = 14580;
  public static final String IP = "10.45.72.25";

  static SocketHandler socketHandler = new SocketHandler(PORT,IP);
  public static void main(String... args) {
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME);


      AlignmentPipeline pipeline = new AlignmentPipeline();


      UsbCamera camera = CameraServer.getInstance().startAutomaticCapture();
      camera.setResolution(320, 240);
      camera.setWhiteBalanceManual(3260);
      camera.setExposureManual(44);
      CvSink cvSink = CameraServer.getInstance().getVideo();

      CvSource outputStream = CameraServer.getInstance().putVideo("Blur", 320, 240);
      MjpegServer processedVideoServer = new MjpegServer("processed_video_server", 8082);
      processedVideoServer.setSource(outputStream);

      CvSource thresholdStream = CameraServer.getInstance().putVideo("Blur", 320, 240);
      MjpegServer thresholdVideoServer = new MjpegServer("processed_video_server", 8081);
      thresholdVideoServer.setSource(thresholdStream);

      System.out.println("Creating the sink");
      HttpCamera ll = new HttpCamera("LimeLight", "http://10.45.72.59:5800", HttpCamera.HttpCameraKind.kMJPGStreamer) ;
      ll.setResolution(320, 240);
      CvSink theSink = new CvSink("LimeLight") ;
      theSink.setSource(ll);

      LLProcessImage LLProcessor = new LLProcessImage();
      Lidar lidar = new Lidar();
      LimeLight limeLight = new LimeLight();
      Mat theImage = new Mat();
      Mat source = new Mat();

      while (true) {
        //System.out.println("Top of the loop") ;
        long startTime = System.nanoTime();
        long result = theSink.grabFrame(theImage);
        long processStart = System.nanoTime();

        RotatedRect[] rects = new RotatedRect[0];
        cvSink.grabFrame(source);
          if (!source.empty()) {
              //stepStart = System.nanoTime() ;
              pipeline.process(source);
              //stepEnd = System.nanoTime() ;
              //pipelineTime = (stepEnd - stepStart)/1000000 ;

              //System.out.println("pipeline processing time " + (stepEnd - stepStart)/1000000) ;
              outputStream.putFrame(pipeline.getProcessedFrame());
              thresholdStream.putFrame(pipeline.getThresholdOutput());
          }

        if (result != 0) {
          rects = LLProcessor.process(theImage);
        } else {
          System.out.println("Failed to get frame");
        }
        LimeLight.Target3D targ = limeLight.getCamTranslation();
        double LLBearing = targ.rotation.y;
        double LLRange = Math.sqrt((targ.translation.x*targ.translation.x)+(targ.translation.y*targ.translation.y));
        AlignmentPacket nPacket = new AlignmentPacket(pipeline.getTargets().toArray(RotatedRect[]::new),rects,LLBearing,LLRange,lidar.getDistace());
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


  public static final int SIZE_GROUP = 2;
  public static final int POS_GROUP = 4;
  public static final int ANGLE_GROUP = 6;
  public RotatedRect[] parseRRArray(String packet){
      packet = packet.replace(" ","");
      final String regex = "(\\[([^\\[\\]]*?)\\],)(\\[([^\\[\\]]*?)\\])(,([^,]*?)])";

      final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.COMMENTS);
      final Matcher matcher = pattern.matcher(packet);
      ArrayList<RotatedRect> rects = new ArrayList<RotatedRect>();
      while (matcher.find()) {

          double cX=0,cY=0,sW=0,sH=0,angle=0;
          System.out.println("Full match: " + matcher.group(0));
          for (int i = 1; i <= matcher.groupCount(); i++) {
              if(i == SIZE_GROUP){
                  //Looks like X,Y
                  cX = Double.parseDouble(matcher.group(i).split(",")[0]);
                  cY = Double.parseDouble(matcher.group(i).split(",")[1]);
              }
              if(i == POS_GROUP){
                  //Looks like X,Y
                  sW = Double.parseDouble(matcher.group(i).split(",")[0]);
                  sH = Double.parseDouble(matcher.group(i).split(",")[1]);
              }
              if(i == ANGLE_GROUP){
                  //Looks like X,Y
                   angle = Double.parseDouble(matcher.group(i));
              }
          }
          rects.add(new RotatedRect(new Point(cX,cY),new Size(sW,sH),angle));
      }
      return (RotatedRect[]) rects.toArray();
  }
}