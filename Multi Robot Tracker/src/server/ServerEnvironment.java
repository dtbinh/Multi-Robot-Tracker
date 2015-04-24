package server;

import static com.googlecode.javacv.cpp.opencv_core.CV_AA;
import static com.googlecode.javacv.cpp.opencv_core.cvLine;
import static com.googlecode.javacv.cpp.opencv_core.cvPoint;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import server.utils.GroundPoint;
import server.utils.Translator;
import tracking.Robot;

import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import commoninterface.mathutils.Vector2d;
import commoninterface.network.broadcast.VirtualPositionBroadcastMessage;
import commoninterface.network.broadcast.VirtualPositionBroadcastMessage.VirtualPositionType;

public class ServerEnvironment {
	private static int WIDTH = 10;
	
	private int[][] preyLocation = {{15,85,20,105},{140,200,20,105}};
	//Adicionar os IP's dos robots por ordem de ID
	private String[] addresses = {"192.168.3.17"};
	
	private int numberOfPreys = 1;
	private double consumingDistance = 0.15;
	private double preyPercentage = 1;
	
	public LinkedList<Vector2d> objectsPositions = new LinkedList<Vector2d>();
	public LinkedList<GroundPoint> objectsCoordinates = new LinkedList<GroundPoint>();
	
	private LocationServer locationServer;

	private int preysCaught = 0;
	
	public ServerEnvironment() {
		locationServer = new LocationServer();
		
		for (int i = 0; i < numberOfPreys; i++)
			addObjectCoordinates(newPreyPosition());
	}
	
	private Vector2d newPreyPosition() {
		int index = 0;
		
		if(new Random().nextDouble() <= preyPercentage)
			index = 1;
		
		int minX = preyLocation[index][0];
		int maxX = preyLocation[index][1];
		int minY = preyLocation[index][2];
		int maxY = preyLocation[index][3];
		
		double x = (new Random()).nextDouble()*(maxX-minX)+minX;
		double y = (new Random()).nextDouble()*(maxY-minY)+minY;
		
		return new Vector2d(x, y);
	}
	
	public void addObjectCoordinates(Vector2d p) {
		objectsPositions.add(p);
		Vector2d pixelPos = Translator.getPixelPosition(p);
		objectsCoordinates.add(new GroundPoint(pixelPos.x,pixelPos.y,p.x,p.y));
	}
	
	public void drawObjects(IplImage img) {
		for(GroundPoint p : objectsCoordinates) {
			cvLine(img, cvPoint((int)p.getImageX(), (int)p.getImageY()), cvPoint((int)p.getImageX(), (int)p.getImageY()), CvScalar.RED, WIDTH, CV_AA, 0);
		}
	}
	
	public void removeObject(Vector2d object) {	
		for(int i = 0 ; i < objectsCoordinates.size() ; i++) {
			if(object.x == objectsCoordinates.get(i).getGroundX() && object.y == objectsCoordinates.get(i).getGroundY()) {
				objectsCoordinates.remove(i);
			}
		}
	}

	public void removeAllObjects() {
		objectsCoordinates.clear();
	}	

	public void updateRobotsLocation(HashMap<Integer, Robot> robots){
		LinkedList<VirtualPositionBroadcastMessage> virtualPositionMessages = new LinkedList<VirtualPositionBroadcastMessage>();
		
		for (Integer id : robots.keySet()) {
			if(addresses[id] != null){
				String networkAddress = addresses[id];
				Robot r = robots.get(id);
				
				VirtualPositionBroadcastMessage m = new VirtualPositionBroadcastMessage(VirtualPositionType.ROBOT, networkAddress, r.x, r.y, r.orientation);
				virtualPositionMessages.add(m);
				
				Iterator<Vector2d> i = objectsPositions.iterator();
				
				while(i.hasNext()){
					Vector2d prey = i.next();
					if(r.getPosition().distanceTo(prey)  < consumingDistance){
						objectsPositions.remove(prey);
						removeObject(prey);
						addObjectCoordinates(newPreyPosition());
						preysCaught++;
					}
				}
			}
		}
		
		int preyNumber = 0;
		for (Vector2d prey : objectsPositions) {
			String preyName = "prey_"+preyNumber;
			
			VirtualPositionBroadcastMessage m = new VirtualPositionBroadcastMessage(VirtualPositionType.PREY, preyName, prey.x, prey.y, 0);
			virtualPositionMessages.add(m);
		}
		
		for (VirtualPositionBroadcastMessage m : virtualPositionMessages)
			locationServer.sendMessage(m.encode());
		
	}
	
	public int getPreysCaught() {
		return preysCaught;
	}
	
}

