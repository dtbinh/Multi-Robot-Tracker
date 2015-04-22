package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import tracking.Robot;
import tracking.Vector2d;

public class LocationServer extends Thread{
	
	private static int PORT = 1338;
	private ServerSocket server;
	private TrackingSystem tracker;
	
	public LocationServer(TrackingSystem tracker) {
		try {
			server = new ServerSocket(PORT);
			this.tracker = tracker;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				Socket socket = server.accept();
				
				System.out.println("Client connected");
				tracker.getEnvironment().removeAllObjects();
				
				new InputHandler(socket).start();
				new OutputHandler(socket).start();
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	class OutputHandler extends Thread {
		private ObjectOutputStream output;
		
		public OutputHandler(Socket socket) throws IOException {
			output = new ObjectOutputStream(socket.getOutputStream());
		}
		
		public void run() {
			
			try {
			
				while(true) {
					//Enviar o Robot e o ID
					output.writeObject(tracker.getRobots().clone());

					output.flush();
					output.reset();
					Thread.sleep(10);
				}
			} catch(Exception e) {
				System.out.println("Client disconnected");
			}
		}
	}
	
	class InputHandler extends Thread {
		private ObjectInputStream input;
		
		public InputHandler(Socket socket) throws IOException {
			input = new ObjectInputStream(socket.getInputStream());
		}
		
		public void run() {
			
			try {
				while(true) {
					
					Robot object = (Robot)input.readObject();
					if(object.orientation == 1) {
						System.out.println("Received object "+object.x+" "+object.y);
						tracker.getEnvironment().addObjectCoordinates(new Vector2d(object.x,object.y));
					}else if(object.orientation == 0) {
						tracker.getEnvironment().removeObject(new Vector2d(object.x,object.y));
					}
					
				}
			} catch(Exception e) {
			}
		}
		
	}
	
}
