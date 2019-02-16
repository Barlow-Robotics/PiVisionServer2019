import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.MjpegServer;
import edu.wpi.first.vision.VisionPipeline;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class AlignmentPipeline implements VisionPipeline {


    final static double MIN_AREA = 50.0 ;
    final static double MIN_PERIMETER = 150.0 ;

    // Outputs
    private Mat rgbThresholdOutput = new Mat();
    private ArrayList<MatOfPoint> findContoursOutput = new ArrayList<MatOfPoint>();
    private ArrayList<MatOfPoint> filterContoursOutput = new ArrayList<MatOfPoint>();

    MjpegServer videoServer ;
    CvSource cvsource ;

    Mat outputImage ;

    ArrayList<RotatedRect> lines ;

    public int val;

    public AlignmentPipeline() {
        outputImage = new Mat() ;
        lines = new ArrayList<RotatedRect>() ;
        System.out.println("created targets") ;
    }


    public Mat getProcessedFrame() {
        return outputImage ;
    }

    public Mat getThresholdOutput() {
        return this.rgbThresholdOutput ;
    }

    public ArrayList<RotatedRect> getTargets() {
        return this.lines ;
    }


    @Override
    public void process(Mat mat) {
        // Step RGB_Threshold0:

        Mat rgbThresholdInput = mat;
        double[] rgbThresholdRed = { 230, 255.0 };
        double[] rgbThresholdGreen = { 230, 255.0 };
        double[] rgbThresholdBlue = { 230, 255.0 };
        rgbThreshold(rgbThresholdInput, rgbThresholdRed, rgbThresholdGreen, rgbThresholdBlue, rgbThresholdOutput);

        // Step Find_Contours0:
        Mat findContoursInput = rgbThresholdOutput;
        boolean findContoursExternalOnly = false;
        findContours(findContoursInput, findContoursExternalOnly, findContoursOutput);

        outputImage = mat ;

        filterContoursOutput.clear() ;
        if ( lines == null) {
            lines = new ArrayList<RotatedRect>() ;
        }
        lines.clear() ;
        for( MatOfPoint contour: findContoursOutput) {
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true) ;
            //System.out.println("in contour loop. perimeter is " + perimeter) ;
            if ( perimeter > MIN_PERIMETER ) {
                //System.out.println("passed min perimeter check") ;
                filterContoursOutput.add(contour) ;

                //RotatedRect minEllipse = new RotatedRect();
                
                Point[] points = contour.toArray() ;
                if (points.length >= 5) {
                    RotatedRect minEllipse = Imgproc.minAreaRect(new MatOfPoint2f(points)) ;
                    double ratio = minEllipse.size.width / minEllipse.size.height ;
                    System.out.println("target ratio is " + ratio) ;
                    //if ( ratio < 0.5 ) {
                        System.out.println("target angle is " + minEllipse.angle) ;

//                        if ( (minEllipse.angle > -45 && minEllipse.angle < 45) || ( minEllipse.angle > -45  minEllipse.angle > 180-45 ) {
                        //if ( (minEllipse.angle > -45 && minEllipse.angle < 45)  ) {
                            lines.add( minEllipse) ;
                            drawRotatedRect(minEllipse, outputImage, new Scalar( 0, 0, 255));
                        // }
                    //}
                }
            }
        }

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

}
