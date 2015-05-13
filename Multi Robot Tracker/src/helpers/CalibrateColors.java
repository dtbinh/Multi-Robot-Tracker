package helpers;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

import server.Video;
import server.utils.CircleMarker;
import server.utils.HSVColor;
import server.utils.PixelOperations;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint3D32f;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_highgui;
import com.googlecode.javacv.cpp.opencv_highgui.CvCapture;

public class CalibrateColors extends Thread {

	private static final int COLOR_THRESHOLD = 0;
	private static final int calibrationTime = 60; //seconds

	JFrame calibrationFrame;
	JFrame colorsRangeFrame;
	private CvCapture capture;
	private static CanvasFrame canvas;
	private IplImage image;
	private Border border;

	private LinkedList<CalibrationThread> calibrationThreads;
	private HashMap<JTextField, Integer[]> colorIds;
	
	private boolean firstTime = true;
	private int threadsDone = 0;
	private int threadsRunning = 0;
	
	public CalibrateColors() {
		calibrationThreads = new LinkedList<CalibrationThread>();
		colorIds = new HashMap<JTextField, Integer[]>();
		border = BorderFactory.createLineBorder(Color.BLACK);
		
		canvas = new CanvasFrame("Camera");
		canvas.setPreferredSize(new Dimension(600, 400));
		canvas.setLocation(620, 0);
	}

