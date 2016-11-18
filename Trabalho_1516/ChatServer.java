import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class Cliente {
	String sala;
	String nick;
	Socket socket;
	String estado;

	public Cliente(Socket s) {
		socket = s;
		estado = "init";
		nick = "";
	}
}

class Sala {
	String nome = null;
	LinkedList<Cliente> clients = new LinkedList<Cliente>();

	Sala(String n) {
		nome = n;
	}
}

public class ChatServer {
	// A pre-allocated buffer for the received data
	static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

	// Decoder for incoming text -- assume UTF-8
	static private final Charset charset = Charset.forName("UTF8");
	static private final CharsetDecoder decoder = charset.newDecoder();

	static private LinkedList<Cliente> users = new LinkedList<Cliente>();
	static private LinkedList<Sala> rooms = new LinkedList<Sala>();

	static public void main(String args[]) throws Exception {
		// Parse port from command line
		int port = Integer.parseInt(args[0]);

		try {
			// Instead of creating a ServerSocket, create a ServerSocketChannel
			ServerSocketChannel ssc = ServerSocketChannel.open();

			// Set it to non-blocking, so we can use select
			ssc.configureBlocking(false);

			// Get the Socket connected to this channel, and bind it to the
			// listening port
			ServerSocket ss = ssc.socket();
			InetSocketAddress isa = new InetSocketAddress(port);
			ss.bind(isa);

			// Create a new Selector for selecting
			Selector selector = Selector.open();

			// Register the ServerSocketChannel, so we can listen for incoming
			// connections
			ssc.register(selector, SelectionKey.OP_ACCEPT);
			System.out.println("Listening on port " + port);

			while (true) {
				// See if we've had any activity -- either an incoming
				// connection,
				// or incoming data on an existing connection
				int num = selector.select();

				// If we don't have any activity, loop around and wait again
				if (num == 0) {
					continue;
				}

				// Get the keys corresponding to the activity that has been
				// detected, and process them one by one
				Set keys = selector.selectedKeys();
				Iterator it = keys.iterator();
				while (it.hasNext()) {
					// Get a key representing one of bits of I/O activity
					SelectionKey key = (SelectionKey) it.next();

					// What kind of activity is it?
					if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {

						// It's an incoming connection. Register this socket
						// with
						// the Selector so we can listen for input on it

						Socket s = ss.accept();
						users.add(new Cliente(s));// ..
						System.out.println("Got connection from " + s);

						// Make sure to make it non-blocking, so we can use a
						// selector
						// on it.
						SocketChannel sc = s.getChannel();
						sc.configureBlocking(false);

						// Register it with the selector, for reading
						sc.register(selector, SelectionKey.OP_READ);

					} else if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {

						SocketChannel sc = null;

						try {

							// It's incoming data on a connection -- process it
							sc = (SocketChannel) key.channel();
							boolean ok = processInput(sc);

							// If the connection is dead, remove it from the
							// selector
							// and close it
							if (!ok) {
								key.cancel();

								Socket s = null;
								Sala sala = null;
								try {
									s = sc.socket();
									System.out.println("Closing connection to "
											+ s);
									for (Cliente i : users)
										if (i.socket.equals(s)) {
											sala = saladisponivel(i.sala);
											if (sala != null) {
												sendMessagetoRoom(i, sala, "",4); 
												sala.clients.remove(i);
												if (sala.clients.isEmpty()) {// remover sala da lista de salas
													rooms.remove(sala);
												}
											}
											users.remove(i);
											break;
										}

									s.close();
								} catch (IOException ie) {
									System.err.println("Error closing socket "
											+ s + ": " + ie);
								}
							}

						} catch (IOException ie) {

							// On exception, remove this channel from the
							// selector
							key.cancel();

							try {

								sc.close();

							} catch (IOException ie2) {
								System.out.println(ie2);
							}

							System.out.println("Closed " + sc);
						}
					}
				}

				// We remove the selected keys, because we've dealt with them.
				keys.clear();
			}
		} catch (IOException ie) {
			System.err.println(ie);
		}
	}

	static boolean disponivel(String nick) {
		if (users.isEmpty())
			return false;
		for (Cliente i : users) {
			if (i.nick.equals(nick))
				return false;
		}
		return true;
	}

	static Sala saladisponivel(String name) {
		for (Sala s : rooms) {
			if (s.nome.equals(name))
				return s;
		}
		return null;
	}
	
