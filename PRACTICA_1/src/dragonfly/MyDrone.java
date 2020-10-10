package dragonfly;

import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class MyDrone extends IntegratedAgent {
    
    String receiver;

    @Override
    public void setup() {
        super.setup();
        doCheckinPlatform();
        doCheckinLARVA();
        //receiver = this.whoLarvaAgent();
    }

    @Override
    public void plainExecute() {
        Info("Enviando credenciales");
        ACLMessage out = new ACLMessage();
        
        
        //PARSEAR JSON CON LOS DATOS A ENVIAR        
        String command = "login";
        String world = "BasePlayground";
        String attach[] = {"alive", "distance", "altimeter"};
        
        JsonObject json = new JsonObject();
        json.add("command", command);
        json.add("world", world);
        JsonArray vector = new JsonArray();
        
        for(int i = 0; i < attach.length; i++){
            vector.add(attach[i]);
        }
        
        json.add("attach", vector);
        String resultado = json.toString();
        Info(resultado);        
        
        //ENVIAMOS LOS CREDENCIALES
        out.setSender(getAID());
        out.addReceiver(new AID("WorldManager", AID.ISLOCALNAME));
        out.setContent(resultado);
        this.send(out);
        
        Info("Enviado");
        
        //ESPERAMOS RESPUESTA
        ACLMessage in = this.blockingReceive();
        String answer = in.getContent();
        out = in.createReply();
        Info("El server dice: " + answer);
        
        json  = Json.parse(answer).asObject();
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
        resultado = json.toString();
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


}
