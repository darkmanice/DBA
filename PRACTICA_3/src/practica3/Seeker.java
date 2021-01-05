/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package practica3;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import jade.lang.acl.ACLMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author prueba
 */
public class Seeker extends Drone{
    
    @Override
    public void setup(){
        super.setup();
        
        //Lista de articulos deseados   //"alive", "distance", "gps", "visual", "angular", "compass", "energy"
        myWishlist.add("DISTANCE"); 
        myWishlist.add("GPS"); 
        myWishlist.add("ANGULAR"); 
        myWishlist.add("COMPASS"); 
        myWishlist.add("ENERGY"); 
        
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

                if (updateShops()) {
                    //Algoritmo que gestiona las compras
                    comprar(myWishlist);
                }

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
                Info("CONTENIDO: " +in.getContent());
                
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo hacer login en el problema con " + this.myWorldManager
                            + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                        
                myStatus = "SEEKING";
                break;

                
            case "SEEKING":
                
                //Leer los sensores
                readSensores();
                
                //Elevar al agente a la maxima altura para evitar colisiones
                if(!elevar()){
                   Info("NO SE HA PODIDO ELEVAR");
                   myStatus = "CHECKOUT-LARVA";
                   break;
                }
                //Calcular acciones posibles
                ArrayList<String> acciones = calcularAccionesPosibles();
                //Para cada una de las acciones, enviar mensajes al servidor
                for(int i = 0; i<acciones.size(); i++){
                    if(acciones.get(i).equals("FIN"))
                        myStatus="CHECKOUT-LARVA";
                    else{
                        in = sendAction(acciones.get(i));
                        myStatus ="SEEKING";
                    }
                    
                }
             
                
                //myStatus = "SEEKING";
                
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
}
