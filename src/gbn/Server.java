package gbn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The server that response and send datagram to the client.
 *
 */
public class Server {

  // socket: the udp socket of the server.
  // serverPort: listening port of the server
  // Length: maximum
  // length of the data
  // clientPort: listening port of the client
  // packet: using to transfer data
  // clientAddress: address of the client
  // state: state of the current process
  // dateFormat: system time format
  // WINDOWSIZE: size of the sending window
  // sequence number size: size of the sequence number
  // TIMEOUT: time to timeout
  private final DatagramSocket socket;
  private final int serverPort = 8388;
  private final int LENGTH = 1471;
  private final int packNum = 50;
  private int clientPort;
  private DatagramPacket packet;
  private InetAddress clientAddress;
  private int state = 0;
  private final DateFormat dateFormat =
      new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss", Locale.ENGLISH);
  // window size + 1 < sequence number size
  private final int WINDOWSIZE = 10;
  private final int SEQSIZE = 20;
  private final int TIMEOUT = 10000;
  private List<byte[]> data;
  private PacketManager manager;
  private int count;
  private final int MAXCOUNT = 5;
  private final double lostRate = 0.3;

  /**
   * Initiate the server's socket
   * 
   * @throws SocketException
   */
  public Server() throws SocketException {
    // create the server's socket
    socket = new DatagramSocket(serverPort);
    socket.setSoTimeout(TIMEOUT);
    System.out.println("Start a server process");
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
        new DatagramPacket(dataBytes, dataBytes.length, clientAddress, clientPort);
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
        new DatagramPacket(data, data.length, clientAddress, clientPort);
    return resPonsePacket;
  }

  public boolean response() {
    // get query from received data
    String query = new String(packet.getData(), 0, packet.getLength());
    if (query.equals("-testgbn")) {
      manager = new PacketManager(WINDOWSIZE, SEQSIZE, packNum, LENGTH);
      data = manager.data();
      // start to test gbn
      state = 1;
    } else if (query.equals("-time")) {
      // send time to the client
      String times = dateFormat.format(new Date());
      try {
        socket.send(genPac(times));
        System.out.println("Send time " + times + " to the client");
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    } else if (query.equals("-quit")) {
      // send ending message to the client
      String bye = "stop-the-transfer";
      try {
        socket.send(genPac(bye));
        System.out.println("Stop the transfer process");
        return false;
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    } else {
      try {
        socket.send(genPac(packet.getData()));
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return true;
  }

  /**
   * When timeout occurs, resend all the packages that aren't got ack.
   */
  public void timeout() {
    for (int i = manager.getBase(); i < manager.getNextSeq(); i++) {
      DatagramPacket sendingPacket = genPac(data.get(i));
      try {
        socket.send(sendingPacket);
        System.out.println("Resend packet #" + i + ", sequence number is " + i % SEQSIZE);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    // reset timer
    count = 0;
  }

  /**
   * Sending all available package in the waiting list.
   */
  public void send() {
    for (int i = manager.getNextSeq(); i < manager.getBase() + WINDOWSIZE; i++) {
      // start counting when the base is equal to getNextSeq
      if(manager.getNextSeq() == manager.getBase()) {
        count = 0;
      }
      manager.setNextSeq(manager.getNextSeq() + 1);
      // simulate the package lost
      if (Math.random() < lostRate) {
        System.out.println("package #" + i + " lost, sequence number is " + i % SEQSIZE);
        continue;
      }
      DatagramPacket sendingPacket = genPac(data.get(i));
      try {
        socket.send(sendingPacket);
        System.out.println("Sending the package #" + i + ", sequence number is " + i % SEQSIZE);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  /**
   * Start to test through gbn
   * 
   * @throws InterruptedException
   */
  public void gbn() {
    while (true) {
      // there is still package that can be sent
      if (manager.getNextSeq() < packNum) {
        send();
      }
      packet = new DatagramPacket(new byte[LENGTH], LENGTH);
      try {
        socket.receive(packet);
        byte seqNum = packet.getData()[0];
        // receive valid message
        if ((int) seqNum == manager.getBase() % SEQSIZE) {
          manager.sliding(1);
        } else {
          System.out.println("Server received invalid ack " + seqNum + ", count + 1");
          count++;
        }
        if (manager.getBase() == packNum) {
          System.out.println("transfer over");
          // stop gbn mode
          state = 0;
          break;
        }
      } catch (SocketTimeoutException e) {
        System.out.println("Server receiving ack time out, count + 1");
        count++;
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("---------------------------");
      if (count == MAXCOUNT) {
        System.out.println("Start to resend all un acked package");
        timeout();
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public void process() {
    while (true) {
      try {
        packet = new DatagramPacket(new byte[LENGTH], LENGTH);
        // trying to receive from client
        socket.receive(packet);
        // get port and address of the client
        clientPort = packet.getPort();
        clientAddress = packet.getAddress();
        System.out.println("receive package from " + clientAddress + ", port: " + clientPort);
        if (state == 0) {
          // process before start to transfer data
          if(!response()) {
            break;
          }
        }
        // if state changed after process, goes to the data transfer mode
        if (state == 1) {
          gbn();
        }
      } catch (SocketTimeoutException e) {
        continue;
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] arg) throws SocketException {
    Server server = new Server();
    server.process();
  }
}
