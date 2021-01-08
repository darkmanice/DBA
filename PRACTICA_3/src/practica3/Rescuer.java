/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package practica3;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author prueba
 */
public class Rescuer extends Drone {
    
    private String myPartner;
    private int destinox;
    private int destinoy;
    
    @Override
    public void setup(){
        super.setup();
        
        //Lista de articulos deseados
        myWishlist.add("GPS");
        myWishlist.add("COMPASS");

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
                myStatus = "WAITING";
                break;

            case "WAITING":
                in = blockingReceive();
                if (in.getPerformative() == ACLMessage.QUERY_IF) {
                    myConvID = in.getConversationId();
                    //Coge las coordenadas de el content
                    Info("Contenido: " + in.getContent());
                    CoordInicio[0] = Json.parse(in.getContent()).asObject().get("X").asInt();
                    CoordInicio[1] = Json.parse(in.getContent()).asObject().get("Y").asInt();
                    
                    
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

                //Guardamos la ReplyWith
                myReplyWith = in.getReplyWith();

                //Guardamos nuestras monedas
                for (JsonValue j : Json.parse(in.getContent()).asObject().get("coins").asArray()) {
                    myCoins.add(j.asString());
                }
                myStatus = "START-SHOPPING";
                break;

            case "START-SHOPPING": //Coger las tiendas de esta sesion
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
                //Esperamos a que Pantoja abra las rebajas
                in = blockingReceive();
            
                if (updateShops()) {
                    //Algoritmo que gestiona las compras
                    comprar(myWishlist);
                }

                //avisar de que he terminado mis compras
                in = sendFinCompra();
                
                myStatus = "LOGIN-PROBLEM";
                break;

                
            case "LOGIN-PROBLEM":
                
                try {
                    //Cargar el mapa
                    Info("Cargando mapa...");
                    myMap.loadMap(myProblem + ".png");
                } catch (IOException ex) {
                    Logger.getLogger(Seeker.class.getName()).log(Level.SEVERE, null, ex);
                }
                //Inicializamos los sensores del dron
                inicializarSensores(myMap);
                //Pasamos los sensores y las coordenadas de inicio al WM
                
                in = sendLoginProblem();
                
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo hacer login en el problema con " + this.myWorldManager
                            + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                //Guardar la energia inicial
                energy = 10;
                
                
                myStatus = "WAITING-TARGET";
                break;
                
            case "WAITING-TARGET":
                in = blockingReceive();
                
                if(in.getPerformative() == ACLMessage.REQUEST){
                    this.myPartner = in.getSender().toString();
                    this.destinox = Json.parse(in.getContent()).asObject().get("posx").asInt();
                    this.destinoy = Json.parse(in.getContent()).asObject().get("posx").asInt();
                    Info("Recibidas coordenadas de: " + this.myPartner);
                    myStatus = "RESCUING";
                }
                break;
                
            case "RESCUING":
                recarga();
                energy = 1000;
                
                irA(destinox,destinoy);
                
                rescatar();
                sendRescued();
                
                irA(CoordInicio[0],CoordInicio[1]);
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

    private void rescatar() {
        int aux = altimeter;
        int aux2 = energy;
        ArrayList<String> rescate = new ArrayList<>();
        
        for (int i = 0; i < aux / 5; i++) {
            if(aux > 0){
                rescate.add("moveD");
            }
        }
        rescate.add("touchD");
        rescate.add("rescue");

        if(energy_u > energy - coste(rescate)){
            recarga();
            energy = 1000;
        }
        
        for(String r : rescate){
            in = sendAction(r);
        }
        energy -= coste(rescate);
        Info("GERETTET !!!");
    }

    private void sendRescued() {
        ACLMessage outRescueTeam = new ACLMessage();
        outRescueTeam.setSender(this.getAID());
        outRescueTeam.addReceiver(new AID(myPartner, AID.ISLOCALNAME));
        outRescueTeam.setContent("");
        outRescueTeam.setConversationId(myConvID);
        outRescueTeam.setProtocol("REGULAR");
        outRescueTeam.setPerformative(ACLMessage.INFORM);
        
        this.send(outRescueTeam);
    }

}
