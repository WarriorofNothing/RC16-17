import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;


public class ChatServer{


  enum State {INIT, OUTSIDE, INSIDE};

  public static class User{

    private String nickName;
    private SocketChannel sc;
    private State state;
    private String room;

    public User(String nickName, SocketChannel sc) {
     this.nickName = nickName;
     this.sc = sc;
     this.state = State.INIT;
     this.room = "";
   }

     public User(SocketChannel sc) {
      this.nickName = "";
      this.sc = sc;
      this.state = State.INIT;
      this.room = "";
    }

    public String getNickName() {
      return this.nickName;
    }

    public void setNickName(String n) {
      this.nickName = n;
    }

    public SocketChannel getSC() {
      return this.sc;
    }

    public State getState() {
      return this.state;
    }

    public void setState(State s) {
      this.state = s;
    }

    public String getRoom(){
      return this.room;
    }

    public void setRoom(String r){
      this.room = r;
    }

  }


  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();
  static private final CharsetEncoder encoder = charset.newEncoder();
  //users
  static private HashMap<SocketChannel,User> users = new HashMap<SocketChannel,User>();
  //nickNames
  static private HashMap<String, SocketChannel> nicks = new HashMap<String, SocketChannel>();
  //rooms
  static private HashMap<String,Set<SocketChannel>> rooms = new HashMap<String,Set<SocketChannel>>();
  //message types
  static private enum Kind {NEWNICK, MSG, JOINED, LEFT, ERROR, BYE};

  static private boolean inception = false;

