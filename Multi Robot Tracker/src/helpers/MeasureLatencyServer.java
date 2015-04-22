package helpers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MeasureLatencyServer extends Thread{
	
	private static int PORT = 1339;
	private ServerSocket server;
	private BufferedOutputStream output;
	
	
	public MeasureLatencyServer() {
		try {
			server = new ServerSocket(PORT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				Socket socket = server.accept();
				output = new BufferedOutputStream(socket.getOutputStream());
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void send(int response) {
		try {
			output.write(response);
			output.flush();
		}catch(Exception e){e.printStackTrace();}

	}
}
