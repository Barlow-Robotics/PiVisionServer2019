
import com.google.gson.Gson;
import org.opencv.core.RotatedRect;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class SocketHandler{
    public int port;
    public static String ip;
    private DatagramSocket socket;
    private Gson gson = new Gson();



    public SocketHandler(int port,String ip){
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.ip = ip;
        this.port = port;
    }

    public void sendData(AlignmentPacket data){
        String jsonOut = gson.toJson(data,AlignmentPacket.class);
        byte buf[] = null;
        buf = jsonOut.getBytes();
        DatagramPacket packet;
        try {
            packet = new DatagramPacket(buf, buf.length,InetAddress.getByName(ip),port);
            socket.send(packet);
        } catch (Exception e1) {

        }
    }
}
class AlignmentPacket{
    public RotatedRect[] Alignmentlines;
    public RotatedRect[] wallRects;
    public RotatedRect centerLine;
    public double LLBearing,LLRange,lidarDist;

    public AlignmentPacket(RotatedRect[] Alignmentlines, RotatedRect[] wallRects, RotatedRect centerLine,double LLBearing, double LLRange, double lidarDist){
        this.Alignmentlines = Alignmentlines;
        this.wallRects = wallRects;
        this.centerLine = centerLine;
        this.LLBearing = LLBearing;
        this.LLRange = LLRange;
        this.lidarDist = lidarDist;
    }
}