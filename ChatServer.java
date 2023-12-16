import java.io.*;
import java.net.*;
import java.security.KeyStore.Entry;
import java.util.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import java.time.LocalTime;

public class ChatServer {

	protected int serverPort = 8888;
	protected List<Socket> clients = new ArrayList<Socket>(); // list of clients
	private ChatServerConnector conn_delete_name;//To delete usernames

	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				synchronized(this) {
					clients.add(newClientSocket); // add client to the list of clients
				}
				ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); // create a new thread for communication with the new client
				conn_delete_name=conn;
				conn.start(); // run the new thread
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");

		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String message) throws Exception {
		Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	//To send only one person (self(Server msg)/someone else)
	public void sendToClient(String message, int port){
		Iterator<Socket> i = clients.iterator();
		Socket sendTo=null;
		while(i.hasNext()){
			Socket socket = (Socket) i.next();
			if(socket.getPort()==port){
				sendTo=socket;
				break;
			}
		}

		try{
			DataOutputStream out = new DataOutputStream(sendTo.getOutputStream());
			out.writeUTF(message);
		} catch (Exception e) {
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}
	}

	public void removeClient(Socket socket) {
		synchronized(this) {
			clients.remove(socket);
			conn_delete_name.RemoveUser(socket.getPort());//Remove username
		}
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;
	private String[] commands={"!a [username] -->add name","!w [username] -->whisper","!u -->all users","!n -->username","!? -->commands"}; 
	// To save usernames
	protected static Map<String,Integer> names=new HashMap<>();
	//To transform String(in msg) to JSONObject
	private JSONParser parser=new JSONParser();

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());
		
		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket);
			return;
		}

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			JSONObject message;
			try {
				msg_received = in.readUTF(); // read the message from the client
				message=(JSONObject)parser.parse(msg_received);
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket);
				break;
			}

			if (((String)message.get("message")).length() == 0){ // invalid message
				continue;
			}

			if(((String)message.get("type")).equals("private")){
				server_command(message);
			}else{
				message=AddNameSender(message);
				
				//Check if sender has a username
				if(((String)message.get("name")).equals("")){
					String errMessage="No username: !a [username], to set it!";
					send_err(message, errMessage, this.socket.getPort());
					continue;
				}

				try {
					this.server.sendToAllClients(message.toString()); // send message to all clients
				} catch (Exception e) {
					System.err.println("[system] there was a problem while sending the message to all clients");
					e.printStackTrace(System.err);
					continue;
				}
			}
		}
	}


	//To remove users
	public void RemoveUser(int port){
		String name="";
		if(names.containsValue(port)){
			for(Map.Entry<String,Integer> set: names.entrySet()){
				if(set.getValue()==port){
					name+=set.getKey();
					break;
				}
			}

			synchronized(this){
				names.remove(name);
			}

			try{
				JSONObject messageJSON=new JSONObject();
				messageJSON.put("type","public");
				messageJSON.put("name","Server");
				messageJSON.put("time",LocalTime.now().withNano(0).toString());
				messageJSON.put("message","User "+name+" Left!");
				this.server.sendToAllClients(messageJSON.toString());
			}catch(Exception e){
				System.err.println("[system] there was a problem while sending the message to all clients");
				e.printStackTrace(System.err);
			}
		}
	}

	//To send error messages
	private void send_err(JSONObject message,String errMessage,int port){
		message.put("type","Error");
		message.put("name","Server");
		message.put("message",errMessage);
		this.server.sendToClient(message.toString(),port);
	}

	//ServerCommands
	private void server_command(JSONObject inObj){
		String message=(String) inObj.get("message");
		String command=message.substring(1,2).toLowerCase();

		if(message.indexOf(' ')!=-1){
			message=message.substring(message.indexOf(' ')+1);
		}

		switch(command){
			case "a":

				//No name
				if(message.length()==2){
					String errMessage="Input name";
					send_err(inObj,errMessage,this.socket.getPort());
					break;
				}

				String name=message.substring(message.indexOf(' ')+1);

				//CheckName
				if(name.indexOf(' ')!=-1){
					String errMessage="No space in name!";
					send_err(inObj,errMessage, this.socket.getPort());
					break;
				}

				//Add user
				synchronized(this){
					if(names.containsKey(name) || names.containsValue(this.socket.getPort())){
						String errMessage="User exists!(!a [username] for new name) \n\tOr you already have a name!(check with !n)";
						send_err(inObj,errMessage, this.socket.getPort());
						break;
					}else{
						names.put(name,this.socket.getPort());
					}
				}

				//Message all users
				try {
					message="User joined: "+name;
					inObj.put("name","Server");
					inObj.put("message",message);
					this.server.sendToAllClients(inObj.toString());
				} catch (Exception e) {
					System.err.println("[system] there was a problem while sending the message to all clients");
					e.printStackTrace(System.err);
				}
				break;

			case "w":
				String recipient=null;

				//recipient
				try{
					recipient=message.substring(0,message.indexOf(' '));
				}catch(StringIndexOutOfBoundsException e1){
					String error="Empty message";
					send_err(inObj,error, this.socket.getPort());
					break;
				}

				message=message.substring(message.indexOf(' '));

				//Posiljanje sporocila
				try{
					inObj.put("message",message);
					inObj=AddNameSender(inObj);

					//V primeru, da pošiljatelj še nima imena
					if(((String)inObj.get("name")).equals("")){
						String errMessage="No username !a [username], to set it!";
						send_err(inObj, errMessage, this.socket.getPort());
						break;
					}

					this.server.sendToClient(inObj.toString(), names.get(recipient));
				}catch(NullPointerException e2){
					String error="User "+recipient+" doesn't exist!";
					send_err(inObj,error, this.socket.getPort());
				}

				break;

			case "?":
				String messageOut="";

				for(int i=0;i<commands.length;i++){
					messageOut+="\n\t"+commands[i];
				}

				inObj.put("name","Server");
				inObj.put("message",messageOut);
				this.server.sendToClient(inObj.toString(), this.socket.getPort());

				break;
			
			case "n":
				if(names.containsValue(this.socket.getPort())){
					String username="Your name is: ";
					for(Map.Entry<String,Integer> set: names.entrySet()){
						if(set.getValue()==this.socket.getPort()){
							username+=set.getKey();
							break;
						}
					}
					inObj.put("name","Server");
					inObj.put("message",username);
					this.server.sendToClient(inObj.toString(),this.socket.getPort());
				}else{
					String errMessage="No username add it with !a [ime]";
					send_err(inObj,errMessage, this.socket.getPort());
				}

				break;
			
			case "u":
				if(!names.isEmpty()){
					String users="Current Users: ";
					for(Map.Entry<String,Integer> set: names.entrySet()){
						users+="\n\t"+set.getKey();
						if(set.getValue()==this.socket.getPort()){
							users+="(you)";
						}
					}
					inObj.put("name","Server");
					inObj.put("message",users);
					this.server.sendToClient(inObj.toString(),this.socket.getPort());
				}else{
					String error="No users!";
					send_err(inObj,error, this.socket.getPort());
				}

				break;

			default:
				String error="No such command, !? for list of commands!";
				send_err(inObj,error, this.socket.getPort());

				break;
		}
	}
	
	//Doda ime pošiljatelja
	private JSONObject AddNameSender(JSONObject message){
		String name="";
		if(names.containsValue(this.socket.getPort())){
			for(Map.Entry<String,Integer> set: names.entrySet()){
				if(set.getValue()==this.socket.getPort()){
					name=set.getKey();
					break;
				}
			}
		}
		message.put("name",name);
		return message;
	}
}
