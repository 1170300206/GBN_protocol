package gbn;

import java.util.ArrayList;
import java.util.List;

public class PacketManager {
  // window size + 1 < sequence number size
  private int WINDOWSIZE;
  // sequence size should be smaller than 255
  private final int SEQSIZE;
  private final int PACKNUM;
  private final int MAXLENGTH;
  private int base = 0;
  private int nextSeqNum = 0;
  private final List<byte[]> packets;
  private final boolean[] acks;

  public PacketManager(int windowsize, int seqsize, int packNum, int maxLength) {
    WINDOWSIZE = windowsize;
    SEQSIZE = seqsize;
    PACKNUM = packNum;
    MAXLENGTH = maxLength;
    packets = new ArrayList<>();
    acks = new boolean[windowsize];
    // initiate all pacage's state inside the window
    for (int i = 0; i < windowsize; i++)
      acks[i] = false;
    for (int i = 0; i < PACKNUM; i++) {
      byte[] tmp = new byte[MAXLENGTH];
      tmp[0] = (byte) (i % seqsize);
      tmp[1] = (byte) i;
      packets.add(tmp);
    }
  }

  /**
   * Return the next sequence number
   * 
   * @return the next sequence number
   */
  public int getNextSeq() {
    return nextSeqNum;
  }

  /**
   * Return the base number.
   * 
   * @return the base number
   */
  public int getBase() {
    return base;
  }

  /**
   * Set the next sequence number.
   * 
   * @param seqNum next sequence number, base <= seqNum < base + WINDOWSIZE
   * @return true if successfully set, false if not
   */
  public boolean setNextSeq(int seqNum) {
    if (seqNum >= base && seqNum <= base + WINDOWSIZE) {
      nextSeqNum = seqNum;
      return true;
    }
    return false;
  }
  
  /**
   * Return the ack state of the current sequence number
   * @param index the sequence number
   * @return true if the package is received
   */
  public boolean getACK(int index) {
    return acks[(SEQSIZE + index-(base%SEQSIZE))%SEQSIZE];
  }

  /**
   * Set the ack = true for specific data package;
   * 
   * @param number sequence number of the package
   */
  public void setAck(int number) {
    acks[(SEQSIZE + number-(base%SEQSIZE))%SEQSIZE] = true;
  }

  /**
   * Sliding as much as it can
   * 
   * @return actual steps the window went.
   */
  public int sliding() {
    int steps = 0;
    for (int i = 0; i < WINDOWSIZE; i++) {
      if (acks[i]) {
        steps++;
      } else {
        // if meet with one that are not acked yet, stop sliding
        break;
      }
    }
    return sliding(steps);
  }

  /**
   * slide the window steps ahead.
   * 
   * @param steps steps the window needs to go, steps <= WINDOWSIZE
   * @return actual steps the window went.
   */
  public int sliding(int steps) {
    // steps = (steps > PACKNUM - (base + WINDOWSIZE)) ? PACKNUM - (base + WINDOWSIZE) : steps;
    // shift the ack values
    for (int i = 0; i < WINDOWSIZE - steps; i++) {
      acks[i] = acks[i + steps];
    }
    for (int i = WINDOWSIZE - steps; i < WINDOWSIZE; i++) {
      acks[i] = false;
    }
    // ensure the window will never go over the package list
    if (steps > PACKNUM - (base + WINDOWSIZE)) {
      WINDOWSIZE -= steps - (PACKNUM - (base + WINDOWSIZE));
    }
    // update the base
    base += steps;
    if(steps > 0) {
    System.out.println("window runs ahead " + steps + " steps, to the index " + base
        + ", window size is " + WINDOWSIZE);
    }
    return steps;
  }

  /**
   * Return the current window size.
   * @return the current window size
   */
  public int getWindowSize() {
    return WINDOWSIZE;
  }
  
  /**
   * Return the data with sequence number that are begin transfered.
   * 
   * @return the data that are begin transfered
   */
  public List<byte[]> data() {
    return packets;
  }
}
