import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;

import edu.wpi.first.wpilibj.DigitalSource;
import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.highgui.*;
import org.opencv.imgcodecs.*;
import org.opencv.imgproc.*;
import org.opencv.core.*;
import org.opencv.videoio.*;

import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.* ;


import java.util.*;

public class MyPipeline implements VisionPipeline {

    // Outputs
    private Mat rgbThresholdOutput = new Mat();
    private ArrayList<MatOfPoint> findContoursOutput = new ArrayList<MatOfPoint>();
    private ArrayList<MatOfPoint> filterContoursOutput = new ArrayList<MatOfPoint>();
    private SocketServer server = new SocketServer(14578,""); //TODO get roborio port
    private Lidar lidar = new Lidar(); //TODO make this work

    MjpegServer videoServer ;
    CvSource cvsource ;

    Mat outputImage ;

    ArrayList<RotatedRect> targets ;

    public int val;

    public void MyPipeLine() {
        outputImage = new Mat() ;
        targets = new ArrayList<RotatedRect>() ;
        System.out.println("created targets") ;
    }


    public Mat getProcessedFrame() {
        return outputImage ;
    }

    public Mat getThresholdOutput() {
        return this.rgbThresholdOutput ;
    }

    public ArrayList<RotatedRect> getTargets() {
        return this.targets ;
    }


    @Override
    public void process(Mat mat) {
        // Step RGB_Threshold0:

        long startTime = 0 ;
        long endTime = 0 ;
        

        startTime = System.nanoTime() ;
        Mat rgbThresholdInput = mat;
        double[] rgbThresholdRed = { 100, 255.0 };
        double[] rgbThresholdGreen = { 100, 255.0 };
        double[] rgbThresholdBlue = { 100, 255.0 };
        rgbThreshold(rgbThresholdInput, rgbThresholdRed, rgbThresholdGreen, rgbThresholdBlue, rgbThresholdOutput);
        endTime = System.nanoTime() ;
        // System.out.println( "rgbThresholdingTime is " + (endTime-startTime)/1000000) ;

        startTime = System.nanoTime() ;
        // Step Find_Contours0:
        Mat findContoursInput = rgbThresholdOutput;
        boolean findContoursExternalOnly = false;
        findContours(findContoursInput, findContoursExternalOnly, findContoursOutput);
        endTime = System.nanoTime() ;
        // System.out.println( "findContours time is " + (endTime-startTime)/1000000) ;

        startTime = System.nanoTime() ;
        // Step Filter_Contours0:
        ArrayList<MatOfPoint> filterContoursContours = findContoursOutput;
        double filterContoursMinArea = 0.0;
        double filterContoursMinPerimeter = 50.0;
        double filterContoursMinWidth = 0;
        double filterContoursMaxWidth = 1000;
        double filterContoursMinHeight = 0;
        double filterContoursMaxHeight = 1000;
        double[] filterContoursSolidity = { 0, 100 };
        double filterContoursMaxVertices = 1000000;
        double filterContoursMinVertices = 0;
        double filterContoursMinRatio = 0;
        double filterContoursMaxRatio = 1000;
        filterContours(filterContoursContours, filterContoursMinArea, filterContoursMinPerimeter,
                filterContoursMinWidth, filterContoursMaxWidth, filterContoursMinHeight, filterContoursMaxHeight,
                filterContoursSolidity, filterContoursMaxVertices, filterContoursMinVertices, filterContoursMinRatio,
                filterContoursMaxRatio, filterContoursOutput);
        endTime = System.nanoTime() ;
        // System.out.println( "filterContours time is " + (endTime-startTime)/1000000) ;
        

        // outputImage = mat.clone() ;
        outputImage = mat ;

        if ( filterContoursOutput.size() > 0 ) {

            if (targets == null ) {
                targets = new ArrayList<RotatedRect>() ;
            }

            targets.clear() ;

            //List<RotatedRect> targets = new ArrayList<RotatedRect>() ;
            
            for (int i = 0; i < filterContoursOutput.size(); i++) {
                RotatedRect minRect;


                Point[] points = filterContoursOutput.get(i).toArray() ;
                if (points.length >= 5) {

                    minRect = Imgproc.minAreaRect(new MatOfPoint2f(points)) ;
                    targets.add(minRect);
                }

            }
        }
        server.sendData(targets,lidar.getDistance());

    }

