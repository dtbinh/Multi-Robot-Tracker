package server;

import static com.googlecode.javacv.cpp.opencv_core.CV_AA;
import static com.googlecode.javacv.cpp.opencv_core.cvLine;
import static com.googlecode.javacv.cpp.opencv_core.cvPoint;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import server.utils.Prey;
import server.utils.Translator;
import tracking.GroundPoint;
import tracking.Robot;

import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import commoninterface.mathutils.Vector2d;
import commoninterface.network.broadcast.VirtualPositionBroadcastMessage;
import commoninterface.network.broadcast.VirtualPositionBroadcastMessage.VirtualPositionType;

public class ServerEnvironment {
	private static int WIDTH = 10;
	
	private int[] preyLocation = {0,200,20,120};
	//Adicionar os IP's dos robots por ordem de ID
	private String[] addresses = {"192.168.3.17","192.168.3.18"};
	
	private int numberOfPreys = 2;
	private double consumingDistance = 0.15;
	
	public LinkedList<Prey> preys = new LinkedList<Prey>();
	
	private LocationServer locationServer;
	private LinkedList<VirtualPositionBroadcastMessage> virtualPositionMessages;
	
	private int preysCaught = 0;
	
	private Thread senderThread;
	private boolean threadRunning = false;
	
	private Vector2d r;
	
	public ServerEnvironment() {
		locationServer = new LocationServer();
		virtualPositionMessages = new LinkedList<VirtualPositionBroadcastMessage>();
		
		for (int i = 0; i < numberOfPreys; i++)
			addObjectCoordinates(newPreyPosition());

		senderThread = new Thread(){
			public void run() {
				while(true){
					synchronized (virtualPositionMessages) {
						for (VirtualPositionBroadcastMessage m : virtualPositionMessages){
							locationServer.sendMessage(m.encode()[0]);
//							System.out.println("Message send!");
						}
					}
					
					try {
						sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
		};
		
	}
	
	private Vector2d newPreyPosition() {

		int minX = preyLocation[0];
		int maxX = preyLocation[1];
		int minY = preyLocation[2];
		int maxY = preyLocation[3];
		
		double x = (new Random()).nextDouble()*(maxX-minX)+minX;
		double y = (new Random()).nextDouble()*(maxY-minY)+minY;
		
		return new Vector2d(x, y);
	}
	
	public void addObjectCoordinates(Vector2d p) {
		Vector2d preyPosition = new Vector2d(p.x/100.0, p.y/100.0);
		Vector2d pixelPos = Translator.getPixelPosition(p);
		GroundPoint gp = new GroundPoint((int)pixelPos.x,(int)pixelPos.y,(int)p.x,(int)p.y);
		
		Prey prey = new Prey();
		prey.gp = gp;
		prey.pp = preyPosition;
		preys.add(prey);
		
	}
	
	public void drawObjects(IplImage img) {
		for(Prey prey : preys) {
			GroundPoint p = prey.gp;
			cvLine(img, cvPoint(p.imageX, p.imageY), cvPoint(p.imageX, p.imageY), CvScalar.RED, WIDTH, CV_AA, 0);
		}
		if(r != null) {
			Vector2d robotPos = new Vector2d(r);
			robotPos.x*=100;
			robotPos.y*=100;
			Vector2d pixelPos = Translator.getPixelPosition(robotPos);
			cvLine(img, cvPoint((int)pixelPos.x, (int)pixelPos.y), cvPoint((int)pixelPos.x, (int)pixelPos.y), CvScalar.RED, WIDTH, CV_AA, 0);
		}
		
	}
	
	public void removeAllObjects() {
		preys.clear();
	}	

	public void updateRobotsLocation(HashMap<Integer, Robot> robots){
		synchronized (virtualPositionMessages) {
			virtualPositionMessages.clear();
			
			int currentPreysCaught = preysCaught;
			
			for (Integer id : robots.keySet()) {
				if(addresses[id] != null){
					String networkAddress = addresses[id];
					Robot r = robots.get(id);
					
					this.r = r.getPosition();
					
					VirtualPositionBroadcastMessage m = new VirtualPositionBroadcastMessage(VirtualPositionType.ROBOT, networkAddress, r.x, r.y, r.orientation);
					virtualPositionMessages.add(m);
					
					Iterator<Prey> i = preys.iterator();
					
					while(i.hasNext()){
						Prey prey = i.next();
//						System.out.println(r.getPosition().distanceTo(prey.pp));
						if(r.getPosition().distanceTo(prey.pp)  < consumingDistance){
							i.remove();
							preysCaught++;
						}
					}
				}
			}
			
			if(currentPreysCaught != preysCaught)
				addObjectCoordinates(newPreyPosition());
			
			int preyNumber = 0;
			for (Prey prey : preys) {
				String preyName = "prey_"+preyNumber;
				
				VirtualPositionBroadcastMessage m = new VirtualPositionBroadcastMessage(VirtualPositionType.PREY, preyName, prey.pp.x, prey.pp.y, 0);
				virtualPositionMessages.add(m);
			}
		}
		
		if(!threadRunning){
			threadRunning = true;
			senderThread.start();
//			System.out.println("Sender Thread Started!");
		}
	}
	
	public int getPreysCaught() {
		return preysCaught;
	}
	
}

