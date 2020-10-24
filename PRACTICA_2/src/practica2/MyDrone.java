package practica2;

import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class MyDrone extends IntegratedAgent {
    
    String receiver;
    TTYControlPanel myControlPanel;

    @Override
    public void setup() {
        super.setup();
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        
        //Panel de control
        myControlPanel = new TTYControlPanel(getAID());
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
        Info("El server dice: " + answer);
        
        
        out = in.createReply();

        JsonObject json = Json.parse(answer).asObject();
        String key = json.get("key").asString();
        
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
        json = new JsonObject();
        json.add("command", "read");
        json.add("key", key);
        String resultado = json.toString();
        out.setContent(resultado);
        this.send(out);
        
        in = this.blockingReceive();
        answer = in.getContent();
        out = in.createReply();
        
        Info(answer);
        
        //CREAMOS LA ACCION
        String command2 = "execute";
        String accion = "rotateL";
        
        json = new JsonObject();
        json.add("command", command2);
        json.add("action", accion);
        json.add("key", key);
        resultado = json.toString();
        
        
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
        
        
        
        
        
        /*
        String reply = new StringBuilder(answer).reverse().toString();
        out = in.createReply();
        out.setContent(reply);
        this.sendServer(out);
       */

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
        //myControlPanel.fee
        Info("******************************************" + in.getContent());
        return in;
    }
    
}
