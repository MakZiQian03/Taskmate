package my.edu.utar.taskmate;

public class ChatMessage {
    public enum Role { USER, BOT }

    public final Role role;
    public final String text;
    public final long timestamp;

    public ChatMessage(Role role, String text, long timestamp) {
        this.role = role;
        this.text = text;
        this.timestamp = timestamp;
    }
}


