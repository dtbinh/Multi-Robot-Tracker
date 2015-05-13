package server;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import helpers.MeasureLatencyServer;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Scanner;

import server.utils.CircleMarker;
import server.utils.HSVColor;
import server.utils.PixelOperations;
import server.utils.Translator;
import tracking.Robot;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvPoint3D32f;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_highgui;
import com.googlecode.javacv.cpp.opencv_highgui.CvCapture;

import commoninterface.mathutils.Vector2d;

/**
 * Handles the robot tracking based on images from a camera.
 * In our setup, the camera is positioned in the ceiling at
 * around 3 meters. It covers a span of around 2.3 meters in
 * width and 1.5 meters in height. The robot has a marker on top,
 * with a diameter of 8 centimeters. The marker is a white circle
 * with a black cross. The arms of the cross have a width of roughly
 * 3 centimeters. An example of the marker can be seen here:
 * http://miguelduarte.pt/media/robot_marker.jpg
 * 
 * The parameters of the video processing algorithm might have to
 * be tweaked depending on the lighting or camera setup. The algorithm
 * is as follows:
 * 
 * 1) Value-based threshold to find the white circle, on a grayscale version
 * 		of the image
 * 2) Dilation of the image to "hide" the cross in the circle
 * 3) Erosion to return the circle to its original size, without the cross
 * 4) Canny to detect the edges of the circle
 * 5) Hough Circles to detect the center and radius of the circle
 * 6) Image crop to work on the circle area only
 * 7) Value-based threshold to find the cross
 * 8) Hough Lines to find all the lines in the cross
 * 9) Line intersection to find perpendicular lines
 * 10) Detection of longer line in order to assess the orientation
 * 
 * In order to increase performance, the image is cropped if the circle
 * was found on the previous cycle. From my measurements, it takes roughly
 * 3 miliseconds to process one frame, on a 3.0Ghz 8-core AMD processor.
 * 
 * This currently tracks only one robot.
 * 
 * @author miguelduarte
 *
 */
public class Video extends Thread{
	static int widthRoi = 100;
	public static double markerRadius = 40;
	
	public int frames = 0;
	public int successAngle = 0;
	public int successCircle = 0;
	public IplImage currentImage;
    private CanvasFrame canvas;
    private boolean displayVideo = false;
    //ColorID - Circule
    private HashMap<Integer, CircleMarker> circlesMap;
    private HashMap<Integer, CircleMarker> oldCirclesMap;
    //ColorID - Number of appearances
    private HashMap<Integer, Integer> colorAppearances;
    //ColorID - Thread
    private HashMap<Integer, ComputateCrossMarker> processingThreads;
    private HashMap<ComputateCrossMarker, Robot> threadsRobots;
    //ColorID - Robot
    private HashMap<Integer, Robot> robots;
    private HashMap<Integer, Color> robotIdsColor;
    
    private int threadsDone = 0;
	private int numberOfRobots = 0;
    
	private int[][] colorsIntervals;
	
	Random r = new Random();
    IplImage image;
    
    long startingTime = System.currentTimeMillis();
	
    private MeasureLatencyServer latencyServer;
    private ServerEnvironment environment;
    
    public Video(boolean displayVideo, boolean measureLatency) {
		colorAppearances = new HashMap<Integer, Integer>();
    	circlesMap = new HashMap<Integer, CircleMarker>();
    	oldCirclesMap = new HashMap<Integer, CircleMarker>();
    	processingThreads = new HashMap<Integer, ComputateCrossMarker>();
    	threadsRobots = new HashMap<ComputateCrossMarker, Robot>();
    	robots = new HashMap<Integer, Robot>();
    	robotIdsColor = new HashMap<Integer, Color>();
    	environment = new ServerEnvironment();
    	
    	colorsIntervals = loadColorsConfigurationFromFile();
    	
    	for (int i = 0; i < colorsIntervals.length; i++) 
			colorAppearances.put(i, 0);
		
		
    	if(measureLatency){
			latencyServer = new MeasureLatencyServer();
			latencyServer.start();
		}
    	
		this.displayVideo = displayVideo;
		if(displayVideo) {
			canvas = new CanvasFrame("Camera");
			canvas.setPreferredSize(new Dimension(600, 400));
			canvas.setLocation(620, 0);
		}
		
	}
    