  static private String incomplete_message = new String("");
  static private boolean incomplete = false;

  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );

    /**************************** SERVER *********************************/
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();
      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );
      // Get the Socket connected to this channel, and bind it to the
      //listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );
      // Create a new Selector for selecting
      Selector selector = Selector.open();
      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );
      /*************************************************************/

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();
          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );
            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            /************* NEW SOCKET(USER) *********************/
            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );
            //CREATES A NEW USER AND ADDS IT TO THE HashMap
            users.put(sc,new User(sc));

          } else if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
              SocketChannel sc = null;

            try {

              // It's incoming data on a connection -> process it!!!
              sc = (SocketChannel)key.channel();
              boolean ok = processInput(sc);

              /*************** USER LEAVES THE CHAT *********************/
              // If the connection is dead, remove it from the selector and close it
              if (!ok) {
                key.cancel();
                //REMOVE USER...

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }
        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc) throws IOException {
    //user
    User u = users.get(sc);
    State st = u.getState();
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();//modo de escrita
    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }
    //Decode and print the message to stdout
    String msg = decoder.decode(buffer).toString();
    /** GET \n **/
    if(!msg.contains("\n")){
      System.out.println("INCOMPLETE");
      incomplete_message+=msg;
      System.out.println(incomplete_message);
      incomplete = true;
    }
    else if(incomplete){
      System.out.println("INCOMPLETE -> COMPLETE");
      incomplete_message+=msg;
      msg = incomplete_message;
      incomplete = false;
    }

    if(!incomplete){
      System.out.println("COMPLETE");
     String[] splited = msg.split("\n+");
     /*** ESCAPES MULTIPLE \n ***/
     for( int i =0 ; i<splited.length; i++)
        System.out.println(splited[i]);
    //CHECKS IF message IS A MESSAGE OR COMMAND
    for( String message: splited){
      if(message.length()>0 && message.charAt(0)=='/'){
        if(message.length()>1 && message.charAt(1)=='/' && st == State.INSIDE)
          processMessage(sc, message.substring(1,message.length()), Kind.MSG);//escape "/"
        else
          processCommand(sc, message.substring(1,message.length()));//escape "/"
      }
      else if(st == State.INSIDE && message.length()>0)
        processMessage(sc, message, Kind.MSG);
      else
        processMessage(sc, message, Kind.ERROR);
      }
      incomplete_message = new String("");
    }
    return true;
  }

  //SENDS MESSAGE
 static private void sendMessage(SocketChannel sc, ByteBuffer msgBuffer, Kind k){
   User u = users.get(sc);
   if(u.getState() == State.INSIDE){
     Set<SocketChannel> set = rooms.get(u.getRoom());
     Iterator<SocketChannel> it = set.iterator();
     //msgBuffer.flip();
     if(k == Kind.MSG){
       try{
        //writes to it self
        sc.write(msgBuffer);
      }catch( IOException e ) {
          System.err.println( "Exception: couldn't write to socket" );
        }
     }
     if(k != Kind.ERROR){
       while(it.hasNext()){
         SocketChannel other_sc = it.next();
           msgBuffer.rewind();
           if(other_sc != sc){
             try{
              //writes to socket
              other_sc.write(msgBuffer);
            }catch( IOException e ) {
                System.err.println( "Exception: couldn't write to socket" );
              }
         }
     }
     }
   }

   //user isn't in any room
    else{
      ByteBuffer errorBuffer = ByteBuffer.allocate(16384);
      try{
        errorBuffer = encoder.encode(CharBuffer.wrap("ERROR\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: couldn't encode message" );
       }
       try{
        //writes error to socket
        sc.write(msgBuffer);
      }catch( IOException ie ) {
          System.err.println( "Exception:  couldn't write to socket" );
        }
    }

 }

 static private void processMessage(SocketChannel sc, String msg, Kind k){
   //FINAL MESSAGE - nickName: message
   ByteBuffer msgBuffer = ByteBuffer.allocate(16384);
   String sendersNick = users.get(sc).getNickName();
   User u = users.get(sc);
   //simple Message
   if(k == Kind.MSG){
     try{
        msgBuffer = encoder.encode(CharBuffer.wrap("MESSAGE "+sendersNick+" "+msg+"\n"));
     }catch(CharacterCodingException e){
       System.err.println( "Exception: "+e );
      }
    }
    //new nick - redefined
    else if(k == Kind.NEWNICK){
      try{
        msgBuffer = encoder.encode(CharBuffer.wrap("NEWNICK "+msg+"\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: "+e );
       }
    }
    //new user joins room
    else if(k == Kind.JOINED){
      try{
        msgBuffer = encoder.encode(CharBuffer.wrap("JOINED "+sendersNick+"\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: "+e );
       }
    }
    //user leaves room
    else if(k == Kind.LEFT){
      try{
        msgBuffer = encoder.encode(CharBuffer.wrap("LEFT "+sendersNick+"\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: "+e );
       }
    }
    //error
    else if(k == Kind.ERROR){
      try{
        msgBuffer = encoder.encode(CharBuffer.wrap("ERROR\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: "+e );
       }
    }
    sendMessage(sc, msgBuffer, k);
}

 static private void processCommand(SocketChannel sc, String cmd){
   String[] splited = cmd.split("\\s+");
   User u = users.get(sc);
   boolean valid = false, left = false;
   //CREATES A NEW nick
   if(splited[0].equals("nick") && splited.length==2){
    //VERIFIES IF THE nick ISN'T ALREADY TAKEN
     if(!nicks.containsKey(splited[1])){
       //reset nick
       if(nicks.containsValue(sc)){
         //removes old nick
         nicks.remove(u.getNickName());
          if(u.getState() == State.INSIDE)
          processMessage(sc, u.getNickName()+" "+splited[1], Kind.NEWNICK);
       }
       if(u.getState() == State.INIT)
        u.setState(State.OUTSIDE);
        u.setNickName(splited[1]);
        users.put(sc,u);
        nicks.put(splited[1],sc);
        valid = true;
        System.out.println("New nick: "+splited[1]);
      }
    else
     System.out.println("nickName - "+splited[1]+" - isn't valid");
  }

    //CREATES NEW ROOM AND ADDS USER TO THE NEW ROOM OR ADDS USER TO THE EXISTING ROOM
    else if(splited[0].equals("join") && splited.length == 2 && u.getState() != State.INIT){
        Set<SocketChannel> set = new HashSet<SocketChannel>();
        //leaves current room
        if(u.getState() == State.INSIDE){
          inception = true;
          processCommand(sc,"leave");
        }
        //ROOM ALREADY EXISTS
        if(rooms.containsKey(splited[1])){
          u.setRoom(splited[1]);
          u.setState(State.INSIDE);
          set = rooms.get(splited[1]);
          set.add(sc);
          rooms.put(splited[1], set);
          processMessage(sc,"", Kind.JOINED);
        }
        //CREATES NEW ROOM
        else{
          u.setRoom(splited[1]);
          u.setState(State.INSIDE);
          set.add(sc);
          rooms.put(splited[1], set);
        }
        inception = false;
        valid = true;
        System.out.println(u.getNickName()+" joined "+splited[1]);
      }
    //USER u LEAVES CHAT ROOM
    else if(splited[0].equals("leave") && splited.length == 1 && u.getState() == State.INSIDE){
      if(u.getRoom().equals(""))
        valid = false;
      else{
        Set<SocketChannel> set = rooms.get(u.getRoom());
        processMessage(sc, u.getNickName(), Kind.LEFT);
        set.remove(sc);
        rooms.put(u.getRoom(),set);
        u.setRoom("");
        u.setState(State.OUTSIDE);
        valid = true;
      }
    }
    else if(splited[0].equals("bye") && splited.length == 1){
      //close socket
      try {
        //send LEFT to the users in the room
        if(u.getState() == State.INSIDE)
          processMessage(sc, u.getNickName(), Kind.LEFT);
        ByteBuffer msgBuffer = ByteBuffer.allocate(16384);
        //message BYE
        try{
          msgBuffer = encoder.encode(CharBuffer.wrap("BYE\n"));
        }catch(CharacterCodingException e){
          System.err.println( "Exception: couldn't encode message" );
         }
         //BYE...
         try{
          //writes to socket
          sc.write(msgBuffer);
        }catch( IOException ie ) {
            System.err.println( "Exception:couldn't write to socket" );
          }
        nicks.remove(u.getNickName());
        users.remove(sc);
        sc.close();
        left = true;
      } catch (IOException e) {
        System.err.println("Exception: couldn't close socket");
      }
      //close socket
      sc.close();
      valid = true;
    }
    //private Message
    else if(splited[0].equals("priv") && splited.length>=3 && u.getState() != State.INIT){
      ByteBuffer msgBuffer = ByteBuffer.allocate(16384);
      //create message
      String msg = new String("PRIVATE "+u.getNickName());
      for(int i=2 ; i<splited.length; i++)
        msg+=" "+splited[i];
      //encode message
      try{
        msgBuffer = encoder.encode(CharBuffer.wrap(msg+"\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: couldn't encode message" );
       }
       //BYE...
       try{
        //writes to socket
        if(nicks.containsKey(splited[1])){
          nicks.get(splited[1]).write(msgBuffer);
          valid = true;
        }
      }catch( IOException ie ) {
          System.err.println( "Exception: couldn't write to socket" );
        }
    }
    //
    if(valid && !inception && !left){
      ByteBuffer ok = ByteBuffer.allocate(1638);
      try{
        ok = encoder.encode(CharBuffer.wrap("OK\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: couldn't encose message");
      }
      try{
          sc.write(ok);
        }catch(IOException e){
          System.err.println( "Exception: couldn't write to socket" );
        }
    }
    //WRITES ERROR TO SOCKET
    else if(!valid){
      ByteBuffer error = ByteBuffer.allocate(1638);
      try{
        error = encoder.encode(CharBuffer.wrap("ERROR\n"));
      }catch(CharacterCodingException e){
        System.err.println( "Exception: couldn't encode message");
      }
      try{
          sc.write(error);
        }catch(IOException e){
          System.err.println( "Exception: invalid command" );
        }
    }
  }
}
