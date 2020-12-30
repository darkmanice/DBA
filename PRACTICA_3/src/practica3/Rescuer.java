package practica3;

import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
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
 * @author 
 */
public class Rescuer extends IntegratedAgent {

    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myConvID, myReplyWith;
    protected ArrayList<String> myShops, myWishlist;
    protected boolean myError;
    protected Stack myCoins;
    protected ACLMessage in, out;
    protected Map2DGrayscale myMap;
    protected HashMap<String, Integer> tienda0 = new HashMap<String, Integer>();
    protected HashMap<String, Integer> tienda1 = new HashMap<String, Integer>();
    protected HashMap<String, Integer> tienda2 = new HashMap<String, Integer>();

    @Override
    public void setup() {
        _identitymanager = "Sphinx";
        super.setup();

        Info("Booting");

        // Description of my group
        myService = "Analytics group Cellnex";

        // First state of the agent
        myStatus = "CHECKIN-LARVA";
        
        //Shops
        myShops = new ArrayList<>();
        
        //Lista de articulos deseados
        myWishlist = new ArrayList<>();
        myWishlist.add("LIDAR");
        
        //Coins
        myCoins = new Stack();

        // To detect possible errors
        myError = false;
        myYP = new YellowPages();

        _exitRequested = false;
    }

