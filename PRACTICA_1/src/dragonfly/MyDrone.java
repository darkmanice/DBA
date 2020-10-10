package dragonfly;

//import IntegratedAgent.IntegratedAgent;
import LarvaAgent.LarvaAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class MyDrone extends LarvaAgent {

    @Override
    public void plainExecute() {
        Info("Hablando con el servidor");
        ACLMessage mens = new ACLMessage();
        //PARSE JSON CON LOS DATOS A ENVIAR
        mens.setSender(getAID());
        mens.addReceiver(new AID("Songoanda", AID.ISLOCALNAME));
        mens.setContent("Hello");
        this.send(mens);
        ACLMessage in = this.blockingReceive();
        String answer = in.getContent();
        Info("El server dice: " + answer);
        String reply = new StringBuilder(answer).reverse().toString();
        mens = in.createReply();
        mens.setContent(reply);
        this.sendServer(mens);
        _exitRequested = true;
    }

}
