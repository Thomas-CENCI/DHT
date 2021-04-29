package DHT;

import peersim.core.*;

import java.time.LocalTime;
import java.util.HashMap;

import peersim.config.*;

/*
  Module d'initialisation de helloWorld: 
  Fonctionnement:
    pour chaque noeud, le module fait le lien entre la couche transport et la couche applicative
    ensuite, il fait envoyer au noeud 0 un message "Hello" a tous les autres noeuds
 */
public class Initializer implements peersim.core.Control {
    
    private int DHTNodePid;

    public Initializer(String prefix) {
		//recuperation du pid de la couche applicative
		this.DHTNodePid = Configuration.getPid(prefix + ".DHTProtocolPid");
    }

    public boolean execute() {
	int nodeNb;
	DHTNode emitter, current;
	Node dest;

	//recuperation de la taille du reseau
	nodeNb = Network.size();
	if (nodeNb < 1) {
	    System.err.println("Network size is not positive");
	    System.exit(1);
	}

	//recuperation de la couche applicative de l'emetteur (le noeud 0)
	emitter = (DHTNode)Network.get(0).getProtocol(this.DHTNodePid);
	emitter.setTransportLayer(0);

	// Nombre de noeuds modifiable
	int numberOfNodes = 100;
	for (int i = 1; i < numberOfNodes; i++) {
	    dest = new GeneralNode("protocol.applicative DHT.DHTNode");
		Network.add(dest);
	    current = (DHTNode)dest.getProtocol(this.DHTNodePid);
	    current.setTransportLayer(i);
		current.join();
	}

	DHTNode randomNode = (DHTNode) Network.get(3).getProtocol(this.DHTNodePid);
	randomNode.receiveData(new Message(randomNode.generateRandomId(), "Hi!"));

	LocalTime time = java.time.LocalTime.now(); 

	for(HashMap<Integer, Object> element : randomNode.getData()) {
	    System.out.println("\nAt " + time + "\n" + "Data from parent node : " + ((Message) element.get(randomNode.getRandomId())).getContent());
	}

	DHTNode leaveNode = (DHTNode) Network.get(7).getProtocol(this.DHTNodePid);
	leaveNode.leave();
	
	// Permet de visualiser le réseaux sans qu'il y ait trop de noeuds
	if(Network.size() <= 20) {
		emitter.visualizeNetwork();
	}

	System.out.println("Initialization completed");
	return false;
    }
}