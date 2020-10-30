package practica2;

import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MyDrone extends IntegratedAgent {

    private String receiver;
    private String key;
    private TTYControlPanel myControlPanel;
    private int width, height, maxflight;

    private int mundo[][];  //Se inicializa con width x width a 0
    private int memoria[][]; //Se inicializa con width x width 0, 1 si ya hemos pasado
    private int position[] = new int[3]; //Posicion del drone
    private int lidar[][] = new int[7][7];
    private double thermal[][];

    private int compass = -90;
    private double angular;
    private int ontarget;
    private int alive;
    private double payload;
    private double distance;
    private int altimeter;
    private int energy;
    
    private int nSensores;  //Numero de sensores añadidos al drone

    private int energy_u = 50; //Umbral para recargar la batería

    @Override
    public void setup() {
        super.setup();
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();

        //Panel de control
        myControlPanel = new TTYControlPanel(this.getAID());
    }

    @Override
    public void plainExecute() {
        Info("Enviando credenciales");
        ACLMessage out = new ACLMessage();

        //Decision de los sensores con los que se loguea en el mundo
        String attach[] = {"alive", "distance", "altimeter", "gps", "visual", "angular", "compass", "energy"};
        nSensores = attach.length;

        //Iniciar sesion en el mundo
        ACLMessage in = login(out, "Playground1", attach);

        String answer = in.getContent();
        out = in.createReply();

        //Control de errores
        JsonObject json = Json.parse(answer).asObject();
        String details = null;
        if (json.get("details") != null) {
            details = json.get("details").asString();
            Info("Detalles del error: " + details);
        }

        while (ontarget != 1) {
            //Lecutra de sensores
            readSensores(out, in);
            ArrayList<String> acciones = calcularAccion();

            for (String accion : acciones) {
                json = new JsonObject();
                json.add("command", "execute");
                json.add("action", accion);
                json.add("key", key);
                String resultado = json.toString();

                //Enviar la accion
                out.setContent(resultado);
                this.send(out);

                //ESPERAMOS RESPUESTA
                in = this.blockingReceive();
                answer = in.getContent();
                //if(
                
                if(Json.parse(answer).asObject().get("result").asString().equals("error"))
                    Info(answer);
            }
        }

        logout(out);

        _exitRequested = true;
    }

    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }

    private void logout(ACLMessage out) {
        JsonObject json = new JsonObject();
        json.add("command", "logout");
        String resultado = json.toString();

        out.setContent(resultado);

        Info("Sesion cerrada");
        this.send(out);
        myControlPanel.close();
    }

    private ACLMessage login(ACLMessage out, String mundo, String sensores[]) {
        //PARSEAR JSON CON LOS DATOS A ENVIAR        
        String command = "login";
        String world = mundo;

        JsonObject json = new JsonObject();
        json.add("command", command);
        json.add("world", world);
        JsonArray vector = new JsonArray();

        for (int i = 0; i < sensores.length; i++) {
            vector.add(sensores[i]);
        }

        json.add("attach", vector);
        String resultado = json.toString();
        //Info(resultado);

        //ENVIAMOS LOS CREDENCIALES
        out.setSender(getAID());
        out.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        out.setContent(resultado);
        this.send(out);

        Info("Enviada petición de login");

        //ESPERAMOS RESPUESTA
        ACLMessage in = this.blockingReceive();

        json = Json.parse(in.getContent()).asObject();
        key = json.get("key").asString();

        
        width = json.get("width").asInt();
        height = json.get("height").asInt();
        maxflight = json.get("maxflight").asInt();
        
        Info("La key almacenada es " + key);
        myControlPanel.feedData(in, width, height, maxflight);
        
        
        
        memoria = new int[width+2][width+2];
        this.mundo = new int[width+2][width+2];
        thermal = new double[width+2][width+2];

        //Inicializar las matrices
        for (int i = 0; i < width+1; i++) {
            for (int j = 0; j < width+1; j++) {
                memoria[i][j] = 0;
                this.mundo[i][j] = 0;
                thermal[i][j] = -1;
            }
        }

        return in;
    }

    private void readSensores(ACLMessage out, ACLMessage in) {
        JsonObject json = new JsonObject();
        json.add("command", "read");
        json.add("key", key);
        String resultado = json.toString();
        out.setContent(resultado);
        this.send(out);

        in = this.blockingReceive();
        String answer = in.getContent();
        json = Json.parse(answer).asObject();
        out = in.createReply();

        //Info("La lectora de sensores es: " + answer);
        myControlPanel.feedData(in, width, height, maxflight);
        myControlPanel.fancyShow();

        //Actualizacion de los sensores 
        for (JsonValue j : json.get("details").asObject().get("perceptions").asArray()) {
            switch (j.asObject().get("sensor").asString()) {
                case ("alive"):
                    alive = j.asObject().get("data").asArray().get(0).asInt();
                    break;
                case ("compass"):
                    compass = j.asObject().get("data").asArray().get(0).asInt();
                    break;
                case ("angular"):
                    angular = j.asObject().get("data").asArray().get(0).asDouble();
                    break;
                case ("gps"):
                    position[0] = j.asObject().get("data").asArray().get(0).asArray().get(0).asInt();
                    position[1] = j.asObject().get("data").asArray().get(0).asArray().get(1).asInt();
                    position[2] = j.asObject().get("data").asArray().get(0).asArray().get(2).asInt();
                    memoria[position[0]][position[1]] = 1;
                    break;

                case ("ontarget"):
                    ontarget = j.asObject().get("data").asArray().get(0).asInt();
                    break;
                case ("payload"):
                    payload = j.asObject().get("data").asArray().get(0).asInt();
                    break;
                case ("distance"):
                    distance = j.asObject().get("data").asArray().get(0).asDouble();
                    break;
                case ("energy"):
                    energy = j.asObject().get("data").asArray().get(0).asInt();
                    break;
                case ("altimeter"):
                    altimeter = j.asObject().get("data").asArray().get(0).asInt();
                    break;
            }
        }

        //Rellenar mundo, lidar y thermal tras leer el resto de sonsores
        for (JsonValue j : json.get("details").asObject().get("perceptions").asArray()) {
            if (j.asObject().get("sensor").asString() == "visual") {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if((position[0] - 3 + i)>=0 && (position[1] - 3 + k) >= 0)
                        mundo[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asInt();
                    }
                }
            }

            if (j.asObject().get("sensor").asString() == "lidar") {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if((position[0] - 3 + i)>=0 && (position[1] - 3 + k) >= 0)
                        lidar[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asInt();
                    }
                }
            }

            if (j.asObject().get("sensor").asString() == "thermal") {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if((position[0] - 3 + i)>=0 && (position[1] - 3 + k) >= 0)
                        thermal[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asDouble();
                    }
                }
            }

        }
    }

    private ArrayList<String> calcularAccion() {
        ArrayList acciones = new ArrayList<String>();

        //Hacia donde ir
        ArrayList<String> casillas = new ArrayList<>();
        ArrayList<Double> distancias = new ArrayList<>();

        diferenciaDistancias(casillas, distancias);
        
        burbuja(casillas, distancias);

        
        //En orden, mirar que se pueda ir a la siguiente casilla
        
        //Mirar si hay que recarga batería antes de realizar las acciones
        Info("Coste: " + coste(acciones));
        Info("Queda energia: " + energy);
        
        if (energy_u > (energy - coste(acciones))) {
            ArrayList<String> bajar = new ArrayList<>();
            //Bajar hasta altimetro = 5
            Info("El altimetro: " + altimeter);
            Info("Bajamos " + altimeter/5 + " veces");
            for (int i = 0; i < altimeter/5; i++) {
                Info("**");
                bajar.add("moveD");
            }
            //Aterrizar (touchD)
            bajar.add("touchD");
            //recharge
            bajar.add("recharge");
            //muveUP
            bajar.add("moveUP");
            
            return bajar;
        }

        return acciones;
    }
    
    private void burbuja(ArrayList<String> casillas, ArrayList<Double> distancias){
        //Ordenamos por orden de distancias
        int i;
        boolean flag = true;
        double temp;
        String temps;

        while (flag) {
            flag = false;
            for (i = 0; i < distancias.size() - 1; i++) {
                if (distancias.get(i) > distancias.get(i + 1)) {
                    temp = distancias.get(i);
                    temps = casillas.get(i);
                    
                    distancias.set(i, distancias.get(i+1));
                    distancias.set(i+1, temp);
                    
                    casillas.set(i, casillas.get(i+1));
                    casillas.set(i+1, temps);

                    flag = true;
                }
            }
        }
    }
    
    private void diferenciaDistancias(ArrayList<String> casillas, ArrayList<Double> distancias){
        //TODO Tener en cuenta que el [0,0] es la esquina superior
        casillas.add("NO");
        if(memoria[position[0]-1][position[1]+1] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular - (-45)));
        }
        
        casillas.add("N");
        if(memoria[position[0]][position[1]+1] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular));
        }
        
        casillas.add("NE");
        if(memoria[position[0]+1][position[1]+1] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
           distancias.add(Math.abs((double) angular - (45)));
        }
       
        casillas.add("E");
        if(memoria[position[0]+1][position[1]] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular - (90)));
        }
        
        casillas.add("SE");
        if(memoria[position[0]+1][position[1]-1] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular - (135)));
        }
        
        casillas.add("S");
        if(memoria[position[0]][position[1]-1] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular - (180)));
        }
        
        casillas.add("SO");
        if(memoria[position[0]-1][position[1]-1] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular - (-135)));
        }
        
        casillas.add("O");
        if(memoria[position[0]-1][position[1]] == 1){
            distancias.add(Double.POSITIVE_INFINITY);
        }else {
            distancias.add(Math.abs((double) angular - (-90)));
        }
    }
    
    private int coste(ArrayList<String> acciones){
        int coste = nSensores;
        
        for(int i=0; i<acciones.size(); i++){
            switch(acciones.get(i)){
                case "moveF":
                    coste += 1;
                    break;
                case "rotateL":
                    coste += 1;
                    break;
                case "rotateR":
                    coste += 1;
                    break;
                case "moveUP":
                    coste += 5;
                    break;
                case "moveD":
                    coste += 5;
                    break;
                case "touchD":
                    coste += 5;
                    break;
            }
        }
        
        return coste;
    }
}