	@Override
	public void run() {
		setName("Main Thread");
		// Select capturing camera
		capture = opencv_highgui.cvCreateCameraCapture(0);
		
		opencv_highgui.cvSetCaptureProperty(capture,opencv_highgui.CV_CAP_PROP_FRAME_WIDTH, 1280);
		opencv_highgui.cvSetCaptureProperty(capture,opencv_highgui.CV_CAP_PROP_FRAME_HEIGHT, 960);

		long startingTime = System.currentTimeMillis();
		long endtime = startingTime + calibrationTime * 1000;
		
		while (System.currentTimeMillis() < endtime) {
//			System.out.println("Capturing Image");
			image = opencv_highgui.cvQueryFrame(capture);
			calibration();
		}
		
		for (CalibrationThread t : calibrationThreads) {
			System.out.println( t.getName() + " - " + t.getNumberOfComputations());
			System.out.println("--------");
			t.interrupt();
		}
		
		for (CalibrationThread t : calibrationThreads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("--------");
		
		showResults();
		showCollectedColors();
	}

	private void calibration() {
		threadsDone = 0;
		threadsRunning = 0;
		
//		CvRect roi = cvRect(40, 85, 1160, 730);
//		cvSetImageROI(image, roi);
		IplImage croppedImage = IplImage.create(cvSize(image.width(), image.height()), 8, 3);
		cvCopy(image, croppedImage);
//		cvResetImageROI(image);
		
		showImage(croppedImage);
		
		IplImage imageGray = IplImage.create(cvGetSize(croppedImage), 8, 1);

		cvCvtColor(croppedImage, imageGray, CV_RGB2GRAY);

		CvMemStorage storage = cvCreateMemStorage(0);

		cvThreshold(imageGray, imageGray, 115, 255, CV_THRESH_BINARY);

		cvSmooth(imageGray, imageGray, CV_GAUSSIAN, 3);
		
		cvCanny(imageGray, imageGray, 100, 100, 3);// 100 100 3

		// Find circles
		CvSeq circles = cvHoughCircles(imageGray, // Input image
				storage, // Memory Storage
				CV_HOUGH_GRADIENT, // Detection method
				1, // Inverse ratio
				100, // Minimum distance between the centers of the detected
						// circles
				10, // Higher threshold for canny edge detector
				15, // Threshold at the center detection stage
				15, // min radius
				30 // max radius
		);

		int numberOfCircles = circles.total();
		if(numberOfCircles == 0)
			System.out.println("Not detecting any circle");
		
		if(firstTime == true){
			for (int i = 0; i < numberOfCircles; i++) {
				CalibrationThread t = new CalibrationThread(this);
				t.start();
				calibrationThreads.add(t);
			}
			firstTime = false;
		}		
		
		for (int i = 0; i < numberOfCircles; i++) {
			CvPoint3D32f point3D = new CvPoint3D32f(cvGetSeqElem(circles, i));
			CircleMarker circle = new CircleMarker(point3D.x(), point3D.y(), point3D.z());
			CvPoint3D32f.deallocateReferences();
			
			int nullMarkers = 0;
			CalibrationThread nullMarkerThread = null;
			
			for (CalibrationThread t : calibrationThreads) {
				if(t.getMarker() != null){
					CircleMarker m = t.getMarker();
					double dist = circle.calculateDistance(m);
					if(dist <= Video.markerRadius){
						t.setParameters(croppedImage, circle);
						threadsRunning++;
						nullMarkerThread = null;
						break;
					}
				}else{
					nullMarkers++;
					nullMarkerThread = t;
				}
			}
			
			if(nullMarkers > 0 && nullMarkerThread != null){
				nullMarkerThread.setParameters(croppedImage, circle);
				threadsRunning++;
			}
				
		}
		
		synchronized (this) {
			while (threadsDone != threadsRunning){
				try {
//					System.out.println("Waiting threads to finish ...");
					wait();
//					System.out.println("Threads Done: " + threadsDone + " - Finished: " + threadsRunning);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
//		System.out.println("--------");
		
		cvReleaseMemStorage(circles.storage());
		imageGray.release();
		croppedImage.release();
	}

	private void showResults() {
		calibrationFrame = new JFrame("Calibration Colors");
		JPanel panel = new JPanel(new BorderLayout());
		JPanel centerPanel = new JPanel(new GridLayout(calibrationThreads.size(), 1));
		JPanel rightPanel = new JPanel(new GridLayout(calibrationThreads.size(), 1));

		for (CalibrationThread t : calibrationThreads) {
			
			int minH = validateValue(t.getResult()[0] - COLOR_THRESHOLD);
			int maxH = validateValue(t.getResult()[1] + COLOR_THRESHOLD);

			int minS = t.getResult()[2];
			int maxS = t.getResult()[3];

			int minV = t.getResult()[4];
			int maxV = t.getResult()[5];
			
			Integer[] colorInterval = new Integer[]{minH,maxH,minS,maxS,minV,maxV};
			
			HSVColor minHSVColor = t.getHsvList().get(0);
			HSVColor maxHSVColor = t.getHsvList().get(t.getHsvList().size()-1);
			
			int colorMinH = (int) minHSVColor.getHue() - COLOR_THRESHOLD;
			int colorMaxH = (int) maxHSVColor.getHue() + COLOR_THRESHOLD;

			int colorMinS = (int) minHSVColor.getSaturation();
			int colorMaxS = (int) maxHSVColor.getSaturation();

			int colorMinV = (int) minHSVColor.getBrightness();
			int colorMaxV = (int) maxHSVColor.getBrightness();
			
			JTextArea minColorArea = new JTextArea();
			minColorArea.setBackground(PixelOperations.getHSVColor(colorMinH, colorMinS,colorMinV));
			minColorArea.setBorder(BorderFactory.createCompoundBorder(border,BorderFactory.createEmptyBorder(10, 10, 10, 10)));
			minColorArea.setEditable(false);

			JTextArea maxColorArea = new JTextArea();
			maxColorArea.setBackground(PixelOperations.getHSVColor(colorMaxH, colorMaxS,colorMaxV));
			maxColorArea.setBorder(BorderFactory.createCompoundBorder(border,BorderFactory.createEmptyBorder(10, 10, 10, 10)));
			maxColorArea.setEditable(false);

//			System.out.println("{" + minH + "," + maxH + "," + minS + "," + maxS + "," + minV + "," + maxV + "}");
			JTextField parameters = new JTextField(printArray(colorInterval));
			parameters.setEditable(false);

			JTextField robotIdTextfield = new JTextField(3);
			
			centerPanel.add(minColorArea);
			centerPanel.add(maxColorArea);
			centerPanel.add(parameters);
			rightPanel.add(robotIdTextfield);
			
			colorIds.put(robotIdTextfield, colorInterval);
		}

		JButton confirmButton = new JButton("Confirm");
		
		confirmButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				PrintWriter fileWrite = null;
				
				try {
					fileWrite = new PrintWriter(new File("color_configuration.txt"));
					fileWrite.write("#" + colorIds.keySet().size() + "\n");
					for (JTextField idTextfield : colorIds.keySet()) {
						String line = idTextfield.getText() + " " + printArray(colorIds.get(idTextfield)) + "\n";
						fileWrite.write(line);
					}
										
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} finally {
					if(fileWrite != null)
						fileWrite.close();
					calibrationFrame.dispose();
					colorsRangeFrame.dispose();
				}
			}
		});
		
		panel.add(centerPanel);
		panel.add(rightPanel, BorderLayout.EAST);
		calibrationFrame.getContentPane().add(panel);
		calibrationFrame.getContentPane().add(confirmButton, BorderLayout.SOUTH);
		calibrationFrame.pack();
		calibrationFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		calibrationFrame.setVisible(true);
	}

	private void showCollectedColors() {
		colorsRangeFrame = new JFrame("Collected Colors");
		JPanel panel = new JPanel(new GridLayout(calibrationThreads.size(), 1));
		
		if(calibrationThreads.isEmpty())
			System.out.println("Calibration Threads empty!");
		
		for (CalibrationThread cT : calibrationThreads) {
			JPanel panel2 = new JPanel(new GridLayout(1, 1));
			
			if(cT.getHsvList().isEmpty())
				System.out.println("Nothing on the Hue List");
			
			for (int i = 0; i < cT.getHsvList().size(); i++) {
				JTextField textField = new JTextField(20);
				HSVColor hsv = cT.getHsvList().get(i);
				textField.setBackground(PixelOperations.getHSVColor(hsv.getHue(), hsv.getSaturation(), hsv.getBrightness()));
				panel2.add(textField);
			}
			panel.add(panel2);
		}
		
		colorsRangeFrame.getContentPane().add(panel);
		colorsRangeFrame.pack();
		colorsRangeFrame.setLocationRelativeTo(null);
		colorsRangeFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		colorsRangeFrame.setVisible(true);
	}
	
	public int validateValue(int value) {
		if (value < 0)
			return 0;
		else if (value > 360)
			return 360;
		else
			return value;
	}
	
	private String printArray(Integer[] array){
		return array[0] + "," + array[1] + "," + array[2] + "," + array[3] + "," + array[4] + "," + array[5];
	}

	private void showImage(IplImage img) {
		if (img != null && canvas != null) {
			if (canvas != null)
				canvas.showImage(img);
		}
	}

	public synchronized void incrementThreadsDone(){
		threadsDone++;
//		System.out.println("Threads Done: " + threadsDone + " - Started: " + threadsRunning);
		
		if(threadsDone == threadsRunning){
//			System.out.println("All threads finished");
//			System.out.println("Threads Done: " + threadsDone + " - Finished: " + threadsRunning);
			notifyAll();
		}else if(threadsDone > threadsRunning){
			System.out.println("Threads done can't be bigger then number of threads running");
		}
	}
	
	public static void main(String[] args) {
		try {
			CalibrateColors calibrate = new CalibrateColors();
			calibrate.start();
			calibrate.join();
			canvas.dispose();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
