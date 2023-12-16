import java.io.*;
import java.net.*;
import java.time.LocalTime;
import org.json.simple.*;
import org.json.simple.parser.*;


public class ChatClient extends Thread
{
	protected int serverPort = 8888;

	public static void main(String[] args) throws Exception {
		new ChatClient();
	}

	public ChatClient() throws Exception {
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;

		BufferedReader std_in = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Input Username.");
		String name= std_in.readLine();

		// connect to the chat server
		try {
			System.out.println("[system] connecting to chat server ...");
			socket = new Socket("localhost", serverPort); // create socket connection
			in = new DataInputStream(socket.getInputStream()); // create input stream for listening for incoming messages
			out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages
			System.out.println("[system] connected");
				
			//Send name
			this.sendMessage("!a "+name, out);

			ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in); // create a separate thread for listening to messages from the chat server
			message_receiver.start(); // run the new thread
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// read from STDIN and send messages to the chat server
		String userInput;
		while ((userInput = std_in.readLine()) != null) { // read a line from the console
			this.sendMessage(userInput, out); // send the message to the chat server
		}

		// cleanup
		out.close();
		in.close();
		std_in.close();
		socket.close();
	}

	private void sendMessage(String message, DataOutputStream out) {
		JSONObject JSONMessage=StartParseMessage(message);
		try {
			out.writeUTF(JSONMessage.toString()); // send the message to the chat server
			out.flush(); // ensure the message has been sent
		} catch (IOException e) {
			System.err.println("[system] could not send message");
			e.printStackTrace(System.err);
		}
	}

	// Parse message
	private JSONObject StartParseMessage(String message){
		JSONObject messageJSON=new JSONObject();

		//Private messages are commands(including whisper)
		if(message.charAt(0)=='!' && message.length()!=1){
			messageJSON.put("type","private");
		}else{
			messageJSON.put("type","public");
		}

		messageJSON.put("name","N/A");// Server adds username
		messageJSON.put("time",LocalTime.now().withNano(0).toString());
		messageJSON.put("message",message);

		return messageJSON;
	}
}

// wait for messages from the chat server and print the out
class ChatClientMessageReceiver extends Thread {
	private DataInputStream in;

	//To transform from String(message in) to JSONObject
	private JSONParser parser=new JSONParser();

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
	}

	public void run() {
		try {
			String message;
			while ((message = this.in.readUTF()) != null) { // read new message
				JSONObject messageJSON=new JSONObject();
				messageJSON=(JSONObject)parser.parse(message);
				System.out.println(EndMessageParse(messageJSON)); // print the message to the console
			}
		} catch (Exception e) {
			System.err.println("[!system!] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	//Parse end message
	private String EndMessageParse(JSONObject message){
		//[time name]: message
		return String.format("[%s %s]: %s",(String)message.get("time"),
		(String)message.get("name"),(String)message.get("message"));

	}
}
