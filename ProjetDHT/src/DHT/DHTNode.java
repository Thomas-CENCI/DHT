package DHT;

import peersim.edsim.*;
import peersim.core.*;
import peersim.config.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Random;
import java.util.HashMap;

public class DHTNode implements EDProtocol {
    
    //identifiant de la couche transport
    private int transportPid;

    //objet couche transport
    private HWTransport transport;

    //identifiant de la couche courante (la couche applicative)
    private int mypid;

    //le numero de noeud
    private int nodeId;

    //prefixe de la couche (nom de la variable de protocole du fichier de config)
    private String prefix;

    private int randomId;

    private DHTNode leftNeighbor;
    private DHTNode rightNeighbor;
    private DHTNode longNeighbor = this;

    Message msg;

    private ArrayList<HashMap<Integer, Object>> data = new ArrayList<HashMap<Integer, Object>>();

    private int lastUpdate = 1;

    public DHTNode(String prefix) {
        this.prefix = prefix;
        //initialisation des identifiants a partir du fichier de configuration
        this.transportPid = Configuration.getPid(prefix + ".transport");
        this.mypid = Configuration.getPid(prefix + ".myself");
        this.transport = null;
        this.leftNeighbor = this;
        this.rightNeighbor = this;
        this.randomId = generateRandomId();

    }

    public int getId() { return this.nodeId; }

    public int getRandomId() { return this.randomId; }

    public DHTNode getRightNeighbor() { return this.rightNeighbor; }

    public DHTNode getLeftNeighbor() { return this.leftNeighbor; }

    public void setRightNeighbor(DHTNode node) { this.rightNeighbor = node; }

    public void setLeftNeighbor(DHTNode node) { this.leftNeighbor = node; }

    public ArrayList<HashMap<Integer, Object>> getData() { return this.data; }

    public ArrayList<DHTNode> getMinMax(DHTNode currentNode) {
        DHTNode minNode = currentNode;
        DHTNode maxNode = currentNode;

        // Détermination du min/max
        for(int i=0; i < Network.size() - 1; i++){
            if(!Network.get(i).equals(null)) {
                DHTNode tempNode = (DHTNode) Network.get(i).getProtocol(this.mypid);
                if(tempNode.getRandomId() > maxNode.getRandomId() && !maxNode.equals(this)) { maxNode = tempNode; }
                if(tempNode.getRandomId() < minNode.getRandomId() && !minNode.equals(this)) { minNode = tempNode; }
            }
        }

        ArrayList<DHTNode> res = new ArrayList<DHTNode>();
        res.add(minNode);
        res.add(maxNode);

        return res;
    }

    public void join(){
                   
        DHTNode currentNode = (DHTNode) Network.get(getRandomNode(this.getId())).getProtocol(this.mypid);
        ArrayList<DHTNode> minMax = getMinMax(currentNode);
        DHTNode minNode = minMax.get(0);
        DHTNode maxNode = minMax.get(1);

        // Mise en place du min/max dans la DHT
        if(this.getRandomId() < minNode.getRandomId() || this.getRandomId() > maxNode.getRandomId()) {

            maxNode.setRightNeighbor(this);
            this.setLeftNeighbor(maxNode);
            minNode.setLeftNeighbor(this);
            this.setRightNeighbor(minNode);
        }

        // Sens croissant
        else if(this.getRandomId() > currentNode.getRandomId()) {
            while(this.getRandomId() > currentNode.getRandomId()) {
                currentNode = (DHTNode) Network.get(currentNode.getRightNeighbor().getId()).getProtocol(this.mypid);
            }
            currentNode.getLeftNeighbor().setRightNeighbor(this);
            this.setLeftNeighbor(currentNode.getLeftNeighbor());
            currentNode.setLeftNeighbor(this);
            this.setRightNeighbor(currentNode);
        }

        // Sens décroissant
        else {
            while(this.getRandomId() < currentNode.getRandomId()) {
                currentNode = (DHTNode) Network.get(currentNode.getLeftNeighbor().getId()).getProtocol(this.mypid);
            }
            currentNode.getRightNeighbor().setLeftNeighbor(this);
            this.setRightNeighbor(currentNode.getRightNeighbor());
            currentNode.setRightNeighbor(this);
            this.setLeftNeighbor(currentNode);
        }


        // Mise à jour des données stockées en cas de join
        for(HashMap<Integer, Object> element : this.getLeftNeighbor().getData()) {
            if((Integer) element.keySet().toArray()[0] == this.getLeftNeighbor().getRandomId()) {
                this.addData((Message)element.values().toArray()[0]);
                this.getRightNeighbor().removeData((Message)element.values().toArray()[0]);
            }
        }

        for(HashMap<Integer, Object> element : this.getRightNeighbor().getData()) {
            if((Integer) element.keySet().toArray()[0] == this.getRightNeighbor().getRandomId()) {
                this.addData((Message)element.values().toArray()[0]);
                this.getLeftNeighbor().removeData((Message)element.values().toArray()[0]);
            }
        }

        // Mise à jour des voisins lointains de plus en plus rare en foction de la taille du réseau
        if(this.lastUpdate + this.lastUpdate * 0.1 == Network.size()) {
            this.findAndSetLongNeighbor();
        }
        else{
            this.setLongNeighbor(this.getLeftNeighbor().getLongNeighbor().getRightNeighbor());
        }

    }

