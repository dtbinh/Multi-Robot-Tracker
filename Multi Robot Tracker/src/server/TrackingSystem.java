package server;

import static com.googlecode.javacv.cpp.opencv_core.cvCreateImage;
import static com.googlecode.javacv.cpp.opencv_core.cvSize;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvResize;

import java.awt.Dimension;
import java.util.HashMap;

import tracking.Robot;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

/**
 * Main class for the Location Server. It initiates the server
 * and the video tracker to get the robot's location and orientation.
 * 
 * @author miguelduarte
 *
 */
public class TrackingSystem extends Thread{
	
	private Video video;
	private ServerEnvironment environment;
	private LocationServer server;
	private IplImage smallImg;
	private static int WIDTH = 600;
	private static int HEIGHT = 400;
	
	private boolean measureLatency = false;
	
	public TrackingSystem() {
		video = new Video(true, measureLatency); 
		environment = new ServerEnvironment();
		server = new LocationServer(this);
	}
	
	@Override
	public void run() {
		
		video.start();
		server.start();
		
		CanvasFrame frame = new CanvasFrame("Tracking System");
		frame.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);
		frame.setPreferredSize(new Dimension(WIDTH, HEIGHT));
		smallImg = cvCreateImage(cvSize(WIDTH,HEIGHT), 8, 3);
		
		while(true) {
			try {
				
				Thread.sleep(50);
				
				if(video.currentImage != null) {
					environment.drawObjects(video.currentImage);
					
//					for(GroundPoint[] a : Translator.points) {
//						for(GroundPoint p : a)
//							cvLine(video.currentImage, cvPoint((int)p.imageX, (int)p.imageY), cvPoint((int)p.imageX, (int)p.imageY), CvScalar.RED, 5, CV_AA, 0);
//					}
					
					cvResize(video.currentImage, smallImg);
					frame.showImage(smallImg);
				}
				
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		new TrackingSystem().start();
	}
	
	public IplImage getImage() {
		return smallImg;
	}
	
	public HashMap<Integer, Robot> getRobots() {
		return video.getRobots();
	}
	
	public ServerEnvironment getEnvironment() {
		return environment;
	}

}
