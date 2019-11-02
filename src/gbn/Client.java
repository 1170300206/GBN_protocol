package gbn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Client {
  private final DatagramSocket socket;
  private final int INIWINDOWSIZE = 6;
  private final int serverPort = 8388;
  private final int clientPort = 8800;
  private final int SEQSIZE = 20;
  private final int packNum = 50;
  private final int LENGTH = 1471;
  private final InetAddress serverAddress = InetAddress.getLocalHost();
  private final int TIMEOUT = 5000;
  private List<byte[]> messages;
  private PacketManager manager;
  private List<byte[]> tempWindow;

  public Client() throws SocketException, UnknownHostException {
    tempWindow = new ArrayList<>();
    for (int i = 0; i < INIWINDOWSIZE; i++) {
      tempWindow.add(new byte[LENGTH]);
    }
    messages = new ArrayList<byte[]>();
    socket = new DatagramSocket(clientPort);
    socket.setSoTimeout(TIMEOUT);
    System.out.println("initiate the client service");
  }

  /**
   * generate a response packet to send to client
   * 
   * @param data the string format data
   * @return data that are already packet up
   */
  public DatagramPacket genPac(String data) {
    byte[] dataBytes = data.getBytes();
    DatagramPacket resPonsePacket =
        new DatagramPacket(dataBytes, dataBytes.length, serverAddress, serverPort);
    return resPonsePacket;
  }

  /**
   * generate a response packet to send to client
   * 
   * @param data the byte[] format data
   * @return data that are already packet up
   */
  public DatagramPacket genPac(byte[] data) {
    DatagramPacket resPonsePacket =
        new DatagramPacket(data, data.length, serverAddress, serverPort);
    return resPonsePacket;
  }

  /**
   * Sending a ack message with sequence number
   * 
   * @param num ack sequence number
   */
  public void sendAck(int num) {
    byte[] oneAck = new byte[1];
    oneAck[0] = (byte) num;
    try {
      socket.send(genPac(oneAck));
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
  }

  /**
   * Sending message to the server and fetch response
   * 
   * @param data the message to be sent
   */
  public String process(String data) {
    String msg = "no response";
    try {
      socket.send(genPac(data));
      System.out.println("sending msg:" + data);
      DatagramPacket packet = new DatagramPacket(new byte[LENGTH], LENGTH);
      socket.receive(packet);
      msg = new String(packet.getData());
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return msg;
  }

  public void gbn() {
    System.out.println("start gbn testing");
    int ack = SEQSIZE, expAck = 0, pacRec = 0;
    try {
      socket.send(genPac("-testgbn"));
      DatagramPacket packet = new DatagramPacket(new byte[LENGTH], LENGTH);
      while (true) {
        try {
          if (pacRec == packNum) {
            // got enough package
            process("-quit");
            // print all data
            int k = 0;
            for (byte[] tmp : messages) {
              k++;
              System.out.println("number " + k + " package is " + tmp[0]);
            }
            break;
          }
          socket.receive(packet);
          // get sequence number
          int seqNum = (int) packet.getData()[0];
          System.out.println(
              "Client received a package, sequence number is " + seqNum + ", expected " + expAck);
          // received correct ack
          if (seqNum == expAck) {
            // append all messages
            byte[] tmp = new byte[LENGTH - 1];
            System.arraycopy(packet.getData(), 1, tmp, 0, LENGTH - 1);
            messages.add(tmp);
            pacRec++;
            System.out.println("Client get expected sequence number: " + seqNum);
            expAck = (expAck + 1) % SEQSIZE;
            ack = seqNum;
          }
          sendAck(ack);
          System.out.println("sending ack: " + ack);
        } catch (SocketTimeoutException e) {
          if (pacRec != packNum) {
            // receive timeout, send ack again
            System.out.println("Resend ack: " + ack);
            sendAck(ack);
          }
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void sr() {
    manager = new PacketManager(INIWINDOWSIZE, SEQSIZE, packNum, LENGTH);
    messages = manager.data();
    try {
      socket.send(genPac("-testsr"));
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    DatagramPacket packet = new DatagramPacket(new byte[LENGTH], LENGTH);
    int pacRec = 0;
    while (true) {
      if (pacRec == packNum) {
        // got enough package
        process("-quit");
        // print all data
        int k = 0;
        for (byte[] tmp : messages) {
          k++;
          System.out.println("number " + k + " package is " + tmp[0]);
        }
        break;
      }
      try {
        socket.receive(packet);
        // get sequence number
        int seqNum = (int) packet.getData()[0];
        // drop the package if it is out of the window
        if (((SEQSIZE + seqNum - manager.getBase() % SEQSIZE) % SEQSIZE) >= manager
            .getWindowSize()) {
          continue;
        }
        if (!manager.getACK(seqNum)) {
          // received a valid package
          System.out.println("Client received a valid package, sequence number is " + seqNum);
          manager.setAck(seqNum);
          sendAck(seqNum);
          System.out.println("sending ack: " + seqNum);
          // append messages
          byte[] tmp = new byte[LENGTH - 1];
          System.arraycopy(packet.getData(), 1, tmp, 0, LENGTH - 1);
          System.out.println("update it to the #"
              + ((SEQSIZE + seqNum - manager.getBase() % SEQSIZE) % SEQSIZE + manager.getBase())
              + " package");
          messages.set(
              (SEQSIZE + seqNum - manager.getBase() % SEQSIZE) % SEQSIZE + manager.getBase(), tmp);
          manager.sliding();
          pacRec++;
        }
      } catch (SocketTimeoutException e) {

      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] arg) throws SocketException, UnknownHostException {
    Client client = new Client();
    client.process("-time");
    client.process("Hello");
    // client.gbn();
    client.sr();
  }
}