	@Override
	public void run() {
		Thread.currentThread().setName("Thread Video");
		//Select capturing camera
		CvCapture capture = opencv_highgui.cvCreateCameraCapture(0);

        opencv_highgui.cvSetCaptureProperty(capture, opencv_highgui.CV_CAP_PROP_FRAME_HEIGHT, 960);
        opencv_highgui.cvSetCaptureProperty(capture, opencv_highgui.CV_CAP_PROP_FRAME_WIDTH, 1280);
        
		while (true) {
			if((image = opencv_highgui.cvQueryFrame(capture)) == null) {
				break;
			} else {
//				drawpoint();
//				countFramesPerSecond();
				processImage();
			}
		}	
	}

	private void processImage() {
		numberOfRobots = 0;
		threadsDone = 0;
		circlesMap.clear();
		
		if(currentImage == null)
			currentImage = IplImage.create(cvGetSize(image), 8, 3);
		
//		showImage(image);
		
		
		//TODO -- look into this
//		CvRect roi = cvRect(40, 85, 1160, 730);
//		
//		cvSetImageROI(image, roi);
//		IplImage croppedImage = IplImage.create(cvSize(roi.width(), roi.height()), 8, 3);
//		cvCopy(image, croppedImage);
//		cvResetImageROI(image);
		
		IplImage croppedImage = IplImage.create(cvSize(image.width(), image.height()), 8, 3);
		cvCopy(image, croppedImage);
		
		
//		if(successfulIntersection) {
//			roi = cvRect((int)center.x()-widthRoi/2, (int)center.y()-widthRoi/2, widthRoi, widthRoi);
//			cvSetImageROI(image,roi);
//		}
		
		IplImage imageGray = IplImage.create(cvGetSize(croppedImage), 8, 1);
		
		cvCvtColor(croppedImage, imageGray, CV_RGB2GRAY);
		
		CvMemStorage storage = cvCreateMemStorage(0);
			
		cvThreshold(imageGray, imageGray, 115, 255, CV_THRESH_BINARY); //100-255
//		showImage(imageGray);
		
//		cvNot(imageGray, imageGray);
//		showImage(imageGray);
//	    cvDilate(imageGray, imageGray, null, 5);
//	    showImage(imageGray);
//	    cvErode(imageGray, imageGray, null, 5);
//	    showImage(imageGray);
	    
		cvSmooth(imageGray, imageGray, CV_GAUSSIAN, 3);
	    
		cvCanny(imageGray, imageGray, 100, 100, 3);//100 100 3
//		showImage(imageGray);
		
		//Find circles
		CvSeq circles = cvHoughCircles( 
				imageGray, //Input image
				storage, //Memory Storage
			    CV_HOUGH_GRADIENT, //Detection method
			    1, //Inverse ratio
			    100, //Minimum distance between the centers of the detected circles
			    10, //Higher threshold for canny edge detector
			    15, //Threshold at the center detection stage
			    15, //min radius
			    30 //max radius
			    );
		
//		if(successfulIntersection)
//			cvResetImageROI(image);
		
		int numberOfCircles = circles.total();
		if(numberOfCircles == 0)
			System.out.println("Not detecting any circle");
		
		LinkedList<Integer> colorsList = new LinkedList<Integer>();
		for (int i = 0; i < colorsIntervals.length; i++) 
			colorsList.add(i);
		
		//Percorre os circulos e cria o HashMap <Cor, Circulo>
		for (int i = 0; i < numberOfCircles; i++){
			CvPoint3D32f point3D = new CvPoint3D32f(cvGetSeqElem(circles, i));
			
			CircleMarker circle = new CircleMarker(point3D.x(), point3D.y(), point3D.z());
			CvPoint3D32f.deallocateReferences();
			
			CvPoint p = new CvPoint(1124,100);
			cvLine(image, p, p, CvScalar.WHITE, 3, CV_AA, 0);
			
			double[] hsv = getHSVMarkerColor(croppedImage, (int)circle.getX(), (int)circle.getY());
//			double[] corner = PixelOperations.getPixelHSV(image,p.x(),p.y());
			Integer robotID = getColorId(hsv[0], hsv[1], hsv[2]);
//			System.out.println("Robot ID: " + robotID);
			if(robotID != null){
				int times = colorAppearances.get(robotID);
				if(times < 30){
					times ++;
					colorAppearances.put(robotID, times);
				}
				colorsList.remove(robotID);
				
				if (times > 5){
					if(oldCirclesMap.containsKey(robotID)){
						CircleMarker oldCircle = oldCirclesMap.get(robotID);
						double dist = circle.calculateDistance(oldCircle);
//						System.out.println("Robot ID: " + robotID + ", distance: " + dist);
						if(dist <= markerRadius){
							addToCirclesMap(robotID, circle, hsv);
						}//else
//							System.out.println("Long distance -> X: " + circle.getX() + ", Y: " + circle.getY() + ", oldX: " + oldCircle.getX() + ", oldY: " + oldCircle.getY());
					}else{
						addToCirclesMap(robotID, circle, hsv);
					}
				}
			}
		}

//		Put the counter value of the colors in the List to 0 
		for (Integer robotID : colorsList) {
			if(colorAppearances.get(robotID) > 0){
				int times = colorAppearances.get(robotID) - 1;
				colorAppearances.put(robotID, times);
			}
		}
		
//		If color disappear:
//		Removes the color from the Hash
//		Removes the running threads of the colors in the List and
		for (Integer robotID : colorAppearances.keySet()) {
			if(colorAppearances.get(robotID) <= 0){
					if(oldCirclesMap.containsKey(robotID)){
						oldCirclesMap.remove(robotID);
//						System.out.println("Color ID" + robotID + " removed from the old circles hash map!");
					}
					
					if(processingThreads.containsKey(robotID)){
						ComputateCrossMarker ccm = processingThreads.get(robotID);
						ccm.interrupt();
						processingThreads.remove(robotID);
//						System.out.println(ccm.getName() + " interrupted and color ID " + robotID + "removed from the processing threads hash!");
					}
					
					if(robotIdsColor.containsKey(robotID)){
						robotIdsColor.remove(robotID);
//						System.out.println("Color ID " + robotID + "removed from the colors hash!");
					}
			}
		}
		
		for (Integer robotID : circlesMap.keySet()) {
			ComputateCrossMarker ccm;
			
			if(processingThreads.containsKey(robotID)){
				ccm = processingThreads.get(robotID);
			}else{
				ccm = new ComputateCrossMarker(this,("Thread " + robotID));
				processingThreads.put(robotID, ccm);
				threadsRobots.put(ccm, new Robot());
				ccm.start();
			}
			
			IplImage imageCopy  = IplImage.create( cvGetSize(croppedImage), 8, 3);
			cvCopy(croppedImage, imageCopy);
			ccm.setColor(robotIdsColor.get(robotID));
			ccm.setParameters(imageCopy, circlesMap.get(robotID));
			
		}
		
//		System.out.println("THREADS DONE: " + threadsDone + ", NUMBER OF ROBOTS: " + numberOfRobots);
		while (threadsDone < numberOfRobots){
			synchronized (this) {
				try {
//					System.out.println("Waiting threads to finish ...");
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		boolean successfulIntersection = false;
		
		for (ComputateCrossMarker t : processingThreads.values()) {
			//Check when not get the intersection
			successfulIntersection = t.isSuccessfulIntersection();
			
			cvLine(croppedImage, t.getEstTail(), t.getEstIntersection(), CvScalar.YELLOW, 3, CV_AA, 0);
	        cvLine(croppedImage, t.getEstCenter(), t.getEstCenter(), CvScalar.RED, 3, CV_AA, 0);
	        cvLine(croppedImage, t.getEstTail(), t.getEstTail(), CvScalar.BLUE, 3, CV_AA, 0);
	        cvLine(croppedImage, t.getEstIntersection(), t.getEstIntersection(), CvScalar.BLUE, 3, CV_AA, 0);
	        
	        Robot r = threadsRobots.get(t);
	        
	        Vector2d robotPosition = pointToVector(t.robotPosition);
			Vector2d realPosition = Translator.getRealPosition(robotPosition);
			
			r.setPosition(realPosition);
			r.orientation = Math.toRadians(t.robotOrientation);
			
			Integer robotID = getRobotID(t);
			robots.put(robotID, r);
			
			t.setLabelText(t.getColor(), robotID, r.getPosition(), t.robotOrientation, t.isSuccessfulIntersection());
		}

		if(successfulIntersection){
			//Set the environment changes and broadcast the information
			environment.updateRobotsLocation(robots);
		}
		
		if(latencyServer != null)
			measureLatency();
		
		showImage(croppedImage);
        
		cvCopy(image, currentImage);
		
		cvReleaseMemStorage(circles.storage());
		imageGray.release();
		croppedImage.release();
	}

	public HashMap<Integer, Robot> getRobots() {
		return robots;
	}
	
	private Integer getRobotID(ComputateCrossMarker robotThread){
		for (Integer robotID : processingThreads.keySet()) {
			if(processingThreads.get(robotID).equals(robotThread))
				return robotID;
		}
		return null;
	}
	
	
	private double[] getHSVMarkerColor(IplImage img, int markerX, int markerY){
		ArrayList<HSVColor> colors = new ArrayList<HSVColor>();
		
		int sampleRange = 2;
		
		double[] hsv = PixelOperations.getPixelHSV(img, markerX, markerY);
		colors.add(new HSVColor(hsv[0], hsv[1], hsv[2]));
		
		double[] hsv2 = PixelOperations.getPixelHSV(img, markerX, markerY - sampleRange);
		colors.add(new HSVColor(hsv2[0], hsv2[1], hsv2[2]));
		
		double[] hsv3 = PixelOperations.getPixelHSV(img, markerX - sampleRange, markerY - sampleRange);
		colors.add(new HSVColor(hsv3[0], hsv3[1], hsv3[2]));
		
		double[] hsv4 = PixelOperations.getPixelHSV(img, markerX + sampleRange, markerY - sampleRange);
		colors.add(new HSVColor(hsv4[0], hsv4[1], hsv4[2]));
		
		double[] hsv5 = PixelOperations.getPixelHSV(img, markerX - sampleRange, markerY);
		colors.add(new HSVColor(hsv5[0], hsv5[1], hsv5[2]));
		
		double[] hsv6 = PixelOperations.getPixelHSV(img, markerX + sampleRange, markerY);
		colors.add(new HSVColor(hsv6[0], hsv6[1], hsv6[2]));
		
		double[] hsv7 = PixelOperations.getPixelHSV(img, markerX, markerY + sampleRange);
		colors.add(new HSVColor(hsv7[0], hsv7[1], hsv7[2]));
		
		double[] hsv8 = PixelOperations.getPixelHSV(img, markerX - sampleRange, markerY + sampleRange);
		colors.add(new HSVColor(hsv8[0], hsv8[1], hsv8[2]));
		
		double[] hsv9 = PixelOperations.getPixelHSV(img, markerX + sampleRange, markerY + sampleRange);
		colors.add(new HSVColor(hsv9[0], hsv9[1], hsv9[2]));
		
		double hue = 0;
		double saturation = 0;
		double brightness = 0;
		int size = 0;
		
		for (HSVColor c : colors) {
			hue += c.getHue();
			saturation += c.getSaturation();
			brightness += c.getBrightness();
			size++;
		}
		
		hue /= size;
		saturation /= size;
		brightness /= size;
		
		return new double[] {hue,saturation,brightness};
	}
	
	private void addToCirclesMap(Integer robotID, CircleMarker circle, double[] hsv) {
		numberOfRobots++;
		circlesMap.put(robotID, circle);
		oldCirclesMap.put(robotID, circle);
		Color c = PixelOperations.getHSVColor(hsv[0], hsv[1], hsv[2]);
		robotIdsColor.put(robotID, c);
//		System.out.println("Circle added to the Circles Map");
	}
	
	public synchronized void incrementThreadsDone(){
		threadsDone++;
		
		if(threadsDone == numberOfRobots){
//			System.out.println("All threads finished");
			notify();
		}else if(threadsDone > numberOfRobots){
			System.out.println("Threads done can't be bigger then number of robots");
		}
	}
	
	private Integer getColorId(double hue, double saturation, double brightness) {
		for (int i = 0; i < colorsIntervals.length; i++) {
			if(hue >= colorsIntervals[i][0] && hue <= colorsIntervals[i][1] && saturation >= colorsIntervals[i][2] && saturation <= colorsIntervals[i][3] && brightness >= colorsIntervals[i][4] && brightness <= colorsIntervals[i][5]){
				return i;
			}
		}
//		System.out.println("Null Color -> H: " + hue + ", S: " + saturation + ", V: " + brightness);
		return null;
	}
	
	private int[][] loadColorsConfigurationFromFile() {
		Scanner scanner = null;
		int[][] colorConfig = null;
		try {
			scanner = new Scanner(new File("color_configuration.txt"));
			
			while (scanner.hasNextLine()){
				String line = scanner.nextLine();
				
				if(line.contains("#")){
					line = line.replace("#", "");
					int rows = Integer.valueOf(line);
					colorConfig = new int[rows][6];
				}else{
					String[] aux = line.split(" ");
					int robotID = Integer.valueOf(aux[0]);
					String[] values = aux[1].split(",");
					for (int i = 0; i < colorConfig[robotID].length; i++) {
						colorConfig[robotID][i] = Integer.valueOf(values[i]);
					}
				}
				
			}
			return colorConfig;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}finally{
			if(scanner != null)
				scanner.close();
		}
		return null;
	}
	
	private Vector2d pointToVector(CvPoint p) {
		return new Vector2d(p.x(),p.y());
	}

	public ServerEnvironment getEnvironment() {
		return environment;
	}

	
	public void measureLatency(){
		int arenaCenterX = 560;
		int arenaCenterY = 420;
		
		int[] rgb = PixelOperations.getPixelRGB(image, arenaCenterX, arenaCenterY);
		
		if(rgb[0] > 120 && rgb[1] < 10 && rgb[2] < 10){
			latencyServer.send(1);
		}
	}
	
	private void countFramesPerSecond() {
		
		double timeElapsed = (System.currentTimeMillis()-startingTime)/1000.0;
		
		if(frames > 100)
			System.out.println("fps: "+((frames-100)/timeElapsed +" "+(frames-100)));
		else
			startingTime = System.currentTimeMillis();
		
		frames++;
		
		if(frames > 200)
			frames = 99;
		
	}
	
	private void showImage(IplImage img) {
		if(displayVideo && img != null && canvas != null){
			if(canvas != null)
				canvas.showImage(img);
		}
	}

	public void drawpoint(){
		//Top
		CvPoint t1 = cvPoint(80,120);
		CvPoint t2 = cvPoint(238,125);
		CvPoint t3 = cvPoint(395,130);
		CvPoint t4 = cvPoint(553,135);
		CvPoint t5 = cvPoint(710,138);
		CvPoint t6 = cvPoint(867,145);
		CvPoint t7 = cvPoint(1025,148);
		CvPoint t8 = cvPoint(1180,153);

		cvLine(image, t1, t1, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t2, t2, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t3, t3, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t4, t4, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t5, t5, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t6, t6, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t7, t7, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, t8, t8, CvScalar.RED, 5, CV_AA, 0);
		
		//Right
		CvPoint r1 = cvPoint(1163,778);
		CvPoint r2 = cvPoint(1168,615);
		CvPoint r3 = cvPoint(1170,465);
		CvPoint r4 = cvPoint(1177,308);
		CvPoint r5 = cvPoint(1180,153);
		
		cvLine(image, r1, r1, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, r2, r2, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, r3, r3, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, r4, r4, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, r5, r5, CvScalar.RED, 5, CV_AA, 0);
				
		//Bottom
		CvPoint b1 = cvPoint(68,749);
		CvPoint b2 = cvPoint(222,752);
		CvPoint b3 = cvPoint(382,757);
		CvPoint b4 = cvPoint(535,765);
		CvPoint b5 = cvPoint(692,769);
		CvPoint b6 = cvPoint(855, 772);
		CvPoint b7 = cvPoint(1006,775);
		CvPoint b8 = cvPoint(1163,778);
		
		cvLine(image, b1, b1, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b2, b2, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b3, b3, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b4, b4, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b5, b5, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b6, b6, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b7, b7, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, b8, b8, CvScalar.RED, 5, CV_AA, 0);
		
		//Left
		CvPoint l1 = cvPoint(80,120);
		CvPoint l2 = cvPoint(72,275);
		CvPoint l3 = cvPoint(65,435);
		CvPoint l4 = cvPoint(65,590);
		CvPoint l5 = cvPoint(68,749);
		
		cvLine(image, l1, l1, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, l2, l2, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, l3, l3, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, l4, l4, CvScalar.RED, 5, CV_AA, 0);
		cvLine(image, l5, l5, CvScalar.RED, 5, CV_AA, 0);
		
		showImage(image);
	}

}