	static void sendMessage(Cliente c, String msg) {
		msg = msg + '\n';
		try {
			c.socket.getChannel().write(ByteBuffer.wrap(msg.getBytes()));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// (f)lag 1-> MESSAGE 2 -> JOINED 3-> NEWNICK 4-> LEFT
	static void sendMessagetoRoom(Cliente c, Sala s, String msg, int f) {
		if (f == 1)
			msg = "MESSAGE " + c.nick + " " + msg + '\n';
		else if (f == 2)
			msg = "JOINED " + c.nick + '\n';
		else if (f == 3)
			msg = "NEWNICK " + c.nick + " " + msg + '\n';
		else if (f == 4)
			msg = "LEFT " + c.nick + '\n';

		for (Cliente i : s.clients) {
			try {
				i.socket.getChannel().write(ByteBuffer.wrap(msg.getBytes()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static void sendPrivate(Cliente c, String receptor, String msg) {
		msg = "PRIVATE " + c.nick + msg + '\n';

		for (Cliente i : users) {
			try {
				if (i.nick.equals(receptor))
					i.socket.getChannel()
							.write(ByteBuffer.wrap(msg.getBytes()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Just read the message from the socket and send it to stdout
	static private boolean processInput(SocketChannel sc) throws IOException {
		// Read the message to the buffer
		String message;

		buffer.clear();
		int size = sc.read(buffer);
		System.out.println("bytes read: " + size);
		buffer.flip();

		// If no data, close the connection
		if (buffer.limit() == 0) {
			return false;
		}

		Cliente c = null;
		for (Cliente i : users) {
			if (i.socket.equals(sc.socket())) {
				c = i;
				break;
			}
		}

		message = decoder.decode(buffer).toString();
		//message = message.substring(2, message.length()); // isto é para
															// eliminar o lixo
		System.out.println(message);
		String[] splited = message.split("\\s+");
		
		if (message.length() == 0)
			return true;

		if (c.estado.equals("init")) {

			if (splited.length == 2 && splited[0].equals("/nick")) {
				if (disponivel(splited[1])) {
					c.nick = splited[1];
					c.estado = "outside";
					System.out.println("OK");
					sendMessage(c, "OK");

				} else {
					System.out.println("ERROR");
					sendMessage(c, "ERROR");

				}

				return true;
			}
			if (splited.length == 1 && splited[0].equals("/bye")) {
				sendMessage(c, "BYE");
				return false;
			}

			System.out.println("ERROR");
			sendMessage(c, "ERROR");
			return true;

		}

		if (c.estado.equals("outside")) {
			if (splited.length == 2 && splited[0].equals("/nick")) {
				if (disponivel(splited[1])) {
					c.nick = splited[1];
					System.out.println("OK");
					sendMessage(c, "OK");

				} else {
					System.out.println("ERROR");
					sendMessage(c, "ERROR");

				}
				return true;
			}
			if (splited.length == 2 && splited[0].equals("/join")) {
				Sala s = null;
				s = saladisponivel(splited[1]);
				if (s != null) {
					c.estado = "inside";
					c.sala = splited[1];
					sendMessage(c, "OK");
					sendMessagetoRoom(c, s, "", 2);
					s.clients.add(c);
				} else {
					s = new Sala(splited[1]);
					rooms.add(s);// add sala a lista de salas
					c.estado = "inside";
					c.sala = splited[1];
					sendMessage(c, "OK");
					s.clients.add(c); // add cliente a lista de clients a usar a
										// sala
				}

				return true;
			}
			if (splited.length == 1 && splited[0].equals("/bye")) {
				sendMessage(c, "BYE");
				return false;
			}

			if (splited.length >= 3 && splited[0].equals("/priv")) {
				if (!disponivel(splited[1])) { // se exister alguem com o nick
					sendMessage(c, "OK");
					String privMsg = "";
					for (int i = 2; i < splited.length; i++) {
						privMsg = privMsg + " " + splited[i];
					}
					sendPrivate(c, splited[1], privMsg);
				} else {
					System.out.println("ERROR");
					sendMessage(c, "ERROR");

				}
				return true;
			}

			System.out.println("ERROR");
			sendMessage(c, "ERROR");
			return true;
		}

		if (c.estado.equals("inside")) {
			Sala s = saladisponivel(c.sala); // s -> sala em que esta
			if (splited.length == 2 && splited[0].equals("/nick")) {
				if (disponivel(splited[1])) {
					sendMessagetoRoom(c, s, splited[1], 3);
					c.nick = splited[1];
					System.out.println("OK");
					sendMessage(c, "OK");

				} else {
					System.out.println("ERROR");
					sendMessage(c, "ERROR");
				}
				return true;
			}

			if (splited.length == 2 && splited[0].equals("/join")) {
				Sala s_new = null;
				s_new = saladisponivel(splited[1]); // Sala nova
				if (s_new != null) {
					c.sala = splited[1];
					sendMessage(c, "OK");
					sendMessagetoRoom(c, s, "", 4); // left sala antiga
					s.clients.remove(c);
					sendMessagetoRoom(c, s_new, "", 2); // joined nova sala
					s_new.clients.add(c);
				} else {
					s_new = new Sala(splited[1]);
					rooms.add(s_new);// add sala a lista de salas
					c.sala = splited[1];
					sendMessage(c, "OK");
					sendMessagetoRoom(c, s, "", 4); // left sala antiga
					s.clients.remove(c);
					s_new.clients.add(c); // add cliente a lista de clients a
											// usar a sala
				}

				return true;
			}

			if (splited.length == 1 && splited[0].equals("/leave")) {
				sendMessage(c, "OK");
				sendMessagetoRoom(c, s, "", 4);
				s.clients.remove(c);
				c.estado = "outside";
				c.sala = null;
				return true;
			}

			if (splited.length == 1 && splited[0].equals("/bye")) {
				sendMessage(c, "BYE");
				sendMessagetoRoom(c, s, "", 4);
				c.sala = null; // para depois nao dar 2 left quando cliente sai
				s.clients.remove(c);
				return false;
			}

			if (splited.length >= 3 && splited[0].equals("/priv")) {
				if (!disponivel(splited[1])) { // se exister alguem com o nick
					sendMessage(c, "OK");
					String privMsg = "";
					for (int i = 2; i < splited.length; i++) {
						privMsg = privMsg + " " + splited[i];
					}
					sendPrivate(c, splited[1], privMsg);
				} else {
					System.out.println("ERROR");
					sendMessage(c, "ERROR");

				}
				return true;
			}

			if (message.charAt(0) == '/')
				sendMessagetoRoom(c, s, message.substring(1), 1);
			else
				sendMessagetoRoom(c, s, message, 1);
			return true;
		}
		return true;
	}
}
