import com.pi4j.io.i2c.*;

public class Lidar {
    // - I2CBus.BUS_2 uses header pin CON6:3 as SDA and header pin CON6:5 as SCL
    // - I2CBus.BUS_3 uses header pin CON6:27 as SDA and header pin CON6:28 as SCL
    I2CBus i2c;
    DistanceUpdater lid;
    Thread lidarUpdateThread;
    public Lidar(){
        try{
            lid = new DistanceUpdater();
            lidarUpdateThread = new Thread(lid);
            lidarUpdateThread.start();
        }catch(Exception e){}
    }

    public double getDistace(){
        return accessDistance(true,0);
    }

    private static int distance = 0;

    public static synchronized int accessDistance(boolean read, int nDist){
        if(read) return distance;
        else distance = nDist;
        return 0;
    }
    class DistanceUpdater implements Runnable{
        private I2CDevice i2c;
        public final int LIDARLITE_ADDR_DEFAULT = 0x62;

        public DistanceUpdater() throws Exception{

            i2c = I2CFactory.getInstance(I2CBus.BUS_2).getDevice(LIDARLITE_ADDR_DEFAULT);
            byte b = 0x1d;
            i2c.write(0x02,b);

        }
        @Override
        public void run(){
            while(true) {
                try{
                    Lidar.accessDistance(false, getDistance());
                }catch(Exception e){}
            }
        }
        public int getDistance() throws Exception{
            byte[] buffer;
            buffer = new byte[2];
            byte b = 0x04;
            i2c.write(0x00, b);

            try{
                Thread.sleep(40);
            }catch(Exception e){}
            i2c.read(0x8f,buffer,0,2);


            return (int)Integer.toUnsignedLong(buffer[0] << 8) + Byte.toUnsignedInt(buffer[1]);
        }
    }
}