    private void drawRotatedRect(RotatedRect rect, Mat theImage, Scalar color) {
        for(int i=0; i<4; ++i){
          Point points[] = new Point[4];
          rect.points(points);
          // Drawing a line
          Imgproc.line (
             theImage,   
             points[i],  
             points[(i+1)%4],
             color,
             1
          );
        }
       }
    




    /**
     * Segment an image based on color ranges.
     * 
     * @param input  The image on which to perform the RGB threshold.
     * @param red    The min and max red.
     * @param green  The min and max green.
     * @param blue   The min and max blue.
     * @param output The image in which to store the output.
     */
    private void rgbThreshold(Mat input, double[] red, double[] green, double[] blue, Mat out) {
        Imgproc.cvtColor(input, out, Imgproc.COLOR_BGR2RGB);
        Core.inRange(out, new Scalar(red[0], green[0], blue[0]), new Scalar(red[1], green[1], blue[1]), out);
    }

    /**
     * Sets the values of pixels in a binary image to their distance to the nearest
     * black pixel.
     * 
     * @param input    The image on which to perform the Distance Transform.
     * @param type     The Transform.
     * @param maskSize the size of the mask.
     * @param output   The image in which to store the output.
     */
    private void findContours(Mat input, boolean externalOnly, List<MatOfPoint> contours) {
        Mat hierarchy = new Mat();
        contours.clear();
        int mode;
        if (externalOnly) {
            mode = Imgproc.RETR_EXTERNAL;
        } else {
            mode = Imgproc.RETR_LIST;
        }
        int method = Imgproc.CHAIN_APPROX_SIMPLE;
        Imgproc.findContours(input, contours, hierarchy, mode, method);
    }

    /**
     * Filters out contours that do not meet certain criteria.
     * 
     * @param inputContours  is the input list of contours
     * @param output         is the the output list of contours
     * @param minArea        is the minimum area of a contour that will be kept
     * @param minPerimeter   is the minimum perimeter of a contour that will be kept
     * @param minWidth       minimum width of a contour
     * @param maxWidth       maximum width
     * @param minHeight      minimum height
     * @param maxHeight      maximimum height
     * @param Solidity       the minimum and maximum solidity of a contour
     * @param minVertexCount minimum vertex Count of the contours
     * @param maxVertexCount maximum vertex Count
     * @param minRatio       minimum ratio of width to height
     * @param maxRatio       maximum ratio of width to height
     */
    private void filterContours(List<MatOfPoint> inputContours, double minArea, double minPerimeter, double minWidth,
            double maxWidth, double minHeight, double maxHeight, double[] solidity, double maxVertexCount,
            double minVertexCount, double minRatio, double maxRatio, List<MatOfPoint> output) {
        final MatOfInt hull = new MatOfInt();
        output.clear();
        // operation
        for (int i = 0; i < inputContours.size(); i++) {
            final MatOfPoint contour = inputContours.get(i);
            final Rect bb = Imgproc.boundingRect(contour);
            if (bb.width < minWidth || bb.width > maxWidth)
                continue;
            if (bb.height < minHeight || bb.height > maxHeight)
                continue;
            final double area = Imgproc.contourArea(contour);
            if (area < minArea)
                continue;
            if (Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true) < minPerimeter)
                continue;
            Imgproc.convexHull(contour, hull);
            MatOfPoint mopHull = new MatOfPoint();
            mopHull.create((int) hull.size().height, 1, CvType.CV_32SC2);
            for (int j = 0; j < hull.size().height; j++) {
                int index = (int) hull.get(j, 0)[0];
                double[] point = new double[] { contour.get(index, 0)[0], contour.get(index, 0)[1] };
                mopHull.put(j, 0, point);
            }
            final double solid = 100 * area / Imgproc.contourArea(mopHull);
            if (solid < solidity[0] || solid > solidity[1])
                continue;
            if (contour.rows() < minVertexCount || contour.rows() > maxVertexCount)
                continue;
            final double ratio = bb.width / (double) bb.height;
            if (ratio < minRatio || ratio > maxRatio)
                continue;
            output.add(contour);
        }
    }
}
