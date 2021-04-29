package DHT;

public class Message {

    private int type;
    private String content;

    Message(int type, String content) {
        this.type = type;
        this.content = content;
    }

    public String getContent() { return this.content; }

    public int getType() { return this.type; }
    
}