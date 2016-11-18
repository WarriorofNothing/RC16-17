import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {

	static Socket clientSocket;
	static DataOutputStream outToServer;
	static BufferedReader inFromServer;
	// Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
	JFrame frame = new JFrame("Chat Client");
	private JTextField chatBox = new JTextField();
	private JTextArea chatArea = new JTextArea();

	// --- Fim das variáveis relacionadas coma interface gráfica

	// Se for necessário adicionar variáveis ao objecto ChatClient, devem
	// ser colocadas aqui

	// Método a usar para acrescentar uma string à caixa de texto
	// * NÃO MODIFICAR *
	public void printMessage(final String message) {
		chatArea.append(message);
	}

	// Construtor
	public ChatClient() throws IOException {

		// Inicialização da interface gráfica --- * NÃO MODIFICAR *
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
		    public void WindowClosing(WindowEvent e) {
		        //System.out.println("test");
		        //fechar a socket quando fechar a janela de chat
		    	try{
		    	clientSocket.close();}
		    	catch(Exception eg){
		    		
		    	}
		    }
		});
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.add(chatBox);
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.SOUTH);
		frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
		frame.setSize(500, 300);
		frame.setVisible(true);
		chatArea.setEditable(false);
		chatBox.setEditable(true);
		chatBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					newMessage(chatBox.getText());
				} catch (IOException ex) {
				} finally {
					chatBox.setText("");
				}
			}
		});
		// --- Fim da inicialização da interface gráfica

		// Se for necessário adicionar código de inicialização ao
		// construtor, deve ser colocado aqui

	}

	// Método invocado sempre que o utilizador insere uma mensagem
	// na caixa de entrada
	public void newMessage(String message) throws IOException {
		// PREENCHER AQUI com código que envia a mensagem ao servidor
		
		
		// BufferedReader inFromUser = new BufferedReader(new
		// InputStreamReader(System.in));
		System.out.println(message);
		
		//outToServer.writeUTF(message);// bytes

		outToServer.write(  message.getBytes("UTF-8"));

		

	}

	// Método principal do objecto
	public void run() throws IOException {
		// PREENCHER AQUI
		String srv_sentence;
		String message="";
		String[] splited ;
		while((srv_sentence = inFromServer.readLine())!= null){
			//printMessage(srv_sentence+'\n');
			splited = srv_sentence.split(" ");// deixar ele mandar quantos espaços quer
			if(splited[0].equals("MESSAGE")){
				for(int i=2;i<splited.length;i++){
					message= message+" "+splited[i];
				}
				printMessage(splited[1]+":"+message+'\n');
				message="";
			}
			else if(splited[0].equals("NEWNICK")){
				printMessage(splited[1]+" mudou de nome para "+splited[2]+'\n');
			}
			else{
				printMessage(srv_sentence+'\n');
			}
		}
	}

	// Instancia o ChatClient e arranca-o invocando o seu método run()
	// * NÃO MODIFICAR *
	public static void main(String[] args) throws IOException {
		ChatClient client = new ChatClient();
		clientSocket = new Socket(args[0], Integer.parseInt(args[1]));
		outToServer = new DataOutputStream(clientSocket.getOutputStream());
		inFromServer = new BufferedReader(new InputStreamReader(
				clientSocket.getInputStream()));
		client.run();
	}

}
