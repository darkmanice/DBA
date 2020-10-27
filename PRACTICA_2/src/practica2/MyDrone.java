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

public class MyDrone extends IntegratedAgent {
    
    private String receiver;
    private String key;
    private TTYControlPanel myControlPanel;
    private int width, height, maxflight;
    
    private int mundo[][];
    private int position;

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
        String attach[] = {"alive", "distance", "altimeter"};
        
        //Iniciar sesion en el mundo
        ACLMessage in = login(out, "Playground1", attach);
        
        String answer = in.getContent();
        
        out = in.createReply();

        JsonObject json = Json.parse(answer).asObject();
        
        
        String details = null;
        if(json.get("details") != null){
            details = json.get("details").asString();
        }
        
        Info("La clave recibida es: " + key);
        
        //MUESTRA EL ERROR SI HA HABIDO ALGUN PROBLEMA
        if(details != null){
            Info("Detalles del error: " + details);
        }
        
        //Lectura de sensores
        json = readSensores(out, in);
        
        Info(answer);
        
        //CREAMOS LA ACCION
        String command2 = "execute";
        String accion = "rotateL";
        
        json = new JsonObject();
        json.add("command", command2);
        json.add("action", accion);
        json.add("key", key);
        String resultado = json.toString();
        
        
        //ENVIAMOS LA ACCION
        out.setContent(resultado);
        this.send(out);
        
        Info(resultado);
        
        //ESPERAMOS RESPUESTA
        in = this.blockingReceive();
        answer = in.getContent();
        Info("El server dice: " + answer);
        
        json  = Json.parse(answer).asObject();
        String result = json.get("result").asString();
        
        Info("Resultado de la accion: " + result);
        
        if(result == "ok"){
            logout(out);
        }
        _exitRequested = true;
    }
    
    @Override
    public void takeDown() {
        myControlPanel.close();
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }
    
    private void logout(ACLMessage out){
        JsonObject json = new JsonObject();
        json.add("command", "logout");
        String resultado = json.toString();
        
        out.setContent(resultado);
        
        Info("Sesion cerrada"); 
        this.send(out);
    }

    private ACLMessage login (ACLMessage out, String mundo, String sensores[]) {
        //PARSEAR JSON CON LOS DATOS A ENVIAR        
        String command = "login";
        String world = mundo;
        
        
        JsonObject json = new JsonObject();
        json.add("command", command);
        json.add("world", world);
        JsonArray vector = new JsonArray();
        
        for(int i = 0; i < sensores.length; i++){
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
        Info("******************************************" + in.getContent());
        
        json = Json.parse(in.getContent()).asObject();
        key = json.get("key").asString();
        
        
        Info("La key almacenada es " + key);
        width = json.get("width").asInt();
        height = json.get("height").asInt();
        maxflight = json.get("maxflight").asInt();
        myControlPanel.feedData(in, width, height, maxflight);
        return in;
    }
    
    private JsonObject readSensores(ACLMessage out, ACLMessage in) {
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
        
        Info("La lectora de sensores es: " + answer);
        
        myControlPanel.feedData(in, width, height, maxflight);
        //myControlPanel.fancyShow();
        
        //Actualizacion de los sensores 
        for (JsonValue j: json.get("details").asObject().get("perceptions").asArray()){
            switch (j.asObject().get("sensor").asString()) {
                case ("alive"):
                    
                    break;
                case("..."):
                    break;
            }
        }
        
        return json;
    }
    
    private void calcularAccion(){
        //Acualizar mapa del mundo
        
    }
}
