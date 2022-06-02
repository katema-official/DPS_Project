package alessio_la_greca_990973.smart_city.taxi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TaxiMain {

    public static void main(String args[]) {
        /*A Taxi is initialized by specifying
        - ID
        - listening port for the communications with the other taxis
        - Administrator Server ’s address*/
        System.out.println("Welcome! Please specify an id between 0 and 16383 for this taxi");
        int id = -1;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            String line = reader.readLine();
            id = Integer.parseInt(line);

            if(id < 0 || id > 16383){
                System.out.println("Please insert an integer between 0 and 16383");
            }else {

                Taxi taxi = new Taxi(id, "localhost");
                taxi.init();


                //from now on, it's just the command line for giving orders to the taxi
                while(true){
                    try {
                        line = reader.readLine();
                    } catch (IOException e) {e.printStackTrace();}
                    if(line.equals("recharge")){
                        taxi.setExplicitRechargeRequest(true);
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }catch(NumberFormatException e){
            System.out.println("Please insert an integer between 0 and 16383");
        }
    }
}