    public void leave() {
        // Mise à jour des données stockées en cas de leave
        for(HashMap<Integer, Object> element : this.getLeftNeighbor().getData()) {
            if((Integer) element.keySet().toArray()[0] == this.getLeftNeighbor().getRandomId()) {
                this.getRightNeighbor().addData((Message)element.values().toArray()[0]);
            }
        }

        for(HashMap<Integer, Object> element : this.getRightNeighbor().getData()) {
            if((Integer) element.keySet().toArray()[0] == this.getRightNeighbor().getRandomId()) {
                this.getLeftNeighbor().addData((Message)element.values().toArray()[0]);
            }
        }

        this.getLeftNeighbor().setRightNeighbor(this.getRightNeighbor());
        this.getRightNeighbor().setLeftNeighbor(this.getLeftNeighbor());

        msg = new Message(this.getId(), this + " left");
        this.send(msg, this.getLeftNeighbor().getRandomId());
        this.send(msg, this.getRightNeighbor().getRandomId());

        Network.remove(this.getId());

        this.updateLongNeighbor();
    }

    public void visualizeNetwork() {
        DHTNode currentNode = (DHTNode) Network.get(0).getProtocol(this.mypid);
        DHTNode minNode = currentNode;
        DHTNode maxNode = currentNode;

        // Détermination du min/max
        for(int i=0; i < Network.size(); i++){
            if(!Network.get(i).equals(null)) {
                DHTNode tempNode = (DHTNode) Network.get(i).getProtocol(this.mypid);
                if(tempNode.getRandomId() > maxNode.getRandomId()) { maxNode = tempNode; }
                if(tempNode.getRandomId() < minNode.getRandomId()) { minNode = tempNode; }
            }
        }

        System.out.println("\n");

        // Vérificaton de l'emplacement de chaque noeud en fonction de leur RandomId
        for(int i=0; i < Network.size(); i++) {
            if(!Network.get(i).equals(null)) {
                DHTNode node = (DHTNode) Network.get(i).getProtocol(this.mypid);
                boolean test = node.getLeftNeighbor().getRandomId() < node.getRightNeighbor().getRandomId();
                System.out.println("Node " + i + "     " + node.getLeftNeighbor().getId() + ":" + node.getLeftNeighbor().getRandomId() + "     " + node.getRandomId() + "     " + node.getRightNeighbor().getId() + ":" + node.getRightNeighbor().getRandomId());
                if(test || node.getRandomId() == maxNode.getRandomId() || node.getRandomId() == minNode.getRandomId()) {
                    System.out.println("Correctly placed\n");

                    // envoi de message
                    // DHTNode current = (DHTNode) Network.get(i).getProtocol(this.mypid);
                    // msg = new Message(this.getId(), "Node " + Integer.toString(i) + " is your new right neighbor!");
                    // current.send(msg, current.getLeftNeighbor().getRandomId());
            
                    // msg = new Message(this.getId(), "Node " + Integer.toString(i) + " is your new left neighbor!");
                    // current.send(msg, current.getRightNeighbor().getRandomId());
                }
                else {
                    System.out.println("/!\\ Misplaced /!\\\n");
                }
            }
        }
        System.out.println("\nVisualization over\n");
    }

