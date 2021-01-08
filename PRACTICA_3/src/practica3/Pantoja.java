package practica3;

import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import com.eclipsesource.json.JsonObject;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agente que hace las veces de controlador y de jefe de equipo. Es el primero
 * en conectarse al WM y enviar el codigo de la sesion al resto de agentes
 *
 * @author
 */
public class Pantoja extends IntegratedAgent {

    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myWorld, myConvID;
    protected boolean myError;
    protected ACLMessage in, out;
    protected Map2DGrayscale myMap;
    
    int contador;

    @Override
    public void setup() {
        contador = 2;
        _identitymanager = "Sphinx";
        super.setup();

        myYP = new YellowPages();

        Info("Booting");

        // Description of my group
        myService = "Analytics group Cellnex";

        // The world I am going to open
        myWorld = "Playground1";

        // First state of the agent
        myStatus = "CHECKIN-LARVA";

        // To detect possible errors
        myError = false;

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
                myStatus = "SUBSCRIBE-WM";
                break;

            case "SUBSCRIBE-WM":
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
                //System.out.print(myYP.prettyPrint());

                if (myYP.queryProvidersofService(myService).isEmpty()) {
                    Info("\t" + "No hay ningun agente que proporcione el servicio: " + myService);
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                //Cogemos el World Manager de la lista de servicios
                myWorldManager = myYP.queryProvidersofService(myService).iterator().next();
                //Nos suscribimos
                in = sendSubscribeWM(myWorld);

                myError = in.getPerformative() != ACLMessage.INFORM;
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo abrir sesion "
                            + myWorldManager + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                //Guardamos el conversationID
                myConvID = in.getConversationId();
               
                myStatus = "PROCESS-MAP";
    
                break;
                
            case "PROCESS-MAP":
                System("Save map of world " + myWorld);
                // Examines the content of the message from server
                JsonObject jscontent = getJsonContentACLM(in);
                //System.out.println(jscontent);
                if (jscontent.names().contains("map")) {
                    JsonObject jsonMapFile = jscontent.get("map").asObject();
                    String mapfilename = jsonMapFile.getString("filename", "nonamefound");
                    Info("Mapa Encontrado " + mapfilename);
                    myMap = new Map2DGrayscale();
                    if (myMap.fromJson(jsonMapFile)) {
                        Info("Map " + mapfilename + "( " + myMap.getWidth() + "cols x" + myMap.getHeight()
                                + "rows ) saved on disk (project's root folder) and ready in memory");
                        Info("Sampling three random points for cross-check:");
                        int px, py;
                        for (int ntimes = 0; ntimes < 3; ntimes++) {
                            px = (int) (Math.random() * myMap.getWidth());
                            py = (int) (Math.random() * myMap.getHeight());
                            Info("\tX: " + px + ", Y:" + py + " = " + myMap.getLevel(px, py));
                
                        }
                        myStatus = "CANCEL-WM";
                    } else {
                        Info("\t" + "There was an error processing and saving the image ");
                        myStatus = "CANCEL-WM";
                        break;
                    }
                } else {
                    Info("\t" + "There is no map found in the message");
                    myStatus = "CANCEL-WM";
                    break;
                }
                myStatus = "MANDAR-CONVID";
                break; 
                
            case "MANDAR-CONVID":
                Info("Enviando ConversationID a todos los agentes");
                
                int coordx, coordy;
                JsonObject contenido;
                
                //Generar las coordenadas de inicio de los 
                //Seeker1
                coordx = myMap.getWidth() / 2  -2;
                coordy = myMap.getHeight() / 2  -2;
                contenido = new JsonObject();
                contenido.add("X", coordx);
                contenido.add("Y", coordy);
                contenido.add("altura_max", 255);
                contenido.add("nombre", "Cajal");
                mandarConvId("Cajal", contenido.toString());
                
                //Seeker2
                //Rescuer1 (Coentro del mapa)
                coordx = myMap.getWidth() / 2;
                coordy = myMap.getHeight() / 2;
                contenido = new JsonObject();
                contenido.add("X", coordx);
                contenido.add("Y", coordy);
                contenido.add("altura_max",255);
                contenido.add("nombre", "Ramon");
                mandarConvId("Ramon", contenido.toString());
                
                
                //Rescuer2
                
                mandarConvId("Listener", "");
                
                mandarConvId("AWACS_CELLNEX", "");
                myStatus = "WAITING-COMPRAS";
                break;
                
                
            case "WAITING-COMPRAS":
            {
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Pantoja.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
                //Establecemos una cola para comprar
                //1.Cajal (Seeker) puede comprar
                in = sendPuedesComprar("Cajal");
                
                //2.Ramon (Rescuer) puede comprar
                in = sendPuedesComprar("Ramon");
                
                
                sendLoginProblem("Ramon");
                sendLoginProblem("Cajal");
                
                myStatus = "WAITING";
                break;

            
            case "WAITING":
                
                while (contador != 0){
                    in = blockingReceive();
                    if(in.getContent().equals("LOGOUT")){
                        contador--;
                    }
                    
                }
                
                    try { //Simulamos que los rescuers nos mandan un adios
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Pantoja.class.getName()).log(Level.SEVERE, null, ex);
                    }
                //Mandamos mensaje a Listener para que cierre
                mandarMensaje("Listener", "Cerramos el chiringuito");
                myStatus="CANCEL-WM";
                break;

                
            case "CANCEL-WM":
                Info("Cerrando el juego");
                in = sendCANCELWM();
                //Avisamos a AWACS
                sendCANCELAWACS();
                myStatus = "CHECKOUT-LARVA";
                break;
                

            case "CHECKOUT-LARVA":
                Info("Haciendo checkout de LARVA en" + _identitymanager);
                in = sendCheckoutLARVA(_identitymanager);
                myStatus = "EXIT";
                break;

            case "EXIT":
                Info("El agente muere");
                _exitRequested = true;
                break;
                
            default:
                Info("Algun nombre del switch case no esta coincidiendo: " + myStatus);
                myStatus = "CANCEL-WM";
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

    private ACLMessage sendSubscribeWM(String problem) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent(new JsonObject().add("problem", problem).toString());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
        return this.blockingReceive();
    }

    private ACLMessage sendCANCELWM() {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setContent("");
        out.setConversationId(myConvID);
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.CANCEL);
        send(out);
        return blockingReceive();
    }

    private void mandarMensaje(String im, String content) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(im, AID.ISLOCALNAME));
        out.setContent(content);
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.QUERY_IF);
        send(out);
        
    }
    private void mandarConvId(String im, String coor) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(im, AID.ISLOCALNAME));
        out.setContent(coor);
        out.setConversationId(myConvID);
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.QUERY_IF);
        send(out);
        
    }

    private void sendCANCELAWACS() {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID("AWACS", AID.ISLOCALNAME));
        out.setContent("");
        out.setConversationId(myConvID);
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.CANCEL);
        send(out);
    }

    private ACLMessage sendPuedesComprar(String agent) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agent, AID.ISLOCALNAME));
        out.setContent("");
        out.setConversationId(myConvID);
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.REQUEST);
        send(out);
        return this.blockingReceive();
    }

    private void sendLoginProblem(String agent) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agent, AID.ISLOCALNAME));
        out.setContent("");
        out.setConversationId(myConvID);
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        send(out);
    }
}
