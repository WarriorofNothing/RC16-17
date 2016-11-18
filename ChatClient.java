import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;


public class ChatClient {

    // VariÃ¡veis relacionadas com a interface grÃ¡fica --- * NÃƒO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variÃ¡veis relacionadas coma interface grÃ¡fica

    // Se for necessÃ¡rio adicionar variÃ¡veis ao objecto ChatClient, devem
    // ser colocadas aqui
    private SocketChannel sc;
    private BufferedReader buffer;
    private Boolean connected = false;
    private final Charset charset = Charset.forName("UTF8");
    private final CharsetEncoder encoder = charset.newEncoder();


    // MÃ©todo a usar para acrescentar uma string Ã  caixa de texto
    // * NÃƒO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // InicializaÃ§Ã£o da interface grÃ¡fica --- * NÃƒO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
                if (connected)
                  System.exit(0);
            }
        });
        // --- Fim da inicializaÃ§Ã£o da interface grÃ¡fica

        // Se for necessÃ¡rio adicionar cÃ³digo de inicializaÃ§Ã£o ao
        // construtor, deve ser colocado aqui
        try {
          sc = SocketChannel.open();
          sc.configureBlocking(true);
          sc.connect(new InetSocketAddress(server, port));
        } catch (IOException e) {
          System.out.println(e);
        }


    }


    // MÃ©todo invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
      sc.write(encoder.encode(CharBuffer.wrap(message+"\n")));
    }

    // MÃ©todo principal do objecto
    public void run() throws IOException {
      try {
        while (!sc.finishConnect());
      } catch (Exception ex) {
        System.out.println("There was an error connecting with the server! (" + ex.getMessage() + ")");
        System.exit(0);
        return;
      }

      buffer = new BufferedReader(new InputStreamReader(sc.socket().getInputStream()));

      while (true) {
        String msg = buffer.readLine();
        if (msg == null)
          break;
        // *** beauty **
        String[] splited = msg.split("\\s+");
        if(splited[0].equals("MESSAGE")){
          msg=new String(splited[1]+":");
          for(int i=2 ; i<splited.length; i++)
            msg+=" "+splited[i];
        }
        else if(splited[0].equals("NEWNICK"))
          msg=new String(splited[1]+" mudou de nome para "+splited[2]);

        printMessage(msg+"\n");
      }
      sc.close();

      try {
        Thread.sleep(46);//???
      } catch (InterruptedException ex) {
        System.out.println(ex.getMessage());
        System.exit(0);
        return;
      }
      connected = true;
    }


    // Instancia o ChatClient e arranca-o invocando o seu mÃ©todo run()
    // * NÃƒO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
Contact GitHub API Training Shop Blog About
