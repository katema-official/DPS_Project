package alessio_la_greca_990973.smart_city.taxi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TaxiMain {

    public static void main(String args[]) {
        System.out.println("Welcome! Please specify an id between 0 and 16383 for this taxi");
        int id = -1;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            String name = reader.readLine();
            id = Integer.parseInt(name);

            if(id < 0 || id > 16383){
                System.out.println("Please insert an integer between 0 and 16383");
            }else {

                Taxi taxi = new Taxi(id, "localhost");
                taxi.init();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }catch(NumberFormatException e){
            System.out.println("Please insert an integer between 0 and 16383");
        }
    }
}
