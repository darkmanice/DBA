package practica3;

import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import com.eclipsesource.json.JsonObject;
import java.util.ArrayList;


/**
 * Agente que se dedica a escuchar lo que pasa en el mundo. 
 * Funcionamiento:
 * Se loguea en Sphinx y obtiene el WorldManager, se espera a recibir el convID de su controlador y se suscribe al WM para
 * escuchar todo lo que el WM envia. Es el último agente en hacer logout.
 * 
 * @author Marina: implementación
 * @author Román: implementación
 * @author Javier: implementación
 */
public class Listener extends IntegratedAgent {

    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myWorld, myConvID;
    protected boolean myError;
    protected ACLMessage in, out;
    protected Map2DGrayscale myMap;

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

        // The world I am going to open
        myWorld = "World1";

        // First state of the agent
        myStatus = "CHECKIN-LARVA";

        // To detect possible errors
        myError = false;
        myYP = new YellowPages();

        _exitRequested = false;
    }
    
    /**
     * Bucle principal que consiste en un switch con cada uno de los estados posibles
     * de ejecución
     * 
     * @author Marina: implementación
     * @author Román: implementación
     * @author Javier: implementación
     */
    @Override
    public void plainExecute() {
        
        switch (myStatus.toUpperCase()) {
            
            //Caso que identifica a este agente en Sphinx
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
                
            //Caso que pide las YP a Sphinx y obtiene el nombre de nuestro WorldManager
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
            
            //Caso donde el agente duerme hasta recibir el convID de su controlador: Pantoja
            case "WAITING":
                Info("Esperando SESSIONID");
                in = blockingReceive();
                if(in.getPerformative() == ACLMessage.QUERY_IF){
                    myConvID = in.getConversationId();
                    myStatus = "SUBSCRIBE-WM";
                }
                break;
                
            //Caso donde el agente se suscribe al WorldManager con el ID de la sesión
            case "SUBSCRIBE-WM":
                in = sendSubscribeWM("LISTENER");

                myError = in.getPerformative() != ACLMessage.INFORM;
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo abrir sesion "
                            + myWorldManager + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                myStatus = "LISTENING";
                break;
                
            //Caso donde el agente se queda en bucle escuchando mensajes del servidor hasta que el Coach lo avisa de que todos
            //los agentes se van. Entonces, el listener puede cerrar sesión
            case "LISTENING":
                in = blockingReceive();
                if(in.getPerformative() == ACLMessage.INFORM){
                    Info("RADIO: " + in.getContent());
                    myStatus = "LISTENING";
                }
                else{
                    Info("Mensaje de finalización del Coach ->" + in.getContent());
                    myStatus = "CHECKOUT-LARVA";
                }
                break;
                
            //Caso para cerrar sesión en Sphinx    
            case "CHECKOUT-LARVA":
                Info("Haciendo checkout de LARVA en" + _identitymanager);
                in = sendCheckoutLARVA(_identitymanager);
                myStatus = "EXIT";
                break;
            
            //Caso para salir del programa
            case "EXIT":
                Info("El agente muere");
                _exitRequested = true;
                break;
            

        }
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
    
    /**
     * Metodo para enviar el mensaje de suscripción al WM como tipo
     *
     * @author Marina
     * @author Román
     * @author Javier
     * @param tipo String con el tipo de agente = {LISTENER, RESCUER, SEEKER}
     * @return respuesta al mensaje
     */
    private ACLMessage sendSubscribeWM(String tipo) {
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
}