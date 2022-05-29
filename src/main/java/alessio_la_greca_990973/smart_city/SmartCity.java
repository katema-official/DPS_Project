package alessio_la_greca_990973.smart_city;

import static alessio_la_greca_990973.smart_city.District.*;
import java.util.Random;

public class SmartCity {
    //class to represent the smart city, a 10x10 grid with the indexes that go from 0 to 9 (both included)
    private static int minX = 0;
    private static int maxX = 9;
    private static int minY = 0;
    private static int maxY = 9;

    private static int district_minX = 0;
    private static int district_halfX = 4;
    private static int district_maxX = 9;
    private static int district_minY = 0;
    private static int district_halfY = 4;
    private static int district_maxY = 9;

    //method for obtaining the district to which the (x,y) coordinates belong
    public static District getDistrict(int x, int y){
        if(x<minX || x>maxX || y<minY || y>maxY) {
            //in this case, the coordinates were outside the smart city bounds.
            return DISTRICT_ERROR;
        }

        if(x>=district_minX && x<=district_halfX && y>=district_minY && y<=district_halfY){
            return DISTRICT1;
        }
        if(x>district_halfX && x<=district_maxX && y>=district_minY && y<=district_halfY){
            return DISTRICT2;
        }
        if(x>=district_minX && x<=district_halfX && y>district_halfY && y<=district_maxY){
            return DISTRICT4;
        }
        if(x>district_halfX && x<=district_maxX && y>district_halfY && y<=district_maxY){
            return DISTRICT3;
        }

        return DISTRICT_ERROR;

    }

    public static int generateRandomXInsideSmartCity(){
        Random rand = new Random();
        int x = minX + rand.nextInt(maxX+1);
        return x;
    }

    public static int generateRandomYInsideSmartCity(){
        Random rand = new Random();
        int y = minY + rand.nextInt(maxY+1);
        return y;
    }

    public static int[] getCoordinatesForRechargeStation(District d){
        int[] ret = new int[2];
        switch(d){
            case DISTRICT1: ret[0] = 0; ret[1] = 0; return ret;
            case DISTRICT2: ret[0] = 9; ret[1] = 0; return ret;
            case DISTRICT3: ret[0] = 9; ret[1] = 9; return ret;
            case DISTRICT4: ret[0] = 0; ret[1] = 9; return ret;
            default: return null;
        }
    }

    public static double distance(int x1, int y1, int x2, int y2){
        return Math.ceil(Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)));
    }


}
