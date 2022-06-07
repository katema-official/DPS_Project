package alessio_la_greca_990973.smart_city.taxi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

public class TaxiMain {

    public static Object taxiMain_lock;
    public static Integer ok;

    public static void main(String args[]) {
        /*A Taxi is initialized by specifying
        - ID
        - listening port for the communications with the other taxis
        - Administrator Server ’s address*/
        System.out.println("Welcome! Please specify an id between 0 and 16383 for this taxi");
        int id = -1;
        //BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Scanner scan = new Scanner(System.in);
        taxiMain_lock = new Object();
        try {
            //String line = reader.readLine();
            String line;
            line = scan.nextLine();
            id = Integer.parseInt(line);

            if(id < 0 || id > 16383){
                System.out.println("Please insert an integer between 0 and 16383");
            }else {

                Taxi taxi = new Taxi(id, "localhost");
                ok = -1;
                Thread thread_taxi = new Thread(taxi);
                thread_taxi.start();

                synchronized (taxiMain_lock){
                    if(ok == -1) {
                        try {
                            taxiMain_lock.wait();
                        } catch (InterruptedException e) {throw new RuntimeException(e);}
                    }
                }

                System.out.println("ok = " + ok);
                if(ok == 1) {
                    //from now on, it's just the command line for giving orders to the taxi
                    while (true) {
                        //try {
                            //line = reader.readLine();
                            line = scan.nextLine();
                            System.out.println("Line = " + line);
                        //} catch (IOException e) {
                          //  e.printStackTrace();
                        //}
                        if (line.equals("recharge")) {
                            System.out.println("Recharge request accepted");
                            taxi.setExplicitRechargeRequest(true);
                        }else if(line.equals("quit")){
                            System.out.println("Quit request accepted");
                            taxi.shutdownTaxiServer();
                            break;
                        }
                    }
                }

            }
        } catch(NumberFormatException e){
            System.out.println("Please insert an integer between 0 and 16383");
        }
    }
}
