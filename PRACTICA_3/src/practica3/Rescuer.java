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
 *
 * @author
 */
public class Rescuer extends IntegratedAgent {

    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myConvID, myReplyWith, myCoach;
    protected ArrayList<String> myShops, myWishlist, mySensors;
    protected boolean myError;
    protected Stack myCoins;
    protected ACLMessage in, out;
    protected Map2DGrayscale myMap;

    //Tiendas disponibles en el mundo
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

        //Agente coach
        myCoach = "Pantoja";

        //Shops
        myShops = new ArrayList<>();
        mySensors = new ArrayList<>();

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
                if (in.getPerformative() == ACLMessage.QUERY_IF) {
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
                for (JsonValue j : Json.parse(in.getContent()).asObject().get("coins").asArray()) {
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

                for (String str : myYP.queryProvidersofService("shop@" + myConvID)) {
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

                if (updateShops()) {
                    //Algoritmo que gestiona las compras
                    comprar(myWishlist);
                }
                Info ("Hemos acabado de hacer la compra: salimos"); 
                
                
                myStatus = "CHECKOUT-LARVA";
                break;

            case "CHECKOUT-LARVA":
                //TODO: Mandar mensaje al coach de que me voy
                Info("Haciendo checkout de LARVA en" + _identitymanager);
                sendLogoutCoach();
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

    private void comprar(ArrayList<String> miLista) {

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
                    Info("PRECIOS DISPONIBLES: " + precios.get(i));
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
                        Info("0 La mejor eleccion es: " + eleccionTienda0 + " " + precioAuxiliar0);
                        eleccionF = eleccionTienda0;
                        tiendaF = myShops.get(0);
                        precioF = precioAuxiliar0;
                        break;
                    case 1:
                        Info("1 La mejor eleccion es: " + eleccionTienda1 + " " + precioAuxiliar1);
                        eleccionF = eleccionTienda1;
                        tiendaF = myShops.get(1);
                        precioF = precioAuxiliar1;
                        break;
                    case 2:
                        Info("2 La mejor eleccion es: " + eleccionTienda2 + " " + precioAuxiliar2);
                        eleccionF = eleccionTienda2;
                        tiendaF = myShops.get(2);
                        precioF = precioAuxiliar2;
                        break;
                }

                if (precioF <= myCoins.size() && eleccionF.contains("#")) {
                    in = sendComprar(eleccionF, tiendaF, precioF);
                } else {
                    Info("No se puede comprar o no quedan existencias");
                    compraCarga = false;
                    return;
                }

                //Guardar los productos que hemos comprado
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Compra de productos ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }

                String producto = Json.parse(in.getContent()).asObject().get("reference").asString();
                Info("Hemos comprado el producto: " + producto);
                this.mySensors.add(producto);
            }
        }

    }

    private ACLMessage sendComprar(String producto, String tienda, int precio) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(tienda, AID.ISLOCALNAME));
        JsonObject contenido = new JsonObject();
        contenido.add("operation", "buy").toString();
        contenido.add("reference", producto).toString();
        JsonArray array = new JsonArray();
        //Añadimos tantas monedas como cueste el elemento
        for (int i = 0; i < precio; i++) {
            array.add((String) myCoins.pop());
        }
        contenido.add("payment", array).toString();
        Info("TIenda: " + tienda);
        Info("Content " + contenido);
        out.setContent(contenido.toString());
        out.setPerformative(ACLMessage.REQUEST);
        out.setConversationId(myConvID);
        this.send(out);
        return this.blockingReceive();
    }

    private void sendLogoutCoach() {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myCoach, AID.ISLOCALNAME));
        out.setContent("LOGOUT");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }

    private boolean updateShops() {
        Info("Actualizando tiendas....");

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
        System.out.println(in.getContent());

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

        System.out.println(in.getContent());

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

        System.out.println(in.getContent());

        //Pasamos el content a HashMap
        for (JsonValue j : Json.parse(in.getContent()).asObject().get("products").asArray()) {
            tienda2.put(j.asObject().get("reference").asString(), j.asObject().get("price").asInt());
        }

        return true;
    }

}
