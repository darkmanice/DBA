package practica3;

import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.Stack;


/**
 * Agente que se dedica a rescatar. 
 * @author 
 */
public class Rescuer extends IntegratedAgent {

    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myConvID;
    protected ArrayList<String> myShops, myWishlist;
    protected boolean myError;
    protected Stack myCoins;
    protected ACLMessage in, out;
    protected Map2DGrayscale myMap;

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
                //System.out.println(myYP.prettyPrint());
                
                for(String str: myYP.queryProvidersofService("shop")){
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
                Info(in.getContent());
                /* in = getProductos(myShops.get(1));
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                Info(in.getContent());
                 in = getProductos(myShops.get(2));
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Lectura de Productos ha fallado por: " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                Info(in.getContent());*/
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
}