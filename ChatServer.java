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
	private ChatServerConnector conn_delete_name;//Za brisanje imen uporabnikov, potem ko se uporabnik odklopi

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

	//Za posiljanje eni osebi(sebi(Server sporočila)/drugemu)
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
			conn_delete_name.odstrani_uporabnika(socket.getPort());//odstrani ime uporabnika
		}
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;
	private String[] ukazi={"!a [ime] -->dodajanje imena","!w [prejemnik] -->zasebno sporocilo(whisper)","!u -->vsi uporabniki","!n -->uporabnikovo ime","!? -->ukazi"}; 
	///Za shranjevanje imen uporabnikov
	protected static Map<String,Integer> imena=new HashMap<>();
	//Za transformacijo iz String(vhodno sporočilo) v JSONObject, ki bo nato uporabljen
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
				message=dodajIme_Pos(message);
				
				//Preverimo, ali ima pošiljatelj ime
				if(((String)message.get("name")).equals("")){
					String errMessage="Nimas se dolocenega imena !a [ime], da si ga nastaviš!";
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


	//Za odstranjevanje uporabnika
	public void odstrani_uporabnika(int port){
		String ime="";
		if(imena.containsValue(port)){
			for(Map.Entry<String,Integer> set: imena.entrySet()){
				if(set.getValue()==port){
					ime+=set.getKey();
					break;
				}
			}

			synchronized(this){
				imena.remove(ime);
			}

			try{
				JSONObject sporocilo=new JSONObject();
				sporocilo.put("type","public");
				sporocilo.put("name","Server");
				sporocilo.put("time",LocalTime.now().withNano(0).toString());
				sporocilo.put("message","Oseba "+ime+" je zapustila pogovor!");
				this.server.sendToAllClients(sporocilo.toString());
			}catch(Exception e){
				System.err.println("[system] there was a problem while sending the message to all clients");
				e.printStackTrace(System.err);
			}
		}
	}

	//Za posiljanje error sporocil 
	private void send_err(JSONObject message,String errMessage,int port){
		message.put("type","Error");
		message.put("name","Server");
		message.put("message",errMessage);
		this.server.sendToClient(message.toString(),port);
	}

	//Obravnavanje ukazov(dodajanje imena, privatna sporocila, itd..)
	private void server_command(JSONObject vhodniObj){
		String message=(String) vhodniObj.get("message");
		String command=message.substring(1,2).toLowerCase();

		if(message.indexOf(' ')!=-1){
			message=message.substring(message.indexOf(' ')+1);
		}

		switch(command){
			case "a":

				//Preverjanje dolzine sporocila(brez imena)
				if(message.length()==2){
					String errMessage="Vpisi veljavno ime";
					send_err(vhodniObj,errMessage,this.socket.getPort());
					break;
				}

				String ime=message.substring(message.indexOf(' ')+1);

				//Preverjanje imena(ne sme vsebovati presledkov)
				if(ime.indexOf(' ')!=-1){
					String errMessage="Ime mora biti brez presledkov!";
					send_err(vhodniObj,errMessage, this.socket.getPort());
					break;
				}

				//Dodajanje osebe
				synchronized(this){
					if(imena.containsKey(ime) || imena.containsValue(this.socket.getPort())){
						String errMessage="Oseba s tem imenom ze obstaja!(!a [ime] za novo ime) \n\tali pa ze imas doloceno ime!(preveri s !n)";
						send_err(vhodniObj,errMessage, this.socket.getPort());
						break;
					}else{
						imena.put(ime,this.socket.getPort());
					}
				}

				//Sporocilo vsem uporabnikom o pridruzitvi novega uporabnika
				try {
					message="Pridruzila se je oseba z imenom: "+ime;
					vhodniObj.put("name","Server");
					vhodniObj.put("message",message);
					this.server.sendToAllClients(vhodniObj.toString());
				} catch (Exception e) {
					System.err.println("[system] there was a problem while sending the message to all clients");
					e.printStackTrace(System.err);
				}
				break;

			case "w":
				String prejemnik=null;

				//Pridobivanje imena prejemnika
				try{
					prejemnik=message.substring(0,message.indexOf(' '));
				}catch(StringIndexOutOfBoundsException e1){
					String error="Prazno sporocilo";
					send_err(vhodniObj,error, this.socket.getPort());
					break;
				}

				message=message.substring(message.indexOf(' '));

				//Posiljanje sporocila
				try{
					vhodniObj.put("message",message);
					vhodniObj=dodajIme_Pos(vhodniObj);

					//V primeru, da pošiljatelj še nima imena
					if(((String)vhodniObj.get("name")).equals("")){
						String errMessage="Nimas se dolocenega imena !a [ime], da si ga nastaviš!";
						send_err(vhodniObj, errMessage, this.socket.getPort());
						break;
					}

					this.server.sendToClient(vhodniObj.toString(), imena.get(prejemnik));
				}catch(NullPointerException e2){
					String error="Oseba "+prejemnik+" ne obstaja!";
					send_err(vhodniObj,error, this.socket.getPort());
				}

				break;

			case "?":
				String sporocilo="";

				//Zbiranje in pošiljanje ukazov
				for(int i=0;i<ukazi.length;i++){
					sporocilo+="\n\t"+ukazi[i];
				}

				vhodniObj.put("name","Server");
				vhodniObj.put("message",sporocilo);
				this.server.sendToClient(vhodniObj.toString(), this.socket.getPort());

				break;
			
			case "n":
				//Vračanje imena
				if(imena.containsValue(this.socket.getPort())){
					String imeUporabnik="Tvoje ime je: ";
					for(Map.Entry<String,Integer> set: imena.entrySet()){
						if(set.getValue()==this.socket.getPort()){
							imeUporabnik+=set.getKey();
							break;
						}
					}
					vhodniObj.put("name","Server");
					vhodniObj.put("message",imeUporabnik);
					this.server.sendToClient(vhodniObj.toString(),this.socket.getPort());
				}else{
					String errMessage="Nimas se imena, dodaj ime s !a [ime]";
					send_err(vhodniObj,errMessage, this.socket.getPort());
				}

				break;
			
			case "u":
				//Vračanje uporabnikov
				if(!imena.isEmpty()){
					String uporabniki="Trenutni uporabniki: ";
					for(Map.Entry<String,Integer> set: imena.entrySet()){
						uporabniki+="\n\t"+set.getKey();
						if(set.getValue()==this.socket.getPort()){
							uporabniki+="(ti)";
						}
					}
					vhodniObj.put("name","Server");
					vhodniObj.put("message",uporabniki);
					this.server.sendToClient(vhodniObj.toString(),this.socket.getPort());
				}else{
					String error="Ni uporabnikov!";
					send_err(vhodniObj,error, this.socket.getPort());
				}

				break;

			default:
				//V primeru neveljavnega/neobstoječega ukaza
				String error="No such command, !? for list of commands!";
				send_err(vhodniObj,error, this.socket.getPort());

				break;
		}
	}
	
	//Doda ime pošiljatelja
	private JSONObject dodajIme_Pos(JSONObject message){
		String ime="";
		if(imena.containsValue(this.socket.getPort())){
			for(Map.Entry<String,Integer> set: imena.entrySet()){
				if(set.getValue()==this.socket.getPort()){
					ime=set.getKey();
					break;
				}
			}
		}
		message.put("name",ime);
		return message;
	}
}