    @Override
    public void plainExecute() {
        switch (myStatus.toUpperCase()) {
            case "CHECKIN-LARVA":
                Info("Haciendo el checkin en LARVA con " + _identitymanager);
                in = sendCheckinLARVA(_identitymanager);
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " El checkin ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "EXIT";
                    break;
                }
                myStatus = "GETYP";
                break;
                
            case "GETYP":
                Info("Petición de las Yellow Pages.");
                in = sendYPQueryRef(_identitymanager);
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de YP ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                //Mostrar las YP
                myYP.updateYellowPages(in);
               // System.out.print(myYP.prettyPrint());

                if (myYP.queryProvidersofService(myService).isEmpty()) {
                    Info("\t" + "No hay ningun agente que proporcione el servicio: " + myService);
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                //Cogemos el World Manager de la lista de servicios
                myWorldManager = myYP.queryProvidersofService(myService).iterator().next();
                Info("Cogemos el WorldManager");
                myStatus = "WAITING";
                break;
            
            case "WAITING":
                Info("Esperando SESSIONID");
                in = blockingReceive();
                if(in.getPerformative() == ACLMessage.QUERY_IF){
                    myConvID = in.getContent();
                    myStatus = "SUBSCRIBE-WM";
                }
                break;
                
            case "SUBSCRIBE-WM":
                in = sendSubscribeWM("RESCUER");

                myError = in.getPerformative() != ACLMessage.INFORM;
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo abrir sesion "
                            + myWorldManager + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                Info("RESCUER suscrito al WM");
                
                //Guardamos la ReplyWith
                myReplyWith = in.getReplyWith();
                Info("ReplyWith: " + myReplyWith);
                
                //Guardamos nuestras monedas
                for(JsonValue j : Json.parse(in.getContent()).asObject().get("coins").asArray()){
                    myCoins.add(j.asString());
                    Info(j.asString());
                }
                myStatus = "START-SHOPPING";
                break;
                
            case "START-SHOPPING": //Coger las tiendas de esta sesion
                Info("Petición de las Yellow Pages para las compras");
                in = sendYPQueryRef(_identitymanager);
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de YP ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                //Actualizar las YP
                myYP.updateYellowPages(in);
               // System.out.println(myYP.prettyPrint());
                
                for(String str: myYP.queryProvidersofService("shop@" + myConvID)){
                    myShops.add(str);
                }
                if (myShops.isEmpty()) {
                    Info("\t" + "No hay ningun agente que proporcione el servicio: " + myService);
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                
                myStatus = "SHOPPING";
                break;
                
            case "SHOPPING":
                
                in = getProductos(myShops.get(0));
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                // Info(in.getContent());
                
                //Pasamos el content a HashMap
                for(JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()){
                    tienda0.put(j.asObject().get("reference").asString(),j.asObject().get("price").asInt());
                    
                }
                
                in = getProductos(myShops.get(1));
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                
                //Pasamos el content a HashMap
                for(JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()){
                    tienda1.put(j.asObject().get("reference").asString(),j.asObject().get("price").asInt());
                    
                }
                
                in = getProductos(myShops.get(2));
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                
                //Pasamos el content a HashMap
                for(JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()){
                    tienda2.put(j.asObject().get("reference").asString(),j.asObject().get("price").asInt());
                }
                
                //Algoritmo que gestiona las compras
                comprar(myWishlist);
                
                myStatus = "CHECKOUT-LARVA";
                break;
                
            case "CHECKOUT-LARVA":
                //TODO: Mandar mensaje al coach de que me voy
                Info("Haciendo checkout de LARVA en" + _identitymanager);
                in = sendCheckoutLARVA(_identitymanager);
                myStatus = "EXIT";
                break;
            
            case "EXIT":
                Info("El agente muere");
                _exitRequested = true;
                break;
            

        }
    }

    @Override
    public void takeDown() {
        Info("Taking down");
        super.takeDown();
    }

    private ACLMessage sendCheckinLARVA(String im) {
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
    
    private ACLMessage sendCheckoutLARVA(String im) {
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

    private ACLMessage sendYPQueryRef(String im) {
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
    
    private ACLMessage sendSubscribeWM(String tipo) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setProtocol("REGULAR");
        out.setContent(new JsonObject().add("type", tipo).toString());
        Info(new JsonObject().add("type", tipo).toString());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        out.setConversationId(myConvID);
        this.send(out);
        return this.blockingReceive();
    }

    private ACLMessage getProductos(String shop) {
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
    
    private void comprar(ArrayList<String> miLista){
        Info("Comprando...");
        
        String eleccionTienda0 = "";
        String eleccionTienda1 = "";
        String eleccionTienda2 = "";
        
        int precioAuxiliar0 = 0;
        int precioAuxiliar1 = 0;
        int precioAuxiliar2 = 0;
        
        //Por cada elemento de la lista de deseos
        for(String elemento: miLista){
            //Comprobamos en cada tienda cual es el articulo mas barato con ese nombre
            //TIENDA 0
            for(String i : tienda0.keySet()){
                String auxiliar = i.split("#")[0];
                if(auxiliar.equals(elemento)){
                    Info("ENcuentro un elemento igual");
                    if(eleccionTienda0 == ""){
                       eleccionTienda0 = i;
                       precioAuxiliar0 = tienda0.get(i);
                    }
                    else{ //Hay que elegir el precio mas barato
                        if(precioAuxiliar0 > tienda0.get(i)){ //Intercambiamos el articulo
                            eleccionTienda0 = i;
                        }
                    }
                }
            }
            //TIENDA 1
            for(String i : tienda1.keySet()){
                String auxiliar = i.split("#")[0];
                if(auxiliar.equals(elemento)){
                    if(eleccionTienda1 == ""){
                       eleccionTienda1 = i;
                       precioAuxiliar1 = tienda1.get(i);
                    }
                    else{ //Hay que elegir el precio mas barato
                        if(precioAuxiliar1 > tienda1.get(i)){ //Intercambiamos el articulo
                            eleccionTienda1 = i;
                        }
                    }
                }
            }
            //TIENDA 2
            for(String i : tienda2.keySet()){
                String auxiliar = i.split("#")[0];
                if(auxiliar.equals(elemento)){
                    if(eleccionTienda2 == ""){
                       eleccionTienda2 = i;
                       precioAuxiliar2 = tienda2.get(i);
                    }
                    else{ //Hay que elegir el precio mas barato
                        if(precioAuxiliar2 > tienda2.get(i)){ //Intercambiamos el articulo
                            eleccionTienda2 = i;
                        }
                    }
                }
            }
            
            //Comparamos los elementos elegidos de las tres tiendas
            ArrayList<Integer> precios = new ArrayList<>();
            precios.add(precioAuxiliar0);
            precios.add(precioAuxiliar1);
            precios.add(precioAuxiliar2);
            
            int min = 0, indice = 0;
            for (int i=0; i<precios.size(); i++) {
                Info("PRECIOS DISPONIBLES: " + precios.get(i));
                if (precios.get(i) < min) {
                    min = precios.get(i);
                    indice = i;
                }
            }
            
            
            //Obtenemos la eleccion y enviamos accion de comprar
            switch(indice){
                case 0:
                    Info("La mejor eleccion es: " +  eleccionTienda0 + " " + precioAuxiliar0);
                    in = sendComprar(eleccionTienda0, myShops.get(0));
                    break;
                case 1:
                    Info("La mejor eleccion es: " +  eleccionTienda1 + " " + precioAuxiliar1);
                   
                    in = sendComprar(eleccionTienda1, myShops.get(1));
                    break;
                case 2:
                    Info("La mejor eleccion es: " +  eleccionTienda2 + " " + precioAuxiliar2);
                   
                    in = sendComprar(eleccionTienda2, myShops.get(2));
                    break;
            }
             
            
        }
    }
    
     private ACLMessage sendComprar(String producto, String tienda) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(tienda, AID.ISLOCALNAME));
        JsonObject contenido = new JsonObject();
        contenido.add("operation", "buy").toString();
        contenido.add("reference", producto).toString();
        JsonArray array = new JsonArray();
        array.add((String) myCoins.get(0));
        contenido.add("payment", array).toString();
        Info("Content " + contenido);
        out.setContent(new JsonObject().add("operation", "buy").toString());
        out.setPerformative(ACLMessage.REQUEST);
        out.setConversationId(myConvID);
        this.send(out);
        return this.blockingReceive();
    }
    
}