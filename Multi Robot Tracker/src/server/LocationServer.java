package server;

import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import commoninterface.network.NetworkUtils;

public class LocationServer implements Serializable{
	
	private static final long serialVersionUID = 4377822865023552482L;
	private static int PORT = 8888;
	private static int RETRANSMIT_PORT = 8888+100;
	private BroadcastSender sender;
	private String ownAddress;
	private boolean retransmit = true;
	
	
	public LocationServer() {
		sender = new BroadcastSender();
	}
	
	public void sendMessage(String message) {
		sender.sendMessage(message);
	}
	
	class BroadcastSender {
		
		private DatagramSocket socket;
		private DatagramSocket retransmitSocket;
		
		public BroadcastSender() {
			try {
				InetAddress ownInetAddress = InetAddress.getByName(NetworkUtils.getAddress());
				ownAddress = ownInetAddress.getHostAddress();
				System.out.println("SENDER "+ownInetAddress);
				socket = new DatagramSocket(PORT+1, ownInetAddress);
				socket.setBroadcast(true);
				
				if(retransmit) {
					retransmitSocket = new DatagramSocket(RETRANSMIT_PORT-1,ownInetAddress);
					retransmitSocket.setBroadcast(true);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		public void sendMessage(String message) {
			try {
				byte[] sendData = message.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), PORT);
				socket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		public void retransmit(String message) {
			if(!retransmit)
				return;
			
			try {
				byte[] sendData = message.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), RETRANSMIT_PORT);
				retransmitSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