    //methode appelee lorsqu'un message est recu par le protocole HelloWorld du noeud
    public void processEvent( Node node, int pid, Object event ) {
	this.receive((Message)event);
    }
    
    //methode necessaire pour la creation du reseau (qui se fait par clonage d'un prototype)
    public Object clone() {
        DHTNode dolly = new DHTNode(this.prefix);
        return dolly;
    }

    public int generateRandomId() {
        return new Random().nextInt() & Integer.MAX_VALUE;
	}

    public int getRandomNode(int nodeToAvoid) {
        int randomNodeId = (int)Math.floor(Math.random()*(Network.size()));
        while(randomNodeId == nodeToAvoid) {
            randomNodeId = (int)Math.floor(Math.random()*(Network.size()));
        }
        return randomNodeId;
    }

    //liaison entre un objet de la couche applicative et un 
    //objet de la couche transport situes sur le meme noeud
    public void setTransportLayer(int nodeId) {
        this.nodeId = nodeId;
        this.transport = (HWTransport) Network.get(this.nodeId).getProtocol(this.transportPid);
    }

    //envoi d'un message (l'envoi se fait via la couche transport)
    public void send(Message msg, int destNodeRandomId) {
        DHTNode currentNode = this;
        while(currentNode.getRandomId() != destNodeRandomId) {
            currentNode = currentNode.getRightNeighbor();
        }

        Node dest = Network.get(currentNode.getId());
	    this.transport.send(getMyNode(), dest, msg, this.mypid);
    }

    //affichage a la reception
    private void receive(Message msg) {
        LocalTime time = java.time.LocalTime.now(); 
	    System.out.println("At " + time + "\n" + this + ": Received \"" + msg.getContent() + "\"\n");
    }

    public void addData(Message object) {
        HashMap<Integer, Object> dataToInsert = new HashMap<Integer, Object>();
        dataToInsert.put(this.getRandomId(), object);

        this.getData().add(dataToInsert);
    }

    public void removeData(Message object) {
        HashMap<Integer, Object> dataToRemove = new HashMap<Integer, Object>();
        dataToRemove.put(this.getRandomId(), object);

        this.getData().remove(dataToRemove);
    }

    public void receiveData(Message object) {
        DHTNode bestNode = this.getRightNeighbor();
        for(int i=0; i < Network.size(); i++) {
            if(!bestNode.equals(null)) {
                DHTNode tempNode = (DHTNode) Network.get(i).getProtocol(this.mypid);
                if((int) Math.abs(tempNode.getRandomId() - object.getType()) < bestNode.getRandomId()) {
                    bestNode = tempNode;
                }
            }
        } 

        bestNode.addData(object);
        bestNode.getRightNeighbor().addData(object);
        bestNode.getLeftNeighbor().addData(object);

        LocalTime time = java.time.LocalTime.now(); 
	    System.out.println("\nAt " + time + "\n" + "Data inserted in : " + bestNode);
        System.out.println("Object iD : " + object.getType() + " | Node iD : " + bestNode.getRandomId() + "\n");
    }

    public DHTNode getLongNeighbor() { return this.longNeighbor; }

    public void setLongNeighbor(DHTNode longNeighbor) { this.longNeighbor = longNeighbor; }

    public void findAndSetLongNeighbor() {
        DHTNode currentNode = this;
        int iterationNumber = (int) Math.floor(Network.size()/2);
        for(int i = 0; i < iterationNumber; i++) {
            currentNode = currentNode.getRightNeighbor();
        }
        this.setLongNeighbor(currentNode);
        System.out.println("Taille du réseau : " + Network.size());
        System.out.print("Distance : " + iterationNumber + "\nOriginal Node : " + this.getId() + ":" + this.getRandomId() + "     Long Neighbor : " + currentNode.getId() + ":" + currentNode.getRandomId() +"\n\n");
    }

    public void updateLongNeighbor() {
        for(int i = 0; i < Network.size(); i++) {
            DHTNode currentNode = (DHTNode) Network.get(i).getProtocol(this.mypid);
            currentNode.findAndSetLongNeighbor();
        }
    }

    //retourne le noeud courant
    private Node getMyNode() {
	    return Network.get(this.nodeId);
    }

    public String toString() {
	    return "Node "+ this.nodeId;
    }

}