package practica3;

import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import ControlPanel.TTYControlPanel;
import Geometry.Point;
import Geometry.Vector;
import World.Compass;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Stack;

/**
 * Agente abstracto que implementa operaciones que puede utilizar todo el equipo de rescate
 *
 * @author Marina: implementación
 * @author Román: implementación
 * @author Javier: implementación
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
    protected int altura_max = 0; //Comun a todos los mapas
    protected String myName;

    //Tiendas disponibles en el mundo
    protected HashMap<String, Integer> tienda0 = new HashMap<String, Integer>();
    protected HashMap<String, Integer> tienda1 = new HashMap<String, Integer>();
    protected HashMap<String, Integer> tienda2 = new HashMap<String, Integer>();
    
    //Sensores
    protected int memoria[][]; //Se inicializa con width x width 0, 1 si ya hemos pasado
    protected int position[] = new int[3]; //Posicion del drone
    protected int lidar[][] = new int[7][7];
    protected double thermal[][];

    protected int compass = -90;
    protected double angular;
    protected int ontarget;
    protected int alive;
    protected double payload;
    protected double distance;
    protected int altimeter = 0;
    protected int energy;
    
    
    //Variables para conteo de la memoria del dron
    protected int iterador = 0;
    private int umbral_k = 800;
    
    //Umbral para recargar la batería
    protected int energy_u = 50; 
    
    /**
     * Setup para inicializar nuestras variables.
     * 
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     */
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
        myProblem = "World6";
        myMap = new Map2DGrayscale();
        
        //Panel de control
        myControlPanel = new TTYControlPanel(this.getAID());
        
        
        _exitRequested = false;
    }

    /**
     * Metodo para terminar la ejecución del programa
     *
     * @author Marina
     * @author Román
     * @author Javier
     */
    @Override
    public void takeDown() {
        Info("Taking down");
        super.takeDown();
    }

    /**
     * Metodo para enviar el mensaje de suscripción a Sphinx
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param im String con el nombre del agente
     * @return respuesta al mensaje
     */
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

    /**
     * Metodo para enviar el mensaje de desuscripción a Sphinx
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @return respuesta al mensaje
     * @param im String con el nombre del agente
     * @return respuesta al mensaje
     */
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

    /**
     * Metodo para enviar el mensaje de petición de YP
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @return respuesta al mensaje
     * @param im String con el nombre del agente
     * @return respuesta al mensaje
     */
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

    /**
     * Metodo para enviar el mensaje de suscripción al WM como tipo
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param tipo String con el tipo de agente = {LISTENER, RESCUER, SEEKER}
     * @return respuesta al mensaje
     */
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

    /**
     * Metodo para enviar el mensaje a una tienda y que nos devuelva su lista de productos
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param shop String con el nombre de la tienda
     * @return respuesta al mensaje
     */
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

     /**
     * Algoritmo para gestionar las compras, busca el producto mas barato de cada tienda y el mas barato de las tres tiendas
     * @author Marina
     * @author Román
     * @author Javier
     * @param miLista ArrayList<String> con la lista de la compra
     */
    protected void comprar(ArrayList<String> miLista) {

        //Compramos un primer charge
        miLista.add("CHARGE");

        Info("Comprando...");

        //Por cada elemento de la lista de deseos
        for (String elemento : miLista) {
            boolean compraCarga = true;
  
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
                    
                    if(producto.contains("CHARGE")){
                       
                        this.myCharges.push(producto);
                    }
                    else{
                        this.mySensors.add(producto);
                    }
                    auxCoins = new Stack();
                }
        }
    }

     /**
     * Metodo para enviar el mensaje de compra
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param producto String con la referencia del producto
     * @param tienda String con el nombre de la tienda
     * @param precio int con el numero de coins a gastar
     * @return respuesta al mensaje
     */
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

     /**
     * Metodo para enviar un mensaje al controlador de que nos vamos del programa
     * 
     * @author Marina
     * @author Román
     * @author Javier
     */
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

     /**
     * Metodo para actualizar las tiendas y guardarlas en tres hash map globales
     * 
     * @author Marina
     * @author Román
     * @author Javier
     * @return exito/fracaso de la operacion
     */
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

        //Pasamos el content a HashMap
        for (JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()) {
            tienda2.put(j.asObject().get("reference").asString(), j.asObject().get("price").asInt());
        }

        return true;
    }

     /**
     * Metodo para inicializar sensores
     * 
     * @author Marina
     * @author Román
     * @author Javier
     * @param mapa Mapa del mundo
     */
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
    
    /**
     * Metodo para enviar el mensaje de suscripción al WM 
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @return respuesta al mensaje
     */
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
    
    /**
     * Metodo para leer los sensores y almacenarlos en sus variables globales correspondientes
     *
     * @author Marina
     * @author Román
     * @author Javier
     */
    protected void readSensores() {
        out = new ACLMessage();
        JsonObject json = new JsonObject();
        json.add("operation", "read");
        String resultado = json.toString();
        out.setConversationId(myConvID);
        out.setSender(this.getAID());
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setContent(resultado);
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_REF);
        this.send(out);

        in = this.blockingReceive();
        
        
        String answer = in.getContent();
        json = Json.parse(answer).asObject();
        
        if(!answer.contains("perceptions")){
            in = this.blockingReceive();
            answer = in.getContent();
            json = Json.parse(answer).asObject();
        }

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
                    break;
                case ("energy"):
                    energy = j.asObject().get("data").asArray().get(0).asInt();
                    break;
                case ("altimeter"):
                    altimeter = j.asObject().get("data").asArray().get(0).asInt();
                    break;
            }
        }

        //Rellenar lidar y thermal tras leer el resto de sonsores
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
        
        //Calculo del altimetro
        altimeter = position[2] - myMap.getLevel(position[0], position[1]);

    }
    
    /**
     * Metodo para elevar un dron a maxima altura del mundo
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param agent String con el nombre del agente
     * @return exito/fracaso de la operacion
     */
    protected boolean elevar() {
        while (position[2] < this.altura_max) {
            if (energy_u > (energy - 5)) {
                Info("No tengo bateria para elevar, recargo");
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
            //Cada vez que subimos restamos energia
            energy -= 5;
        }
        return true;
    }

    /**
     * Metodo para enviar un mensaje con una accion de movimiento determinada
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param accion String con el nombre de la acción
     * @return respuesta al mensaje
     */
    protected ACLMessage sendAction(String accion){
        out = new ACLMessage();
        JsonObject contenido = new JsonObject();
        contenido.add("operation", accion);
        out.setContent(contenido.toString());
        out.setConversationId(myConvID);
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        
        this.send(out);
        return this.blockingReceive();
    }
    
    /**
     * Metodo para recargar bateria, compramos un ticket de recarga y enviamos mensaje
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @return exito/fracaso de la operacion
     */
    protected boolean recarga(){
        if(myCharges.isEmpty()){
            ArrayList<String> comprarCharges = new ArrayList<>();
            comprarCharges.add("CHARGE");
            comprar(comprarCharges);
        }

        Info("Recargando...");
        int aux = altimeter;
        int aux2 = energy;
        
        
        for (int i = 0; i < aux / 5; i++) {
            if(aux > 0){
                in = sendAction("moveD");
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo hacer moveD en " + this.myWorldManager
                            + " debido a " + getDetailsLARVA(in));
                    return false;
                }
                //Restamos bateria manualmente
                //energy -= 5;
            }
        }
        
        in = sendAction("touchD");
        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " No se pudo hacer touchD en " + this.myWorldManager
                    + " debido a " + getDetailsLARVA(in));
            return false;
        }
        
        
        //recharge
        in = sendRecharge();

        myError = (in.getPerformative() != ACLMessage.INFORM);
        if (myError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " No se pudo hacer recharge en " + this.myWorldManager
                    + " debido a " + getDetailsLARVA(in));
            return false;
        }

        return true;
    }
    
    /**
     * Metodo para enviar el mensaje de recarga al servidor
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @return respuesta al mensaje
     */
    protected ACLMessage sendRecharge(){
        out = new ACLMessage();
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
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
    
    /**
     * Metodo para enviar al controlador un mensaje de que hemos acabado de comprar
     *
     * @author Marina
     * @author Román
     * @author Javier
     */
    protected void sendFinCompra(){
        ACLMessage outPantoja = new ACLMessage();
        outPantoja.setSender(this.getAID());
        outPantoja = new ACLMessage();
        outPantoja.setContent("");
        outPantoja.addReceiver(new AID("Pantoja", AID.ISLOCALNAME));
        outPantoja.setProtocol("REGULAR");
        outPantoja.setPerformative(ACLMessage.INFORM);
        
        this.send(outPantoja);
        
    }
    
    /**
     * Metodo para calcular el coste de un array de acciones
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param acciones ArrayList<String> vector de acciones
     * @return coste de las acciones + coste de leer los sensores
     */
    protected int coste(ArrayList<String> acciones){
        int coste = mySensors.size();
       
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
    
    /**
     * Metodo reutilizado de la P2, calcula la diferencia de distancias y las almacena en un array
     * 
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     * @param casillas casillas a las que el drone puede ir
     * @param distancias distancias de las casillas a las que el drone puede ir
     */
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
    
    /**
     * Metodo reutilizado de la P2, metodo para ordenar un array de menor a mayor
     * 
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     * @param casillas Array con las casillas a las que el drone puede ir
     * @param distancias Array con las distancias a las casillas que el drone puede ir
     */
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

    /**
     * Método reutilizado de la P2, calcula el numero de giros y subidas o bajadas que debe hacer el agente para movernos segun el angulo y la altura de la siguiente casilla a visitar
     * 
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     * @param angulo angulo en grados al que queremos girar el drone
     * @param altura altura de la casilla a la que apunta el grado
     * @return array de acciones a ejecutar
     */
    protected ArrayList<String> calculaGiroySubida(double angulo, int altura) {
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
     * Metodo que dada una posicion calcula y ejecuta las acciones que nos llevarán a ese punto
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     */
    protected void irA(int destinox, int destinoy) {
        double distancia;
        double angulo;
        ArrayList<String> actions = new ArrayList<>();
        
        if (2*energy_u > energy ) {
            recarga();
            energy = 1000;
        }
        
        int anguloCasilla;
        Point orig = new Point(destinox,destinoy);
        Point here;
        
        //Leer sensores por primera vez
        readSensores();
        do{
            //Calculamos el angulo segun el destino
            here = new Point(position[0], position[1]);
            distancia = here.fastDistanceXYTo(orig);
             
            angulo = Compass.VECTOR[Compass.NORTH].angleXYTo(new Vector(here,orig));
            
            //Calculamos la casilla segun el angulo obtenido
            if(angulo >= (-45 - 22.5) && angulo < (-45 + 22.5)){
                anguloCasilla = -45;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]-1, position[1]-1)); 
            }
            if(angulo >= (0 - 22.5) && angulo < (45 - 22.5)){
                anguloCasilla = 0;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0], position[1]-1));
            }
            if(angulo >= (45 - 22.5) && angulo < (90 - 22.5)){
                anguloCasilla = 45;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]+1, position[1]-1));
            }
            if(angulo >= (90 - 22.5) && angulo < (135 - 22.5)){
                anguloCasilla = 90;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]+1, position[1]));  
            }
            if(angulo >= (135 - 22.5) && angulo < (180 - 22.5)){
                anguloCasilla = 135;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]+1, position[1]+1));
            }
            if(angulo >= (180 - 22.5) && angulo < (-135 - 22.5)){
                anguloCasilla = 180;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0], position[1]+1));
            }
            if(angulo < (-135 + 22.5) && angulo >= (-135 - 22.5)){
                anguloCasilla = -135;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]-1, position[1]+1));
            }
            if(angulo >= (-90 - 22.5) && angulo < (-90 + 22.5)){
                anguloCasilla = -90;
                //Calculamos cuanto tiene que girar, subir o bajar
                actions = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]-1, position[1]));  
            }
            
            //Mirar si hay que recarga batería antes de realizar las acciones
            if (energy_u > (energy - coste(actions) - altimeter)){
                //Recargamos energia y volvemos a subir
                recarga();
                energy = 1000;
               // elevar();
            }
            else{
                energy -= coste(actions);
            }
            
            //Para cada accion, enviar mensaje al servidor
            for(String a : actions){
                in = sendAction(a);
            }
            //Actualizar sensores
            readSensores();
            energy -= mySensors.size();
        }while(position[0] != destinox || position[1] != destinoy);
        

    }
}
