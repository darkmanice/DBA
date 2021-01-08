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
                    altura_max = Json.parse(in.getContent()).asObject().get("altura_max").asInt();
                    
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
                Info("CONTENIDO: " +in.getContent());
                
                myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo hacer login en el problema con " + this.myWorldManager
                            + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                
                //Se ha logueado correctamente, valores de inicio
                energy = 10;
                //Recargamos, estamos a la altura del suelo
                in = sendRecharge();
                 myError = (in.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " No se pudo hacer primer recharge en el problema con " + this.myWorldManager
                            + " debido a " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                        
                myStatus = "SEEKING";
                break;

                
            case "SEEKING":
                
               /* //Leer los sensores
                readSensores();
                Info("Bateria despues de leer sensores: " + energy);
                
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
             
                
                //myStatus = "SEEKING";*/
                //**************************************************
                //Incrementamos el iterador
                iterador++;
                //Leer los sensores
                readSensores();
                
                //Calcular acciones posibles
                ArrayList<String> acciones = calcularAccionesPosibles();
                
                //Para cada una de las acciones, enviar mensajes al servidor
                for(int i = 0; i<acciones.size(); i++){
                        in = sendAction(acciones.get(i));
                        myStatus ="SEEKING";
                }
                break;

            case "WAITING-RESCUER":
                in = blockingReceive();
                if (in.getPerformative() == ACLMessage.INFORM) {
                    myStatus = "SEEKING";
                }
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
    protected ArrayList<String> calcularAccionesPosibles(){
        ArrayList<String> acciones = new ArrayList<>();
        
        //Hacia donde ir
        ArrayList<String> casillas = new ArrayList<>();
        ArrayList<Double> distancias = new ArrayList<>();
        
        diferenciaDistancias(casillas, distancias);
        
        burbuja(casillas, distancias);
        
        //Miramos si estamos encima del objetivo
        if (distance == 0){
            Info("Target encontrado en: (" + position[0] + "," + position[1] + ")");
           
            //comunicar al rescuer la posicion del aleman
            if(position[1] <= myMap.getHeight()/2 ){
                //El target esta en la mitad superior y lo comunica al agente correspondiente -> Ramon
                Info("Enviando coordenadas a Ortega");
                //sendSearchPoint("Ortega");
            }
            else{
                Info("Enviando coordenadas a Ramon");
                sendSearchPoint("Ramon");
            }
            //Elevar al Seeker y ponerlo a esperar una respuesta de su rescuer
            acciones.add("moveUP");
            acciones.add("moveUP");
            
            if (energy_u > (energy - 2*coste(acciones))){
                //Recargamos energia 
                recarga();
            }
            
            myStatus = "WAITING-RESCUER";
            return acciones;
            
            //Lo mandamos al lado contrario del que estamos
            /*if(position[0] <= myMap.getWidth() / 2){
                //Esta a la izq, lo mandamos a la derecha
                irA(position[0]+myMap.getWidth()/2, position[1]);
                //Volvemos a calcular las acciones posibles para el siguiente objetivo
                return calcularAccionesPosibles(); 
            }
            else{
                //Esta a la derecha, lo mandamos a la izq
                irA(position[0]-myMap.getWidth()/2, position[1]);
                //Volvemos a calcular las acciones posibles para el siguiente objetivo
                return calcularAccionesPosibles(); 
            }*/
        }
        
        //En orden, mirar que se pueda ir a la siguiente casilla
        String casilla = casillas.get(0);
        
        //Info("Voy a casilla " + casilla);
        int anguloCasilla;
        double direccionGiro;
        int ngiros;
        int diferenciaAltura;
        //Segun a la casilla a la que el drone decida ir:
        //Info("Tengo que ir a la casilla " + casilla + "////////////");
        switch(casilla){
            case "NO":
                anguloCasilla = -45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]-1, position[1]-1));
                
                break;
            case "N":
                anguloCasilla = 0;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0], position[1]-1));
                break;
            case "NE":
                anguloCasilla = 45;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]+1, position[1]-1));
                break;
            case "E":
                anguloCasilla = 90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]+1, position[1]));
                break;
            case "SE":
                anguloCasilla = 135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]+1, position[1]+1));
                break;
            case "S":
                anguloCasilla = 180;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0], position[1]+1));
                break;
            case "SO":
                anguloCasilla = -135;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]-1, position[1]+1));
                break;
            case "O":
                anguloCasilla = -90;
                //Calculamos cuanto tiene que girar, subir o bajar
                acciones = calculaGiroySubida(anguloCasilla, myMap.getLevel(position[0]-1, position[1]));
                break;
        }
        
        //Mirar si hay que recarga batería antes de realizar las acciones
        for(int i = 0; i < acciones.size(); i++){
            Info("Acciones: "+ acciones.get(i));
        }
        
        //Hemos quitado -altimeter
        if (energy_u > (energy - coste(acciones))){
            //Info("NO TENGO SUFICIENTE ENERGIA. Umbral: " + energy_u + " Energia actual: " + energy + " coste acciones: " + coste(acciones) + " altimetro: " +altimeter);
            //Recargamos energia y volvemos a subir
            recarga();
           // elevar();
        }
        else{
            Info("TENGO SUFICIENTE ENERGIA " + energy + " PARA EJECUTAR " + coste(acciones));
        }
        return acciones;
    }

    private void sendSearchPoint(String agent) {
        ACLMessage outRecueTeam = new ACLMessage();
        JsonObject contenido = new JsonObject();
        contenido.add("posx", position[0]);
        contenido.add("posy", position[1]);
        outRecueTeam.setSender(getAID());
        outRecueTeam.addReceiver(new AID(agent, AID.ISLOCALNAME));
        outRecueTeam.setContent(contenido.toString());
        outRecueTeam.setConversationId(myConvID);
        outRecueTeam.setProtocol("REGULAR");
        outRecueTeam.setPerformative(ACLMessage.REQUEST);
        
        this.send(outRecueTeam);
    }

    
}
