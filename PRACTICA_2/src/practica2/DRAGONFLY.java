/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package practica2;

import AppBoot.ConsoleBoot;

public class DRAGONFLY {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("DRAGONFLY", args);
        app.selectConnection();
        
        app.launchAgent("743849999999", MyDrone.class);
        app.shutDown();        
    }
}