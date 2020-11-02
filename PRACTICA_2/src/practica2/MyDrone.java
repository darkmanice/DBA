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
    private boolean rescatado = false;

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
        String attach[] = {"alive", "distance", "altimeter", "gps", "visual", "angular", "compass", "energy", "ontarget"};
        nSensores = attach.length;

        //Iniciar sesion en el mundo
        ACLMessage in = login(out, "World5", attach);

        String answer = in.getContent();
        out = in.createReply();

        //Control de errores
        JsonObject json = Json.parse(answer).asObject();
        String details = null;
        if (json.get("details") != null) {
            details = json.get("details").asString();
            Info("Detalles del error: " + details);
        }
        
        while (!rescatado) {
            //Lecutra de sensores
            readSensores(out, in);
            //muestra el mundo
//            for(int i = 0; i < width; i++){
//               for(int j = 0; j < width; j++){
//                   System.out.print(mundo[i][j] + " ");
//               } 
//               System.out.println("");
//            }
            ArrayList<String> acciones = calcularAccion();
            
            for (String accion : acciones) {
                json = new JsonObject();
                json.add("command", "execute");
                json.add("action", accion);
                json.add("key", key);
                String resultado = json.toString();

                //Enviar la accion
                out.setContent(resultado);
                
//                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(MyDrone.class.getName()).log(Level.SEVERE, null, ex);
//                }
                              
                
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
        
        
        
        memoria = new int[width+4][width+4];
        this.mundo = new int[width+4][width+4];
        thermal = new double[width+4][width+4];

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
        Info(answer);
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
                    compass = (int) j.asObject().get("data").asArray().get(0).asDouble();
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
            if (j.asObject().get("sensor").asString().equals("visual")) {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if((position[0] - 3 + i)>=0 && (position[1] - 3 + k) >= 0)
                        mundo[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asInt();
                    }
                }
            }

            if (j.asObject().get("sensor").asString().equals("lidar")) {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if((position[0] - 3 + i)>=0 && (position[1] - 3 + k) >= 0)
                        lidar[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asInt();
                    }
                }
            }

            if (j.asObject().get("sensor").asString().equals("thermal")) {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if((position[0] - 3 + i)>=0 && (position[1] - 3 + k) >= 0)
                        thermal[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asDouble();
                    }
                }
            }

        }
        
        //Info("Casilla SUR, altura: " + mundo[position[0]+1][position[1]]);
        
    }

    private ArrayList<String> calculaGiroySubida(double angulo, int altura){
        ArrayList<String> acciones = new ArrayList<>();
        int anguloCasilla;
        double direccionGiro;
        int ngiros;
        int diferenciaAltura;
        //calcular cuanto tiene que girar
                direccionGiro = Math.abs((angulo+360)%360 - (compass+360)%360);
                ngiros = (int) Math.abs((angulo - compass) / 45);
                for(int i = 0; i < ngiros; i++){
                    if(direccionGiro >= 180){
                        acciones.add("rotateL");
                    }
                    else {
                        acciones.add("rotateR");
                    }
                }
                
                //Mirar la altura de la casilla
                Info("Altura: " + altura);
                diferenciaAltura = position[2] - (altura + 5);
                if( Math.abs(diferenciaAltura/5) == 0){
                    acciones.add("moveF");
                }
                for(int i = 0; i < Math.abs(diferenciaAltura/5); i++){
                    if(diferenciaAltura >= 0){
                        //Baja
                        if(i == 0)
                            acciones.add("moveF");
                        acciones.add("moveD");
                    } else {
                        //Sube 
                        acciones.add("moveUP");
                        if(i == (diferenciaAltura/5 - 1))
                            acciones.add("moveF");
                    }
                }
                
        
        return acciones;
    }
    
    private ArrayList<String> calcularAccion() {
        ArrayList acciones = new ArrayList<String>();

        //Hacia donde ir
        ArrayList<String> casillas = new ArrayList<>();
        ArrayList<Double> distancias = new ArrayList<>();

        diferenciaDistancias(casillas, distancias);
        
        burbuja(casillas, distancias);
        //Miramos si estamos encima del objetivo
        if (distance == 0){
            Info("Target encontrado ****************");
            //Bajamos a rescatarlo
            ArrayList<String> bajar = new ArrayList<>();
            //Bajar hasta altimetro = 5
            Info("El altimetro: " + altimeter);
            Info("Bajamos " + altimeter/5 + " veces");
            for (int i = 0; i < altimeter/5; i++) {
                //Info("**");
                bajar.add("moveD");
            }
            //Aterrizar (touchD)
            bajar.add("touchD");
            rescatado = true;
            
            return bajar;
        }
        

        
        //En orden, mirar que se pueda ir a la siguiente casilla
        String casilla = casillas.get(0);
        int anguloCasilla;
        double direccionGiro;
        int ngiros;
        int diferenciaAltura;
        switch(casilla){
            case "NO":
                anguloCasilla = -45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]-1][position[1]-1]);
                
                break;
            case "N":
                anguloCasilla = 0;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]-1][position[1]]);
                break;
            case "NE":
                anguloCasilla = 45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]+1][position[1]-1]);
                break;
            case "E":
                anguloCasilla = 90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]][position[1]+1]);
                break;
            case "SE":
                anguloCasilla = 135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]+1][position[1]+1]);
                break;
            case "S":
                anguloCasilla = 180;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]+1][position[1]]);
                break;
            case "SO":
                anguloCasilla = -135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]-1][position[1]+1]);
                break;
            case "O":
                anguloCasilla = -90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, mundo[position[0]][position[1]-1]);
                break;
        }
        
        //Mirar si hay que recarga batería antes de realizar las acciones
        Info("Coste: " + coste(acciones));
        Info("Queda energia: " + energy);
        
        for(int i = 0; i < acciones.size(); i++){
                System.out.print(acciones.get(i) + ", ");
            }

        
        if (energy_u > (energy - coste(acciones))) {
            ArrayList<String> bajar = new ArrayList<>();
            //Bajar hasta altimetro = 5
            Info("El altimetro: " + altimeter);
            Info("Bajamos " + altimeter/5 + " veces");
            for (int i = 0; i < altimeter/5; i++) {
                //Info("**");
                bajar.add("moveD");
            }
            //Aterrizar (touchD)
            bajar.add("touchD");
            //recharge
            Info("Recarga bateria");
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
        //TODO meter solo las casillas disponibles: cuando este en los bordes que no lo haga
        if (position[0] > 0 && position[1] > 0){
            casillas.add("NO");
            if(memoria[position[0]-1][position[1]-1] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]-1][position[1]-1] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (-45)));
            }
        }
            
        
        if (position[1] > 0){
            casillas.add("N");
            if(memoria[position[0]][position[1]-1] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]][position[1]-1] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular));
            }
        }
            
        
        if (position[1] > 0 && position[0] < (width - 1)){
            casillas.add("NE");
            if(memoria[position[0]+1][position[1]-1] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]+1][position[1]-1] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
               distancias.add(Math.abs((double) angular - (45)));
            }
        }
            
       
        if (position[0] < (width - 1)){
            casillas.add("E");
            if(memoria[position[0]+1][position[1]] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]+1][position[1]] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (90)));
            }
        }
            
        
        if (position[0] < (width - 1) && position[1] < (width - 1)){
            casillas.add("SE");
            if(memoria[position[0]+1][position[1]+1] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]+1][position[1]+1] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (135)));
            }
        }
            
        
        if (position[1] < (width - 1)){
            casillas.add("S");
            if(memoria[position[0]][position[1]+1] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]][position[1]+1] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (180)));
            }
        }
            
        
        if (position[1] < (width - 1) && position[0] > 0){
            casillas.add("SO");
            if(memoria[position[0]-1][position[1]+1] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]-1][position[1]+1] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (-135)));
            }
        }
            
        
        if (position[0] > 0){
            casillas.add("O");
            if(memoria[position[0]-1][position[1]] == 1){
                distancias.add(10000.0);
            }else if(mundo[position[0]-1][position[1]] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (-90)));
            }
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
