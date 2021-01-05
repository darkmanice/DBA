package practica3;

import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import ControlPanel.TTYControlPanel;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Stack;

/**
 * Agente que se dedica a rescatar.
 *
 * @author
 */
public abstract class Drone extends IntegratedAgent {

    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myConvID, myReplyWith, myCoach, myProblem;
    protected ArrayList<String> myShops, myWishlist, mySensors;
    protected boolean myError;
    protected Stack myCoins, auxCoins, myCharges;
    protected ACLMessage in, out;
    protected Map2DGrayscale myMap;
    private TTYControlPanel myControlPanel;

    protected int[] CoordInicio = new int[2];
    protected int altura_max = 255; //Comun a todos los mapas

    //Tiendas disponibles en el mundo
    protected HashMap<String, Integer> tienda0 = new HashMap<String, Integer>();
    protected HashMap<String, Integer> tienda1 = new HashMap<String, Integer>();
    protected HashMap<String, Integer> tienda2 = new HashMap<String, Integer>();
    
    //Sensores
    private int memoria[][]; //Se inicializa con width x width 0, 1 si ya hemos pasado
    private int position[] = new int[3]; //Posicion del drone
    private int lidar[][] = new int[7][7];
    private double thermal[][];
    /*private int visual[] = new int[7*7];    //Vector con los datos arrojados por visual
                                            //NO 16    //N 17  //NE 18
                                            //O 23     //D 24  //E 25
                                            //SO 30    //S 31  //SE 32*/

    private int compass = -90;
    private double angular;
    private int ontarget;
    private int alive;
    private double payload;
    private double distance;
    private int altimeter = 0;
    private int energy;
    
    
    //Variables para conteo de la memoria del dron
    private int iterador = 0;
    private int umbral_k = 800;
    
    //Umbral para recargar la batería
    private int energy_u = 20; 
    
    @Override
    public void setup() {
        _identitymanager = "Sphinx";
        super.setup();

        Info("Booting");

        // Description of my group
        myService = "Analytics group Cellnex";

        // First state of the agent
        myStatus = "CHECKIN-LARVA";

        //Agente coach
        myCoach = "Pantoja";

        //Shops
        myShops = new ArrayList<>();
        mySensors = new ArrayList<>();
        myWishlist = new ArrayList<>();

        //Coins
        myCoins = new Stack();
        auxCoins = new Stack();
        myCharges = new Stack();

        // To detect possible errors
        myError = false;
        myYP = new YellowPages();
        
        //Problem
        myProblem = "World1";
        myMap = new Map2DGrayscale();
        
        //Panel de control
        myControlPanel = new TTYControlPanel(this.getAID());
        
        
        _exitRequested = false;
    }

    @Override
    public void takeDown() {
        Info("Taking down");
        super.takeDown();
    }

    protected ACLMessage sendCheckinLARVA(String im) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(im, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        send(out);
        return blockingReceive();
    }

