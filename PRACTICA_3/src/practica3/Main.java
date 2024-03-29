/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package practica3;

import AppBoot.ConsoleBoot;
import AWACS.Awacs;

public class Main {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("HACKATHON", args);
        app.selectConnection();
        
        app.launchAgent("AWACS_CELLNEX", Awacs.class);
        app.launchAgent("Listener", Listener.class);
        app.launchAgent("Ramon", Rescuer.class);
//        app.launchAgent("Rescuer2", Rescuer.class);
        app.launchAgent("Cajal", Seeker.class);
//        app.launchAgent("Seeker2", Seeker.class);
        app.launchAgent("Pantoja", Pantoja.class);
        
        app.shutDown();        
    }
    
}
