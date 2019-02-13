import org.opencv.core.RotatedRect;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class SocketServer {
    DatagramSocket socket;
    int port;
    String ip;

    public SocketServer(int port, String ip){
        try {
            socket = new DatagramSocket();
        }catch(Exception e){System.err.println(e);}

        this.port = port;
        this.ip = ip;
    }

    public void sendData(ArrayList<RotatedRect> targets, double lidar){
        String toSend = "{" +
                        "\"Time\": " + System.currentTimeMillis() + "," +
                        "\"Targets\": [";
        for(RotatedRect rect : targets){
            toSend += "{ \"Center\": { " +
                            "\"x\": " + rect.center.x +
                            ",\"y\": " + rect.center.y +
                        "}, \"Size\": {" +
                            "\"w\": " + rect.size.width +
                            ",\"h\": " + rect.size.height +
                        "}, \"Angle\": " + rect.angle +
                        "},";
        }

        toSend = toSend.substring(0,targets.size() > 0 ? toSend.length()-1 : toSend.length()) + "]";
        toSend += "\"Lidar\": " + lidar + "}";

        byte[] buf = toSend.getBytes();
        try {
            DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip), port);
            socket.send(packet);
        }catch(Exception e){
            System.err.println(e);
        }
    }
}