    protected ACLMessage sendCheckoutLARVA(String im) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(im, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.CANCEL);
        send(out);
        return blockingReceive();
    }

    protected ACLMessage sendYPQueryRef(String im) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(im, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.QUERY_REF);
        send(out);
        return blockingReceive();
    }

    protected ACLMessage sendSubscribeWM(String tipo) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setProtocol("REGULAR");
        out.setContent(new JsonObject().add("type", tipo).toString());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        out.setConversationId(myConvID);
        this.send(out);
        return this.blockingReceive();
    }

    protected ACLMessage getProductos(String shop) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(shop, AID.ISLOCALNAME));
        out.setProtocol("REGULAR");
        out.setContent("{}");
        out.setPerformative(ACLMessage.QUERY_REF);
        out.setConversationId(myConvID);
        this.send(out);
        return this.blockingReceive();
    }

    protected void comprar(ArrayList<String> miLista) {

        miLista.add("CHARGE");

        Info("Comprando...");

        //Por cada elemento de la lista de deseos
        for (String elemento : miLista) {
            boolean compraCarga = true;
            while (compraCarga) {
                if (!elemento.equals("CHARGE")) {
                    compraCarga = false;
                }
                //Actualizamos las tiendas
                updateShops();
                String eleccionTienda0 = "";
                String eleccionTienda1 = "";
                String eleccionTienda2 = "";

                int precioAuxiliar0 = 0;
                int precioAuxiliar1 = 0;
                int precioAuxiliar2 = 0;
                //Comprobamos en cada tienda cual es el articulo mas barato con ese nombre
                //TIENDA 0
                for (String i : tienda0.keySet()) {
                    String auxiliar = i.split("#")[0];
                    if (auxiliar.equals(elemento)) {
                        if (eleccionTienda0.equals("")) {
                            eleccionTienda0 = i;
                            precioAuxiliar0 = tienda0.get(i);
                        } else { //Hay que elegir el precio mas barato
                            if (precioAuxiliar0 > tienda0.get(i)) { //Intercambiamos el articulo
                                eleccionTienda0 = i;
                                precioAuxiliar0 = tienda0.get(i);
                            }
                        }
                    }
                }
                //TIENDA 1
                for (String i : tienda1.keySet()) {
                    String auxiliar = i.split("#")[0];
                    if (auxiliar.equals(elemento)) {
                        if (eleccionTienda1.equals("")) {
                            eleccionTienda1 = i;
                            precioAuxiliar1 = tienda1.get(i);
                        } else { //Hay que elegir el precio mas barato
                            if (precioAuxiliar1 > tienda1.get(i)) { //Intercambiamos el articulo
                                eleccionTienda1 = i;
                                precioAuxiliar1 = tienda1.get(i);
                            }
                        }
                    }
                }
                //TIENDA 2
                for (String i : tienda2.keySet()) {
                    String auxiliar = i.split("#")[0];
                    if (auxiliar.equals(elemento)) {
                        if (eleccionTienda2.equals("")) {
                            eleccionTienda2 = i;
                            precioAuxiliar2 = tienda2.get(i);
                        } else { //Hay que elegir el precio mas barato
                            if (precioAuxiliar2 > tienda2.get(i)) { //Intercambiamos el articulo
                                eleccionTienda2 = i;
                                precioAuxiliar2 = tienda2.get(i);
                            }
                        }
                    }
                }

                //Comparamos los elementos elegidos de las tres tiendas
                ArrayList<Integer> precios = new ArrayList<>();
                precios.add(precioAuxiliar0);
                precios.add(precioAuxiliar1);
                precios.add(precioAuxiliar2);

                int min = Integer.MAX_VALUE, indice = 0;
                for (int i = 0; i < precios.size(); i++) {
                    if (precios.get(i) < min) {
                        min = precios.get(i);
                        indice = i;
                    }
                }

                //Obtenemos la eleccion y enviamos accion de comprar
                String eleccionF = "", tiendaF = "";
                int precioF = -1;
                switch (indice) {
                    case 0:
                        eleccionF = eleccionTienda0;
                        tiendaF = myShops.get(0);
                        precioF = precioAuxiliar0;
                        break;
                    case 1:
                        eleccionF = eleccionTienda1;
                        tiendaF = myShops.get(1);
                        precioF = precioAuxiliar1;
                        break;
                    case 2:
                        eleccionF = eleccionTienda2;
                        tiendaF = myShops.get(2);
                        precioF = precioAuxiliar2;
                        break;
                }

                if (precioF <= myCoins.size() && eleccionF.contains("#")) {
                    in = sendComprar(eleccionF, tiendaF, precioF);
                } else {
                    auxCoins = new Stack();
                    Info("No se puede comprar o no quedan existencias");
                    compraCarga = false;
                    return;
                }

                //Guardar los productos que hemos comprado
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Compra de productos ha fallado por: " + getDetailsLARVA(in));
                    //Volvemos a llenar el monedero
                    for(int i = 0; i < auxCoins.size(); i++){
                        myCoins.push((String) auxCoins.pop() );
                    }
                    
                } else {
                    String producto = Json.parse(in.getContent()).asObject().get("reference").asString();
                    Info("producto: " +  producto);
                    if(producto.contains("CHARGE")){
                        Info("Meto el charge: "+producto);
                        this.myCharges.push(producto);
                    }
                    else{
                        this.mySensors.add(producto);
                    }
                    auxCoins = new Stack();
                }

            }
        }
    }

    protected ACLMessage sendComprar(String producto, String tienda, int precio) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(tienda, AID.ISLOCALNAME));
        JsonObject contenido = new JsonObject();
        contenido.add("operation", "buy").toString();
        contenido.add("reference", producto).toString();
        JsonArray array = new JsonArray();
        //Añadimos tantas monedas como cueste el elemento
        for (int i = 0; i < precio; i++) {
            String actualCoin = (String) myCoins.pop();
            
            array.add(actualCoin);
            auxCoins.push(actualCoin);
        }
        contenido.add("payment", array).toString();
        out.setContent(contenido.toString());
        out.setPerformative(ACLMessage.REQUEST);
        out.setConversationId(myConvID);
        this.send(out);
        return this.blockingReceive();
    }

    protected void sendLogoutCoach() {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myCoach, AID.ISLOCALNAME));
        out.setContent("LOGOUT");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }

    protected boolean updateShops() {

        tienda0 = new HashMap<String, Integer>();
        tienda1 = new HashMap<String, Integer>();
        tienda2 = new HashMap<String, Integer>();

        in = getProductos(myShops.get(0));
        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                    + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
            myStatus = "CHECKOUT-LARVA";
            return false;
        }
        //System.out.println(in.getContent());

        //Pasamos el content a HashMap
        for (JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()) {
            tienda0.put(j.asObject().get("reference").asString(), j.asObject().get("price").asInt());

        }

        in = getProductos(myShops.get(1));
        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                    + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
            myStatus = "CHECKOUT-LARVA";
            return false;
        }

        //System.out.println(in.getContent());
        //Pasamos el content a HashMap
        for (JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()) {
            tienda1.put(j.asObject().get("reference").asString(), j.asObject().get("price").asInt());

        }

        in = getProductos(myShops.get(2));
        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                    + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
            myStatus = "CHECKOUT-LARVA";
            return false;
        }

        //System.out.println(in.getContent());
        //Pasamos el content a HashMap
        for (JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()) {
            tienda2.put(j.asObject().get("reference").asString(), j.asObject().get("price").asInt());
        }

        return true;
    }

    protected void inicializarSensores( Map2DGrayscale mapa){
        //Captamos el mapa
        myMap = mapa;
        
        //Sensores
        
        memoria = new int[myMap.getWidth()+4][myMap.getWidth()+4];
        thermal = new double[myMap.getWidth()+4][myMap.getWidth()+4];

        //Inicializar las matrices
        for (int i = 0; i < myMap.getWidth()+1; i++) {
            for (int j = 0; j < myMap.getWidth()+1; j++) {
                memoria[i][j] = -umbral_k;
                thermal[i][j] = -1;
            }
        }
    }
    
    protected ACLMessage sendLoginProblem() {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setProtocol("REGULAR");
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.REQUEST);
        JsonObject contenido = new JsonObject();
        contenido.add("operation", "login");
        JsonArray array = new JsonArray();
        //Añadimos tantas monedas como cueste el elemento
        for (int i = 0; i < this.mySensors.size(); i++) {
            array.add((mySensors.get(i)));
        }
        contenido.add("attach", array).toString();
        contenido.add("posx", CoordInicio[0]);
        contenido.add("posy", CoordInicio[1]);
        out.setContent(contenido.toString());

        this.send(out);
        return this.blockingReceive();
    }
    
    protected void readSensores() {
        out = in.createReply();
        JsonObject json = new JsonObject();
        json.add("operation", "read");
        String resultado = json.toString();
        out.setConversationId(myConvID);
        out.setContent(resultado);
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_REF);
        this.send(out);

        in = this.blockingReceive();
        String answer = in.getContent();
        Info(answer);
        json = Json.parse(answer).asObject();
        out = in.createReply();

        //Info("La lectora de sensores es: " + answer);
        myControlPanel.feedData(in, myMap.getWidth(), myMap.getHeight(), myMap.getMaxHeight());
        myControlPanel.fancyShow();
        //myControlPanel.fancyShowMicro();

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
                    memoria[position[0]][position[1]] = iterador;
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
            

            if (j.asObject().get("sensor").asString().equals("lidar")) {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if ((position[0] - 3 + i) >= 0 && (position[1] - 3 + k) >= 0) {
                            lidar[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asInt();
                        }
                    }
                }
            }

            if (j.asObject().get("sensor").asString().equals("thermal")) {
                for (int i = 0; i < 7; i++) {
                    for (int k = 0; k < 7; k++) {
                        if ((position[0] - 3 + i) >= 0 && (position[1] - 3 + k) >= 0) {
                            thermal[position[0] - 3 + i][position[1] - 3 + k] = j.asObject().get("data").asArray().get(i).asArray().get(k).asDouble();
                        }
                    }
                }
            }

        }

    }
    
    protected boolean elevar() {
       Info("Bateria: "+ energy);
        while (position[2] < this.altura_max) {
            Info("Subiendo");
            if (energy_u > (energy - 5)) {
                if(!recarga()){
                    return false;
                }
            }
            position[2] += 5;
            in = sendAction("moveUP");

            myError = (in.getPerformative() != ACLMessage.INFORM);
            if (myError) {
                Info(ACLMessage.getPerformative(in.getPerformative())
                        + " No se pudo hacer moveU en " + this.myWorldManager
                        + " debido a " + getDetailsLARVA(in));
                return false;
            }
            altimeter += 5;
        }
        return true;
    }

    protected ACLMessage sendAction(String accion){
        JsonObject contenido = new JsonObject();
        contenido.add("operation", accion);
        out.setContent(contenido.toString());
        out.setConversationId(myConvID);
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        
        this.send(out);
        return this.blockingReceive();
    }
    
    protected boolean recarga(){
        //Bajar hasta altimetro = 5
        //Info("El altimetro: " + altimeter);
        //Info("Bajamos " + altimeter/5 + " veces");
        Info("Recargando...");
        for (int i = 0; i < position[2] / 5; i++) {
            if(altimeter > 0){
                in = sendAction("moveD");
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo hacer moveD en " + this.myWorldManager
                            + " debido a " + getDetailsLARVA(in));
                    return false;
                }
                altimeter -= 5;
            }
           
        }
        //Aterrizar (touchD)
        sendAction("touchD");
        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " No se pudo hacer touchD en " + this.myWorldManager
                    + " debido a " + getDetailsLARVA(in));
            return false;
        }
        //recharge
        Info("Recarga bateria");
        in = sendRecharge();

        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " No se pudo hacer recharge en " + this.myWorldManager
                    + " debido a " + getDetailsLARVA(in));
            return false;
        }
        //actualizamos manualmente la energia
        energy = 1000;


        return true;
    }
    
    protected ACLMessage sendRecharge(){
        JsonObject contenido = new JsonObject();
        contenido.add("operation", "recharge");
        contenido.add("recharge", (String) myCharges.pop());
        out.setContent(contenido.toString());
        out.setConversationId(myConvID);
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        
        this.send(out);
        return this.blockingReceive();
    }
    
    protected ArrayList<String> calcularAccionesPosibles(){
        ArrayList<String> acciones = new ArrayList<>();
        
        //Hacia donde ir
        ArrayList<String> casillas = new ArrayList<>();
        ArrayList<Double> distancias = new ArrayList<>();
        
        diferenciaDistancias(casillas, distancias);
        
        burbuja(casillas, distancias);
        
        //Miramos si estamos encima del objetivo
        if (distance == 0){
            Info("Target encontrado");
            //PRUEBA
            acciones.add("FIN");
        }
        
        //En orden, mirar que se pueda ir a la siguiente casilla
        String casilla = casillas.get(0);
        
        //Info("Voy a casilla " + casilla);
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
                acciones = calculaGiroySubida(anguloCasilla);
                
                break;
            case "N":
                anguloCasilla = 0;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
            case "NE":
                anguloCasilla = 45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
            case "E":
                anguloCasilla = 90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
            case "SE":
                anguloCasilla = 135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
            case "S":
                anguloCasilla = 180;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
            case "SO":
                anguloCasilla = -135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
            case "O":
                anguloCasilla = -90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla);
                break;
        }
        
        //TODO: Mirar si hay que recarga batería antes de realizar las acciones
        
        return acciones;
    }
    
    protected void diferenciaDistancias(ArrayList<String> casillas, ArrayList<Double> distancias){
        if (position[0] < (myMap.getWidth() - 1)){
            casillas.add("E");
            if(iterador - memoria[position[0]+1][position[1]] < umbral_k){
                distancias.add(10000.0);
            } else {
                distancias.add(Math.abs((double) (angular + 360) % 360 - (90)));
            }
        }
        if (position[0] < (myMap.getWidth() - 1) && position[1] < (myMap.getWidth() - 1)) {
            casillas.add("SE");
            if (iterador - memoria[position[0] + 1][position[1] + 1] < umbral_k) {
                distancias.add(10000.0);
            } else {
                distancias.add(Math.abs((double) (angular + 360) % 360 - (135)));
            }
        }
        
        if (position[1] < (myMap.getWidth() - 1)){
            casillas.add("S");
            if(iterador - memoria[position[0]][position[1]+1] < umbral_k){
                distancias.add(10000.0);
            }
            else {
                distancias.add(Math.abs((double) (angular+360)%360 - (180)));
            }
        }
        
        if (position[0] > 0 && position[1] > 0){
            casillas.add("NO");
            if(iterador - memoria[position[0]-1][position[1]-1] < umbral_k){
                distancias.add(10000.0);
            }
            else {
                distancias.add(Math.abs((double) (angular +360)%360 - (315)));
            }
        }
        
        if (position[1] > 0){
            casillas.add("N");
            if(iterador - memoria[position[0]][position[1]-1] < umbral_k){
                distancias.add(10000.0);
            }
            else {
                distancias.add(Math.abs((double) angular));
            }
        }
        
        if (position[1] > 0 && position[0] < (myMap.getWidth() - 1)){
            casillas.add("NE");
            if(iterador - memoria[position[0]+1][position[1]-1] < umbral_k){
                distancias.add(10000.0);
            }
            else {
               distancias.add(Math.abs((double) (angular+360)%360 - (45)));
            }
        }
        
        if (position[1] < (myMap.getWidth() - 1) && position[0] > 0){
            casillas.add("SO");
            if(iterador - memoria[position[0]-1][position[1]+1] < umbral_k){
                distancias.add(10000.0);
            }
            else {
                distancias.add(Math.abs((double) (angular+360)%360 - (225)));
            }
        }
        
        if (position[0] > 0){
            casillas.add("O");
            if(iterador - memoria[position[0]-1][position[1]] < umbral_k){
                distancias.add(10000.0);
            }
            else {
                distancias.add(Math.abs((double) (angular+360)%360 - (270)));
            }
        }
    }
    
    protected void burbuja(ArrayList<String> casillas, ArrayList<Double> distancias) {
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

                    distancias.set(i, distancias.get(i + 1));
                    distancias.set(i + 1, temp);

                    casillas.set(i, casillas.get(i + 1));
                    casillas.set(i + 1, temps);

                    flag = true;
                }
            }
        }
    }

    private ArrayList<String> calculaGiroySubida(double angulo) {
        ArrayList<String> acciones = new ArrayList<>();
        int anguloCasilla;
        double direccionGiro;
        int ngirosD = 0;
        int ngirosI = 0;
        int anguloAux = (compass + 360) % 360;
        int diferenciaAltura;
        //calcular cuanto tiene que girar
        while (anguloAux != (angulo + 360) % 360) {
            ngirosD++;
            anguloAux = (anguloAux + 45) % 360;
        }

        anguloAux = (compass + 360) % 360;

        while (anguloAux != (angulo + 360) % 360) {
            ngirosI++;
            anguloAux = (anguloAux - 45 + 360) % 360;
        }

        if (ngirosD < ngirosI) {
            for (int i = 0; i < ngirosD; i++) {
                acciones.add("rotateR");
            }
        } else {
            for (int i = 0; i < ngirosI; i++) {
                acciones.add("rotateL");
            }
        }

        acciones.add("moveF");

        return acciones;
    }
}
