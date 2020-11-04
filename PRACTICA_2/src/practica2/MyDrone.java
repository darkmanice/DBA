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
    private int visual[] = new int[7*7];    //Vector con los datos arrojados por visual
                                            //NO 16    //N 17  //NE 18
                                            //O 23     //D 24  //E 25
                                            //SO 30    //S 31  //SE 32

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
        String attach[] = {"alive", "distance", "gps", "visual", "angular", "compass", "energy"};
        nSensores = attach.length;

        //Iniciar sesion en el mundo
        ACLMessage in = login(out, "World9", attach);

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
    /**
     * 
     * @author Marina
     * @author Román
     * @author Javier
     * @author Alejandro
     * @param out ACLMessage de salida
     * @param mundo mundo al que nos queremos conectar
     * @param sensores Cadena de los sensores que queremos conectar al drone
     * @return 
     */
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

    /**
     * @author Marina
     * @author Román
     * @author Javier
     * @author Alejandro
     * @param out ACLMessage de salida
     * @param in ACLMessage de entrada
     */
    private void readSensores(ACLMessage out, ACLMessage in) {
        JsonObject json = new JsonObject();
        json.add("command", "read");
        json.add("key", key);
        String resultado = json.toString();
        out.setContent(resultado);
        this.send(out);

        in = this.blockingReceive();
        String answer = in.getContent();
        //Info(answer);
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
                    //Info("Distancia: " + distance);
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
                  for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        visual[7*i+k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asInt();
                    }
                }
                  
                  //Calculo de altumetro
                  altimeter = position[2] - visual[24];
                  
                    
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
        
    }

    /**
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     * @param angulo angulo en grados al que queremos girar el drone
     * @param altura altura de la casilla a la que apunta el grado
     * @return 
     */
    private ArrayList<String> calculaGiroySubida(double angulo, int altura){
        ArrayList<String> acciones = new ArrayList<>();
        int anguloCasilla;
        double direccionGiro;
        int ngirosD=0;
        int ngirosI=0;
        int anguloAux = (compass+360)%360;
        int diferenciaAltura;
        //calcular cuanto tiene que girar
                  while( anguloAux != (angulo+360)%360 ){
                      ngirosD++;
                      anguloAux = (anguloAux+45)%360;
                  }
                  
                  anguloAux = (compass+360)%360;
                  
                  while( anguloAux != (angulo+360)%360 ){
                      ngirosI++;
                      anguloAux = (anguloAux-45+360)%360;
                  }
                  
                  if(ngirosD < ngirosI){
                      for(int i = 0; i < ngirosD; i++){
                          acciones.add("rotateR");
                      }
                  }
                  else{
                      for(int i = 0; i < ngirosI; i++){
                          acciones.add("rotateL");
                      }
                  }
                
                //Mirar la altura de la casilla
                //Info("La altura de la casilla es " + altura);
                //Info("La altura máxima de vuelo es: " + maxflight);
                //Info("La altura del agente antes de las acciones es: " + position[2]);
                diferenciaAltura = position[2] - (altura + 1);
                if( Math.abs(diferenciaAltura/5) < 1){
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
                        if(i == (Math.abs(diferenciaAltura/5) - 1))
                            acciones.add("moveF");
                    }
                }
                
        
        return acciones;
    }
    
    /**
     * Calcula la siguiente acción (o cadena de acciones) que debe realizar el drone
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     * @return 
     */
    private ArrayList<String> calcularAccion() {
        ArrayList acciones = new ArrayList<String>();

        //Hacia donde ir
        ArrayList<String> casillas = new ArrayList<>();
        ArrayList<Double> distancias = new ArrayList<>();

        diferenciaDistancias(casillas, distancias);
        
        burbuja(casillas, distancias);
        //Miramos si estamos encima del objetivo
        if (distance == 0){
            Info("Target encontrado");
            //Bajamos a rescatarlo
            ArrayList<String> bajar = new ArrayList<>();
            //Bajar hasta altimetro = 5
            //Info("El altimetro: " + altimeter);
            //Info("Bajamos " + altimeter/5 + " veces");
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
        //Segun a la casilla a la que el drone decida ir:
        //Info("Tengo que ir a la casilla " + casilla + "////////////");
        switch(casilla){
            case "NO":
                anguloCasilla = -45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[16]);
                
                break;
            case "N":
                anguloCasilla = 0;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[17]);
                break;
            case "NE":
                anguloCasilla = 45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[18]);
                break;
            case "E":
                anguloCasilla = 90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[25]);
                break;
            case "SE":
                anguloCasilla = 135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[32]);
                break;
            case "S":
                anguloCasilla = 180;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[31]);
                break;
            case "SO":
                anguloCasilla = -135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[30]);
                break;
            case "O":
                anguloCasilla = -90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, visual[23]);
                break;
        }

        //Mirar si hay que recarga batería antes de realizar las acciones
        if (energy_u > (energy - coste(acciones))) {
            ArrayList<String> bajar = new ArrayList<>();
            //Bajar hasta altimetro = 5
            //Info("El altimetro: " + altimeter);
            //Info("Bajamos " + altimeter/5 + " veces");
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
            //bajar.add("moveUP");
            
            return bajar;
        }
        
        String cadenaAcciones = "";
        
        for(int i = 0; i < acciones.size(); i++){
                cadenaAcciones += acciones.get(i) + ", ";
            }
        //Info("El drone va a realizar: " + cadenaAcciones);

        return acciones;
    }
    /**
     * @author Marina: formación
     * @author Román: implementación
     * @author Javier: modularización de la funcionalidad
     * @param casillas Array con las casillas a las que el drone puede ir
     * @param distancias Array con las distancias a las casillas que el drone puede ir
     */
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
    /**
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: Modularizacion de la funcionalidad
     * @param casillas casillas a las que el drone puede ir
     * @param distancias distancias de las casillas a las que el drone puede ir
     */
    private void diferenciaDistancias(ArrayList<String> casillas, ArrayList<Double> distancias){
        //TODO meter solo las casillas disponibles: cuando este en los bordes que no lo haga
        if (position[0] > 0 && position[1] > 0){
            casillas.add("NO");
            if(memoria[position[0]-1][position[1]-1] == 1){
                distancias.add(10000.0);
            }else if(visual[16] >= maxflight) {
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
            }else if(visual[17] >= maxflight) {
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
            }else if(visual[18] >= maxflight) {
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
            }else if(visual[25] >= maxflight) {
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
            }else if(visual[32] >= maxflight) {
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
            }else if(visual[31] >= maxflight) {
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
            }else if(visual[30] >= maxflight) {
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
            }else if(visual[23] >= maxflight) {
                //Si esta casilla es mas alta que maxflight
                distancias.add(Double.POSITIVE_INFINITY);
            }
            else {
                distancias.add(Math.abs((double) angular - (-90)));
            }
        }
    }
    
    /**
     * @autor Roman
     * @param acciones lista de acciones que ejecuta el drone
     * @return 
     */
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
